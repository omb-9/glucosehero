package com.omb9.glucosehero.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.omb9.glucosehero.data.billing.BillingRepository
import com.omb9.glucosehero.data.backup.CloudBackupProvider
import com.omb9.glucosehero.data.backup.EncryptedCloudBackupManager
import com.omb9.glucosehero.data.export.BackupManager
import com.omb9.glucosehero.data.export.BackupPreview
import com.omb9.glucosehero.data.export.ImportMode
import com.omb9.glucosehero.data.export.MarkdownExporter
import com.omb9.glucosehero.data.health.HealthConnectAvailability
import com.omb9.glucosehero.data.health.HealthConnectRepository
import com.omb9.glucosehero.data.health.HealthConnectStatus
import com.omb9.glucosehero.data.local.datastore.InitialImportRange
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.data.remote.AiEndpointGuard
import com.omb9.glucosehero.data.remote.TrustedHosts
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
import com.omb9.glucosehero.util.AiQuota
import com.omb9.glucosehero.util.AiTier
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.work.HealthConnectSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI state for the Back up & restore section. */
data class BackupUiState(
    val isWorking: Boolean = false,
    val progress: Float = 0f,
    val lastBackup: Long? = null,
    val backupEnabled: Boolean = false,
    val backupDirUri: String? = null,
    val preview: BackupPreview? = null,
    val message: String? = null,
    val hasSnapshot: Boolean = false,
    val cloudProvider: CloudBackupProvider = CloudBackupProvider.NONE,
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val hasWebDavPassword: Boolean = false,
    val hasDriveToken: Boolean = false,
    val lastCloudBackup: Long? = null,
)

