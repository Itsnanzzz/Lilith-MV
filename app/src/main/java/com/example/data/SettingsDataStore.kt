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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "lilith_settings")

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class AppAccent {
    WHITE, BLUE, GREEN, YELLOW, RED, ORANGE, PURPLE, CYAN
}

enum class VideoViewMode {
    LIST, GRID
}

enum class IslandVisibility {
    BRIEFLY, ALWAYS
}

enum class IslandPosition {
    BELOW_STATUS_BAR, AROUND_CAMERA
}

object DynamicIslandBridge {
    private val _previewRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val previewRequests: SharedFlow<Unit> = _previewRequests.asSharedFlow()

    @Volatile
    private var pendingPreviewUntilMs: Long = 0L

    private val _errorMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    fun triggerPreview() {
        pendingPreviewUntilMs = System.currentTimeMillis() + 2500L
        _previewRequests.tryEmit(Unit)
    }

    fun consumePendingPreview(): Boolean {
        val now = System.currentTimeMillis()
        val active = pendingPreviewUntilMs > now
        pendingPreviewUntilMs = 0L
        return active
    }

    fun emitError(message: String) {
        _errorMessages.tryEmit(message)
    }
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
        val VIDEO_VIEW_MODE = stringPreferencesKey("video_view_mode")
        val ISLAND_ENABLED = booleanPreferencesKey("island_enabled")
        val ISLAND_VISIBILITY = stringPreferencesKey("island_visibility")
        val ISLAND_POSITION = stringPreferencesKey("island_position")
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

    val videoViewModeFlow: Flow<VideoViewMode> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.VIDEO_VIEW_MODE] ?: VideoViewMode.LIST.name
        try {
            VideoViewMode.valueOf(name)
        } catch (_: Exception) {
            VideoViewMode.LIST
        }
    }

    val islandEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ISLAND_ENABLED] ?: false
    }

    val islandVisibilityFlow: Flow<IslandVisibility> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.ISLAND_VISIBILITY] ?: IslandVisibility.BRIEFLY.name
        try {
            IslandVisibility.valueOf(name)
        } catch (_: Exception) {
            IslandVisibility.BRIEFLY
        }
    }

    val islandPositionFlow: Flow<IslandPosition> = context.dataStore.data.map { preferences ->
        val name = preferences[PreferencesKeys.ISLAND_POSITION] ?: IslandPosition.BELOW_STATUS_BAR.name
        try {
            IslandPosition.valueOf(name)
        } catch (_: Exception) {
            IslandPosition.BELOW_STATUS_BAR
        }
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

    suspend fun setVideoViewMode(mode: VideoViewMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIDEO_VIEW_MODE] = mode.name
        }
    }

    suspend fun setIslandEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ISLAND_ENABLED] = enabled
        }
    }

    suspend fun setIslandVisibility(visibility: IslandVisibility) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ISLAND_VISIBILITY] = visibility.name
        }
    }

    suspend fun setIslandPosition(position: IslandPosition) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ISLAND_POSITION] = position.name
        }
    }
}
