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
import com.omb9.glucosehero.data.cgm.CgmGlucose
import com.omb9.glucosehero.data.cgm.CgmIngestSettings
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutAuthMode
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutLimits
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.DosingProfile
import com.omb9.glucosehero.domain.model.DosingProfileCodec
import com.omb9.glucosehero.domain.model.DosingProfileLoad
import com.omb9.glucosehero.domain.model.DosingProfileValidation
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock = Clock.systemUTC(),
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
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
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
        // FEATURE: dosing-profiles
        val DOSING_PROFILE_JSON = stringPreferencesKey("dosing_profile_json")
        val DOSING_PROFILE_EXPLAINER_SEEN = booleanPreferencesKey("dosing_profile_explainer_seen")
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
        // FEATURE: cgm-direct-ingest
        // Nightscout credential: ciphertext blob only (KeystoreManager.encrypt),
        // matching AI_API_KEY_ENC. Libre password stays Phase 4.
        val CGM_DEDUP_WINDOW_MILLIS = longPreferencesKey("cgm_dedup_window_millis")
        val CGM_NIGHTSCOUT_ENABLED = booleanPreferencesKey("cgm_nightscout_enabled")
        val CGM_NIGHTSCOUT_URL = stringPreferencesKey("cgm_nightscout_url")
        val CGM_NIGHTSCOUT_AUTH_MODE = stringPreferencesKey("cgm_nightscout_auth_mode")
        val CGM_NIGHTSCOUT_CREDENTIAL_ENC = stringPreferencesKey("cgm_nightscout_credential_enc")
        val CGM_NIGHTSCOUT_ACKNOWLEDGED_HOSTS =
            stringSetPreferencesKey("cgm_nightscout_acknowledged_hosts")
        val CGM_NIGHTSCOUT_BACKFILL_HOURS = intPreferencesKey("cgm_nightscout_backfill_hours")
        val CGM_NIGHTSCOUT_FOREGROUND_SERVICE =
            booleanPreferencesKey("cgm_nightscout_foreground_service")
        val CGM_XDRIP_BROADCAST_ENABLED = booleanPreferencesKey("cgm_xdrip_broadcast_enabled")
        val CGM_LIBRE_LINK_UP_ENABLED = booleanPreferencesKey("cgm_libre_link_up_enabled")
        val CGM_LAST_SUCCESS_NIGHTSCOUT = longPreferencesKey("cgm_last_success_nightscout")
        val CGM_LAST_SUCCESS_XDRIP_BROADCAST = longPreferencesKey("cgm_last_success_xdrip_broadcast")
        val CGM_LAST_SUCCESS_LIBRE_LINK_UP = longPreferencesKey("cgm_last_success_libre_link_up")
        val CGM_LAST_SUCCESS_HEALTH_CONNECT = longPreferencesKey("cgm_last_success_health_connect")
        val CGM_LAST_SUCCESS_MANUAL_IMPORT = longPreferencesKey("cgm_last_success_manual_import")
        val CGM_LAST_ERROR_NIGHTSCOUT = stringPreferencesKey("cgm_last_error_nightscout")
        val CGM_LAST_ERROR_XDRIP_BROADCAST = stringPreferencesKey("cgm_last_error_xdrip_broadcast")
        val CGM_LAST_ERROR_LIBRE_LINK_UP = stringPreferencesKey("cgm_last_error_libre_link_up")
        val CGM_LAST_ERROR_HEALTH_CONNECT = stringPreferencesKey("cgm_last_error_health_connect")
        val CGM_LAST_ERROR_MANUAL_IMPORT = stringPreferencesKey("cgm_last_error_manual_import")
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
            notificationsEnabled = runCatching { p[Keys.NOTIFICATIONS_ENABLED] }.getOrNull() ?: true,
            sendMealPhotosToHeroAi = runCatching { p[Keys.SEND_MEAL_PHOTOS_TO_HERO_AI] }.getOrNull() ?: false,
            targetLowMgdl = runCatching { p[Keys.TARGET_LOW] }.getOrNull() ?: 70f,
            targetHighMgdl = runCatching { p[Keys.TARGET_HIGH] }.getOrNull() ?: 180f,
        )
    }

    val barcodeLookupEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.BARCODE_LOOKUP_ENABLED] }.getOrNull() ?: true
    }

    val notificationsEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.NOTIFICATIONS_ENABLED] }.getOrNull() ?: true
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

    /**
     * Stored time-of-day profile. Absent JSON migrates from the four flat keys.
     * Unparseable or invalid JSON is [DosingProfileLoad.Invalid] and must not
     * be treated as a valid ISF/CIR/target schedule.
     *
     * FEATURE: dosing-profiles
     */
    val dosingProfile: Flow<DosingProfileLoad> = safeData.map { p -> p.toDosingProfileLoad() }

    val dosingProfileExplainerSeen: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.DOSING_PROFILE_EXPLAINER_SEEN] }.getOrNull() ?: false
    }

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

    // FEATURE: cgm-direct-ingest
    val cgmDedupWindowMillis: Flow<Long> = safeData.map { p ->
        runCatching { p[Keys.CGM_DEDUP_WINDOW_MILLIS] }.getOrNull()
            ?: CgmGlucose.DEFAULT_DEDUP_WINDOW_MILLIS
    }

    val nightscoutEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.CGM_NIGHTSCOUT_ENABLED] }.getOrNull() ?: false
    }

    val nightscoutUrl: Flow<String> = safeData.map { p ->
        runCatching { p[Keys.CGM_NIGHTSCOUT_URL] }.getOrNull().orEmpty()
    }

    val nightscoutAuthMode: Flow<NightscoutAuthMode> = safeData.map { p ->
        runCatching { p[Keys.CGM_NIGHTSCOUT_AUTH_MODE] }.getOrNull()
            .toEnum(NightscoutAuthMode.TOKEN)
    }

    val hasNightscoutCredential: Flow<Boolean> = safeData.map { p ->
        !runCatching { p[Keys.CGM_NIGHTSCOUT_CREDENTIAL_ENC] }.getOrNull().isNullOrBlank()
    }

    val acknowledgedNightscoutHosts: Flow<Set<String>> = safeData.map { p ->
        runCatching { p[Keys.CGM_NIGHTSCOUT_ACKNOWLEDGED_HOSTS] }.getOrNull() ?: emptySet()
    }

    val nightscoutBackfillHours: Flow<Int> = safeData.map { p ->
        NightscoutLimits.clampBackfillHours(
            runCatching { p[Keys.CGM_NIGHTSCOUT_BACKFILL_HOURS] }.getOrNull()
                ?: NightscoutLimits.DEFAULT_BACKFILL_HOURS,
        )
    }

    val nightscoutForegroundService: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.CGM_NIGHTSCOUT_FOREGROUND_SERVICE] }.getOrNull() ?: false
    }

    val xdripBroadcastEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.CGM_XDRIP_BROADCAST_ENABLED] }.getOrNull() ?: false
    }

    val libreLinkUpEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.CGM_LIBRE_LINK_UP_ENABLED] }.getOrNull() ?: false
    }

    val cgmIngestSettings: Flow<CgmIngestSettings> = safeData.map { p -> p.toCgmIngestSettings() }

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

    /**
     * Convenience "resolved for right now" snapshot. **Not for dosing math.**
     * Forecast, exercise, and clinical attribution must call
     * [dosingProfileSnapshot] and resolve explicitly. If the stored profile is
     * invalid this still returns [BolusSettings] built only from global DIA
     * plus the 00:00 *flat keys* so IOB UI can keep a DIA value; ISF/CIR/target
     * in that case are the legacy flats and must not be treated as a validated
     * schedule.
     *
     * FEATURE: dosing-profiles
     */
    suspend fun bolusSettingsSnapshot(): BolusSettings = safeData.first().toBolusSettings()

    suspend fun dosingProfileSnapshot(): DosingProfileLoad =
        safeData.first().toDosingProfileLoad()

    /** Single fresh snapshot — read per-request by the network layer. */
    suspend fun aiConfigSnapshot(): AiConfig = safeData.first().toAiConfig()

    suspend fun encryptedApiKey(): String? =
        runCatching { safeData.first()[Keys.AI_API_KEY_ENC] }
            .getOrNull()?.takeIf { it.isNotBlank() }

    suspend fun aiQuotaUsedTodaySnapshot(): Int = safeData.first().aiQuotaUsedToday()

    /** Single fresh snapshot — read once per saved entry by the webhook broadcaster. */
    suspend fun webhookUrlSnapshot(): String =
        runCatching { safeData.first()[Keys.WEBHOOK_URL] }.getOrNull().orEmpty()

    // FEATURE: cgm-direct-ingest
    suspend fun cgmDedupWindowMillisSnapshot(): Long =
        runCatching { safeData.first()[Keys.CGM_DEDUP_WINDOW_MILLIS] }.getOrNull()
            ?: CgmGlucose.DEFAULT_DEDUP_WINDOW_MILLIS

    suspend fun nightscoutEnabledSnapshot(): Boolean =
        runCatching { safeData.first()[Keys.CGM_NIGHTSCOUT_ENABLED] }.getOrNull() ?: false

    suspend fun nightscoutUrlSnapshot(): String =
        runCatching { safeData.first()[Keys.CGM_NIGHTSCOUT_URL] }.getOrNull().orEmpty()

    suspend fun nightscoutAuthModeSnapshot(): NightscoutAuthMode =
        runCatching { safeData.first()[Keys.CGM_NIGHTSCOUT_AUTH_MODE] }.getOrNull()
            .toEnum(NightscoutAuthMode.TOKEN)

    suspend fun encryptedNightscoutCredential(): String? =
        runCatching { safeData.first()[Keys.CGM_NIGHTSCOUT_CREDENTIAL_ENC] }
            .getOrNull()?.takeIf { it.isNotBlank() }

    suspend fun acknowledgedNightscoutHostsSnapshot(): Set<String> =
        runCatching { safeData.first()[Keys.CGM_NIGHTSCOUT_ACKNOWLEDGED_HOSTS] }
            .getOrNull() ?: emptySet()

    suspend fun nightscoutBackfillHoursSnapshot(): Int =
        NightscoutLimits.clampBackfillHours(
            runCatching { safeData.first()[Keys.CGM_NIGHTSCOUT_BACKFILL_HOURS] }.getOrNull()
                ?: NightscoutLimits.DEFAULT_BACKFILL_HOURS,
        )

    suspend fun nightscoutForegroundServiceSnapshot(): Boolean =
        runCatching { safeData.first()[Keys.CGM_NIGHTSCOUT_FOREGROUND_SERVICE] }.getOrNull()
            ?: false

    suspend fun xdripBroadcastEnabledSnapshot(): Boolean =
        runCatching { safeData.first()[Keys.CGM_XDRIP_BROADCAST_ENABLED] }.getOrNull() ?: false

    suspend fun libreLinkUpEnabledSnapshot(): Boolean =
        runCatching { safeData.first()[Keys.CGM_LIBRE_LINK_UP_ENABLED] }.getOrNull() ?: false

    suspend fun cgmIngestSettingsSnapshot(): CgmIngestSettings =
        safeData.first().toCgmIngestSettings()

    suspend fun cgmLastIngestSuccessSnapshot(source: GlucoseSampleSource): Long? =
        runCatching { safeData.first()[lastSuccessKey(source)] }.getOrNull()

    suspend fun cgmLastIngestErrorSnapshot(source: GlucoseSampleSource): String? =
        runCatching { safeData.first()[lastErrorKey(source)] }.getOrNull()?.takeIf { it.isNotBlank() }

    fun cgmLastIngestSuccess(source: GlucoseSampleSource): Flow<Long?> = safeData.map { p ->
        runCatching { p[lastSuccessKey(source)] }.getOrNull()
    }

    fun cgmLastIngestError(source: GlucoseSampleSource): Flow<String?> = safeData.map { p ->
        runCatching { p[lastErrorKey(source)] }.getOrNull()?.takeIf { it.isNotBlank() }
    }

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

    private fun Preferences.toDosingProfileLoad(): DosingProfileLoad = DosingProfileCodec.load(
        profileJson = runCatching { this[Keys.DOSING_PROFILE_JSON] }.getOrNull(),
        diaHours = runCatching { this[Keys.DIA_HOURS] }.getOrNull(),
        cirRatio = runCatching { this[Keys.CIR_RATIO] }.getOrNull(),
        isfMgdl = runCatching { this[Keys.ISF_MGDL] }.getOrNull(),
        targetGlucoseMgdl = runCatching { this[Keys.TARGET_GLUCOSE_MGDL] }.getOrNull(),
    )

    /**
     * Resolved-at-now when the profile is valid; otherwise DIA plus 00:00 flat
     * keys. Dosing paths must not use this for ISF/CIR/target.
     */
    private fun Preferences.toBolusSettings(): BolusSettings {
        val load = toDosingProfileLoad()
        return when (load) {
            is DosingProfileLoad.Valid -> load.profile.toBolusSettings(
                Instant.now(clock),
                ZoneId.systemDefault(),
            )
            is DosingProfileLoad.Invalid -> BolusSettings(
                diaHours = load.diaHours,
                cirRatio = runCatching { this[Keys.CIR_RATIO] }.getOrNull() ?: 10.0f,
                isfMgdl = runCatching { this[Keys.ISF_MGDL] }.getOrNull() ?: 50.0f,
                targetGlucoseMgdl = runCatching { this[Keys.TARGET_GLUCOSE_MGDL] }.getOrNull()
                    ?: 100.0f,
            )
        }
    }

    private fun Preferences.toCgmIngestSettings(): CgmIngestSettings = CgmIngestSettings(
        dedupWindowMillis = runCatching { this[Keys.CGM_DEDUP_WINDOW_MILLIS] }.getOrNull()
            ?: CgmGlucose.DEFAULT_DEDUP_WINDOW_MILLIS,
        nightscoutEnabled = runCatching { this[Keys.CGM_NIGHTSCOUT_ENABLED] }.getOrNull() ?: false,
        xdripBroadcastEnabled = runCatching { this[Keys.CGM_XDRIP_BROADCAST_ENABLED] }.getOrNull() ?: false,
        libreLinkUpEnabled = runCatching { this[Keys.CGM_LIBRE_LINK_UP_ENABLED] }.getOrNull() ?: false,
        nightscoutLastSuccessMillis = runCatching { this[Keys.CGM_LAST_SUCCESS_NIGHTSCOUT] }.getOrNull(),
        xdripBroadcastLastSuccessMillis = runCatching { this[Keys.CGM_LAST_SUCCESS_XDRIP_BROADCAST] }.getOrNull(),
        libreLinkUpLastSuccessMillis = runCatching { this[Keys.CGM_LAST_SUCCESS_LIBRE_LINK_UP] }.getOrNull(),
        healthConnectLastSuccessMillis = runCatching { this[Keys.CGM_LAST_SUCCESS_HEALTH_CONNECT] }.getOrNull(),
        manualImportLastSuccessMillis = runCatching { this[Keys.CGM_LAST_SUCCESS_MANUAL_IMPORT] }.getOrNull(),
        nightscoutLastError = runCatching { this[Keys.CGM_LAST_ERROR_NIGHTSCOUT] }.getOrNull()?.takeIf { it.isNotBlank() },
        xdripBroadcastLastError = runCatching { this[Keys.CGM_LAST_ERROR_XDRIP_BROADCAST] }.getOrNull()?.takeIf { it.isNotBlank() },
        libreLinkUpLastError = runCatching { this[Keys.CGM_LAST_ERROR_LIBRE_LINK_UP] }.getOrNull()?.takeIf { it.isNotBlank() },
        healthConnectLastError = runCatching { this[Keys.CGM_LAST_ERROR_HEALTH_CONNECT] }.getOrNull()?.takeIf { it.isNotBlank() },
        manualImportLastError = runCatching { this[Keys.CGM_LAST_ERROR_MANUAL_IMPORT] }.getOrNull()?.takeIf { it.isNotBlank() },
    )

    private fun lastSuccessKey(source: GlucoseSampleSource) = when (source) {
        GlucoseSampleSource.NIGHTSCOUT -> Keys.CGM_LAST_SUCCESS_NIGHTSCOUT
        GlucoseSampleSource.XDRIP_BROADCAST -> Keys.CGM_LAST_SUCCESS_XDRIP_BROADCAST
        GlucoseSampleSource.LIBRE_LINK_UP -> Keys.CGM_LAST_SUCCESS_LIBRE_LINK_UP
        GlucoseSampleSource.HEALTH_CONNECT -> Keys.CGM_LAST_SUCCESS_HEALTH_CONNECT
        GlucoseSampleSource.MANUAL_IMPORT -> Keys.CGM_LAST_SUCCESS_MANUAL_IMPORT
    }

    private fun lastErrorKey(source: GlucoseSampleSource) = when (source) {
        GlucoseSampleSource.NIGHTSCOUT -> Keys.CGM_LAST_ERROR_NIGHTSCOUT
        GlucoseSampleSource.XDRIP_BROADCAST -> Keys.CGM_LAST_ERROR_XDRIP_BROADCAST
        GlucoseSampleSource.LIBRE_LINK_UP -> Keys.CGM_LAST_ERROR_LIBRE_LINK_UP
        GlucoseSampleSource.HEALTH_CONNECT -> Keys.CGM_LAST_ERROR_HEALTH_CONNECT
        GlucoseSampleSource.MANUAL_IMPORT -> Keys.CGM_LAST_ERROR_MANUAL_IMPORT
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }
    suspend fun setAccent(accent: AccentColor) = edit { it[Keys.ACCENT] = accent.name }
    suspend fun setUnit(unit: GlucoseUnit) = edit { it[Keys.UNIT] = unit.name }
    suspend fun setUse24HourTime(enabled: Boolean) = edit { it[Keys.USE_24H] = enabled }
    suspend fun setIsHeroAiEnabled(enabled: Boolean) = edit { it[Keys.HERO_AI_ENABLED] = enabled }
    suspend fun setShowAdvancedMacros(enabled: Boolean) = edit { it[Keys.SHOW_ADVANCED_MACROS] = enabled }
    suspend fun setPostMealRemindersEnabled(enabled: Boolean) =
        edit { it[Keys.POST_MEAL_REMINDERS_ENABLED] = enabled }

    suspend fun setNotificationsEnabled(enabled: Boolean) =
        edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }

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

    // FEATURE: cgm-direct-ingest
    suspend fun setCgmDedupWindowMillis(millis: Long) = edit {
        it[Keys.CGM_DEDUP_WINDOW_MILLIS] = millis.coerceIn(0L, CgmGlucose.MAX_DEDUP_WINDOW_MILLIS)
    }

    suspend fun setNightscoutEnabled(enabled: Boolean) =
        edit { it[Keys.CGM_NIGHTSCOUT_ENABLED] = enabled }

    suspend fun setNightscoutUrl(url: String) = edit {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) it.remove(Keys.CGM_NIGHTSCOUT_URL) else it[Keys.CGM_NIGHTSCOUT_URL] = trimmed
    }

    suspend fun setNightscoutAuthMode(mode: NightscoutAuthMode) =
        edit { it[Keys.CGM_NIGHTSCOUT_AUTH_MODE] = mode.name }

    suspend fun setEncryptedNightscoutCredential(encrypted: String?) = edit {
        if (encrypted.isNullOrBlank()) it.remove(Keys.CGM_NIGHTSCOUT_CREDENTIAL_ENC)
        else it[Keys.CGM_NIGHTSCOUT_CREDENTIAL_ENC] = encrypted
    }

    suspend fun acknowledgeNightscoutHost(host: String) {
        val normalized = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (normalized.isBlank()) return
        edit {
            val current = it[Keys.CGM_NIGHTSCOUT_ACKNOWLEDGED_HOSTS] ?: emptySet()
            it[Keys.CGM_NIGHTSCOUT_ACKNOWLEDGED_HOSTS] = current + normalized
        }
    }

    suspend fun setNightscoutBackfillHours(hours: Int) = edit {
        it[Keys.CGM_NIGHTSCOUT_BACKFILL_HOURS] = NightscoutLimits.clampBackfillHours(hours)
    }

    suspend fun setNightscoutForegroundService(enabled: Boolean) =
        edit { it[Keys.CGM_NIGHTSCOUT_FOREGROUND_SERVICE] = enabled }

    suspend fun setXdripBroadcastEnabled(enabled: Boolean) =
        edit { it[Keys.CGM_XDRIP_BROADCAST_ENABLED] = enabled }

    suspend fun setLibreLinkUpEnabled(enabled: Boolean) =
        edit { it[Keys.CGM_LIBRE_LINK_UP_ENABLED] = enabled }

    suspend fun setCgmLastIngestSuccess(source: GlucoseSampleSource, timestamp: Long?) = edit {
        val key = lastSuccessKey(source)
        if (timestamp == null) it.remove(key) else it[key] = timestamp
    }

    /**
     * Stores a short, non-secret error label. Do not pass tokens, API secrets,
     * or Libre passwords. Nightscout credentials belong in KeystoreManager.
     */
    suspend fun setCgmLastIngestError(source: GlucoseSampleSource, message: String?) = edit {
        val key = lastErrorKey(source)
        val trimmed = message?.trim().orEmpty().take(200)
        if (trimmed.isEmpty()) it.remove(key) else it[key] = trimmed
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

    suspend fun setDiaHours(diaHours: Float) {
        when (val load = dosingProfileSnapshot()) {
            is DosingProfileLoad.Valid ->
                setDosingProfile(load.profile.copy(diaHours = diaHours))
            is DosingProfileLoad.Invalid -> edit { it[Keys.DIA_HOURS] = diaHours }
        }
    }

    suspend fun setCirRatio(ratio: Float) = updateMidnightSegment { it.copy(cirRatio = ratio) }
    suspend fun setIsfMgdl(isf: Float) = updateMidnightSegment { it.copy(isfMgdl = isf) }
    suspend fun setTargetGlucoseMgdl(target: Float) =
        updateMidnightSegment { it.copy(targetGlucoseMgdl = target) }

    /**
     * Persists a validated profile. Also writes the four legacy flat keys as
     * the **00:00 segment** (plus global DIA) so a pre-profile reader and a v3
     * backup mentally round-trip those four numbers. Flat keys are not
     * "current resolved" values.
     *
     * FEATURE: dosing-profiles
     */
    suspend fun setDosingProfile(profile: DosingProfile) {
        check(profile.validate() is DosingProfileValidation.Valid) {
            "Refusing to persist an invalid dosing profile"
        }
        val midnight = profile.midnightBolusSettings()
        edit {
            it[Keys.DOSING_PROFILE_JSON] = DosingProfileCodec.encode(profile)
            it[Keys.DIA_HOURS] = profile.diaHours
            it[Keys.CIR_RATIO] = midnight.cirRatio
            it[Keys.ISF_MGDL] = midnight.isfMgdl
            it[Keys.TARGET_GLUCOSE_MGDL] = midnight.targetGlucoseMgdl
        }
    }

    suspend fun setDosingProfileExplainerSeen(seen: Boolean) = edit {
        it[Keys.DOSING_PROFILE_EXPLAINER_SEEN] = seen
    }

    private suspend fun updateMidnightSegment(
        mutate: (com.omb9.glucosehero.domain.model.DosingSegment) -> com.omb9.glucosehero.domain.model.DosingSegment,
    ) {
        val load = dosingProfileSnapshot()
        val draft = when (load) {
            is DosingProfileLoad.Valid -> load.profile
            is DosingProfileLoad.Invalid -> load.editorDraft
                ?: DosingProfile.single(
                    BolusSettings(diaHours = load.diaHours),
                )
        }
        val segments = draft.segments.toMutableList()
        if (segments.isEmpty()) return
        segments[0] = mutate(segments[0])
        val next = draft.copy(segments = segments)
        when (val v = next.validate()) {
            is DosingProfileValidation.Valid -> setDosingProfile(v.profile)
            is DosingProfileValidation.Invalid -> {
                val midnight = next.midnightBolusSettings()
                edit {
                    it[Keys.DIA_HOURS] = next.diaHours
                    it[Keys.CIR_RATIO] = midnight.cirRatio
                    it[Keys.ISF_MGDL] = midnight.isfMgdl
                    it[Keys.TARGET_GLUCOSE_MGDL] = midnight.targetGlucoseMgdl
                }
            }
        }
    }

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
