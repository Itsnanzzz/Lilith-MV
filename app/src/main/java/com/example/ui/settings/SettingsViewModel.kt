package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsDataStore
import com.example.data.ThemeMode
import com.example.data.model.SubtitleSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStore = SettingsDataStore(application)

    val themeMode: StateFlow<ThemeMode> = settingsDataStore.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, ThemeMode.SYSTEM)

    val gaplessPlayback: StateFlow<Boolean> = settingsDataStore.gaplessPlaybackFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val pauseOnHeadsetUnplug: StateFlow<Boolean> = settingsDataStore.pauseOnHeadsetUnplugFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val preferredSubtitleLanguage: StateFlow<String> = settingsDataStore.preferredSubtitleLanguageFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, "id")

    val subtitleSettings: StateFlow<SubtitleSettings> = settingsDataStore.subtitleSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, SubtitleSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsDataStore.setThemeMode(mode)
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
}
