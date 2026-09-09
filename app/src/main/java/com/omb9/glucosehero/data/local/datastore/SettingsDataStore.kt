package com.omb9.glucosehero.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omb9.glucosehero.data.backup.CloudBackupProvider
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.crisis.CaregiverContact
import com.omb9.glucosehero.crisis.HypoSosPending
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.util.AiQuota
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.util.CrisisDetector
import kotlinx.serialization.encodeToString
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent")
        val UNIT = stringPreferencesKey("glucose_unit")
        val USE_24H = booleanPreferencesKey("use_24h")
        val TARGET_LOW = floatPreferencesKey("target_low_mgdl")
        val TARGET_HIGH = floatPreferencesKey("target_high_mgdl")
        val HERO_AI_ENABLED = booleanPreferencesKey("hero_ai_enabled")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val AI_API_KEY_ENC = stringPreferencesKey("ai_api_key_enc")
        val AI_QUOTA_COUNT = intPreferencesKey("ai_quota_count")
        val AI_QUOTA_DAY = stringPreferencesKey("ai_quota_day")
        val SHOW_ADVANCED_MACROS = booleanPreferencesKey("show_advanced_macros")
        val POST_MEAL_REMINDERS_ENABLED = booleanPreferencesKey("post_meal_reminders_enabled")
        val SEND_MEAL_PHOTOS_TO_HERO_AI = booleanPreferencesKey("send_meal_photos_to_hero_ai")
        val PROFILE_TARGET = stringPreferencesKey("profile_target")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_AGE = intPreferencesKey("profile_age")
        val PROFILE_DIABETES_TYPE = stringPreferencesKey("profile_diabetes_type")
        val PROFILE_HEIGHT_CM = floatPreferencesKey("profile_height_cm")
        val PROFILE_WEIGHT_KG = floatPreferencesKey("profile_weight_kg")
        val DIA_HOURS = floatPreferencesKey("dia_hours")
        val CIR_RATIO = floatPreferencesKey("cir_ratio")
        val ISF_MGDL = floatPreferencesKey("isf_mgdl")
        val TARGET_GLUCOSE_MGDL = floatPreferencesKey("target_glucose_mgdl")
        val HEALTH_CONNECT_SYNC_ENABLED = booleanPreferencesKey("health_connect_sync_enabled")
        val HEALTH_CONNECT_REVOKED = booleanPreferencesKey("health_connect_revoked")
        val HEALTH_CONNECT_CHANGES_TOKEN = stringPreferencesKey("health_connect_changes_token")
        val HEALTH_CONNECT_LAST_SYNC = longPreferencesKey("health_connect_last_sync")
        val GLUCOSE_IMPORT_ENABLED = booleanPreferencesKey("glucose_import_enabled")
        val NUTRITION_IMPORT_ENABLED = booleanPreferencesKey("nutrition_import_enabled")
        val EXERCISE_IMPORT_ENABLED = booleanPreferencesKey("exercise_import_enabled")
        val SLEEP_IMPORT_ENABLED = booleanPreferencesKey("sleep_import_enabled")
        val CYCLE_IMPORT_ENABLED = booleanPreferencesKey("cycle_import_enabled")
        val HEALTH_CONNECT_INITIAL_IMPORT_RANGE = stringPreferencesKey("health_connect_initial_import_range")
        val BARCODE_LOOKUP_ENABLED = booleanPreferencesKey("barcode_lookup_enabled")
        val AI_ACKNOWLEDGED_HOSTS = stringSetPreferencesKey("ai_acknowledged_hosts")
        val DISMISSED_FOOD_TAGS = stringSetPreferencesKey("dismissed_food_tags")
        val BACKUP_DIR_URI = stringPreferencesKey("backup_dir_uri")
        val BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        val BACKUP_LAST_RUN = longPreferencesKey("backup_last_run")
        val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        val CLOUD_BACKUP_PROVIDER = stringPreferencesKey("cloud_backup_provider")
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD_ENC = stringPreferencesKey("webdav_password_enc")
        val DRIVE_ACCESS_TOKEN_ENC = stringPreferencesKey("drive_access_token_enc")
        val CLOUD_BACKUP_LAST_RUN = longPreferencesKey("cloud_backup_last_run")
        val CLOUD_BACKUP_REMOTE_NAME = stringPreferencesKey("cloud_backup_remote_name")
        val CLOUD_BACKUP_REMOTE_ID = stringPreferencesKey("cloud_backup_remote_id")
        val GLUCOSE_FORECAST_JSON = stringPreferencesKey("glucose_forecast_json")
        val EXERCISE_FUELING_ALERTS_ENABLED = booleanPreferencesKey("exercise_fueling_alerts_enabled")
        val EXERCISE_FUELING_LAST_ALERT = longPreferencesKey("exercise_fueling_last_alert")
        val HYPO_SOS_ENABLED = booleanPreferencesKey("hypo_sos_enabled")
        val HYPO_SOS_TIMEOUT_MINUTES = intPreferencesKey("hypo_sos_timeout_minutes")
        val HYPO_SOS_PENDING_JSON = stringPreferencesKey("hypo_sos_pending_json")
        val HYPO_SOS_LAST_DISMISS = longPreferencesKey("hypo_sos_last_dismiss")
        val HYPO_SOS_LAST_SENT = longPreferencesKey("hypo_sos_last_sent")
        val CAREGIVER_CONTACTS_JSON = stringPreferencesKey("caregiver_contacts_json")
        val CLINICAL_TEST_SESSION_JSON = stringPreferencesKey("clinical_test_session_json")
    }

    /**
     * All reads go through this flow: DataStore surfaces disk/corruption
     * problems as an IOException thrown *into the flow*, which would
     * otherwise propagate through MainActivity's eager stateIn and crash the
     * app on every launch. On a fresh install with a restored backup
     * (allowBackup=true) the preferences file can also contain a stale type
     * for [Keys.HERO_AI_ENABLED] (e.g. String from a dev build where the
     * toggle was prototyped as a string) — reading it with
     * [booleanPreferencesKey] throws [ClassCastException]. That is a
     * framework-level mismatch, not a programming error, so we degrade to
     * empty preferences (all defaults) instead of crashing. Any other
     * non-IO failure still propagates.
     */
    private val safeData: Flow<Preferences> = context.dataStore.data
        .catch { e ->
            if (e is IOException || e is ClassCastException) emit(emptyPreferences()) else throw e
        }

    val settings: Flow<UserSettings> = safeData.map { p ->
        UserSettings(
            themeMode = p[Keys.THEME_MODE].toEnum(ThemeMode.LIGHT),
            accent = p[Keys.ACCENT].toEnum(AccentColor.LIGHT_RED),
            unit = p[Keys.UNIT].toEnum(GlucoseUnit.MGDL),
            // THEME_MODE/ACCENT/UNIT are stringPreferencesKeys: a type
            // mismatch falls through toEnum()'s default via getOrNull, so no
            // exception. HERO_AI_ENABLED and USE_24H are booleans and target
            // bounds are floats — a stale type in the file (e.g. String from
            // a dev build, or a restored backup after allowBackup) throws
            // ClassCastException from Preferences.get(). That exception is
            // raised inside this map, DOWNSTREAM of safeData's catch, so it
            // would propagate through MainActivity's eager stateIn and crash
            // on launch even on a fresh install (restored preferences). Guard
            // each typed read individually and degrade only that field to its
            // default.
            use24HourTime = runCatching { p[Keys.USE_24H] }.getOrNull() ?: false,
            isHeroAiEnabled = runCatching { p[Keys.HERO_AI_ENABLED] }.getOrNull() ?: true,
            showAdvancedMacros = runCatching { p[Keys.SHOW_ADVANCED_MACROS] }.getOrNull() ?: false,
            postMealRemindersEnabled = runCatching { p[Keys.POST_MEAL_REMINDERS_ENABLED] }.getOrNull() ?: true,
            sendMealPhotosToHeroAi = runCatching { p[Keys.SEND_MEAL_PHOTOS_TO_HERO_AI] }.getOrNull() ?: false,
            targetLowMgdl = runCatching { p[Keys.TARGET_LOW] }.getOrNull() ?: 70f,
            targetHighMgdl = runCatching { p[Keys.TARGET_HIGH] }.getOrNull() ?: 180f,
        )
    }

    val barcodeLookupEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.BARCODE_LOOKUP_ENABLED] }.getOrNull() ?: true
    }

    /** Hosts the user explicitly trusted for a custom OpenAI-compatible endpoint. */
    val acknowledgedAiHosts: Flow<Set<String>> = safeData.map { p ->
        runCatching { p[Keys.AI_ACKNOWLEDGED_HOSTS] }.getOrNull() ?: emptySet()
    }

    suspend fun acknowledgedAiHostsSnapshot(): Set<String> =
        runCatching { safeData.first()[Keys.AI_ACKNOWLEDGED_HOSTS] }.getOrNull() ?: emptySet()

    /** Tags dismissed on the Food impact screen; persists across nightly recomputation. */
    val dismissedFoodTags: Flow<Set<String>> = safeData.map { p ->
        runCatching { p[Keys.DISMISSED_FOOD_TAGS] }.getOrNull() ?: emptySet()
    }

    val aiConfig: Flow<AiConfig> = safeData.map { p -> p.toAiConfig() }

    /** Effective "used today" count after applying the local-day reset rule. */
    val aiQuotaUsedToday: Flow<Int> = safeData.map { p -> p.aiQuotaUsedToday() }

    val profile: Flow<UserProfile> = safeData.map { p -> p.toUserProfile() }

    val bolusSettings: Flow<BolusSettings> = safeData.map { p -> p.toBolusSettings() }

    val healthConnectSyncEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.HEALTH_CONNECT_SYNC_ENABLED] }.getOrNull() ?: false
    }

    val healthConnectRevoked: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.HEALTH_CONNECT_REVOKED] }.getOrNull() ?: false
    }

    val healthConnectChangesToken: Flow<String?> = safeData.map { p ->
        runCatching { p[Keys.HEALTH_CONNECT_CHANGES_TOKEN] }.getOrNull()
    }

    val healthConnectLastSync: Flow<Long?> = safeData.map { p ->
        runCatching { p[Keys.HEALTH_CONNECT_LAST_SYNC] }.getOrNull()
    }

    val glucoseImportEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.GLUCOSE_IMPORT_ENABLED] }.getOrNull() ?: true
    }

    val nutritionImportEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.NUTRITION_IMPORT_ENABLED] }.getOrNull() ?: false
    }

    val exerciseImportEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.EXERCISE_IMPORT_ENABLED] }.getOrNull() ?: false
    }

    val sleepImportEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.SLEEP_IMPORT_ENABLED] }.getOrNull() ?: false
    }

    val cycleImportEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.CYCLE_IMPORT_ENABLED] }.getOrNull() ?: false
    }

    val healthConnectInitialImportRange: Flow<InitialImportRange> = safeData.map { p ->
        runCatching { p[Keys.HEALTH_CONNECT_INITIAL_IMPORT_RANGE] }
            .getOrNull().toEnum(InitialImportRange.DAYS_90)
    }

    /** Persisted document-tree URI nominated for scheduled auto-backups. */
    val backupDirUri: Flow<String?> = safeData.map { p ->
        runCatching { p[Keys.BACKUP_DIR_URI] }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Whether the daily auto-backup schedule is enabled. */
    val backupEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.BACKUP_ENABLED] }.getOrNull() ?: false
    }

    /** Epoch millis of the last successful auto-backup, if any. */
    val backupLastRun: Flow<Long?> = safeData.map { p ->
        runCatching { p[Keys.BACKUP_LAST_RUN] }.getOrNull()
    }

    val webhookUrl: Flow<String> = safeData.map { p ->
        runCatching { p[Keys.WEBHOOK_URL] }.getOrNull().orEmpty()
    }

    val cloudBackupProvider: Flow<CloudBackupProvider> = safeData.map { p ->
        runCatching { p[Keys.CLOUD_BACKUP_PROVIDER] }.getOrNull().toEnum(CloudBackupProvider.NONE)
    }

    val webDavUrl: Flow<String> = safeData.map { p ->
        runCatching { p[Keys.WEBDAV_URL] }.getOrNull().orEmpty()
    }

    val webDavUsername: Flow<String> = safeData.map { p ->
        runCatching { p[Keys.WEBDAV_USERNAME] }.getOrNull().orEmpty()
    }

    val webDavPasswordEnc: Flow<String?> = safeData.map { p ->
        runCatching { p[Keys.WEBDAV_PASSWORD_ENC] }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    val hasWebDavPassword: Flow<Boolean> = webDavPasswordEnc.map { !it.isNullOrBlank() }

    val driveAccessTokenEnc: Flow<String?> = safeData.map { p ->
        runCatching { p[Keys.DRIVE_ACCESS_TOKEN_ENC] }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    val hasDriveAccessToken: Flow<Boolean> = driveAccessTokenEnc.map { !it.isNullOrBlank() }

    val cloudBackupLastRun: Flow<Long?> = safeData.map { p ->
        runCatching { p[Keys.CLOUD_BACKUP_LAST_RUN] }.getOrNull()
    }

    val cloudBackupRemoteName: Flow<String?> = safeData.map { p ->
        runCatching { p[Keys.CLOUD_BACKUP_REMOTE_NAME] }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    val cloudBackupRemoteId: Flow<String?> = safeData.map { p ->
        runCatching { p[Keys.CLOUD_BACKUP_REMOTE_ID] }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    val glucoseForecastJson: Flow<String?> = safeData.map { p ->
        runCatching { p[Keys.GLUCOSE_FORECAST_JSON] }.getOrNull()
    }

    val exerciseFuelingAlertsEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.EXERCISE_FUELING_ALERTS_ENABLED] }.getOrNull() ?: true
    }

    val exerciseFuelingLastAlertMillis: Flow<Long?> = safeData.map { p ->
        runCatching { p[Keys.EXERCISE_FUELING_LAST_ALERT] }.getOrNull()
    }

    val hypoSosEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.HYPO_SOS_ENABLED] }.getOrNull() ?: false
    }

    val hypoSosTimeoutMinutes: Flow<Int> = safeData.map { p ->
        CrisisDetector.clampSosTimeoutMinutes(
            runCatching { p[Keys.HYPO_SOS_TIMEOUT_MINUTES] }.getOrNull()
                ?: CrisisDetector.DEFAULT_SOS_TIMEOUT_MINUTES,
        )
    }

    val hypoSosPendingJson: Flow<String?> = safeData.map { p ->
        runCatching { p[Keys.HYPO_SOS_PENDING_JSON] }.getOrNull()
    }

    val hypoSosPending: Flow<HypoSosPending?> = hypoSosPendingJson.map { raw ->
        if (raw.isNullOrBlank()) null
        else runCatching { AppJson.decodeFromString<HypoSosPending>(raw) }.getOrNull()
    }

    val hypoSosLastDismissMillis: Flow<Long?> = safeData.map { p ->
        runCatching { p[Keys.HYPO_SOS_LAST_DISMISS] }.getOrNull()
    }

    val hypoSosLastSentMillis: Flow<Long?> = safeData.map { p ->
        runCatching { p[Keys.HYPO_SOS_LAST_SENT] }.getOrNull()
    }

    val caregiverContacts: Flow<List<CaregiverContact>> = safeData.map { p ->
        val raw = runCatching { p[Keys.CAREGIVER_CONTACTS_JSON] }.getOrNull()
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { AppJson.decodeFromString<List<CaregiverContact>>(raw) }.getOrNull()
            ?: emptyList()
    }

    val clinicalTestSessionJson: Flow<String?> = safeData.map { p ->
        runCatching { p[Keys.CLINICAL_TEST_SESSION_JSON] }.getOrNull()
    }

    /** Single fresh snapshot used by the export utility when assembling a report. */
    suspend fun profileSnapshot(): UserProfile = safeData.first().toUserProfile()

    /** Single fresh snapshot of the insulin-dosing parameters. */
    suspend fun bolusSettingsSnapshot(): BolusSettings = safeData.first().toBolusSettings()

    /** Single fresh snapshot — read per-request by the network layer. */
    suspend fun aiConfigSnapshot(): AiConfig = safeData.first().toAiConfig()

    suspend fun encryptedApiKey(): String? =
        runCatching { safeData.first()[Keys.AI_API_KEY_ENC] }
            .getOrNull()?.takeIf { it.isNotBlank() }

    suspend fun aiQuotaUsedTodaySnapshot(): Int = safeData.first().aiQuotaUsedToday()

    /** Single fresh snapshot — read once per saved entry by the webhook broadcaster. */
    suspend fun webhookUrlSnapshot(): String =
        runCatching { safeData.first()[Keys.WEBHOOK_URL] }.getOrNull().orEmpty()

    private fun Preferences.aiQuotaUsedToday(): Int {
        val storedDay = runCatching { this[Keys.AI_QUOTA_DAY] }.getOrNull()
        val today = AiQuota.todayDay()
        return if (AiQuota.shouldReset(storedDay, today)) {
            0
        } else {
            runCatching { this[Keys.AI_QUOTA_COUNT] }.getOrNull() ?: 0
        }
    }

    private fun Preferences.toAiConfig(): AiConfig {
        val provider = runCatching { this[Keys.AI_PROVIDER] }
            .getOrNull().toEnum(AiProvider.GEMINI)
        return AiConfig(
            provider = provider,
            baseUrl = runCatching { this[Keys.AI_BASE_URL] }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
            model = runCatching { this[Keys.AI_MODEL] }
                .getOrNull()?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
            hasApiKey = !runCatching { this[Keys.AI_API_KEY_ENC] }.getOrNull().isNullOrBlank(),
        )
    }

    private fun Preferences.toUserProfile(): UserProfile = UserProfile(
        profileTarget = runCatching { this[Keys.PROFILE_TARGET] }
            .getOrNull().toEnum(ProfileTarget.SELF),
        name = runCatching { this[Keys.PROFILE_NAME] }.getOrNull().orEmpty(),
        age = runCatching { this[Keys.PROFILE_AGE] }.getOrNull(),
        diabetesType = runCatching { this[Keys.PROFILE_DIABETES_TYPE] }
            .getOrNull()?.takeIf { it.isNotBlank() },
        heightCm = runCatching { this[Keys.PROFILE_HEIGHT_CM] }.getOrNull(),
        weightKg = runCatching { this[Keys.PROFILE_WEIGHT_KG] }.getOrNull(),
    )

    private fun Preferences.toBolusSettings(): BolusSettings = BolusSettings(
        diaHours = runCatching { this[Keys.DIA_HOURS] }.getOrNull() ?: 4.0f,
        cirRatio = runCatching { this[Keys.CIR_RATIO] }.getOrNull() ?: 10.0f,
        isfMgdl = runCatching { this[Keys.ISF_MGDL] }.getOrNull() ?: 50.0f,
        targetGlucoseMgdl = runCatching { this[Keys.TARGET_GLUCOSE_MGDL] }.getOrNull() ?: 100.0f,
    )

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setAccent(accent: AccentColor) = edit { it[Keys.ACCENT] = accent.name }
    suspend fun setUnit(unit: GlucoseUnit) = edit { it[Keys.UNIT] = unit.name }
    suspend fun setUse24HourTime(enabled: Boolean) = edit { it[Keys.USE_24H] = enabled }
    suspend fun setIsHeroAiEnabled(enabled: Boolean) = edit { it[Keys.HERO_AI_ENABLED] = enabled }
    suspend fun setShowAdvancedMacros(enabled: Boolean) = edit { it[Keys.SHOW_ADVANCED_MACROS] = enabled }
    suspend fun setPostMealRemindersEnabled(enabled: Boolean) =
        edit { it[Keys.POST_MEAL_REMINDERS_ENABLED] = enabled }

    suspend fun setSendMealPhotosToHeroAi(enabled: Boolean) =
        edit { it[Keys.SEND_MEAL_PHOTOS_TO_HERO_AI] = enabled }

    suspend fun setBarcodeLookupEnabled(enabled: Boolean) =
        edit { it[Keys.BARCODE_LOOKUP_ENABLED] = enabled }

    suspend fun acknowledgeAiHost(host: String) {
        val normalized = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (normalized.isBlank()) return
        edit {
            val current = it[Keys.AI_ACKNOWLEDGED_HOSTS] ?: emptySet()
            it[Keys.AI_ACKNOWLEDGED_HOSTS] = current + normalized
        }
    }

    suspend fun dismissFoodTag(tag: String) = edit {
        val current = it[Keys.DISMISSED_FOOD_TAGS] ?: emptySet()
        it[Keys.DISMISSED_FOOD_TAGS] = current + tag
    }

    suspend fun restoreFoodTag(tag: String) = edit {
        val current = it[Keys.DISMISSED_FOOD_TAGS] ?: emptySet()
        it[Keys.DISMISSED_FOOD_TAGS] = current - tag
    }

    suspend fun setHealthConnectSyncEnabled(enabled: Boolean) =
        edit { it[Keys.HEALTH_CONNECT_SYNC_ENABLED] = enabled }

    suspend fun setHealthConnectRevoked(revoked: Boolean) =
        edit { it[Keys.HEALTH_CONNECT_REVOKED] = revoked }

    suspend fun setHealthConnectChangesToken(token: String?) = edit {
        if (token == null) it.remove(Keys.HEALTH_CONNECT_CHANGES_TOKEN)
        else it[Keys.HEALTH_CONNECT_CHANGES_TOKEN] = token
    }

    suspend fun setHealthConnectLastSync(timestamp: Long) =
        edit { it[Keys.HEALTH_CONNECT_LAST_SYNC] = timestamp }

    suspend fun setGlucoseImportEnabled(enabled: Boolean) =
        edit { it[Keys.GLUCOSE_IMPORT_ENABLED] = enabled }

    suspend fun setNutritionImportEnabled(enabled: Boolean) =
        edit { it[Keys.NUTRITION_IMPORT_ENABLED] = enabled }

    suspend fun setExerciseImportEnabled(enabled: Boolean) =
        edit { it[Keys.EXERCISE_IMPORT_ENABLED] = enabled }

    suspend fun setSleepImportEnabled(enabled: Boolean) =
        edit { it[Keys.SLEEP_IMPORT_ENABLED] = enabled }

    suspend fun setCycleImportEnabled(enabled: Boolean) =
        edit { it[Keys.CYCLE_IMPORT_ENABLED] = enabled }

    suspend fun setHealthConnectInitialImportRange(range: InitialImportRange) =
        edit { it[Keys.HEALTH_CONNECT_INITIAL_IMPORT_RANGE] = range.name }

    suspend fun setBackupDirUri(uri: String?) = edit {
        if (uri.isNullOrBlank()) it.remove(Keys.BACKUP_DIR_URI) else it[Keys.BACKUP_DIR_URI] = uri
    }

    suspend fun setBackupEnabled(enabled: Boolean) = edit { it[Keys.BACKUP_ENABLED] = enabled }

    suspend fun setBackupLastRun(timestamp: Long) = edit { it[Keys.BACKUP_LAST_RUN] = timestamp }

    suspend fun setWebhookUrl(url: String) = edit {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) it.remove(Keys.WEBHOOK_URL) else it[Keys.WEBHOOK_URL] = trimmed
    }

    suspend fun setCloudBackupProvider(provider: CloudBackupProvider) = edit {
        it[Keys.CLOUD_BACKUP_PROVIDER] = provider.name
    }

    suspend fun setWebDavUrl(url: String) = edit {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) it.remove(Keys.WEBDAV_URL) else it[Keys.WEBDAV_URL] = trimmed
    }

    suspend fun setWebDavUsername(username: String) = edit {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) it.remove(Keys.WEBDAV_USERNAME) else it[Keys.WEBDAV_USERNAME] = trimmed
    }

    suspend fun setWebDavPasswordEnc(encrypted: String?) = edit {
        if (encrypted.isNullOrBlank()) it.remove(Keys.WEBDAV_PASSWORD_ENC)
        else it[Keys.WEBDAV_PASSWORD_ENC] = encrypted
    }

    suspend fun setDriveAccessTokenEnc(encrypted: String?) = edit {
        if (encrypted.isNullOrBlank()) it.remove(Keys.DRIVE_ACCESS_TOKEN_ENC)
        else it[Keys.DRIVE_ACCESS_TOKEN_ENC] = encrypted
    }

    suspend fun setCloudBackupLastRun(timestamp: Long) = edit {
        it[Keys.CLOUD_BACKUP_LAST_RUN] = timestamp
    }

    suspend fun setCloudBackupRemoteName(name: String?) = edit {
        if (name.isNullOrBlank()) it.remove(Keys.CLOUD_BACKUP_REMOTE_NAME)
        else it[Keys.CLOUD_BACKUP_REMOTE_NAME] = name
    }

    suspend fun setCloudBackupRemoteId(id: String?) = edit {
        if (id.isNullOrBlank()) it.remove(Keys.CLOUD_BACKUP_REMOTE_ID)
        else it[Keys.CLOUD_BACKUP_REMOTE_ID] = id
    }

    suspend fun setGlucoseForecastJson(json: String?) = edit {
        if (json.isNullOrBlank()) it.remove(Keys.GLUCOSE_FORECAST_JSON)
        else it[Keys.GLUCOSE_FORECAST_JSON] = json
    }

    suspend fun setExerciseFuelingAlertsEnabled(enabled: Boolean) =
        edit { it[Keys.EXERCISE_FUELING_ALERTS_ENABLED] = enabled }

    suspend fun setExerciseFuelingLastAlertMillis(timestamp: Long) =
        edit { it[Keys.EXERCISE_FUELING_LAST_ALERT] = timestamp }

    suspend fun setHypoSosEnabled(enabled: Boolean) =
        edit { it[Keys.HYPO_SOS_ENABLED] = enabled }

    suspend fun setHypoSosTimeoutMinutes(minutes: Int) =
        edit { it[Keys.HYPO_SOS_TIMEOUT_MINUTES] = CrisisDetector.clampSosTimeoutMinutes(minutes) }

    suspend fun setHypoSosPendingJson(json: String?) = edit {
        if (json.isNullOrBlank()) it.remove(Keys.HYPO_SOS_PENDING_JSON)
        else it[Keys.HYPO_SOS_PENDING_JSON] = json
    }

    suspend fun setHypoSosLastDismissMillis(timestamp: Long) =
        edit { it[Keys.HYPO_SOS_LAST_DISMISS] = timestamp }

    suspend fun setHypoSosLastSentMillis(timestamp: Long) =
        edit { it[Keys.HYPO_SOS_LAST_SENT] = timestamp }

    suspend fun caregiverContactsSnapshot(): List<CaregiverContact> =
        caregiverContacts.first()

    suspend fun setCaregiverContacts(contacts: List<CaregiverContact>) = edit {
        if (contacts.isEmpty()) it.remove(Keys.CAREGIVER_CONTACTS_JSON)
        else it[Keys.CAREGIVER_CONTACTS_JSON] = AppJson.encodeToString(contacts)
    }

    suspend fun setClinicalTestSessionJson(json: String?) = edit {
        if (json.isNullOrBlank()) it.remove(Keys.CLINICAL_TEST_SESSION_JSON)
        else it[Keys.CLINICAL_TEST_SESSION_JSON] = json
    }

    suspend fun setProfileTarget(target: ProfileTarget) =
        edit { it[Keys.PROFILE_TARGET] = target.name }

    suspend fun setProfileName(name: String) = edit {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) it.remove(Keys.PROFILE_NAME) else it[Keys.PROFILE_NAME] = trimmed
    }

    suspend fun setProfileAge(age: Int?) = edit {
        if (age == null) it.remove(Keys.PROFILE_AGE) else it[Keys.PROFILE_AGE] = age
    }

    suspend fun setProfileDiabetesType(type: String?) = edit {
        val trimmed = type?.trim().orEmpty()
        if (trimmed.isEmpty()) it.remove(Keys.PROFILE_DIABETES_TYPE)
        else it[Keys.PROFILE_DIABETES_TYPE] = trimmed
    }

    suspend fun setProfileHeightCm(heightCm: Float?) = edit {
        if (heightCm == null) it.remove(Keys.PROFILE_HEIGHT_CM) else it[Keys.PROFILE_HEIGHT_CM] = heightCm
    }

    suspend fun setProfileWeightKg(weightKg: Float?) = edit {
        if (weightKg == null) it.remove(Keys.PROFILE_WEIGHT_KG) else it[Keys.PROFILE_WEIGHT_KG] = weightKg
    }

    suspend fun setTargetRange(low: Float, high: Float) = edit {
        it[Keys.TARGET_LOW] = low
        it[Keys.TARGET_HIGH] = high
    }

    suspend fun setDiaHours(diaHours: Float) = edit { it[Keys.DIA_HOURS] = diaHours }
    suspend fun setCirRatio(ratio: Float) = edit { it[Keys.CIR_RATIO] = ratio }
    suspend fun setIsfMgdl(isf: Float) = edit { it[Keys.ISF_MGDL] = isf }
    suspend fun setTargetGlucoseMgdl(target: Float) = edit { it[Keys.TARGET_GLUCOSE_MGDL] = target }

    /** Switching provider resets base URL + model to the preset defaults. */
    suspend fun setAiProvider(provider: AiProvider) = edit {
        it[Keys.AI_PROVIDER] = provider.name
        it[Keys.AI_BASE_URL] = provider.defaultBaseUrl
        it[Keys.AI_MODEL] = provider.defaultModel
    }

    suspend fun setAiBaseUrl(url: String) = edit { it[Keys.AI_BASE_URL] = url.trim() }
    suspend fun setAiModel(model: String) = edit { it[Keys.AI_MODEL] = model.trim() }

    suspend fun setEncryptedApiKey(encrypted: String?) = edit {
        if (encrypted.isNullOrBlank()) it.remove(Keys.AI_API_KEY_ENC)
        else it[Keys.AI_API_KEY_ENC] = encrypted
    }

    /**
     * Increments the managed-tier AI call counter and stamps the local day it
     * now belongs to. Callers must only invoke this after a successful response.
     */
    suspend fun incrementAiQuota() = edit { prefs ->
        val effective = prefs.aiQuotaUsedToday()
        prefs[Keys.AI_QUOTA_DAY] = AiQuota.todayDay()
        prefs[Keys.AI_QUOTA_COUNT] = effective + 1
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}

/** Bounds for the first Health Connect import after the user connects. */
enum class InitialImportRange(val label: String) {
    DAYS_30("30 days"),
    DAYS_90("90 days"),
    ALL("All"),
}
