package com.example.playback

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val presets: List<String> = emptyList(),
    val currentPreset: Int = 0,
    val bassBoostStrength: Int = 0
)

class EqualizerController {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun bindAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        release()
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = _state.value.isEnabled
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = _state.value.isEnabled
                if (strengthSupported) {
                    setStrength(_state.value.bassBoostStrength.toShort())
                }
            }

            val numPresets = equalizer?.numberOfPresets?.toInt() ?: 0
            val presetList = mutableListOf<String>()
            for (i in 0 until numPresets) {
                presetList.add(equalizer?.getPresetName(i.toShort()) ?: "Preset $i")
            }

            _state.value = _state.value.copy(
                isAvailable = true,
                presets = presetList,
                currentPreset = equalizer?.currentPreset?.toInt() ?: 0
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _state.value = _state.value.copy(isAvailable = false)
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            _state.value = _state.value.copy(isEnabled = enabled)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setPreset(presetIndex: Int) {
        try {
            if (presetIndex in 0 until (_state.value.presets.size)) {
                equalizer?.usePreset(presetIndex.toShort())
                _state.value = _state.value.copy(currentPreset = presetIndex)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setBassBoost(strength: Int) {
        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(strength.coerceIn(0, 1000).toShort())
                    _state.value = _state.value.copy(bassBoostStrength = strength)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            equalizer = null
            bassBoost = null
        }
    }
}
