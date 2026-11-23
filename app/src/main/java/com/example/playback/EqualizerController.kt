package com.example.playback

import android.content.Context
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.example.BuildConfig
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
import kotlin.math.abs
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
    private var baseVolumeProvider: () -> Float = { 1f }

    private var currentAudioSessionId: Int = 0
    private var hwCenterFreqsHz: List<Int> = emptyList()
    private var hwFreqRangesHz: List<Pair<Int, Int>> = emptyList()
    private var hwBandLevelRangeMb: ShortArray = shortArrayOf(-1500, 1500)

    private val _state = MutableStateFlow(createInitialState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private var customBandGains: List<Float> = emptyList()
    @Volatile
    private var isRestoringFromDataStore = true
    private var hasUserMutatedState = false

    init {
        scope.launch {
            try {
                val persisted = dataStore.settingsFlow.first()
                applyPersistedSettings(persisted)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Failed restoring equalizer settings", e)
            } finally {
                synchronized(this@EqualizerController) {
                    isRestoringFromDataStore = false
                }
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

    @Synchronized
    private fun applyPersistedSettings(persisted: com.example.data.PersistedEqualizerSettings) {
        if (hasUserMutatedState) return
        val current = _state.value
        val bandCount = current.bands.size

        customBandGains = if (persisted.customBandLevels.size == bandCount) {
            persisted.customBandLevels
        } else {
            List(bandCount) { 0f }
        }

        val validCustomPresets = persisted.savedCustomPresets.filterValues { it.size == bandCount }

        val restoredGains = when {
            persisted.bandLevels.size == bandCount -> persisted.bandLevels
            validCustomPresets.containsKey(persisted.activePreset) ->
                validCustomPresets[persisted.activePreset] ?: List(bandCount) { 0f }
            else -> calculateGainsForPreset(persisted.activePreset, current.bands)
        }

        val updatedBands = current.bands.mapIndexed { idx, band ->
            val gain = restoredGains.getOrElse(idx) { 0f }.coerceIn(band.minGainDb, band.maxGainDb)
            band.copy(gainDb = gain)
        }

        val resolvedActivePreset = if (
            persisted.activePreset in BUILT_IN_PRESETS.keys ||
            persisted.activePreset == PRESET_CUSTOM ||
            validCustomPresets.containsKey(persisted.activePreset)
        ) {
            persisted.activePreset
        } else {
            PRESET_FLAT
        }

        val allPresets = (BUILT_IN_PRESETS.keys.toList() + listOf(PRESET_CUSTOM) + validCustomPresets.keys.toList()).distinct()

        _state.value = current.copy(
            isEnabled = persisted.isEnabled,
            activePreset = resolvedActivePreset,
            bands = updatedBands,
            presets = allPresets,
            customPresets = validCustomPresets,
            isBassBoostEnabled = persisted.isBassBoostEnabled,
            bassBoostStrength = persisted.bassBoostStrength.coerceIn(0, 1000),
            isVirtualizerEnabled = persisted.isVirtualizerEnabled,
            virtualizerStrength = persisted.virtualizerStrength.coerceIn(0, 1000),
            preampGainDb = persisted.preampGainDb.coerceIn(-12f, 12f)
        )

        applyAllEffectsToAudioEngine()
    }

    @Synchronized
    fun ensureBound(
        audioSessionId: Int,
        player: ExoPlayer,
        baseVolumeProvider: (() -> Float)? = null
    ) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return
        val currentlyBoundPlayer = playerRef?.get()
        if (currentAudioSessionId != audioSessionId || equalizer == null || currentlyBoundPlayer !== player) {
            bindAudioSession(audioSessionId, player, baseVolumeProvider)
        }
    }

    @Synchronized
    fun bindAudioSession(
        audioSessionId: Int,
        player: ExoPlayer? = null,
        baseVolumeProvider: (() -> Float)? = null
    ) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) return
        if (player != null) {
            playerRef = WeakReference(player)
            this.baseVolumeProvider = baseVolumeProvider ?: { 1f }
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
            val eq = Equalizer(0, audioSessionId)
            val numBands = eq.numberOfBands.toInt()
            if (numBands > 0) {
                eqAvailable = true
                equalizer = eq
                val range = eq.bandLevelRange
                hwBandLevelRangeMb = if (range.size >= 2) range else shortArrayOf(-1500, 1500)

                val centerList = mutableListOf<Int>()
                val rangeList = mutableListOf<Pair<Int, Int>>()
                for (i in 0 until numBands) {
                    val centerHz = (eq.getCenterFreq(i.toShort()) / 1000).coerceAtLeast(1)
                    centerList.add(centerHz)
                    val freqRange = try {
                        val r = eq.getBandFreqRange(i.toShort())
                        if (r.size >= 2 && r[1] >= r[0]) {
                            (r[0] / 1000) to (r[1] / 1000)
                        } else {
                            0 to 0
                        }
                    } catch (_: Exception) {
                        0 to 0
                    }
                    rangeList.add(freqRange)
                }
                hwCenterFreqsHz = centerList
                hwFreqRangesHz = rangeList

                if (BuildConfig.DEBUG) {
                    Log.d(
                        TAG,
                        "bindAudioSession sessionId=$audioSessionId hwBands=$numBands " +
                            "rangeMb=[${hwBandLevelRangeMb[0]}, ${hwBandLevelRangeMb[1]}] " +
                            "hwCentersHz=$hwCenterFreqsHz hwRangesHz=$hwFreqRangesHz"
                    )
                }
            } else {
                eq.release()
                equalizer = null
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Equalizer reported 0 hardware bands on sessionId=$audioSessionId")
                }
            }
        } catch (e: Throwable) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Equalizer init failed for sessionId=$audioSessionId", e)
            equalizer = null
            eqAvailable = false
        }

        try {
            val bb = BassBoost(0, audioSessionId)
            if (bb.strengthSupported) {
                bassBoost = bb
                bassSupported = true
            } else {
                bb.release()
                bassBoost = null
                bassSupported = false
            }
        } catch (e: Throwable) {
            if (BuildConfig.DEBUG) Log.d(TAG, "BassBoost init failed for sessionId=$audioSessionId", e)
            bassBoost = null
            bassSupported = false
        }

        try {
            val v = Virtualizer(0, audioSessionId)
            if (v.strengthSupported) {
                virtualizer = v
                virtSupported = true
            } else {
                v.release()
                virtualizer = null
                virtSupported = false
            }
        } catch (e: Throwable) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Virtualizer init failed for sessionId=$audioSessionId", e)
            virtualizer = null
            virtSupported = false
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = false
            }
            preampSupported = true
        } catch (e: Throwable) {
            if (BuildConfig.DEBUG) Log.d(TAG, "LoudnessEnhancer init failed for sessionId=$audioSessionId", e)
            loudnessEnhancer = null
            preampSupported = false
        }

        _state.value = _state.value.copy(
            isAvailable = eqAvailable,
            isBassBoostSupported = bassSupported,
            isVirtualizerSupported = virtSupported,
            isPreampSupported = preampSupported
        )

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "Effects support sessionId=$audioSessionId eq=$eqAvailable bass=$bassSupported virt=$virtSupported preamp=$preampSupported"
            )
        }

        applyAllEffectsToAudioEngine()
    }

    @Synchronized
    fun detachPlayer(player: ExoPlayer) {
        if (playerRef?.get() === player) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Detaching player from EqualizerController (sessionId=$currentAudioSessionId)")
            }
            releaseAudioEffects()
            currentAudioSessionId = 0
            playerRef = null
            baseVolumeProvider = { 1f }
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        hasUserMutatedState = true
        _state.value = _state.value.copy(isEnabled = enabled)
        applyAllEffectsToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun setBandGain(bandIndex: Int, gainDb: Float) {
        val current = _state.value
        if (bandIndex !in current.bands.indices) return

        hasUserMutatedState = true
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

        applyAllBandsToAudioEngine(updatedBands)
        persistAsync()
    }

    @Synchronized
    fun setPreset(presetName: String) {
        hasUserMutatedState = true
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
        hasUserMutatedState = true
        _state.value = _state.value.copy(isBassBoostEnabled = enabled)
        applyBassBoostToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun setBassBoostStrength(strength: Int) {
        hasUserMutatedState = true
        val clamped = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(bassBoostStrength = clamped)
        applyBassBoostToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun setVirtualizerEnabled(enabled: Boolean) {
        hasUserMutatedState = true
        _state.value = _state.value.copy(isVirtualizerEnabled = enabled)
        applyVirtualizerToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun setVirtualizerStrength(strength: Int) {
        hasUserMutatedState = true
        val clamped = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(virtualizerStrength = clamped)
        applyVirtualizerToAudioEngine()
        persistAsync()
    }

    @Synchronized
    fun setPreampGain(gainDb: Float) {
        hasUserMutatedState = true
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
    fun getPreampVolumeMultiplier(): Float {
        val s = _state.value
        val effectiveGain = if (s.isEnabled) s.preampGainDb.coerceIn(-12f, 15f) else 0f
        if (effectiveGain >= 0f) return 1.0f
        return (10.0.pow(effectiveGain / 20.0)).toFloat().coerceIn(0f, 1f)
    }

    @Synchronized
    fun saveCustomPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        hasUserMutatedState = true
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
        hasUserMutatedState = true
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
        baseVolumeProvider = { 1f }
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
        hwCenterFreqsHz = emptyList()
        hwFreqRangesHz = emptyList()
    }

    private fun applyAllEffectsToAudioEngine() {
        val s = _state.value
        val isMasterEnabled = s.isEnabled

        // Equalizer bands
        equalizer?.let { eq ->
            try {
                val status = eq.setEnabled(isMasterEnabled && s.isAvailable)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "eq.setEnabled(${isMasterEnabled && s.isAvailable}) status=$status actual=${eq.enabled}")
                }
                if (isMasterEnabled && s.isAvailable) {
                    applyAllBandsToAudioEngine(s.bands)
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "applyAllEffects eq error", e)
            }
        }

        // Bass Boost
        applyBassBoostToAudioEngine()

        // Virtualizer
        applyVirtualizerToAudioEngine()

        // Preamp
        applyPreampToAudioEngine()
    }

    private fun applyAllBandsToAudioEngine(bands: List<EqualizerBandInfo>) {
        val eq = equalizer ?: return
        val s = _state.value
        if (!s.isEnabled || !s.isAvailable) return
        try {
            if (!eq.enabled) {
                val enableStatus = eq.setEnabled(true)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "eq.setEnabled(true) status=$enableStatus actual=${eq.enabled}")
                }
            }

            val numHwBands = eq.numberOfBands.toInt()
            if (numHwBands <= 0) return

            val centers = if (hwCenterFreqsHz.size == numHwBands) {
                hwCenterFreqsHz
            } else {
                (0 until numHwBands).map { (eq.getCenterFreq(it.toShort()) / 1000).coerceAtLeast(1) }
            }
            val ranges = if (hwFreqRangesHz.size == numHwBands) hwFreqRangesHz else null
            val hwGainsDb = mapUiBandsToHardwareGains(bands, centers, ranges)

            val minMb = hwBandLevelRangeMb.getOrElse(0) { (-1500).toShort() }.toInt()
            val maxMb = hwBandLevelRangeMb.getOrElse(1) { 1500.toShort() }.toInt()

            for (hwIndex in 0 until numHwBands) {
                val targetDb = hwGainsDb.getOrElse(hwIndex) { 0f }
                val millibels = (targetDb * 100f).roundToInt()
                val clamped = millibels.coerceIn(minMb, maxMb).toShort()
                eq.setBandLevel(hwIndex.toShort(), clamped)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "eq hwBand=$hwIndex (${centers[hwIndex]}Hz) levelMb=$clamped")
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "applyAllBandsToAudioEngine error", e)
        }
    }

    private fun applyBassBoostToAudioEngine() {
        val bb = bassBoost ?: return
        val s = _state.value
        val shouldEnable = s.isEnabled && s.isBassBoostEnabled && s.isBassBoostSupported
        try {
            val status = bb.setEnabled(shouldEnable)
            if (shouldEnable && bb.strengthSupported) {
                val strength = s.bassBoostStrength.coerceIn(0, 1000).toShort()
                bb.setStrength(strength)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "bassBoost enabled=$shouldEnable status=$status strength=$strength rounded=${bb.roundedStrength}")
                }
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "bassBoost enabled=$shouldEnable status=$status")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "applyBassBoostToAudioEngine error", e)
        }
    }

    private fun applyVirtualizerToAudioEngine() {
        val v = virtualizer ?: return
        val s = _state.value
        val shouldEnable = s.isEnabled && s.isVirtualizerEnabled && s.isVirtualizerSupported
        try {
            val status = v.setEnabled(shouldEnable)
            if (shouldEnable && v.strengthSupported) {
                val strength = s.virtualizerStrength.coerceIn(0, 1000).toShort()
                v.setStrength(strength)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "virtualizer enabled=$shouldEnable status=$status strength=$strength rounded=${v.roundedStrength}")
                }
            } else if (BuildConfig.DEBUG) {
                Log.d(TAG, "virtualizer enabled=$shouldEnable status=$status")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "applyVirtualizerToAudioEngine error", e)
        }
    }

    private fun applyPreampToAudioEngine() {
        val s = _state.value
        val effectiveGain = (if (s.isEnabled) s.preampGainDb else 0f).coerceIn(-12f, 15f)
        val player = playerRef?.get()
        val baseVol = try {
            baseVolumeProvider().coerceIn(0f, 1f)
        } catch (_: Exception) {
            1f
        }

        try {
            if (effectiveGain > 0f) {
                val targetMb = (effectiveGain * 100f).roundToInt().coerceIn(0, 1500)
                loudnessEnhancer?.apply {
                    setTargetGain(targetMb)
                    val status = setEnabled(true)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "loudnessEnhancer enabled=true status=$status targetMb=$targetMb")
                    }
                }
                if (player != null) {
                    applyPlayerVolume(player, baseVol)
                }
            } else {
                loudnessEnhancer?.apply {
                    setTargetGain(0)
                    val status = setEnabled(false)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "loudnessEnhancer enabled=false status=$status")
                    }
                }
                val factor = if (effectiveGain == 0f) {
                    1.0f
                } else {
                    (10.0.pow(effectiveGain / 20.0)).toFloat().coerceIn(0f, 1f)
                }
                if (player != null) {
                    applyPlayerVolume(player, baseVol * factor)
                }
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "preamp volume factor=$factor baseVol=$baseVol effectiveGainDb=$effectiveGain")
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.d(TAG, "applyPreampToAudioEngine error", e)
        }
    }

    private fun applyPlayerVolume(targetPlayer: ExoPlayer, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        val looper = targetPlayer.applicationLooper
        if (Looper.myLooper() == looper) {
            try {
                targetPlayer.volume = clamped
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Failed setting player.volume on looper", e)
            }
        } else {
            Handler(looper).post {
                try {
                    if (playerRef?.get() === targetPlayer) {
                        targetPlayer.volume = clamped
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Failed setting player.volume via Handler", e)
                }
            }
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
        private const val TAG = "EqDebug"
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

        internal fun mapUiBandsToHardwareGains(
            uiBands: List<EqualizerBandInfo>,
            hwCenterFreqsHz: List<Int>,
            hwFreqRangesHz: List<Pair<Int, Int>>? = null
        ): List<Float> {
            val numHw = hwCenterFreqsHz.size
            if (numHw <= 0 || uiBands.isEmpty()) return emptyList()
            if (numHw == uiBands.size) {
                return uiBands.map { it.gainDb }
            }

            val buckets = Array(numHw) { mutableListOf<Float>() }
            for (uiBand in uiBands) {
                val uiFreq = uiBand.centerFreqHz.coerceAtLeast(1)
                val uiLog = log10(uiFreq.toDouble())

                // Find hardware bands whose declared range encloses uiFreq
                val matchingHwIndices = if (hwFreqRangesHz != null && hwFreqRangesHz.size == numHw) {
                    (0 until numHw).filter { hwIdx ->
                        val (minHz, maxHz) = hwFreqRangesHz[hwIdx]
                        maxHz > minHz && uiFreq in minHz..maxHz
                    }
                } else {
                    emptyList()
                }

                val targetHwIdx = if (matchingHwIndices.isNotEmpty()) {
                    matchingHwIndices.minBy { hwIdx ->
                        abs(uiLog - log10(hwCenterFreqsHz[hwIdx].coerceAtLeast(1).toDouble()))
                    }
                } else {
                    (0 until numHw).minBy { hwIdx ->
                        abs(uiLog - log10(hwCenterFreqsHz[hwIdx].coerceAtLeast(1).toDouble()))
                    }
                }
                buckets[targetHwIdx].add(uiBand.gainDb)
            }

            val uiFreqs = uiBands.map { it.centerFreqHz.coerceAtLeast(1) }
            val uiGains = uiBands.map { it.gainDb }
            return (0 until numHw).map { hwIdx ->
                val bucket = buckets[hwIdx]
                if (bucket.isNotEmpty()) {
                    bucket.average().toFloat()
                } else {
                    interpolateDb(hwCenterFreqsHz[hwIdx], uiFreqs, uiGains)
                }
            }
        }

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

