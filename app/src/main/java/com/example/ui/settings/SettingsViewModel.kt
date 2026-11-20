package com.example.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppAccent
import com.example.data.DynamicIslandBridge
import com.example.data.IslandPosition
import com.example.data.IslandVisibility
import com.example.data.SettingsDataStore
import com.example.data.ThemeMode
import com.example.data.model.SubtitleSettings
import com.example.playback.MusicPlaybackService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)

    val themeMode: StateFlow<ThemeMode> = settingsDataStore.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val appAccent: StateFlow<AppAccent> = settingsDataStore.accentColorFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppAccent.WHITE)

    val gaplessPlayback: StateFlow<Boolean> = settingsDataStore.gaplessPlaybackFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val pauseOnHeadsetUnplug: StateFlow<Boolean> = settingsDataStore.pauseOnHeadsetUnplugFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val preferredSubtitleLanguage: StateFlow<String> = settingsDataStore.preferredSubtitleLanguageFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "id")

    val subtitleSettings: StateFlow<SubtitleSettings> = settingsDataStore.subtitleSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, SubtitleSettings())

    val islandEnabled: StateFlow<Boolean> = settingsDataStore.islandEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val islandVisibility: StateFlow<IslandVisibility> = settingsDataStore.islandVisibilityFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, IslandVisibility.BRIEFLY)

    val islandPosition: StateFlow<IslandPosition> = settingsDataStore.islandPositionFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, IslandPosition.BELOW_STATUS_BAR)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
        }
    }

    fun setAccentColor(accent: AppAccent) {
        viewModelScope.launch {
            settingsDataStore.setAccentColor(accent)
        }
    }

    fun setGaplessPlayback(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setGaplessPlayback(enabled)
        }
    }

    fun setPauseOnHeadsetUnplug(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setPauseOnHeadsetUnplug(enabled)
        }
    }

    fun setPreferredSubtitleLanguage(lang: String) {
        viewModelScope.launch {
            settingsDataStore.setPreferredSubtitleLanguage(lang)
        }
    }

    fun updateSubtitleSettings(settings: SubtitleSettings) {
        viewModelScope.launch {
            settingsDataStore.updateSubtitleSettings(settings)
        }
    }

    fun setIslandEnabled(enabled: Boolean) {
        if (enabled) {
            ensurePlaybackServiceStarted()
        }
        viewModelScope.launch {
            settingsDataStore.setIslandEnabled(enabled)
        }
    }

    fun setIslandVisibility(visibility: IslandVisibility) {
        viewModelScope.launch {
            settingsDataStore.setIslandVisibility(visibility)
        }
    }

    fun setIslandPosition(position: IslandPosition) {
        viewModelScope.launch {
            settingsDataStore.setIslandPosition(position)
        }
    }

    fun triggerIslandPreview() {
        ensurePlaybackServiceStarted()
        DynamicIslandBridge.triggerPreview()
    }

    private fun ensurePlaybackServiceStarted() {
        if (MusicPlaybackService.serviceInstance != null) return
        try {
            val app = getApplication<Application>()
            app.startService(Intent(app, MusicPlaybackService::class.java))
        } catch (_: Exception) {
        }
    }
}
