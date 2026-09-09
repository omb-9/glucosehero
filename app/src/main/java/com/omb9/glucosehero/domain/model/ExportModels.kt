package com.omb9.glucosehero.domain.model

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Clinical export formats offered by the share sheet. */
enum class ExportFormat(val displayName: String, val mimeType: String) {
    PDF("Ambulatory Glucose Profile (PDF)", "application/pdf"),
    CSV("Glucose log (CSV)", "text/csv"),
}

/**
 * A generated report sitting in the app's cache directory, ready to be turned
 * into a `content://` URI by [androidx.core.content.FileProvider].
 */
data class ExportedFile(
    val file: File,
    val mimeType: String,
)

/**
 * Explicit whitelist of domain models that may appear in JSON backups and
 * Markdown / CSV export streams. Anything not listed is excluded: internal
 * DataStore flags, device identifiers, Keystore aliases, OAuth / Drive tokens,
 * WebDAV passwords, and API keys never ride along (including the encrypted
 * `.ghzk` path, which encrypts this already-sanitized JSON).
 *
 * Restore must decode against this list (kotlinx.serialization with unknown
 * keys rejected) so extra JSON fields cannot be injected into Room.
 */
enum class ExportableDomainModel {
    BACKUP_ENVELOPE,
    BACKUP_COUNTS,
    BACKUP_PROFILE,
    BACKUP_SETTINGS,
    BACKUP_ENTRY,
    BACKUP_FOOD,
    BACKUP_SUPPLY,
    BACKUP_INSIGHT,
    BACKUP_CHAT_MESSAGE,
    BACKUP_PENDING_QUERY,
    BACKUP_GLUCOSE_SAMPLE,
}

/**
 * Key and column lists for Feature 20 export sanitization. Encode drops
 * anything outside the whitelist. Restore rejects extra or secret fields.
 */
object ExportWhitelist {

    val envelopeKeys: Set<String> = setOf(
        "format",
        "formatVersion",
        "appVersion",
        "databaseVersion",
        "exportedAt",
        "counts",
        "profile",
        "settings",
        "earliestEntry",
        "latestEntry",
        "foods",
        "entries",
        "supplies",
        "glucoseSamples",
        "chat",
        "pendingAiQueries",
        "insights",
    )

    val arrayKeys: Set<String> = setOf(
        "foods",
        "entries",
        "supplies",
        "glucoseSamples",
        "chat",
        "pendingAiQueries",
        "insights",
    )

    val settingsKeys: Set<String> = setOf(
        "themeMode",
        "accent",
        "unit",
        "use24HourTime",
        "targetLowMgdl",
        "targetHighMgdl",
        "isHeroAiEnabled",
        "showAdvancedMacros",
        "postMealRemindersEnabled",
        "aiProvider",
        "aiBaseUrl",
        "aiModel",
        "diaHours",
        "cirRatio",
        "isfMgdl",
        "targetGlucoseMgdl",
        "barcodeLookupEnabled",
        "healthConnectSyncEnabled",
        "glucoseImportEnabled",
        "nutritionImportEnabled",
        "exerciseImportEnabled",
        "sleepImportEnabled",
        "cycleImportEnabled",
        "healthConnectInitialImportRange",
    )

    val profileKeys: Set<String> = setOf(
        "profileTarget",
        "name",
        "age",
        "diabetesType",
        "heightCm",
        "weightKg",
    )

    val countsKeys: Set<String> = setOf(
        "entries",
        "glucoseSamples",
        "foods",
        "supplies",
        "insights",
        "chat",
        "pendingAiQueries",
    )

    val entryKeys: Set<String> = setOf(
        "id",
        "timestamp",
        "glucoseMgdl",
        "mealContext",
        "insulinBasalUnits",
        "insulinBolusUnits",
        "carbsGrams",
        "proteinGrams",
        "fatGrams",
        "mealDescription",
        "exerciseMinutes",
        "exerciseIntensity",
        "note",
        "source",
        "hcRecordId",
        "endTime",
        "foodId",
        "uuid",
        "moodScore",
        "moodLabel",
    )

    val foodKeys: Set<String> = setOf(
        "id",
        "uuid",
        "name",
        "brand",
        "barcode",
        "carbsGrams",
        "proteinGrams",
        "fatGrams",
        "kcal",
        "servingGrams",
        "servingLabel",
        "source",
        "offFetchedAt",
        "userCorrected",
        "useCount",
        "lastUsedAt",
        "isFavorite",
        "createdAt",
    )

    val supplyKeys: Set<String> = setOf(
        "id",
        "type",
        "startedAt",
        "expectedLifespanDays",
        "replacedAt",
        "uuid",
    )

    val insightKeys: Set<String> = setOf(
        "id",
        "title",
        "description",
        "severityLevel",
        "createdAt",
    )

    val chatKeys: Set<String> = setOf(
        "id",
        "role",
        "content",
        "timestamp",
    )

    val pendingQueryKeys: Set<String> = setOf(
        "id",
        "userMessageId",
        "prompt",
        "createdAt",
        "ttlSeconds",
    )

    val glucoseSampleKeys: Set<String> = setOf(
        "id",
        "timestamp",
        "glucoseMgdl",
        "hcRecordId",
        "sourcePackage",
        "recordingMethod",
        "importedAt",
    )

    /** Markdown / CSV columns (clinical log only, never settings or tokens). */
    val markdownColumns: List<String> = listOf(
        "Timestamp",
        "Glucose (mg/dL)",
        "Basal",
        "Bolus",
        "Carbs",
        "Protein",
        "Fat",
        "Meal",
        "Context",
        "Exercise",
        "Intensity",
        "Notes",
    )

