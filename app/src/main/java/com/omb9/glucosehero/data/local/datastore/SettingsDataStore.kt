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
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.model.UserSettings
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
        val SHOW_ADVANCED_MACROS = booleanPreferencesKey("show_advanced_macros")
        val POST_MEAL_REMINDERS_ENABLED = booleanPreferencesKey("post_meal_reminders_enabled")
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
        val HEALTH_CONNECT_CHANGES_TOKEN = stringPreferencesKey("health_connect_changes_token")
        val HEALTH_CONNECT_LAST_SYNC = longPreferencesKey("health_connect_last_sync")
        val GLUCOSE_IMPORT_ENABLED = booleanPreferencesKey("glucose_import_enabled")
        val NUTRITION_IMPORT_ENABLED = booleanPreferencesKey("nutrition_import_enabled")
        val EXERCISE_IMPORT_ENABLED = booleanPreferencesKey("exercise_import_enabled")
        val HEALTH_CONNECT_INITIAL_IMPORT_RANGE = stringPreferencesKey("health_connect_initial_import_range")
        val BARCODE_LOOKUP_ENABLED = booleanPreferencesKey("barcode_lookup_enabled")
        val DISMISSED_FOOD_TAGS = stringSetPreferencesKey("dismissed_food_tags")
        val BACKUP_DIR_URI = stringPreferencesKey("backup_dir_uri")
        val BACKUP_ENABLED = booleanPreferencesKey("backup_enabled")
        val BACKUP_LAST_RUN = longPreferencesKey("backup_last_run")
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
            targetLowMgdl = runCatching { p[Keys.TARGET_LOW] }.getOrNull() ?: 70f,
            targetHighMgdl = runCatching { p[Keys.TARGET_HIGH] }.getOrNull() ?: 180f,
        )
    }

    val barcodeLookupEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.BARCODE_LOOKUP_ENABLED] }.getOrNull() ?: true
    }

    /** Tags dismissed on the Food impact screen; persists across nightly recomputation. */
    val dismissedFoodTags: Flow<Set<String>> = safeData.map { p ->
        runCatching { p[Keys.DISMISSED_FOOD_TAGS] }.getOrNull() ?: emptySet()
    }

    val aiConfig: Flow<AiConfig> = safeData.map { p -> p.toAiConfig() }

    val profile: Flow<UserProfile> = safeData.map { p -> p.toUserProfile() }

    val bolusSettings: Flow<BolusSettings> = safeData.map { p -> p.toBolusSettings() }

    val healthConnectSyncEnabled: Flow<Boolean> = safeData.map { p ->
        runCatching { p[Keys.HEALTH_CONNECT_SYNC_ENABLED] }.getOrNull() ?: false
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

    /** Single fresh snapshot used by the export utility when assembling a report. */
    suspend fun profileSnapshot(): UserProfile = safeData.first().toUserProfile()

    /** Single fresh snapshot of the insulin-dosing parameters. */
    suspend fun bolusSettingsSnapshot(): BolusSettings = safeData.first().toBolusSettings()

    /** Single fresh snapshot — read per-request by the network layer. */
    suspend fun aiConfigSnapshot(): AiConfig = safeData.first().toAiConfig()

    suspend fun encryptedApiKey(): String? =
        runCatching { safeData.first()[Keys.AI_API_KEY_ENC] }
            .getOrNull()?.takeIf { it.isNotBlank() }

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

    suspend fun setBarcodeLookupEnabled(enabled: Boolean) =
        edit { it[Keys.BARCODE_LOOKUP_ENABLED] = enabled }

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

    suspend fun setHealthConnectInitialImportRange(range: InitialImportRange) =
        edit { it[Keys.HEALTH_CONNECT_INITIAL_IMPORT_RANGE] = range.name }

    suspend fun setBackupDirUri(uri: String?) = edit {
        if (uri.isNullOrBlank()) it.remove(Keys.BACKUP_DIR_URI) else it[Keys.BACKUP_DIR_URI] = uri
    }

    suspend fun setBackupEnabled(enabled: Boolean) = edit { it[Keys.BACKUP_ENABLED] = enabled }

    suspend fun setBackupLastRun(timestamp: Long) = edit { it[Keys.BACKUP_LAST_RUN] = timestamp }

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
