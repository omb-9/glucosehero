package com.omb9.glucosehero.data.export

import com.omb9.glucosehero.data.local.datastore.InitialImportRange
import com.omb9.glucosehero.data.local.entity.ChatMessageEntity
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.data.local.entity.InsightCardEntity
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity
import com.omb9.glucosehero.data.local.entity.SupplyEntity
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.domain.model.ProfileTarget
import com.omb9.glucosehero.domain.model.SupplyType
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.ExportWhitelist
import com.omb9.glucosehero.domain.model.DosingProfile
import com.omb9.glucosehero.domain.model.DosingProfileRecord
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.toProfile
import com.omb9.glucosehero.util.AppJson
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer
import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer

/**
 * Strict Json for backup restore. Unknown keys are errors so extra fields
 * cannot be injected into Room. Encode still uses [AppJson] for stable
 * field names; both share encodeDefaults so round-trips stay complete.
 */
val BackupJson: Json = Json {
    ignoreUnknownKeys = false
    encodeDefaults = true
    explicitNulls = false
    classDiscriminator = "kind"
}

/** Discriminator for the self-owned backup format. */
const val BACKUP_FORMAT = "glucosehero.backup"

/**
 * Incremented only when the *meaning* of the format changes. Deliberately
 * independent of [DATABASE_VERSION] so the envelope survives schema changes
 * that do not alter what a field means.
 *
 * Version 2 adds the optional header fields `earliestEntry` and `latestEntry`
 * so the restore preview can show a date range without decoding any entry
 * array. An older (v1) reader does not cleanly ignore unknown *leading* fields
 * (readHeader treats them as the first array name), so the bump lets the
 * existing version-refusal logic reject a newer file with a clear message
 * instead of misparsing it.
 *
 * Version 3 adds source-generic identity on [BackupGlucoseSample]
 * (`source`, `externalId`, nullable `hcRecordId`, `trendArrow`). A v2 reader
 * rejects unknown keys, so the bump refuses a newer file instead of failing
 * mid-decode. Older files still deserialize: missing `source` defaults to
 * HEALTH_CONNECT and missing `externalId` is filled from `hcRecordId`.
 *
 * FEATURE: cgm-direct-ingest
 *
 * Version 4 adds optional [BackupSettings.dosingProfile] (time-of-day ISF,
 * CIR, and target). A v3 reader rejects unknown keys, so the bump refuses a
 * newer file. Older files still deserialize: missing `dosingProfile` is
 * synthesized into a single 00:00 segment from the four flat keys.
 *
 * FEATURE: dosing-profiles
 */
const val BACKUP_FORMAT_VERSION = 4

/**
 * Room schema version of the database this exporter understands. Keep in sync
 * with [com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase]'s `version`.
 * Independent of [BACKUP_FORMAT_VERSION]: a Room bump that does not change
 * field meaning does not require a format bump.
 */
const val DATABASE_VERSION = 15 // FEATURE: cgm-direct-ingest

/** Thrown for anything structurally wrong with a backup file. */
class BackupFormatException(message: String) : Exception(message)

fun requireSupportedBackupFormat(version: Int) {
    if (version > BACKUP_FORMAT_VERSION) {
        throw BackupFormatException(
            "This backup uses format version $version, which is newer than this app " +
                "supports (up to $BACKUP_FORMAT_VERSION). Update the app to restore it."
        )
    }
}

fun requireSupportedBackup(format: String, formatVersion: Int) {
    if (format != BACKUP_FORMAT) {
        throw BackupFormatException("This file is not a GlucoseHero backup.")
    }
    requireSupportedBackupFormat(formatVersion)
}

/** Suggested name for [androidx.activity.result.contract.ActivityResultContracts.CreateDocument]. */
fun suggestedBackupFileName(date: LocalDate = LocalDate.now()): String =
    "glucosehero-backup-$date.json"

fun suggestedEncryptedBackupFileName(date: LocalDate = LocalDate.now()): String =
    "glucosehero-backup-$date.ghzk"

