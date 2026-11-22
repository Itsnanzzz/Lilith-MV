package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SubtitleSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

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

data class CachedReleaseData(
    val version: String,
    val notes: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val apkSize: Long?,
    val publishedAt: String
)

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
        val UPDATE_AUTO_CHECK = booleanPreferencesKey("update_auto_check")
        val UPDATE_LAST_CHECK_AT = longPreferencesKey("update_last_check_at")
        val UPDATE_SKIPPED_VERSION = stringPreferencesKey("update_skipped_version")
        val UPDATE_ETAG = stringPreferencesKey("update_etag")
        val UPDATE_CACHED_VERSION = stringPreferencesKey("update_cached_version")
        val UPDATE_CACHED_NOTES = stringPreferencesKey("update_cached_notes")
        val UPDATE_CACHED_RELEASE_URL = stringPreferencesKey("update_cached_release_url")
        val UPDATE_CACHED_APK_URL = stringPreferencesKey("update_cached_apk_url")
        val UPDATE_CACHED_APK_SIZE = longPreferencesKey("update_cached_apk_size")
        val UPDATE_CACHED_PUBLISHED_AT = stringPreferencesKey("update_cached_published_at")
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

    private val safePreferencesFlow = context.dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val updateAutoCheckFlow: Flow<Boolean> = safePreferencesFlow.map { preferences ->
        try {
            preferences[PreferencesKeys.UPDATE_AUTO_CHECK] ?: true
        } catch (_: Exception) {
            true
        }
    }

    val updateLastCheckAtFlow: Flow<Long> = safePreferencesFlow.map { preferences ->
        try {
            (preferences[PreferencesKeys.UPDATE_LAST_CHECK_AT] ?: 0L).coerceAtLeast(0L)
        } catch (_: Exception) {
            0L
        }
    }

    val updateSkippedVersionFlow: Flow<String> = safePreferencesFlow.map { preferences ->
        try {
            preferences[PreferencesKeys.UPDATE_SKIPPED_VERSION] ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    val updateETagFlow: Flow<String> = safePreferencesFlow.map { preferences ->
        try {
            preferences[PreferencesKeys.UPDATE_ETAG] ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    val updateCachedReleaseFlow: Flow<CachedReleaseData?> = safePreferencesFlow.map { preferences ->
        try {
            val version = preferences[PreferencesKeys.UPDATE_CACHED_VERSION]?.trim().orEmpty()
            if (version.isEmpty()) {
                null
            } else {
                CachedReleaseData(
                    version = version,
                    notes = (preferences[PreferencesKeys.UPDATE_CACHED_NOTES] ?: "")
                        .take(UpdateConstants.MAX_NOTES_LENGTH),
                    releaseUrl = preferences[PreferencesKeys.UPDATE_CACHED_RELEASE_URL] ?: "",
                    apkUrl = preferences[PreferencesKeys.UPDATE_CACHED_APK_URL]
                        ?.takeIf { it.isNotBlank() },
                    apkSize = preferences[PreferencesKeys.UPDATE_CACHED_APK_SIZE]
                        ?.takeIf { it > 0L },
                    publishedAt = preferences[PreferencesKeys.UPDATE_CACHED_PUBLISHED_AT] ?: ""
                )
            }
        } catch (_: Exception) {
            null
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

    suspend fun setUpdateAutoCheck(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UPDATE_AUTO_CHECK] = enabled
        }
    }

    suspend fun setUpdateLastCheckAt(timestampMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UPDATE_LAST_CHECK_AT] = timestampMs.coerceAtLeast(0L)
        }
    }

    suspend fun setUpdateSkippedVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UPDATE_SKIPPED_VERSION] = version
        }
    }

    suspend fun clearUpdateETag() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.UPDATE_ETAG)
        }
    }

    suspend fun saveUpdateCache(
        etag: String,
        release: CachedReleaseData,
        checkedAtMs: Long
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UPDATE_ETAG] = etag
            preferences[PreferencesKeys.UPDATE_CACHED_VERSION] = release.version
            preferences[PreferencesKeys.UPDATE_CACHED_NOTES] =
                release.notes.take(UpdateConstants.MAX_NOTES_LENGTH)
            preferences[PreferencesKeys.UPDATE_CACHED_RELEASE_URL] = release.releaseUrl
            if (!release.apkUrl.isNullOrBlank()) {
                preferences[PreferencesKeys.UPDATE_CACHED_APK_URL] = release.apkUrl
            } else {
                preferences.remove(PreferencesKeys.UPDATE_CACHED_APK_URL)
            }
            if (release.apkSize != null && release.apkSize > 0L) {
                preferences[PreferencesKeys.UPDATE_CACHED_APK_SIZE] = release.apkSize
            } else {
                preferences.remove(PreferencesKeys.UPDATE_CACHED_APK_SIZE)
            }
            preferences[PreferencesKeys.UPDATE_CACHED_PUBLISHED_AT] = release.publishedAt
            preferences[PreferencesKeys.UPDATE_LAST_CHECK_AT] = checkedAtMs.coerceAtLeast(0L)
        }
    }
}
