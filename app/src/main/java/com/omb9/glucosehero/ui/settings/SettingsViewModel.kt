package com.omb9.glucosehero.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.data.billing.BillingRepository
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val billingRepository: BillingRepository,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val aiConfig: StateFlow<AiConfig> = settingsRepository.aiConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiConfig())

    val profile: StateFlow<UserProfile> = settingsRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    /** Whether the user owns an active Pro subscription. */
    val isPremium: StateFlow<Boolean> = billingRepository.isPremium

    /**
     * Debounced text inputs. Each keystroke updates a conflated [MutableStateFlow];
     * the matching collector waits [DEBOUNCE_MILLIS] of inactivity before
     * persisting, so DataStore no longer writes on every keystroke.
     */
    private val nameInput = MutableStateFlow<String?>(null)
    private val diabetesTypeInput = MutableStateFlow<String?>(null)
    private val heightInput = MutableStateFlow<String?>(null)
    private val weightInput = MutableStateFlow<String?>(null)
    private val baseUrlInput = MutableStateFlow<String?>(null)
    private val modelInput = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            nameInput
                .filterNotNull()
                .debounce(DEBOUNCE_MILLIS)
                .collectLatest { settingsRepository.setProfileName(it) }
        }

        viewModelScope.launch {
            diabetesTypeInput
                .filterNotNull()
                .debounce(DEBOUNCE_MILLIS)
                .collectLatest { settingsRepository.setProfileDiabetesType(it) }
        }

        viewModelScope.launch {
            heightInput
                .filterNotNull()
                .debounce(DEBOUNCE_MILLIS)
                .collectLatest { settingsRepository.setProfileHeightCm(it.toFloatOrNull()) }
        }

        viewModelScope.launch {
            weightInput
                .filterNotNull()
                .debounce(DEBOUNCE_MILLIS)
                .collectLatest { settingsRepository.setProfileWeightKg(it.toFloatOrNull()) }
        }

        viewModelScope.launch {
            baseUrlInput
                .filterNotNull()
                .debounce(DEBOUNCE_MILLIS)
                .collectLatest { settingsRepository.setAiBaseUrl(it) }
        }

        viewModelScope.launch {
            modelInput
                .filterNotNull()
                .debounce(DEBOUNCE_MILLIS)
                .collectLatest { settingsRepository.setAiModel(it) }
        }
    }

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

    fun setShowAdvancedMacros(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setShowAdvancedMacros(enabled) }

    fun setPostMealRemindersEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setPostMealRemindersEnabled(enabled) }

    fun setProfileTarget(target: ProfileTarget) =
        viewModelScope.launch { settingsRepository.setProfileTarget(target) }

    fun setProfileName(name: String) {
        nameInput.value = name
    }

    fun setProfileAge(age: Int?) =
        viewModelScope.launch { settingsRepository.setProfileAge(age) }

    fun setProfileDiabetesType(type: String?) {
        diabetesTypeInput.value = type.orEmpty()
    }

    fun setProfileHeightCm(heightCm: String) {
        heightInput.value = heightCm
    }

    fun setProfileWeightKg(weightKg: String) {
        weightInput.value = weightKg
    }

    fun setTargetRange(lowMgdl: Float, highMgdl: Float) =
        viewModelScope.launch { settingsRepository.setTargetRange(lowMgdl, highMgdl) }

    fun setAiProvider(provider: AiProvider) =
        viewModelScope.launch { settingsRepository.setAiProvider(provider) }

    fun setAiBaseUrl(url: String) {
        baseUrlInput.value = url
    }

    fun setAiModel(model: String) {
        modelInput.value = model
    }

    fun saveApiKey(plainKey: String) =
        viewModelScope.launch { settingsRepository.setApiKey(plainKey) }

    private companion object {
        const val DEBOUNCE_MILLIS = 400L
    }
}