/**
 * Encodes [settings] for the backup envelope after keeping only the
 * [ExportWhitelist] settings keys and dropping secret material. Callers that
 * somehow added a token field still cannot leak it.
 *
 * PERMANENT SECURITY BOUNDARY: the KeyStore-encrypted API key blob
 * (`ai_api_key_enc`) is wrapped by a hardware-backed, non-exportable key.
 * The ciphertext is worthless on any other device, which is the same
 * reasoning already documented in `backup_rules.xml`. Do not add the blob
 * to this JSON "for completeness." WebDAV passwords, Drive tokens, OAuth
 * material, and Keystore aliases are stripped the same way, before any
 * `.ghzk` encryption wraps the JSON.
 */
fun encodeSettingsForBackup(settings: BackupSettings): String {
    val encoded = AppJson.encodeToJsonElement(BackupSettings.serializer(), settings).jsonObject
    return AppJson.encodeToString(JsonObject.serializer(), stripNonExportableSettings(encoded))
}

fun stripNonExportableSettings(obj: JsonObject): JsonObject =
    ExportWhitelist.sanitizeObject(obj, ExportWhitelist.settingsKeys)

fun validateBackupJsonText(text: String) {
    try {
        ExportWhitelist.requireNoForbiddenKeys(text)
    } catch (e: IllegalArgumentException) {
        throw BackupFormatException(e.message ?: "Backup contains a blocked field.")
    }
    val root = try {
        AppJson.parseToJsonElement(text)
    } catch (_: Exception) {
        throw BackupFormatException("Backup JSON is not valid.")
    }
    val obj = root as? JsonObject
        ?: throw BackupFormatException("Backup root must be an object.")
    try {
        ExportWhitelist.validateRestoreObject(obj, ExportWhitelist.envelopeKeys, "envelope")
        for ((key, value) in obj) {
            ExportWhitelist.validateRestoreElement(key, value)
        }
    } catch (e: IllegalArgumentException) {
        throw BackupFormatException(e.message ?: "Backup failed schema validation.")
    }
}

fun <T> decodeWhitelisted(
    serializer: KSerializer<T>,
    json: String,
    fieldName: String,
): T {
    try {
        ExportWhitelist.requireNoForbiddenKeys(json)
        val element: JsonElement = AppJson.parseToJsonElement(json)
        ExportWhitelist.validateRestoreElement(fieldName, element)
        return BackupJson.decodeFromString(serializer, json)
    } catch (e: BackupFormatException) {
        throw e
    } catch (e: IllegalArgumentException) {
        throw BackupFormatException(e.message ?: "Backup failed schema validation.")
    } catch (e: SerializationException) {
        throw BackupFormatException(
            "Backup field '$fieldName' failed schema validation: ${e.message}",
        )
    }
}

// ---------------------------------------------------------------------------
// Profile & settings (DataStore-backed, never the KeyStore-wrapped API key)
// ---------------------------------------------------------------------------

@Serializable
data class BackupProfile(
    val profileTarget: ProfileTarget = ProfileTarget.SELF,
    val name: String = "",
    val age: Int? = null,
    val diabetesType: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
)

/**
 * Everything a user would want back after reinstalling, minus key material.
 *
 * The KeyStore-encrypted API key blob is intentionally absent: it is wrapped
 * by a hardware-backed, non-exportable key, so the ciphertext is worthless on
 * any other device (and on this device it already lives in DataStore). This is
 * the same reasoning documented in `backup_rules.xml`.
 */
