package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

val Context.equalizerDataStore by preferencesDataStore(name = "lilith_equalizer")

data class PersistedEqualizerSettings(
    val isEnabled: Boolean,
    val activePreset: String,
    val bandLevels: List<Float>,
    val customBandLevels: List<Float>,
    val savedCustomPresets: Map<String, List<Float>>,
    val isBassBoostEnabled: Boolean,
    val bassBoostStrength: Int,
    val isVirtualizerEnabled: Boolean,
    val virtualizerStrength: Int,
    val preampGainDb: Float
)

class EqualizerDataStore(private val context: Context) {

    private object PreferencesKeys {
        val IS_ENABLED = booleanPreferencesKey("eq_is_enabled")
        val ACTIVE_PRESET = stringPreferencesKey("eq_active_preset")
        val BAND_LEVELS = stringPreferencesKey("eq_band_levels")
        val CUSTOM_BAND_LEVELS = stringPreferencesKey("eq_custom_band_levels")
        val SAVED_CUSTOM_PRESETS = stringPreferencesKey("eq_saved_custom_presets")
        val BASS_BOOST_ENABLED = booleanPreferencesKey("eq_bass_boost_enabled")
        val BASS_BOOST_STRENGTH = intPreferencesKey("eq_bass_boost_strength")
        val VIRTUALIZER_ENABLED = booleanPreferencesKey("eq_virtualizer_enabled")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("eq_virtualizer_strength")
        val PREAMP_GAIN_DB = floatPreferencesKey("eq_preamp_gain_db")
    }

    val settingsFlow: Flow<PersistedEqualizerSettings> = context.equalizerDataStore.data.map { prefs ->
        val isEnabled = prefs[PreferencesKeys.IS_ENABLED] ?: true
        val activePreset = prefs[PreferencesKeys.ACTIVE_PRESET] ?: "Flat"
        val bandLevelsStr = prefs[PreferencesKeys.BAND_LEVELS] ?: ""
        val customBandLevelsStr = prefs[PreferencesKeys.CUSTOM_BAND_LEVELS] ?: ""
        val savedPresetsStr = prefs[PreferencesKeys.SAVED_CUSTOM_PRESETS] ?: "{}"
        val isBassBoostEnabled = prefs[PreferencesKeys.BASS_BOOST_ENABLED] ?: false
        val bassBoostStrength = prefs[PreferencesKeys.BASS_BOOST_STRENGTH] ?: 0
        val isVirtualizerEnabled = prefs[PreferencesKeys.VIRTUALIZER_ENABLED] ?: false
        val virtualizerStrength = prefs[PreferencesKeys.VIRTUALIZER_STRENGTH] ?: 0
        val preampGainDb = prefs[PreferencesKeys.PREAMP_GAIN_DB] ?: 0.0f

        val bandLevels = parseFloats(bandLevelsStr)
        val customBandLevels = parseFloats(customBandLevelsStr)
        val savedPresets = parseSavedPresets(savedPresetsStr)

        PersistedEqualizerSettings(
            isEnabled = isEnabled,
            activePreset = activePreset,
            bandLevels = bandLevels,
            customBandLevels = customBandLevels,
            savedCustomPresets = savedPresets,
            isBassBoostEnabled = isBassBoostEnabled,
            bassBoostStrength = bassBoostStrength,
            isVirtualizerEnabled = isVirtualizerEnabled,
            virtualizerStrength = virtualizerStrength,
            preampGainDb = preampGainDb
        )
    }

