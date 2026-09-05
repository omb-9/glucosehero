package com.omb9.glucosehero.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.omb9.glucosehero.data.billing.BillingRepository
import com.omb9.glucosehero.data.health.HealthConnectAvailability
import com.omb9.glucosehero.data.health.HealthConnectRepository
import com.omb9.glucosehero.data.health.HealthConnectStatus
import com.omb9.glucosehero.data.local.datastore.InitialImportRange
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UnitSystem
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.work.HealthConnectSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val healthConnectAvailability: HealthConnectAvailability,
    private val healthConnectRepository: HealthConnectRepository,
    private val settingsDataStore: SettingsDataStore,
    private val glucoseSampleDao: GlucoseSampleDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /**
     * [UserSettings.unitSystem] is a presentation-layer preference and is not
     * persisted by [SettingsRepository]; hold the in-session override here and
     * layer it over the stored settings.
     */
    private val unitSystemOverride = MutableStateFlow<UnitSystem?>(null)

    val settings: StateFlow<UserSettings> = combine(
        settingsRepository.settings,
        unitSystemOverride,
    ) { stored, override ->
        if (override == null) stored else stored.copy(unitSystem = override)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val aiConfig: StateFlow<AiConfig> = settingsRepository.aiConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiConfig())

    val profile: StateFlow<UserProfile> = settingsRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    /** Insulin-dosing parameters (DIA, CIR, ISF, target glucose) backing Smart Bolus + IOB. */
    val bolusSettings: StateFlow<BolusSettings> = settingsRepository.bolusSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BolusSettings())

    /** Whether the user owns an active Pro subscription. */
    val isPremium: StateFlow<Boolean> = billingRepository.isPremium

    /** Static availability snapshot — Health Connect's SDK status does not change at runtime. */
    val healthConnectAvailabilityStatus: HealthConnectStatus = healthConnectAvailability.status()

    /** The exact permission set requested by the connect flow (owned by [HealthConnectRepository]). */
    val readPermissions: Set<String> = healthConnectRepository.readPermissions

    private val _isHealthConnectConnected = MutableStateFlow(false)
    val isHealthConnectConnected: StateFlow<Boolean> = _isHealthConnectConnected.asStateFlow()

    private val _healthConnectSampleCount = MutableStateFlow(0)
    val healthConnectSampleCount: StateFlow<Int> = _healthConnectSampleCount.asStateFlow()

    val glucoseImportEnabled: StateFlow<Boolean> = settingsDataStore.glucoseImportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val nutritionImportEnabled: StateFlow<Boolean> = settingsDataStore.nutritionImportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val exerciseImportEnabled: StateFlow<Boolean> = settingsDataStore.exerciseImportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val healthConnectInitialImportRange: StateFlow<InitialImportRange> =
        settingsDataStore.healthConnectInitialImportRange
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InitialImportRange.DAYS_90)

    val healthConnectLastSync: StateFlow<Long?> = settingsDataStore.healthConnectLastSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
        refreshHealthConnectStatus()

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
                .collectLatest { settingsRepository.setProfileHeightCm(Formatters.parseDecimal(it)?.toFloat()) }
        }

        viewModelScope.launch {
            weightInput
                .filterNotNull()
                .debounce(DEBOUNCE_MILLIS)
                .collectLatest { settingsRepository.setProfileWeightKg(Formatters.parseDecimal(it)?.toFloat()) }
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

    fun setUnitSystem(system: UnitSystem) {
        unitSystemOverride.value = system
    }

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

    fun setProfileHeightMetric(value: String) {
        heightInput.value = value
    }

    fun setProfileHeightImperial(feet: String, inches: String) {
        val feetValue = feet.trim().toIntOrNull()
        heightInput.value = if (feetValue == null) {
            ""
        } else {
            val inchesValue = inches.trim().toIntOrNull() ?: 0
            Formatters.feetInchesToCm(feetValue, inchesValue).toString()
        }
    }

    fun setProfileWeightMetric(value: String) {
        weightInput.value = value
    }

    fun setProfileWeightImperial(value: String) {
        val lbs = Formatters.parseDecimal(value)
        weightInput.value = if (lbs == null) value else Formatters.lbsToKg(lbs.toFloat()).toString()
    }

    fun setTargetRange(lowMgdl: Float, highMgdl: Float) =
        viewModelScope.launch { settingsRepository.setTargetRange(lowMgdl, highMgdl) }

    fun setDiaHours(diaHours: Float) =
        viewModelScope.launch { settingsRepository.setDiaHours(diaHours) }

    fun setCirRatio(ratio: Float) =
        viewModelScope.launch { settingsRepository.setCirRatio(ratio) }

    fun setIsfMgdl(isf: Float) =
        viewModelScope.launch { settingsRepository.setIsfMgdl(isf) }

    fun setTargetGlucoseMgdl(target: Float) =
        viewModelScope.launch { settingsRepository.setTargetGlucoseMgdl(target) }

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

    fun onPermissionsResult(granted: Set<String>) {
        val connected = granted.containsAll(readPermissions)
        _isHealthConnectConnected.value = connected
        viewModelScope.launch {
            if (connected) {
                settingsDataStore.setHealthConnectSyncEnabled(true)
                enqueueHealthConnectSync()
            }
            refreshHealthConnectSampleCount()
        }
    }

    fun setGlucoseImportEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setGlucoseImportEnabled(enabled) }

    fun setNutritionImportEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setNutritionImportEnabled(enabled) }

    fun setExerciseImportEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setExerciseImportEnabled(enabled) }

    fun setHealthConnectInitialImportRange(range: InitialImportRange) =
        viewModelScope.launch { settingsDataStore.setHealthConnectInitialImportRange(range) }

    fun clearImportedGlucoseData() {
        viewModelScope.launch {
            healthConnectRepository.clearImportedGlucoseData()
            refreshHealthConnectSampleCount()
        }
    }

    /** Re-reads granted permissions + sample count; permissions can change in system settings. */
    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val granted = healthConnectRepository.grantedPermissions()
            _isHealthConnectConnected.value = granted.containsAll(readPermissions)
            refreshHealthConnectSampleCount()
        }
    }

    private suspend fun refreshHealthConnectSampleCount() {
        _healthConnectSampleCount.value = glucoseSampleDao.count()
    }

    private fun enqueueHealthConnectSync() {
        val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            HealthConnectSyncWorker.EXPEDITED_UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 400L
    }
}
