package com.omb9.glucosehero.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val aiConfig: StateFlow<AiConfig> = settingsRepository.aiConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiConfig())

    fun setThemeMode(mode: ThemeMode) =
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }

    fun setAccent(accent: AccentColor) =
        viewModelScope.launch { settingsRepository.setAccent(accent) }

    fun setUnit(unit: GlucoseUnit) =
        viewModelScope.launch { settingsRepository.setUnit(unit) }

    fun setUse24HourTime(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setUse24HourTime(enabled) }

    fun setIsHeroAiEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setIsHeroAiEnabled(enabled) }

    fun setTargetRange(lowMgdl: Float, highMgdl: Float) =
        viewModelScope.launch { settingsRepository.setTargetRange(lowMgdl, highMgdl) }

    fun setAiProvider(provider: AiProvider) =
        viewModelScope.launch { settingsRepository.setAiProvider(provider) }

    fun setAiBaseUrl(url: String) =
        viewModelScope.launch { settingsRepository.setAiBaseUrl(url) }

    fun setAiModel(model: String) =
        viewModelScope.launch { settingsRepository.setAiModel(model) }

    fun saveApiKey(plainKey: String) =
        viewModelScope.launch { settingsRepository.setApiKey(plainKey) }
}