/** One-shot snackbar feedback for API key save attempts. */
data class ApiKeySaveMessage(
    val text: String,
    val isError: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val billingRepository: BillingRepository,
    private val healthConnectAvailability: HealthConnectAvailability,
    private val healthConnectRepository: HealthConnectRepository,
    private val settingsDataStore: SettingsDataStore,
    private val glucoseSampleDao: GlucoseSampleDao,
    private val backupManager: BackupManager,
    private val encryptedCloudBackupManager: EncryptedCloudBackupManager,
    private val markdownExporter: MarkdownExporter,
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

    /**
     * True when the current Hero AI base URL is not on the built-in HTTPS
     * allowlist and the user has not acknowledged this host yet.
     */
    val aiEndpointNeedsAck: StateFlow<Boolean> = combine(
        settingsRepository.aiConfig,
        settingsDataStore.acknowledgedAiHosts,
    ) { config, hosts ->
        AiEndpointGuard.needsAcknowledgment(config.baseUrl, hosts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val aiEndpointStatus: StateFlow<AiEndpointGuard.Status> = combine(
        settingsRepository.aiConfig,
        settingsDataStore.acknowledgedAiHosts,
    ) { config, hosts ->
        AiEndpointGuard.evaluate(config.baseUrl, hosts)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AiEndpointGuard.Status.InvalidUrl,
    )

    val profile: StateFlow<UserProfile> = settingsRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    /** Insulin-dosing parameters (DIA, CIR, ISF, target glucose) backing Smart Bolus + IOB. */
    val bolusSettings: StateFlow<BolusSettings> = settingsRepository.bolusSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BolusSettings())

    /** Whether the user owns an active Pro subscription. */
    val isPremium: StateFlow<Boolean> = billingRepository.isPremium

    /** Remaining managed-tier AI calls today, or null when uncounted (BYOK). */
    val heroAiRemainingCalls: StateFlow<Int?> = combine(
        settingsRepository.aiConfig,
        billingRepository.isPremium,
        settingsDataStore.aiQuotaUsedToday,
    ) { config, premium, used ->
        val tier = if (config.provider == AiProvider.OPENROUTER && !config.hasApiKey) {
            if (premium) AiTier.PRO else AiTier.FREE
        } else {
            AiTier.BYOK
        }
        AiQuota.remaining(tier, used)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _apiKeySaveMessages = MutableSharedFlow<ApiKeySaveMessage>(extraBufferCapacity = 1)

    /** One-shot API key save feedback surfaced to the UI as a snackbar. */
    val apiKeySaveMessages: SharedFlow<ApiKeySaveMessage> = _apiKeySaveMessages.asSharedFlow()

    /** Static availability snapshot — Health Connect's SDK status does not change at runtime. */
    val healthConnectAvailabilityStatus: HealthConnectStatus = healthConnectAvailability.status()

    /** The exact permission set requested by the connect flow (owned by [HealthConnectRepository]). */
    val readPermissions: Set<String> = healthConnectRepository.readPermissions
    val writePermissions: Set<String> = healthConnectRepository.writePermissions
    val allPermissions: Set<String> = healthConnectRepository.allPermissions

    private val _isHealthConnectConnected = MutableStateFlow(false)
    val isHealthConnectConnected: StateFlow<Boolean> = _isHealthConnectConnected.asStateFlow()

    val isHealthConnectRevoked: StateFlow<Boolean> = settingsDataStore.healthConnectRevoked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _healthConnectSampleCount = MutableStateFlow(0)
    val healthConnectSampleCount: StateFlow<Int> = _healthConnectSampleCount.asStateFlow()

    val barcodeLookupEnabled: StateFlow<Boolean> = settingsDataStore.barcodeLookupEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val glucoseImportEnabled: StateFlow<Boolean> = settingsDataStore.glucoseImportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val nutritionImportEnabled: StateFlow<Boolean> = settingsDataStore.nutritionImportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val exerciseImportEnabled: StateFlow<Boolean> = settingsDataStore.exerciseImportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val sleepImportEnabled: StateFlow<Boolean> = settingsDataStore.sleepImportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val cycleImportEnabled: StateFlow<Boolean> = settingsDataStore.cycleImportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val healthConnectInitialImportRange: StateFlow<InitialImportRange> =
        settingsDataStore.healthConnectInitialImportRange
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InitialImportRange.DAYS_90)

    val healthConnectLastSync: StateFlow<Long?> = settingsDataStore.healthConnectLastSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _backupPreview = MutableStateFlow<BackupPreview?>(null)
    private val _backupMessage = MutableStateFlow<String?>(null)

    private val backupCore = combine(
        backupManager.isWorking,
        backupManager.progress,
        settingsDataStore.backupLastRun,
        settingsDataStore.backupEnabled,
        settingsDataStore.backupDirUri,
    ) { working, progress, lastRun, enabled, dirUri ->
        BackupUiState(
            isWorking = working,
            progress = progress,
            lastBackup = lastRun,
            backupEnabled = enabled,
            backupDirUri = dirUri,
        )
    }

    private val cloudCore = combine(
        combine(
            settingsDataStore.cloudBackupProvider,
            settingsDataStore.webDavUrl,
            settingsDataStore.webDavUsername,
            settingsDataStore.hasWebDavPassword,
            settingsDataStore.hasDriveAccessToken,
        ) { provider, url, username, hasPassword, hasDrive ->
            BackupUiState(
                cloudProvider = provider,
                webDavUrl = url,
                webDavUsername = username,
                hasWebDavPassword = hasPassword,
                hasDriveToken = hasDrive,
            )
        },
        settingsDataStore.cloudBackupLastRun,
    ) { slice, lastCloud ->
        slice.copy(lastCloudBackup = lastCloud)
    }

    val backupState: StateFlow<BackupUiState> = combine(
        backupCore,
        cloudCore,
        _backupPreview,
        _backupMessage,
        backupManager.hasPreImportSnapshot,
    ) { core, cloud, preview, message, hasSnapshot ->
        core.copy(
            preview = preview,
            message = message,
            hasSnapshot = hasSnapshot,
            cloudProvider = cloud.cloudProvider,
            webDavUrl = cloud.webDavUrl,
            webDavUsername = cloud.webDavUsername,
            hasWebDavPassword = cloud.hasWebDavPassword,
            hasDriveToken = cloud.hasDriveToken,
            lastCloudBackup = cloud.lastCloudBackup,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

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

    fun setSendMealPhotosToHeroAi(enabled: Boolean) =
        viewModelScope.launch { settingsRepository.setSendMealPhotosToHeroAi(enabled) }

    fun setBarcodeLookupEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setBarcodeLookupEnabled(enabled) }

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

    fun acknowledgeAiEndpoint() {
        val host = TrustedHosts.hostOf(aiConfig.value.baseUrl) ?: return
        viewModelScope.launch { settingsDataStore.acknowledgeAiHost(host) }
    }

    fun setAiModel(model: String) {
        modelInput.value = model
    }

    /**
     * Immediately persists inputs that are otherwise debounced. Settings screens
     * call this from their back navigation so an in-progress edit is not lost
     * when the screen's ViewModel is cleared.
     */
    suspend fun savePendingChanges() {
        nameInput.value?.let { settingsRepository.setProfileName(it) }
        diabetesTypeInput.value?.let { settingsRepository.setProfileDiabetesType(it) }
        heightInput.value?.let {
            settingsRepository.setProfileHeightCm(Formatters.parseDecimal(it)?.toFloat())
        }
        weightInput.value?.let {
            settingsRepository.setProfileWeightKg(Formatters.parseDecimal(it)?.toFloat())
        }
        baseUrlInput.value?.let { settingsRepository.setAiBaseUrl(it) }
        modelInput.value?.let { settingsRepository.setAiModel(it) }
    }

    fun saveApiKey(plainKey: String) {
        val trimmed = plainKey.trim()
        if (trimmed.isEmpty()) {
            viewModelScope.launch {
                _apiKeySaveMessages.emit(
                    ApiKeySaveMessage("API key can't be empty.", isError = true),
                )
            }
            return
        }

        viewModelScope.launch {
            try {
                settingsRepository.setApiKey(trimmed)
                _apiKeySaveMessages.emit(ApiKeySaveMessage("API Key saved"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _apiKeySaveMessages.emit(
                    ApiKeySaveMessage(
                        "Couldn't save API key: ${e.message ?: "Unknown error"}",
                        isError = true,
                    ),
                )
            }
        }
    }

    fun onPermissionsResult(granted: Set<String>) {
        val connected = granted.containsAll(readPermissions)
        _isHealthConnectConnected.value = connected
        viewModelScope.launch {
            if (connected) {
                settingsDataStore.setHealthConnectRevoked(false)
                settingsDataStore.setHealthConnectSyncEnabled(true)
                HealthConnectSyncWorker.schedulePeriodic(context)
                enqueueHealthConnectSync()
            } else {
                settingsDataStore.setHealthConnectSyncEnabled(false)
                HealthConnectSyncWorker.cancelPeriodic(context)
            }
            refreshHealthConnectSampleCount()
        }
    }

    fun dismissHealthConnectRevokedBanner() {
        viewModelScope.launch {
            settingsDataStore.setHealthConnectRevoked(false)
        }
    }

    fun setGlucoseImportEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setGlucoseImportEnabled(enabled) }

    fun setNutritionImportEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setNutritionImportEnabled(enabled) }

    fun setExerciseImportEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setExerciseImportEnabled(enabled) }

    fun setSleepImportEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setSleepImportEnabled(enabled) }

    fun setCycleImportEnabled(enabled: Boolean) =
        viewModelScope.launch { settingsDataStore.setCycleImportEnabled(enabled) }

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
            val connected = granted.containsAll(readPermissions)
            _isHealthConnectConnected.value = connected
            if (!connected && settingsDataStore.healthConnectSyncEnabled.first()) {
                settingsDataStore.setHealthConnectSyncEnabled(false)
                HealthConnectSyncWorker.cancelPeriodic(context)
                settingsDataStore.setHealthConnectRevoked(true)
            }
            refreshHealthConnectSampleCount()
        }
    }

    private suspend fun refreshHealthConnectSampleCount() {
        _healthConnectSampleCount.value = glucoseSampleDao.count()
    }

    private fun enqueueHealthConnectSync() {
        HealthConnectSyncWorker.enqueueExpedited(context, ExistingWorkPolicy.REPLACE)
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = null
            persistUriPermission(uri)
            runCatching { backupManager.exportTo(uri) }
                .onSuccess { summary ->
                    _backupMessage.value = "Backed up ${summary.counts.entries} entries."
                }
                .onFailure { _backupMessage.value = it.message ?: "Backup failed." }
        }
    }

    fun exportMarkdown(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = null
            persistUriPermission(uri)
            runCatching { markdownExporter.exportToTree(uri) }
                .onSuccess { files ->
                    _backupMessage.value = if (files.isEmpty()) {
                        "No log entries to export."
                    } else {
                        "Saved ${files.size} Markdown ${if (files.size == 1) "file" else "files"}."
                    }
                }
                .onFailure { _backupMessage.value = it.message ?: "Markdown export failed." }
        }
    }

    fun previewImport(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = null
            persistUriPermission(uri)
            runCatching { backupManager.previewFrom(uri) }
                .onSuccess { _backupPreview.value = it }
                .onFailure { _backupMessage.value = it.message ?: "Couldn't read backup." }
        }
    }

    fun dismissImportPreview() {
        _backupPreview.value = null
    }

    fun importBackup(uri: Uri, mode: ImportMode) {
        viewModelScope.launch {
            _backupPreview.value = null
            _backupMessage.value = null
            persistUriPermission(uri)
            runCatching { backupManager.importFrom(uri, mode) }
                .onSuccess {
                    _backupMessage.value = when (mode) {
                        ImportMode.MERGE -> "Backup merged."
                        ImportMode.REPLACE -> "Backup restored."
                    }
                }
                .onFailure { _backupMessage.value = it.message ?: "Import failed." }
        }
    }

    fun restoreLatestSnapshot() {
        viewModelScope.launch {
            _backupPreview.value = null
            _backupMessage.value = null
            runCatching { backupManager.restoreLatestPreImportSnapshot() }
                .onSuccess { _backupMessage.value = "Last snapshot restored." }
                .onFailure { _backupMessage.value = it.message ?: "Couldn't restore snapshot." }
        }
    }

    fun exportEncryptedBackup(uri: Uri) {
        viewModelScope.launch {
            _backupMessage.value = null
            persistUriPermission(uri)
            runCatching { backupManager.exportEncryptedTo(uri) }
                .onSuccess { summary ->
                    _backupMessage.value = "Encrypted backup saved (${summary.counts.entries} entries)."
                }
                .onFailure { _backupMessage.value = it.message ?: "Encrypted backup failed." }
        }
    }

    fun uploadEncryptedCloudBackup() {
        viewModelScope.launch {
            _backupMessage.value = null
            runCatching { encryptedCloudBackupManager.uploadEncrypted() }
                .onSuccess { file ->
                    _backupMessage.value = "Uploaded encrypted backup ${file.name}."
                }
                .onFailure { _backupMessage.value = it.message ?: "Cloud upload failed." }
        }
    }

    fun restoreEncryptedCloudBackup() {
        viewModelScope.launch {
            _backupPreview.value = null
            _backupMessage.value = null
            runCatching { encryptedCloudBackupManager.restoreLatest(ImportMode.REPLACE) }
                .onSuccess { _backupMessage.value = "Encrypted cloud backup restored." }
                .onFailure { _backupMessage.value = it.message ?: "Cloud restore failed." }
        }
    }

    fun setCloudProvider(provider: CloudBackupProvider) {
        viewModelScope.launch { encryptedCloudBackupManager.setProvider(provider) }
    }

    fun saveWebDav(url: String, username: String, password: String) {
        viewModelScope.launch {
            runCatching { encryptedCloudBackupManager.saveWebDav(url, username, password) }
                .onSuccess { _backupMessage.value = "WebDAV settings saved." }
                .onFailure { _backupMessage.value = it.message ?: "Couldn't save WebDAV settings." }
        }
    }

    fun saveDriveToken(token: String) {
        viewModelScope.launch {
            runCatching { encryptedCloudBackupManager.saveDriveAccessToken(token) }
                .onSuccess { _backupMessage.value = "Drive token saved on this device." }
                .onFailure { _backupMessage.value = it.message ?: "Couldn't save Drive token." }
        }
    }

    fun clearCloudCredentials() {
        viewModelScope.launch {
            encryptedCloudBackupManager.clearCloudCredentials()
            _backupMessage.value = "Cloud backup credentials cleared."
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setBackupEnabled(enabled) }
    }

    fun setBackupFolder(uri: Uri?) {
        viewModelScope.launch {
            if (uri != null) {
                persistUriPermission(uri)
                settingsDataStore.setBackupDirUri(uri.toString())
                settingsDataStore.setBackupEnabled(true)
                _backupMessage.value = "Automatic backups enabled."
            } else {
                settingsDataStore.setBackupDirUri(null)
                settingsDataStore.setBackupEnabled(false)
                _backupMessage.value = "Automatic backups disabled."
            }
        }
    }

    private fun persistUriPermission(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 400L
    }
}
