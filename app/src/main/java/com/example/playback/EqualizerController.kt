package com.example.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.EqualizerDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

const val PRESET_FLAT = "Flat"
const val PRESET_ROCK = "Rock"
const val PRESET_POP = "Pop"
const val PRESET_JAZZ = "Jazz"
const val PRESET_CLASSICAL = "Classical"
const val PRESET_DANCE = "Dance"
const val PRESET_HIP_HOP = "Hip-Hop"
const val PRESET_VOCAL = "Vocal"
const val PRESET_BASS_BOOST = "Bass Boost"
const val PRESET_TREBLE_BOOST = "Treble Boost"
const val PRESET_CUSTOM = "Custom"

data class EqualizerBandInfo(
    val index: Int,
    val centerFreqHz: Int,
    val formattedFreq: String,
    val gainDb: Float,
    val minGainDb: Float = -12f,
    val maxGainDb: Float = 12f
)

data class EqualizerState(
    val isAvailable: Boolean = true,
    val isEnabled: Boolean = true,
    val bands: List<EqualizerBandInfo> = emptyList(),
    val minGainDb: Float = -12f,
    val maxGainDb: Float = 12f,
    val presets: List<String> = emptyList(),
    val activePreset: String = PRESET_FLAT,
    val customPresets: Map<String, List<Float>> = emptyMap(),
    val isBassBoostSupported: Boolean = true,
    val isBassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0, // 0..1000
    val isVirtualizerSupported: Boolean = true,
    val isVirtualizerEnabled: Boolean = false,
    val virtualizerStrength: Int = 0, // 0..1000
    val isPreampSupported: Boolean = true,
    val preampGainDb: Float = 0f // -12f..12f
)

class EqualizerController private constructor(private val context: Context) {

    private val dataStore = EqualizerDataStore(context)
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var playerRef: WeakReference<ExoPlayer>? = null

    private var currentAudioSessionId: Int = 0

    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private var customBandGains: List<Float> = emptyList()
    private var isRestoringFromDataStore = true

    init {
        scope.launch {
            try {
                val persisted = dataStore.settingsFlow.first()
                applyPersistedSettings(persisted)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRestoringFromDataStore = false
            }
        }
    }

    private fun createInitialState(): EqualizerState {
        val defaultBands = ISO_FREQUENCIES_HZ.mapIndexed { index, freq ->
            EqualizerBandInfo(
                index = index,
                centerFreqHz = freq,
                formattedFreq = formatFrequency(freq),
                gainDb = 0f,
                minGainDb = -12f,
                maxGainDb = 12f
            )
        }
        val allPresets = BUILT_IN_PRESETS.keys.toList() + listOf(PRESET_CUSTOM)
        return EqualizerState(
            isAvailable = true,
            isEnabled = true,
            bands = defaultBands,
            minGainDb = -12f,
            maxGainDb = 12f,
            presets = allPresets,
            activePreset = PRESET_FLAT,
            customPresets = emptyMap(),
            isBassBoostSupported = true,
            isBassBoostEnabled = false,
            bassBoostStrength = 0,
            isVirtualizerSupported = true,
            isVirtualizerEnabled = false,
            virtualizerStrength = 0,
            isPreampSupported = true,
            preampGainDb = 0f
        )
    }

    private fun applyPersistedSettings(persisted: com.example.data.PersistedEqualizerSettings) {
        val current = _state.value
        val bandCount = current.bands.size
        customBandGains = if (persisted.customBandLevels.size == bandCount) {
            persisted.customBandLevels
        } else {
            List(bandCount) { 0f }
        }

        val restoredGains = if (persisted.bandLevels.size == bandCount) {
            persisted.bandLevels
        } else {
            calculateGainsForPreset(persisted.activePreset, current.bands)
        }

        val updatedBands = current.bands.mapIndexed { idx, band ->
            val gain = restoredGains.getOrElse(idx) { 0f }.coerceIn(band.minGainDb, band.maxGainDb)
            band.copy(gainDb = gain)
        }

        val allPresets = BUILT_IN_PRESETS.keys.toList() + listOf(PRESET_CUSTOM) + persisted.savedCustomPresets.keys.toList()

        _state.value = current.copy(
            isEnabled = persisted.isEnabled,
            activePreset = persisted.activePreset,
            bands = updatedBands,
            presets = allPresets,
            customPresets = persisted.savedCustomPresets,
            isBassBoostEnabled = persisted.isBassBoostEnabled,
            bassBoostStrength = persisted.bassBoostStrength,
            isVirtualizerEnabled = persisted.isVirtualizerEnabled,
            virtualizerStrength = persisted.virtualizerStrength,
            preampGainDb = persisted.preampGainDb
        )

        applyAllEffectsToAudioEngine()
    }

