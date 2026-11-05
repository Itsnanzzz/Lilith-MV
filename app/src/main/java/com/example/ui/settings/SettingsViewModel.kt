package com.example.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppAccent
import com.example.data.SettingsDataStore
import com.example.data.ThemeMode
import com.example.data.model.SubtitleSettings
import com.example.ui.common.getAppLocales
import com.example.ui.common.normalizeLanguageTag
import com.example.ui.common.setAppLocales
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

    private val _currentAppLanguage = kotlinx.coroutines.flow.MutableStateFlow(resolveCurrentLanguageTag())
    val currentAppLanguage: StateFlow<String> = _currentAppLanguage

    private fun resolveCurrentLanguageTag(activeConfigLanguage: String? = null): String {
        return try {
            val locales = getAppLocales(getApplication())
            if (!locales.isEmpty) {
                normalizeLanguageTag(locales.get(0))
            } else if (activeConfigLanguage != null) {
                if (activeConfigLanguage == "en") "en" else "id"
            } else {
                "id"
            }
        } catch (_: Exception) {
            if (activeConfigLanguage == "en") "en" else "id"
        }
    }

    fun refreshAppLanguage(activeConfigLanguage: String? = null) {
        _currentAppLanguage.value = resolveCurrentLanguageTag(activeConfigLanguage)
    }

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

    fun setAccentColor(accent: AppAccent) {
        viewModelScope.launch {
            settingsDataStore.setAccentColor(accent)
        }
    }

    fun setAppLanguage(
        tag: String,
        activeConfigLanguage: String? = null,
        activity: android.app.Activity? = null
    ) {
        val normalized = if (tag == "en") "en" else "id"
        val currentLocales = getAppLocales(getApplication())
        val currentStoredTag = resolveCurrentLanguageTag(activeConfigLanguage)
        if (!currentLocales.isEmpty &&
            currentStoredTag == normalized &&
            (activeConfigLanguage == null || activeConfigLanguage == normalized)
        ) {
            _currentAppLanguage.value = normalized
            return
        }
        _currentAppLanguage.value = normalized
        setAppLocales(getApplication(), normalized)
        com.example.ui.common.logLangDebug(activity ?: getApplication(), "SettingsVM.afterSwitch($normalized)")
        if (!currentLocales.isEmpty &&
            currentStoredTag == normalized &&
            activeConfigLanguage != null &&
            activeConfigLanguage != normalized
        ) {
            activity?.recreate()
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
