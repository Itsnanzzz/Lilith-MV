package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SubtitleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "lilith_settings")

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class AppAccent {
    WHITE, BLUE, GREEN, YELLOW, RED, ORANGE, PURPLE, CYAN
}


class SettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val GAPLESS_PLAYBACK = booleanPreferencesKey("gapless_playback")
        val PAUSE_ON_HEADSET_UNPLUG = booleanPreferencesKey("pause_on_headset_unplug")
        val PREFERRED_SUBTITLE_LANG = stringPreferencesKey("preferred_subtitle_lang")
        val SUBTITLE_FONT_SIZE = floatPreferencesKey("subtitle_font_size")
        val SUBTITLE_TEXT_COLOR = longPreferencesKey("subtitle_text_color")
        val SUBTITLE_BG_COLOR = longPreferencesKey("subtitle_bg_color")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val accentColorFlow: Flow<AppAccent> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.ACCENT_COLOR] ?: AppAccent.WHITE.name
        try {
            AppAccent.valueOf(name)
        } catch (e: Exception) {
            AppAccent.WHITE
        }
    }

    val gaplessPlaybackFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GAPLESS_PLAYBACK] ?: true
    }

    val pauseOnHeadsetUnplugFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PAUSE_ON_HEADSET_UNPLUG] ?: true
    }

    val preferredSubtitleLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PREFERRED_SUBTITLE_LANG] ?: "id"
    }

    val subtitleSettingsFlow: Flow<SubtitleSettings> = context.dataStore.data.map { preferences ->
        SubtitleSettings(
            fontSizeSp = preferences[PreferencesKeys.SUBTITLE_FONT_SIZE] ?: 18f,
            textColorHex = preferences[PreferencesKeys.SUBTITLE_TEXT_COLOR] ?: 0xFFFFFFFF,
            backgroundColorHex = preferences[PreferencesKeys.SUBTITLE_BG_COLOR] ?: 0x99000000,
            delayMs = 0L
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setAccentColor(accent: AppAccent) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR] = accent.name
        }
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GAPLESS_PLAYBACK] = enabled
        }
    }

    suspend fun setPauseOnHeadsetUnplug(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PAUSE_ON_HEADSET_UNPLUG] = enabled
        }
    }

    suspend fun setPreferredSubtitleLanguage(lang: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PREFERRED_SUBTITLE_LANG] = lang
        }
    }

    suspend fun updateSubtitleSettings(settings: SubtitleSettings) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SUBTITLE_FONT_SIZE] = settings.fontSizeSp
            preferences[PreferencesKeys.SUBTITLE_TEXT_COLOR] = settings.textColorHex
            preferences[PreferencesKeys.SUBTITLE_BG_COLOR] = settings.backgroundColorHex
        }
    }
}