    suspend fun saveSettings(
        isEnabled: Boolean,
        activePreset: String,
        bandLevels: List<Float>,
        customBandLevels: List<Float>,
        savedCustomPresets: Map<String, List<Float>>,
        isBassBoostEnabled: Boolean,
        bassBoostStrength: Int,
        isVirtualizerEnabled: Boolean,
        virtualizerStrength: Int,
        preampGainDb: Float
    ) {
        context.equalizerDataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_ENABLED] = isEnabled
            prefs[PreferencesKeys.ACTIVE_PRESET] = activePreset
            prefs[PreferencesKeys.BAND_LEVELS] = bandLevels.joinToString(",")
            prefs[PreferencesKeys.CUSTOM_BAND_LEVELS] = customBandLevels.joinToString(",")
            prefs[PreferencesKeys.SAVED_CUSTOM_PRESETS] = serializeSavedPresets(savedCustomPresets)
            prefs[PreferencesKeys.BASS_BOOST_ENABLED] = isBassBoostEnabled
            prefs[PreferencesKeys.BASS_BOOST_STRENGTH] = bassBoostStrength
            prefs[PreferencesKeys.VIRTUALIZER_ENABLED] = isVirtualizerEnabled
            prefs[PreferencesKeys.VIRTUALIZER_STRENGTH] = virtualizerStrength
            prefs[PreferencesKeys.PREAMP_GAIN_DB] = preampGainDb
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.equalizerDataStore.edit { prefs ->
            prefs[PreferencesKeys.IS_ENABLED] = enabled
        }
    }

    suspend fun setActivePreset(preset: String) {
        context.equalizerDataStore.edit { prefs ->
            prefs[PreferencesKeys.ACTIVE_PRESET] = preset
        }
    }

    suspend fun setBandLevels(levels: List<Float>) {
        context.equalizerDataStore.edit { prefs ->
            prefs[PreferencesKeys.BAND_LEVELS] = levels.joinToString(",")
        }
    }

    suspend fun setCustomBandLevels(levels: List<Float>) {
        context.equalizerDataStore.edit { prefs ->
            prefs[PreferencesKeys.CUSTOM_BAND_LEVELS] = levels.joinToString(",")
        }
    }

    suspend fun setBassBoost(enabled: Boolean, strength: Int) {
        context.equalizerDataStore.edit { prefs ->
            prefs[PreferencesKeys.BASS_BOOST_ENABLED] = enabled
            prefs[PreferencesKeys.BASS_BOOST_STRENGTH] = strength
        }
    }

    suspend fun setVirtualizer(enabled: Boolean, strength: Int) {
        context.equalizerDataStore.edit { prefs ->
            prefs[PreferencesKeys.VIRTUALIZER_ENABLED] = enabled
            prefs[PreferencesKeys.VIRTUALIZER_STRENGTH] = strength
        }
    }

    suspend fun setPreampGain(gainDb: Float) {
        context.equalizerDataStore.edit { prefs ->
            prefs[PreferencesKeys.PREAMP_GAIN_DB] = gainDb
        }
    }

    suspend fun saveNamedCustomPreset(name: String, levels: List<Float>) {
        context.equalizerDataStore.edit { prefs ->
            val existing = parseSavedPresets(prefs[PreferencesKeys.SAVED_CUSTOM_PRESETS] ?: "{}")
            val updated = existing.toMutableMap()
            updated[name] = levels
            prefs[PreferencesKeys.SAVED_CUSTOM_PRESETS] = serializeSavedPresets(updated)
        }
    }

    suspend fun deleteNamedCustomPreset(name: String) {
        context.equalizerDataStore.edit { prefs ->
            val existing = parseSavedPresets(prefs[PreferencesKeys.SAVED_CUSTOM_PRESETS] ?: "{}")
            val updated = existing.toMutableMap()
            updated.remove(name)
            prefs[PreferencesKeys.SAVED_CUSTOM_PRESETS] = serializeSavedPresets(updated)
        }
    }

    private fun parseFloats(str: String): List<Float> {
        if (str.isBlank()) return emptyList()
        return str.split(",").mapNotNull { it.trim().toFloatOrNull() }
    }

    private fun parseSavedPresets(jsonStr: String): Map<String, List<Float>> {
        val result = mutableMapOf<String, List<Float>>()
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val array = json.optJSONArray(key)
                if (array != null) {
                    val list = mutableListOf<Float>()
                    for (i in 0 until array.length()) {
                        list.add(array.optDouble(i, 0.0).toFloat())
                    }
                    result[key] = list
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun serializeSavedPresets(presets: Map<String, List<Float>>): String {
        val json = JSONObject()
        for ((key, list) in presets) {
            val array = org.json.JSONArray()
            for (value in list) {
                array.put(value.toDouble())
            }
            json.put(key, array)
        }
        return json.toString()
    }
}