    @Synchronized
    fun bindAudioSession(audioSessionId: Int, player: ExoPlayer? = null) {
        if (audioSessionId <= 0) return
        if (player != null) {
            playerRef = WeakReference(player)
        }
        if (currentAudioSessionId == audioSessionId && equalizer != null) {
            applyAllEffectsToAudioEngine()
            return
        }

        releaseAudioEffects()
        currentAudioSessionId = audioSessionId

        var eqAvailable = false
        var bassSupported = false
        var virtSupported = false
        var preampSupported = false

        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                val numBands = numberOfBands.toInt()
                if (numBands > 0) {
                    eqAvailable = true
                    val range = bandLevelRange
                    val minDb = range[0] / 100f
                    val maxDb = range[1] / 100f

                    val actualBands = mutableListOf<EqualizerBandInfo>()
                    for (i in 0 until numBands) {
                        val centerFreqHz = getCenterFreq(i.toShort()) / 1000
                        val currentLevelDb = getBandLevel(i.toShort()) / 100f
                        actualBands.add(
                            EqualizerBandInfo(
                                index = i,
                                centerFreqHz = centerFreqHz,
                                formattedFreq = formatFrequency(centerFreqHz),
                                gainDb = currentLevelDb,
                                minGainDb = minDb,
                                maxGainDb = maxDb
                            )
                        )
                    }

                    // Preserve current state gains if possible, or recalculate for current preset
                    val stateBands = if (_state.value.bands.size == actualBands.size) {
                        actualBands.mapIndexed { idx, actual ->
                            val currentGain = _state.value.bands[idx].gainDb
                            actual.copy(gainDb = currentGain.coerceIn(actual.minGainDb, actual.maxGainDb))
                        }
                    } else {
                        val presetGains = calculateGainsForPreset(_state.value.activePreset, actualBands)
                        actualBands.mapIndexed { idx, actual ->
                            actual.copy(gainDb = presetGains[idx].coerceIn(actual.minGainDb, actual.maxGainDb))
                        }
                    }

                    _state.value = _state.value.copy(
                        isAvailable = true,
                        bands = stateBands,
                        minGainDb = minDb,
                        maxGainDb = maxDb
                    )
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            equalizer = null
        }

        try {
            bassBoost = BassBoost(0, audioSessionId).apply {
                bassSupported = strengthSupported
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            bassBoost = null
        }

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                virtSupported = strengthSupported
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            virtualizer = null
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = true
            }
            preampSupported = true
        } catch (e: Throwable) {
            e.printStackTrace()
            loudnessEnhancer = null
            preampSupported = false
        }

        _state.value = _state.value.copy(
            isAvailable = eqAvailable,
            isBassBoostSupported = bassSupported,
            isVirtualizerSupported = virtSupported,
            isPreampSupported = preampSupported
        )