@Serializable
data class BackupSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val accent: AccentColor = AccentColor.LIGHT_RED,
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    val use24HourTime: Boolean = false,
    val targetLowMgdl: Float = 70f,
    val targetHighMgdl: Float = 180f,
    val isHeroAiEnabled: Boolean = true,
    val showAdvancedMacros: Boolean = false,
    val postMealRemindersEnabled: Boolean = true,
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val aiBaseUrl: String = AiProvider.GEMINI.defaultBaseUrl,
    val aiModel: String = AiProvider.GEMINI.defaultModel,
    val diaHours: Float = 4.0f,
    val cirRatio: Float = 10.0f,
    val isfMgdl: Float = 50.0f,
    val targetGlucoseMgdl: Float = 100.0f,
    /**
     * Time-of-day ISF/CIR/target plus global DIA. Null on v3 files; restore
     * synthesizes a single 00:00 segment from the four flat keys.
     *
     * Flat keys on write are the 00:00 segment values (not "now"), so a
     * round-trip does not depend on export time of day.
     *
     * FEATURE: dosing-profiles
     */
    val dosingProfile: DosingProfileRecord? = null,
    val barcodeLookupEnabled: Boolean = true,
    val healthConnectSyncEnabled: Boolean = false,
    val glucoseImportEnabled: Boolean = true,
    val nutritionImportEnabled: Boolean = false,
    val exerciseImportEnabled: Boolean = false,
    val sleepImportEnabled: Boolean = false,
    val cycleImportEnabled: Boolean = false,
    val healthConnectInitialImportRange: InitialImportRange = InitialImportRange.DAYS_90,
) {
    /**
     * Profile to persist on restore. A v3 file (null [dosingProfile]) becomes
     * a single 00:00 segment from the four flat keys.
     *
     * FEATURE: dosing-profiles
     */
    fun restoredDosingProfile(): DosingProfile {
        val record = dosingProfile
        if (record != null) return record.toProfile()
        return DosingProfile.single(
            BolusSettings(
                diaHours = diaHours,
                cirRatio = cirRatio,
                isfMgdl = isfMgdl,
                targetGlucoseMgdl = targetGlucoseMgdl,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Per-table envelopes
// ---------------------------------------------------------------------------

@Serializable
data class BackupEntry(
    val id: Long = 0L,
    val timestamp: Long,
    val glucoseMgdl: Double? = null,
    val mealContext: MealContext? = null,
    val insulinBasalUnits: Double? = null,
    val insulinBolusUnits: Double? = null,
    val carbsGrams: Int? = null,
    val proteinGrams: Int? = null,
    val fatGrams: Int? = null,
    val mealDescription: String? = null,
    val exerciseMinutes: Int? = null,
    val exerciseIntensity: ActivityIntensity? = null,
    val note: String? = null,
    val source: EntrySource = EntrySource.MANUAL,
    val hcRecordId: String? = null,
    val endTime: Long? = null,
    val foodId: Long? = null,
    val uuid: String,
    val moodScore: Int? = null,
    val moodLabel: String? = null,
)

@Serializable
data class BackupFood(
    val id: Long = 0L,
    val uuid: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val carbsGrams: Double,
    val proteinGrams: Double? = null,
    val fatGrams: Double? = null,
    val kcal: Double? = null,
    val servingGrams: Double? = null,
    val servingLabel: String? = null,
    val source: FoodSource,
    val offFetchedAt: Long? = null,
    val userCorrected: Boolean = false,
    val useCount: Int = 0,
    val lastUsedAt: Long? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class BackupSupply(
    val id: Long = 0L,
    val type: SupplyType,
    val startedAt: Long,
    val expectedLifespanDays: Int,
    val replacedAt: Long? = null,
    val uuid: String,
)

@Serializable
data class BackupInsight(
    val id: Long = 0L,
    val title: String,
    val description: String,
    val severityLevel: Int,
    val createdAt: Long,
)

@Serializable
data class BackupChatMessage(
    val id: Long = 0L,
    val role: ChatRole,
    val content: String,
    val timestamp: Long,
)

@Serializable
data class BackupPendingQuery(
    val id: Long = 0L,
    val userMessageId: Long,
    val prompt: String,
    val createdAt: Long,
    /** 0 = unknown age, expired on restore. FEATURE: pending-query-ttl */
    val ttlSeconds: Int = 0,
)

@Serializable
data class BackupGlucoseSample(
    val id: Long = 0L,
    val timestamp: Long,
    val glucoseMgdl: Double,
    /** Defaults so a v14-era (format 2) file still deserializes. FEATURE: cgm-direct-ingest */
    val source: GlucoseSampleSource = GlucoseSampleSource.HEALTH_CONNECT,
    val externalId: String? = null,
    val hcRecordId: String? = null,
    val trendArrow: String? = null,
    val sourcePackage: String? = null,
    val recordingMethod: Int,
    val importedAt: Long,
)

// ---------------------------------------------------------------------------
// Envelope
// ---------------------------------------------------------------------------

@Serializable
data class BackupCounts(
    val entries: Int = 0,
    val glucoseSamples: Int = 0,
    val foods: Int = 0,
    val supplies: Int = 0,
    val insights: Int = 0,
    val chat: Int = 0,
    val pendingAiQueries: Int = 0,
) {
    fun total(): Int =
        entries + glucoseSamples + foods + supplies + insights + chat + pendingAiQueries
}

@Serializable
data class BackupEnvelope(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val appVersion: String = "",
    val databaseVersion: Int = DATABASE_VERSION,
    val exportedAt: Long = 0L,
    val counts: BackupCounts = BackupCounts(),
    val profile: BackupProfile = BackupProfile(),
    val settings: BackupSettings = BackupSettings(),
    val earliestEntry: Long? = null,
    val latestEntry: Long? = null,
    val entries: List<BackupEntry> = emptyList(),
    val glucoseSamples: List<BackupGlucoseSample> = emptyList(),
    val foods: List<BackupFood> = emptyList(),
    val supplies: List<BackupSupply> = emptyList(),
    val insights: List<BackupInsight> = emptyList(),
    val chat: List<BackupChatMessage> = emptyList(),
    val pendingAiQueries: List<BackupPendingQuery> = emptyList(),
)

/**
 * Verifies the counts header written at export time against the arrays that
 * were actually decoded. A truncated or tampered file therefore fails loudly
 * instead of restoring a silently-incomplete subset.
 */
fun verifyCounts(envelope: BackupEnvelope) {
    verifyCounts(
        expected = envelope.counts,
        actual = BackupCounts(
            entries = envelope.entries.size,
            glucoseSamples = envelope.glucoseSamples.size,
            foods = envelope.foods.size,
            supplies = envelope.supplies.size,
            insights = envelope.insights.size,
            chat = envelope.chat.size,
            pendingAiQueries = envelope.pendingAiQueries.size,
        ),
    )
}

/**
 * Throws if the counts actually read from a stream differ from the counts
 * claimed in the header. Used by the streaming importer so a truncated or
 * tampered file fails loudly instead of restoring a partial subset.
 */
fun verifyCounts(expected: BackupCounts, actual: BackupCounts) {
    if (actual != expected) {
        throw BackupFormatException(
            "Backup is incomplete or corrupt: header counts $expected " +
                "do not match decoded contents $actual."
        )
    }
}

// ---------------------------------------------------------------------------
// Full-envelope codec (used by import and by unit tests)
// ---------------------------------------------------------------------------

fun encodeEnvelope(envelope: BackupEnvelope, output: OutputStream) {
    val json = AppJson.encodeToString(BackupEnvelope.serializer(), envelope)
    try {
        ExportWhitelist.requireNoForbiddenKeys(json)
    } catch (e: IllegalArgumentException) {
        throw BackupFormatException(e.message ?: "Backup export contained a blocked field.")
    }
    output.write(json.toByteArray(Charsets.UTF_8))
}

fun decodeEnvelope(input: InputStream): BackupEnvelope {
    val text = input.readBytes().toString(Charsets.UTF_8)
    validateBackupJsonText(text)
    return try {
        BackupJson.decodeFromString(BackupEnvelope.serializer(), text)
    } catch (e: BackupFormatException) {
        throw e
    } catch (e: SerializationException) {
        throw BackupFormatException("Backup failed schema validation: ${e.message}")
    }
}

// ---------------------------------------------------------------------------
// Streaming writer
//
// Token API matches android.util.JsonWriter so a CGM-sized history never
// becomes one giant in-memory String or List. Production export (BackupManager)
// writes through JsonWriter; JVM unit tests use [WriterBackupJsonSink] because
// android.util.JsonWriter is a no-op stub on the JVM.
// Individual rows are serialized with AppJson; only one page is live at a time.
// ---------------------------------------------------------------------------

/** Token sink used by [streamBackupEnvelope]. Mirrors android.util.JsonWriter. */
internal interface BackupJsonSink {
    fun beginObject()
    fun endObject()
    fun beginArray()
    fun endArray()
    fun name(name: String)
    fun value(string: String)
    fun value(number: Long)
    fun value(number: Int)
    fun nullValue()
    fun rawValue(json: String)
    fun flush()
}

/**
 * JVM-safe JsonWriter analogue over a [Writer]. Production uses
 * [android.util.JsonWriter] via [AndroidJsonSink] in BackupManager.
 */
internal class WriterBackupJsonSink(private val writer: Writer) : BackupJsonSink {
    private data class Frame(val inArray: Boolean, var first: Boolean = true)
    private val stack = ArrayDeque<Frame>()

    override fun beginObject() {
        beforeValue()
        writer.write("{")
        stack.addLast(Frame(inArray = false))
    }

    override fun endObject() {
        writer.write("}")
        stack.removeLast()
    }

    override fun beginArray() {
        beforeValue()
        writer.write("[")
        stack.addLast(Frame(inArray = true))
    }

    override fun endArray() {
        writer.write("]")
        stack.removeLast()
    }

    override fun name(name: String) {
        val frame = stack.last()
        check(!frame.inArray) { "name() is only valid inside an object" }
        if (!frame.first) writer.write(",")
        frame.first = false
        writer.write(AppJson.encodeToString(serializer(), name))
        writer.write(":")
    }

    override fun value(string: String) {
        beforeValue()
        writer.write(AppJson.encodeToString(serializer(), string))
    }

    override fun value(number: Long) {
        beforeValue()
        writer.write(number.toString())
    }

    override fun value(number: Int) {
        value(number.toLong())
    }

    override fun nullValue() {
        beforeValue()
        writer.write("null")
    }

    override fun rawValue(json: String) {
        beforeValue()
        writer.write(json)
    }

    override fun flush() {
        writer.flush()
    }

    private fun beforeValue() {
        val frame = stack.lastOrNull() ?: return
        if (frame.inArray) {
            if (!frame.first) writer.write(",")
            frame.first = false
        }
    }
}

suspend fun streamBackupEnvelope(
    output: OutputStream,
    profile: BackupProfile,
    settings: BackupSettings,
    counts: BackupCounts,
    appVersion: String,
    exportedAt: Long,
    earliestEntry: Long? = null,
    latestEntry: Long? = null,
    pageSize: Int = 500,
    foods: suspend (offset: Int, limit: Int) -> List<BackupFood>,
    entries: suspend (offset: Int, limit: Int) -> List<BackupEntry>,
    supplies: suspend (offset: Int, limit: Int) -> List<BackupSupply>,
    glucoseSamples: suspend (offset: Int, limit: Int) -> List<BackupGlucoseSample>,
    chat: suspend (offset: Int, limit: Int) -> List<BackupChatMessage>,
    pendingAiQueries: suspend (offset: Int, limit: Int) -> List<BackupPendingQuery>,
    insights: suspend (offset: Int, limit: Int) -> List<BackupInsight>,
    onProgress: suspend (Float) -> Unit = {},
) {
    val writer = output.bufferedWriter(Charsets.UTF_8)
    streamBackupEnvelope(
        sink = WriterBackupJsonSink(writer),
        profile = profile,
        settings = settings,
        counts = counts,
        appVersion = appVersion,
        exportedAt = exportedAt,
        earliestEntry = earliestEntry,
        latestEntry = latestEntry,
        pageSize = pageSize,
        foods = foods,
        entries = entries,
        supplies = supplies,
        glucoseSamples = glucoseSamples,
        chat = chat,
        pendingAiQueries = pendingAiQueries,
        insights = insights,
        onProgress = onProgress,
    )
    writer.flush()
}

internal suspend fun streamBackupEnvelope(
    sink: BackupJsonSink,
    profile: BackupProfile,
    settings: BackupSettings,
    counts: BackupCounts,
    appVersion: String,
    exportedAt: Long,
    earliestEntry: Long? = null,
    latestEntry: Long? = null,
    pageSize: Int = 500,
    foods: suspend (offset: Int, limit: Int) -> List<BackupFood>,
    entries: suspend (offset: Int, limit: Int) -> List<BackupEntry>,
    supplies: suspend (offset: Int, limit: Int) -> List<BackupSupply>,
    glucoseSamples: suspend (offset: Int, limit: Int) -> List<BackupGlucoseSample>,
    chat: suspend (offset: Int, limit: Int) -> List<BackupChatMessage>,
    pendingAiQueries: suspend (offset: Int, limit: Int) -> List<BackupPendingQuery>,
    insights: suspend (offset: Int, limit: Int) -> List<BackupInsight>,
    onProgress: suspend (Float) -> Unit = {},
) {
    val total = counts.total().coerceAtLeast(1)
    val done = intArrayOf(0)
    val pageDone: suspend (Int) -> Unit = { written ->
        done[0] += written
        onProgress(done[0].toFloat() / total)
    }

    sink.beginObject()
    sink.name("format"); sink.value(BACKUP_FORMAT)
    sink.name("formatVersion"); sink.value(BACKUP_FORMAT_VERSION)
    sink.name("appVersion"); sink.value(appVersion)
    sink.name("databaseVersion"); sink.value(DATABASE_VERSION)
    sink.name("exportedAt"); sink.value(exportedAt)
    // counts is written before any array so a truncated file fails the
    // header check instead of silently restoring a prefix of history.
    sink.name("counts")
    sink.rawValue(AppJson.encodeToString(BackupCounts.serializer(), counts))
    sink.name("profile")
    sink.rawValue(AppJson.encodeToString(BackupProfile.serializer(), profile))
    sink.name("settings")
    sink.rawValue(encodeSettingsForBackup(settings))
    sink.name("earliestEntry")
    if (earliestEntry == null) sink.nullValue() else sink.value(earliestEntry)
    sink.name("latestEntry")
    if (latestEntry == null) sink.nullValue() else sink.value(latestEntry)

    // Dependency order matters for merge: foods precede entries (entries
    // reference foods by id) and chat precedes pending queries (pending
    // queries reference chat messages by id).
    writeJsonArray(sink, "foods", BackupFood.serializer(), pageSize, foods, pageDone)
    writeJsonArray(sink, "entries", BackupEntry.serializer(), pageSize, entries, pageDone)
    writeJsonArray(sink, "supplies", BackupSupply.serializer(), pageSize, supplies, pageDone)
    writeJsonArray(sink, "glucoseSamples", BackupGlucoseSample.serializer(), pageSize, glucoseSamples, pageDone)
    writeJsonArray(sink, "chat", BackupChatMessage.serializer(), pageSize, chat, pageDone)
    writeJsonArray(sink, "pendingAiQueries", BackupPendingQuery.serializer(), pageSize, pendingAiQueries, pageDone)
    writeJsonArray(sink, "insights", BackupInsight.serializer(), pageSize, insights, pageDone)

    sink.endObject()
    sink.flush()
    onProgress(1f)
}

private suspend fun <T> writeJsonArray(
    sink: BackupJsonSink,
    name: String,
    serializer: KSerializer<T>,
    pageSize: Int,
    page: suspend (offset: Int, limit: Int) -> List<T>,
    onPage: suspend (written: Int) -> Unit = {},
) {
    sink.name(name)
    sink.beginArray()
    var offset = 0
    while (true) {
        val items = page(offset, pageSize)
        if (items.isEmpty()) break
        for (item in items) {
            sink.rawValue(AppJson.encodeToString(serializer, item))
        }
        offset += items.size
        onPage(items.size)
        if (items.size < pageSize) break
    }
    sink.endArray()
}

// ---------------------------------------------------------------------------
// Entity <-> envelope mapping
// ---------------------------------------------------------------------------

fun EntryEntity.toBackup() = BackupEntry(
    id = id,
    timestamp = timestamp,
    glucoseMgdl = glucoseMgdl,
    mealContext = mealContext,
    insulinBasalUnits = insulinBasalUnits,
    insulinBolusUnits = insulinBolusUnits,
    carbsGrams = carbsGrams,
    proteinGrams = proteinGrams,
    fatGrams = fatGrams,
    mealDescription = mealDescription,
    exerciseMinutes = exerciseMinutes,
    exerciseIntensity = exerciseIntensity,
    note = note,
    source = source,
    hcRecordId = hcRecordId,
    endTime = endTime,
    foodId = foodId,
    uuid = uuid,
    moodScore = moodScore,
    moodLabel = moodLabel,
)

fun BackupEntry.toEntity() = EntryEntity(
    id = id,
    timestamp = timestamp,
    glucoseMgdl = glucoseMgdl,
    mealContext = mealContext,
    insulinBasalUnits = insulinBasalUnits,
    insulinBolusUnits = insulinBolusUnits,
    carbsGrams = carbsGrams,
    proteinGrams = proteinGrams,
    fatGrams = fatGrams,
    mealDescription = mealDescription,
    exerciseMinutes = exerciseMinutes,
    exerciseIntensity = exerciseIntensity,
    note = note,
    source = source,
    hcRecordId = hcRecordId,
    endTime = endTime,
    foodId = foodId,
    uuid = uuid,
    moodScore = moodScore,
    moodLabel = moodLabel,
)

fun FoodEntity.toBackup() = BackupFood(
    id = id,
    uuid = uuid,
    name = name,
    brand = brand,
    barcode = barcode,
    carbsGrams = carbsGrams,
    proteinGrams = proteinGrams,
    fatGrams = fatGrams,
    kcal = kcal,
    servingGrams = servingGrams,
    servingLabel = servingLabel,
    source = source,
    offFetchedAt = offFetchedAt,
    userCorrected = userCorrected,
    useCount = useCount,
    lastUsedAt = lastUsedAt,
    isFavorite = isFavorite,
    createdAt = createdAt,
)

fun BackupFood.toEntity() = FoodEntity(
    id = id,
    uuid = uuid,
    name = name,
    brand = brand,
    barcode = barcode,
    carbsGrams = carbsGrams,
    proteinGrams = proteinGrams,
    fatGrams = fatGrams,
    kcal = kcal,
    servingGrams = servingGrams,
    servingLabel = servingLabel,
    source = source,
    offFetchedAt = offFetchedAt,
    userCorrected = userCorrected,
    useCount = useCount,
    lastUsedAt = lastUsedAt,
    isFavorite = isFavorite,
    createdAt = createdAt,
)

fun SupplyEntity.toBackup() = BackupSupply(
    id = id,
    type = type,
    startedAt = startedAt,
    expectedLifespanDays = expectedLifespanDays,
    replacedAt = replacedAt,
    uuid = uuid,
)

fun BackupSupply.toEntity() = SupplyEntity(
    id = id,
    type = type,
    startedAt = startedAt,
    expectedLifespanDays = expectedLifespanDays,
    replacedAt = replacedAt,
    uuid = uuid,
)

fun InsightCardEntity.toBackup() = BackupInsight(
    id = id,
    title = title,
    description = description,
    severityLevel = severityLevel,
    createdAt = createdAt,
)

fun BackupInsight.toEntity() = InsightCardEntity(
    id = id,
    title = title,
    description = description,
    severityLevel = severityLevel,
    createdAt = createdAt,
)

fun ChatMessageEntity.toBackup() = BackupChatMessage(
    id = id,
    role = role,
    content = content,
    timestamp = timestamp,
)

fun BackupChatMessage.toEntity() = ChatMessageEntity(
    id = id,
    role = role,
    content = content,
    timestamp = timestamp,
)

fun PendingAiQueryEntity.toBackup() = BackupPendingQuery(
    id = id,
    userMessageId = userMessageId,
    prompt = prompt,
    createdAt = createdAt,
    ttlSeconds = ttlSeconds,
)

fun BackupPendingQuery.toEntity() = PendingAiQueryEntity(
    id = id,
    userMessageId = userMessageId,
    prompt = prompt,
    createdAt = createdAt,
    ttlSeconds = ttlSeconds,
)

fun GlucoseSampleEntity.toBackup() = BackupGlucoseSample(
    id = id,
    timestamp = timestamp,
    glucoseMgdl = glucoseMgdl,
    source = source,
    externalId = externalId,
    hcRecordId = hcRecordId,
    trendArrow = trendArrow,
    sourcePackage = sourcePackage,
    recordingMethod = recordingMethod,
    importedAt = importedAt,
)

fun BackupGlucoseSample.toEntity() = GlucoseSampleEntity(
    id = id,
    timestamp = timestamp,
    glucoseMgdl = glucoseMgdl,
    source = source,
    // v14-era backups have hcRecordId only; later files send externalId.
    externalId = checkNotNull(externalId ?: hcRecordId) {
        "Glucose sample is missing externalId and hcRecordId"
    },
    hcRecordId = hcRecordId,
    trendArrow = trendArrow,
    sourcePackage = sourcePackage,
    recordingMethod = recordingMethod,
    importedAt = importedAt,
)