    val csvColumns: List<String> = listOf(
        "Id",
        "Timestamp",
        "Glucose",
        "Basal",
        "Bolus",
        "Carbs",
        "Protein",
        "Fat",
        "MealDescription",
        "MealContext",
        "ExerciseMinutes",
        "ExerciseIntensity",
        "Notes",
    )

    /**
     * JSON object keys that must never appear in an export stream. Includes
     * camelCase, snake_case, and DataStore names so a later "for completeness"
     * field is still stripped at encode time and rejected on restore.
     */
    val forbiddenKeys: Set<String> = setOf(
        "aiApiKeyEnc",
        "ai_api_key_enc",
        "encryptedApiKey",
        "apiKey",
        "api_key",
        "apiKeyEnc",
        "accessToken",
        "access_token",
        "refreshToken",
        "refresh_token",
        "oauthToken",
        "oauth_token",
        "oAuthToken",
        "idToken",
        "id_token",
        "authToken",
        "auth_token",
        "bearerToken",
        "clientSecret",
        "client_secret",
        "driveAccessToken",
        "driveAccessTokenEnc",
        "drive_access_token_enc",
        "webdavPassword",
        "webdavPasswordEnc",
        "webdav_password_enc",
        "webDavPassword",
        "password",
        "keystoreAlias",
        "keystore_alias",
        "keyAlias",
        "androidKeystore",
        "keystore",
        "wrappingKeyAlias",
        "androidId",
        "android_id",
        "deviceId",
        "device_id",
        "advertisingId",
        "serialNumber",
        "installationId",
        "healthConnectChangesToken",
        "health_connect_changes_token",
        "webhookUrl",
        "webhook_url",
        "backupDirUri",
        "backup_dir_uri",
        "webdavUrl",
        "webdav_url",
        "webdavUsername",
        "webdav_username",
        "cloudBackupRemoteId",
        "cloud_backup_remote_id",
    )

    fun isForbiddenKey(key: String): Boolean =
        forbiddenKeys.any { it.equals(key, ignoreCase = true) }

    fun allowedKeysForEnvelopeField(name: String): Set<String>? = when (name) {
        "settings" -> settingsKeys
        "profile" -> profileKeys
        "counts" -> countsKeys
        "entries" -> entryKeys
        "foods" -> foodKeys
        "supplies" -> supplyKeys
        "insights" -> insightKeys
        "chat" -> chatKeys
        "pendingAiQueries" -> pendingQueryKeys
        "glucoseSamples" -> glucoseSampleKeys
        else -> null
    }

    /** Drops unknown and forbidden keys. Used on the encode path only. */
    fun sanitizeObject(obj: JsonObject, allowedKeys: Set<String>): JsonObject {
        val kept = LinkedHashMap<String, JsonElement>(obj.size)
        for ((key, value) in obj) {
            if (isForbiddenKey(key) || key !in allowedKeys) continue
            kept[key] = sanitizeElement(value, allowedKeysForEnvelopeField(key))
        }
        return JsonObject(kept)
    }

    fun sanitizeElement(element: JsonElement, nestedAllowed: Set<String>? = null): JsonElement =
        when (element) {
            is JsonObject -> {
                val allowed = nestedAllowed ?: element.keys.filterNot { isForbiddenKey(it) }.toSet()
                sanitizeObject(element, allowed)
            }
            is JsonArray -> JsonArray(element.map { sanitizeElement(it, nestedAllowed) })
            is JsonPrimitive -> element
        }

    fun findForbiddenKey(json: String): String? {
        val element = try {
            parseJson.parseToJsonElement(json)
        } catch (_: Exception) {
            return forbiddenKeyColonRegex.find(json)?.groupValues?.get(1)
        }
        return findForbiddenKeyInElement(element)
    }

    fun findForbiddenKeyInElement(element: JsonElement): String? = when (element) {
        is JsonObject -> {
            for ((key, value) in element) {
                if (isForbiddenKey(key)) return key
                findForbiddenKeyInElement(value)?.let { return it }
            }
            null
        }
        is JsonArray -> {
            for (item in element) {
                findForbiddenKeyInElement(item)?.let { return it }
            }
            null
        }
        is JsonPrimitive -> null
    }

    fun containsForbiddenKey(json: String): Boolean = findForbiddenKey(json) != null

    fun requireNoForbiddenKeys(json: String) {
        val found = findForbiddenKey(json) ?: return
        throw IllegalArgumentException("Export contains blocked field '$found'.")
    }

    /**
     * Restore-time check: extra fields and secret keys are errors, not dropped.
     * Prevents injecting Keystore aliases, tokens, or unknown Room columns.
     */
    fun validateRestoreObject(obj: JsonObject, allowedKeys: Set<String>, label: String) {
        for (key in obj.keys) {
            if (isForbiddenKey(key)) {
                throw IllegalArgumentException("Backup contains blocked field '$key' in $label.")
            }
            if (key !in allowedKeys) {
                throw IllegalArgumentException("Backup contains unsupported field '$key' in $label.")
            }
        }
    }

    fun validateRestoreElement(name: String, element: JsonElement) {
        val allowed = allowedKeysForEnvelopeField(name) ?: return
        when (element) {
            is JsonObject -> validateRestoreObject(element, allowed, name)
            is JsonArray -> element.forEach { item ->
                if (item is JsonObject) validateRestoreObject(item, allowed, name)
            }
            else -> Unit
        }
    }

    private val parseJson = Json { ignoreUnknownKeys = true }

    private val forbiddenKeyColonRegex: Regex = Regex(
        "\"(" + forbiddenKeys.joinToString("|") { Regex.escape(it) } + ")\"\\s*:",
        RegexOption.IGNORE_CASE,
    )
}