        applyAllEffectsToAudioEngine()
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isEnabled = enabled)
        applyAllEffectsToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun setBandGain(bandIndex: Int, gainDb: Float) {
        val current = _state.value
        if (bandIndex !in current.bands.indices) return

        val band = current.bands[bandIndex]
        val clampedGain = gainDb.coerceIn(band.minGainDb, band.maxGainDb)
        val updatedBands = current.bands.toMutableList()
        updatedBands[bandIndex] = band.copy(gainDb = clampedGain)

        // User manually moved a slider -> automatically switch active preset to Custom
        val updatedGains = updatedBands.map { it.gainDb }
        customBandGains = updatedGains

        _state.value = current.copy(
            activePreset = PRESET_CUSTOM,
            bands = updatedBands
        )

        applyBandGainToAudioEngine(bandIndex, clampedGain)
        persistAsync()
    }

    @Synchronized
    fun setPreset(presetName: String) {
        val current = _state.value
        val targetGains: List<Float> = when {
            presetName == PRESET_CUSTOM -> {
                if (customBandGains.size == current.bands.size) {
                    customBandGains
                } else {
                    current.bands.map { it.gainDb }
                }
            }
            current.customPresets.containsKey(presetName) -> {
                current.customPresets[presetName] ?: calculateGainsForPreset(PRESET_FLAT, current.bands)
            }
            else -> {
                calculateGainsForPreset(presetName, current.bands)
            }
        }

        val updatedBands = current.bands.mapIndexed { idx, band ->
            val gain = targetGains.getOrElse(idx) { 0f }.coerceIn(band.minGainDb, band.maxGainDb)
            band.copy(gainDb = gain)
        }

        _state.value = current.copy(
            activePreset = presetName,
            bands = updatedBands
        )

        applyAllBandsToAudioEngine(updatedBands)
        persistAsync()
    }

    @Synchronized
    fun resetBands() {
        setPreset(PRESET_FLAT)
    }

    @Synchronized
    fun setBassBoostEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isBassBoostEnabled = enabled)
        applyBassBoostToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(bassBoostStrength = clamped)
        applyBassBoostToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun setVirtualizerEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isVirtualizerEnabled = enabled)
        applyVirtualizerToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun setVirtualizerStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(virtualizerStrength = clamped)
        applyVirtualizerToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun setPreampGain(gainDb: Float) {
        val clamped = gainDb.coerceIn(-12f, 12f)
        _state.value = _state.value.copy(preampGainDb = clamped)
        applyPreampToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun resetPreamp() {
        setPreampGain(0f)
    }

    @Synchronized
    fun saveCustomPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = _state.value
        val levels = current.bands.map { it.gainDb }
        val updatedMap = current.customPresets.toMutableMap()
        updatedMap[trimmed] = levels

        val allPresets = (BUILT_IN_PRESETS.keys.toList() + listOf(PRESET_CUSTOM) + updatedMap.keys.toList()).distinct()

        _state.value = current.copy(
            activePreset = trimmed,
            presets = allPresets,
            customPresets = updatedMap
        )

        scope.launch {
            dataStore.saveNamedCustomPreset(trimmed, levels)
            dataStore.setActivePreset(trimmed)
        }
    }

    @Synchronized
    fun deleteCustomPreset(name: String) {
        val current = _state.value
        if (!current.customPresets.containsKey(name)) return
        val updatedMap = current.customPresets.toMutableMap()
        updatedMap.remove(name)

        val allPresets = (BUILT_IN_PRESETS.keys.toList() + listOf(PRESET_CUSTOM) + updatedMap.keys.toList()).distinct()
        val newActive = if (current.activePreset == name) PRESET_FLAT else current.activePreset

        _state.value = current.copy(
            activePreset = newActive,
            presets = allPresets,
            customPresets = updatedMap
        )

        if (newActive == PRESET_FLAT) {
            resetBands()
        }

        scope.launch {
            dataStore.deleteNamedCustomPreset(name)
            dataStore.setActivePreset(newActive)
        }
    }

    @Synchronized
    fun release() {
        releaseAudioEffects()
        currentAudioSessionId = 0
        playerRef = null
    }

    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        try {
            bassBoost?.release()
        } catch (_: Exception) {}
        try {
            virtualizer?.release()
        } catch (_: Exception) {}
        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }

    private fun applyAllEffectsToAudioEngine() {
        val s = _state.value
        val isMasterEnabled = s.isEnabled

        // Equalizer bands
        try {
            equalizer?.enabled = isMasterEnabled
            if (isMasterEnabled) {
                applyAllBandsToAudioEngine(s.bands)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Bass Boost
        applyBassBoostToAudioEngine()

        // Virtualizer
        applyVirtualizerToAudioEngine()

        // Preamp
        applyPreampToAudioEngine()
    }

    private fun applyBandGainToAudioEngine(bandIndex: Int, gainDb: Float) {
        val eq = equalizer ?: return
        val s = _state.value
        if (!s.isEnabled) return
        try {
            val millibels = (gainDb * 100f).roundToInt().toShort()
            val range = eq.bandLevelRange
            val clamped = millibels.coerceIn(range[0], range[1])
            eq.setBandLevel(bandIndex.toShort(), clamped)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyAllBandsToAudioEngine(bands: List<EqualizerBandInfo>) {
        val eq = equalizer ?: return
        val s = _state.value
        if (!s.isEnabled) return
        try {
            val range = eq.bandLevelRange
            for (band in bands) {
                if (band.index < eq.numberOfBands) {
                    val millibels = (band.gainDb * 100f).roundToInt().toShort()
                    val clamped = millibels.coerceIn(range[0], range[1])
                    eq.setBandLevel(band.index.toShort(), clamped)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyBassBoostToAudioEngine() {
        val bb = bassBoost ?: return
        val s = _state.value
        val shouldEnable = s.isEnabled && s.isBassBoostEnabled
        try {
            bb.enabled = shouldEnable
            if (shouldEnable && bb.strengthSupported) {
                bb.setStrength(s.bassBoostStrength.toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyVirtualizerToAudioEngine() {
        val v = virtualizer ?: return
        val s = _state.value
        val shouldEnable = s.isEnabled && s.isVirtualizerEnabled
        try {
            v.enabled = shouldEnable
            if (shouldEnable && v.strengthSupported) {
                v.setStrength(s.virtualizerStrength.toShort())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyPreampToAudioEngine() {
        val s = _state.value
        val effectiveGain = if (s.isEnabled) s.preampGainDb else 0f
        val player = playerRef?.get()

        try {
            if (effectiveGain >= 0f) {
                loudnessEnhancer?.apply {
                    enabled = effectiveGain > 0f
                    setTargetGain((effectiveGain * 100f).roundToInt())
                }
                player?.volume = 1.0f
            } else {
                loudnessEnhancer?.apply {
                    setTargetGain(0)
                    enabled = false
                }
                val factor = (10.0.pow(effectiveGain / 20.0)).toFloat()
                player?.volume = factor.coerceIn(0f, 1f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistAsync() {
        if (isRestoringFromDataStore) return
        val s = _state.value
        scope.launch {
            dataStore.saveSettings(
                isEnabled = s.isEnabled,
                activePreset = s.activePreset,
                bandLevels = s.bands.map { it.gainDb },
                customBandLevels = customBandGains,
                savedCustomPresets = s.customPresets,
                isBassBoostEnabled = s.isBassBoostEnabled,
                bassBoostStrength = s.bassBoostStrength,
                isVirtualizerEnabled = s.isVirtualizerEnabled,
                virtualizerStrength = s.virtualizerStrength,
                preampGainDb = s.preampGainDb
            )
        }
    }

    companion object {
        val ISO_FREQUENCIES_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        private val BUILT_IN_PRESETS: Map<String, List<Float>> = mapOf(
            PRESET_FLAT to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            PRESET_ROCK to listOf(4.5f, 3.5f, 2.0f, 0.5f, -1.0f, -0.5f, 1.5f, 3.0f, 4.0f, 4.5f),
            PRESET_POP to listOf(-1.5f, 1.0f, 3.0f, 3.5f, 2.0f, -0.5f, -1.0f, 1.0f, 2.5f, 3.0f),
            PRESET_JAZZ to listOf(3.5f, 2.5f, 1.0f, 1.5f, -1.0f, -1.0f, 0.5f, 1.5f, 2.5f, 3.5f),
            PRESET_CLASSICAL to listOf(4.0f, 3.0f, 2.0f, 1.0f, -1.0f, -1.0f, 0.0f, 2.0f, 3.0f, 3.5f),
            PRESET_DANCE to listOf(5.5f, 4.0f, 2.5f, 0.0f, 0.0f, 2.0f, 3.5f, 3.0f, 2.5f, 1.0f),
            PRESET_HIP_HOP to listOf(5.0f, 4.0f, 2.0f, 2.5f, -1.0f, -1.0f, 1.5f, -0.5f, 2.0f, 3.0f),
            PRESET_VOCAL to listOf(-2.0f, -2.5f, -1.0f, 2.0f, 4.0f, 3.5f, 2.0f, 0.5f, -1.0f, -2.0f),
            PRESET_BASS_BOOST to listOf(6.0f, 5.0f, 3.5f, 1.5f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
            PRESET_TREBLE_BOOST to listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 2.5f, 4.5f, 6.0f, 7.0f)
        )

        fun calculateGainsForPreset(presetName: String, bands: List<EqualizerBandInfo>): List<Float> {
            val isoCurve = BUILT_IN_PRESETS[presetName] ?: BUILT_IN_PRESETS[PRESET_FLAT]!!
            if (bands.size == ISO_FREQUENCIES_HZ.size) {
                return isoCurve
            }
            // Interpolate ISO 10-band curve across band center frequencies
            return bands.map { band ->
                interpolateDb(band.centerFreqHz, ISO_FREQUENCIES_HZ, isoCurve)
            }
        }

        private fun interpolateDb(freqHz: Int, freqs: List<Int>, gains: List<Float>): Float {
            if (freqHz <= freqs.first()) return gains.first()
            if (freqHz >= freqs.last()) return gains.last()

            val logFreq = log10(freqHz.toDouble())
            for (i in 0 until freqs.size - 1) {
                val f1 = freqs[i]
                val f2 = freqs[i + 1]
                if (freqHz in f1..f2) {
                    val log1 = log10(f1.toDouble())
                    val log2 = log10(f2.toDouble())
                    val frac = ((logFreq - log1) / (log2 - log1)).toFloat()
                    val g1 = gains[i]
                    val g2 = gains[i + 1]
                    return g1 + frac * (g2 - g1)
                }
            }
            return 0f
        }

        fun formatFrequency(freqHz: Int): String {
            return if (freqHz >= 1000) {
                val kHz = freqHz / 1000.0
                if (kHz % 1.0 == 0.0) {
                    "${kHz.toInt()}k"
                } else {
                    String.format(Locale.US, "%.1fk", kHz)
                }
            } else {
                "${freqHz}Hz"
            }
        }

        @Volatile
        private var instance: EqualizerController? = null

        fun getInstance(context: Context): EqualizerController {
            return instance ?: synchronized(this) {
                instance ?: EqualizerController(context.applicationContext).also { instance = it }
            }
        }
    }
}
