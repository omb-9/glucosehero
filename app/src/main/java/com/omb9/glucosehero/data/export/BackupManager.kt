package com.omb9.glucosehero.data.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.room.withTransaction
import com.omb9.glucosehero.BuildConfig
import com.omb9.glucosehero.data.backup.EncryptedBackupCipher
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.ChatMessageDao
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.FoodDao
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.data.local.db.InsightDao
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao
import com.omb9.glucosehero.data.local.db.SupplyDao
import com.omb9.glucosehero.data.local.db.TagAnalyticDao
import com.omb9.glucosehero.data.local.entity.ChatMessageEntity
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.local.entity.InsightCardEntity
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity
import com.omb9.glucosehero.data.local.entity.SupplyEntity
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.DosingProfileLoad
import com.omb9.glucosehero.domain.model.DosingProfileValidation
import com.omb9.glucosehero.domain.model.ExportWhitelist
import com.omb9.glucosehero.domain.model.toRecord
import com.omb9.glucosehero.util.AppJson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.long
import kotlinx.serialization.serializer

enum class ImportMode { MERGE, REPLACE }

data class BackupExportSummary(val counts: BackupCounts)

data class BackupPreview(
    val counts: BackupCounts,
    val exportedAt: Long,
    val appVersion: String,
    val databaseVersion: Int,
    val earliestEntry: Long?,
    val latestEntry: Long?,
    val currentEntryCount: Int,
    val currentCounts: BackupCounts = BackupCounts(),
)

data class BackupImportSummary(val counts: BackupCounts, val mode: ImportMode)

/**
 * Owns the app's real backup path: a self-contained JSON envelope that covers
 * every user table plus profile/settings (minus the KeyStore-wrapped API key).
 *
 * Streams are sanitized against [ExportWhitelist] **before** any `.ghzk`
 * encryption ([EncryptedBackupCipher]) so secrets never enter the ciphertext.
 * Restore decodes with [BackupJson] (unknown keys rejected) so extra fields
 * cannot be injected into Room.
 *
 * Export streams page-by-page through [streamBackupEnvelope] so a CGM-heavy
 * history never becomes one giant in-memory String or List. Import is run
 * inside a single Room transaction so a partial import is never observable.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: GlucoseHeroDatabase,
    private val settingsDataStore: SettingsDataStore,
    private val encryptedBackupCipher: EncryptedBackupCipher,
) {
    private val entryDao: EntryDao = database.entryDao()
    private val foodDao: FoodDao = database.foodDao()
    private val supplyDao: SupplyDao = database.supplyDao()
    private val glucoseSampleDao: GlucoseSampleDao = database.glucoseSampleDao()
    private val insightDao: InsightDao = database.insightDao()
    private val chatMessageDao: ChatMessageDao = database.chatMessageDao()
    private val pendingAiQueryDao: PendingAiQueryDao = database.pendingAiQueryDao()
    private val tagAnalyticDao: TagAnalyticDao = database.tagAnalyticDao()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    /** 0..1 determinate progress for the current export. */
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _hasPreImportSnapshot = MutableStateFlow(false)
    val hasPreImportSnapshot: StateFlow<Boolean> = _hasPreImportSnapshot.asStateFlow()

    init {
        _hasPreImportSnapshot.value = latestPreImportSnapshot() != null
    }

    // ------------------------------------------------------------------ export

    suspend fun exportTo(uri: Uri): BackupExportSummary {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw BackupFormatException("Couldn't open $uri for writing.")
        return output.use { exportTo(it) }
    }

    suspend fun exportTo(output: OutputStream): BackupExportSummary = withContext(Dispatchers.IO) {
        _isWorking.value = true
        _progress.value = 0f
        try {
            val counts = loadCounts()
            writeBackup(output) { _progress.value = it }
            BackupExportSummary(counts)
        } finally {
            _isWorking.value = false
            _progress.value = 0f
        }
    }

    /**
     * Writes a timestamped backup into a user-selected document tree and prunes
     * it to the most recent [AUTO_BACKUP_KEEP] files. Used by the scheduled
     * auto-backup worker.
     */
    suspend fun exportToTree(treeUri: Uri): Uri = withContext(Dispatchers.IO) {
        val filename = "GlucoseHero_Backup_${FILE_TIMESTAMP.format(Instant.now())}.json"
        val documentUri = DocumentsContract.createDocument(
            context.contentResolver,
            treeUri,
            "application/json",
            filename,
        ) ?: throw BackupFormatException("Couldn't create a backup file in the selected folder.")

        val output = context.contentResolver.openOutputStream(documentUri)
            ?: throw BackupFormatException("Couldn't open $documentUri for writing.")
        output.use { exportTo(it) }

        pruneTreeBackups(treeUri, AUTO_BACKUP_KEEP)
        documentUri
    }

    /**
     * Same JSON envelope as [exportTo], wrapped with AES-GCM so the file can
     * be stored on WebDAV/Drive without exposing glucose history. The wrapping
     * key never leaves Android Keystore.
     */
    suspend fun exportEncryptedTo(uri: Uri): BackupExportSummary {
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw BackupFormatException("Couldn't open $uri for writing.")
        return output.use { exportEncryptedTo(it) }
    }

    suspend fun exportEncryptedTo(output: OutputStream): BackupExportSummary = withContext(Dispatchers.IO) {
        _isWorking.value = true
        _progress.value = 0f
        try {
            val counts = loadCounts()
            // Sanitize JSON (whitelist, no tokens) then encrypt. Never reverse.
            encryptedBackupCipher.encryptingOutputStream(output).use { encrypted ->
                writeBackup(encrypted) { _progress.value = it }
            }
            BackupExportSummary(counts)
        } finally {
            _isWorking.value = false
            _progress.value = 0f
        }
    }

    // ------------------------------------------------------------------ import

    suspend fun previewFrom(uri: Uri): BackupPreview {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupFormatException("Couldn't open $uri for reading.")
        return input.use { previewFrom(it) }
    }

    suspend fun previewFrom(input: InputStream): BackupPreview = withContext(Dispatchers.IO) {
        openMaybeDecrypted(input).use { stream ->
            JsonReader(stream.bufferedReader(Charsets.UTF_8)).use { reader ->
                val (header, _) = readHeader(reader)
                requireSupportedBackup(header.format, header.formatVersion)
                val current = loadCounts()
                BackupPreview(
                    counts = header.counts,
                    exportedAt = header.exportedAt,
                    appVersion = header.appVersion,
                    databaseVersion = header.databaseVersion,
                    earliestEntry = header.earliestEntry,
                    latestEntry = header.latestEntry,
                    currentEntryCount = current.entries,
                    currentCounts = current,
                )
            }
        }
    }

    suspend fun importFrom(uri: Uri, mode: ImportMode): BackupImportSummary {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw BackupFormatException("Couldn't open $uri for reading.")
        return input.use { importFrom(it, mode) }
    }

    suspend fun importFrom(input: InputStream, mode: ImportMode): BackupImportSummary =
        withContext(Dispatchers.IO) {
            _isWorking.value = true
            _progress.value = 0f
            try {
                openMaybeDecrypted(input).use { stream ->
                    JsonReader(stream.bufferedReader(Charsets.UTF_8)).use { reader ->
                        val (header, firstArrayName) = readHeader(reader)
                        requireSupportedBackup(header.format, header.formatVersion)

                        // Snapshot the current database to filesDir (not cache) before
                        // the import transaction starts, so a rollback still leaves
                        // a one-tap undo file.
                        writePreImportSnapshot()

                        database.withTransaction {
                            restoreStreaming(reader, firstArrayName, header, mode)
                        }

                        restoreProfileAndSettings(header.profile, header.settings)
                        BackupImportSummary(header.counts, mode)
                    }
                }
            } finally {
                _isWorking.value = false
                _progress.value = 0f
            }
        }

    /**
     * Restores the newest pre-import snapshot from filesDir. Does not use SAF.
     * Runs through [importFrom] so a snapshot of the current (possibly bad)
     * state is taken first, then REPLACE is applied inside one transaction.
     */
    suspend fun restoreLatestPreImportSnapshot(): BackupImportSummary {
        val file = latestPreImportSnapshot()
            ?: throw BackupFormatException("No snapshot to restore.")
        return FileInputStream(file).use { importFrom(it, ImportMode.REPLACE) }
    }

    fun latestPreImportSnapshot(): File? =
        preImportSnapshotFiles().maxByOrNull { it.lastModified() }

    // ------------------------------------------------------------- internals

    private fun openMaybeDecrypted(input: InputStream): InputStream {
        val peek = EncryptedBackupCipher.looksEncrypted(input)
        if (!peek.encrypted) return peek.stream
        return try {
            encryptedBackupCipher.decryptingInputStream(peek.stream)
        } catch (e: BackupFormatException) {
            throw e
        } catch (_: Exception) {
            throw BackupFormatException(
                "Couldn't decrypt this backup on this device. Encrypted backups can only be opened where they were created.",
            )
        }
    }

    private suspend fun writeBackup(
        output: OutputStream,
        onProgress: suspend (Float) -> Unit,
    ) {
        val counts = loadCounts()
        // Two single-row index scans give the preview date range without
        // materializing any entry array. Cheaper than tracking min/max through
        // the page stream, and it keeps the streaming writer oblivious to
        // entry shape.
        val earliestEntry = entryDao.pageByTimestampForExport(1, 0).firstOrNull()?.timestamp
        val latestEntry = entryDao.recentEntries(1).firstOrNull()?.timestamp
        val profile = settingsDataStore.profileSnapshot().let { p ->
            BackupProfile(
                profileTarget = p.profileTarget,
                name = p.name,
                age = p.age,
                diabetesType = p.diabetesType,
                heightCm = p.heightCm,
                weightKg = p.weightKg,
            )
        }
        val settings = snapshotSettings()

        val jsonWriter = JsonWriter(output.bufferedWriter(Charsets.UTF_8))
        streamBackupEnvelope(
            sink = AndroidJsonSink(jsonWriter),
            profile = profile,
            settings = settings,
            counts = counts,
            appVersion = BuildConfig.VERSION_NAME,
            exportedAt = System.currentTimeMillis(),
            earliestEntry = earliestEntry,
            latestEntry = latestEntry,
            pageSize = EXPORT_PAGE_SIZE,
            foods = { offset, limit -> foodDao.pageForExport(limit, offset).map { it.toBackup() } },
            entries = { offset, limit -> entryDao.pageForExport(limit, offset).map { it.toBackup() } },
            supplies = { offset, limit -> supplyDao.pageForExport(limit, offset).map { it.toBackup() } },
            glucoseSamples = { offset, limit ->
                glucoseSampleDao.pageForExport(limit, offset).map { it.toBackup() }
            },
            chat = { offset, limit -> chatMessageDao.pageForExport(limit, offset).map { it.toBackup() } },
            pendingAiQueries = { offset, limit ->
                pendingAiQueryDao.pageForExport(limit, offset).map { it.toBackup() }
            },
            insights = { offset, limit -> insightDao.pageForExport(limit, offset).map { it.toBackup() } },
            onProgress = onProgress,
        )
        jsonWriter.flush()
    }

    private suspend fun loadCounts() = BackupCounts(
        entries = entryDao.countAll(),
        glucoseSamples = glucoseSampleDao.count(),
        foods = foodDao.countAll(),
        supplies = supplyDao.countAll(),
        insights = insightDao.countAll(),
        chat = chatMessageDao.countAll(),
        pendingAiQueries = pendingAiQueryDao.countAll(),
    )

    private suspend fun snapshotSettings(): BackupSettings {
        val settings = settingsDataStore.settings.first()
        val aiConfig = settingsDataStore.aiConfig.first()
        val profileLoad = settingsDataStore.dosingProfileSnapshot()
        val dosingRecord = when (profileLoad) {
            is DosingProfileLoad.Valid ->
                profileLoad.profile.toRecord()
            is DosingProfileLoad.Invalid ->
                profileLoad.editorDraft?.toRecord()
        }
        val midnight = when (profileLoad) {
            is DosingProfileLoad.Valid ->
                profileLoad.profile.midnightBolusSettings()
            is DosingProfileLoad.Invalid ->
                settingsDataStore.bolusSettings.first()
        }

        // PERMANENT SECURITY BOUNDARY
        // The KeyStore-encrypted API key blob (DataStore key `ai_api_key_enc`)
        // and Nightscout credential blob (`cgm_nightscout_credential_enc`) are
        // wrapped by a hardware-backed, non-exportable Android Keystore key.
        // That wrapping key does not travel with backups, so the ciphertext is
        // worthless on any other device (and after a factory reset on this one).
        // This is the same reasoning documented in backup_rules.xml. Do not add
        // either blob to the backup JSON "for completeness."
        @Suppress("UNUSED_VARIABLE")
        val _encryptedApiKeyToDrop: String? = settingsDataStore.encryptedApiKey()
        @Suppress("UNUSED_VARIABLE")
        val _encryptedNightscoutToDrop: String? = settingsDataStore.encryptedNightscoutCredential()

        return BackupSettings(
            themeMode = settings.themeMode,
            accent = settings.accent,
            unit = settings.unit,
            use24HourTime = settings.use24HourTime,
            targetLowMgdl = settings.targetLowMgdl,
            targetHighMgdl = settings.targetHighMgdl,
            isHeroAiEnabled = settings.isHeroAiEnabled,
            showAdvancedMacros = settings.showAdvancedMacros,
            postMealRemindersEnabled = settings.postMealRemindersEnabled,
            aiProvider = aiConfig.provider,
            aiBaseUrl = aiConfig.baseUrl,
            aiModel = aiConfig.model,
            diaHours = midnight.diaHours,
            cirRatio = midnight.cirRatio,
            isfMgdl = midnight.isfMgdl,
            targetGlucoseMgdl = midnight.targetGlucoseMgdl,
            dosingProfile = dosingRecord,
            barcodeLookupEnabled = settingsDataStore.barcodeLookupEnabled.first(),
            healthConnectSyncEnabled = settingsDataStore.healthConnectSyncEnabled.first(),
            glucoseImportEnabled = settingsDataStore.glucoseImportEnabled.first(),
            nutritionImportEnabled = settingsDataStore.nutritionImportEnabled.first(),
            exerciseImportEnabled = settingsDataStore.exerciseImportEnabled.first(),
            sleepImportEnabled = settingsDataStore.sleepImportEnabled.first(),
            cycleImportEnabled = settingsDataStore.cycleImportEnabled.first(),
            healthConnectInitialImportRange = settingsDataStore.healthConnectInitialImportRange.first(),
        )
    }

    private suspend fun restoreStreaming(
        reader: JsonReader,
        firstArrayName: String?,
        header: BackupHeader,
        mode: ImportMode,
    ) {
        if (mode == ImportMode.REPLACE) {
            wipeUserTables()
        }

        // Foods is a small user-authored table; we load uuid→id so entries can
        // remap foodId. Entries and supplies skip duplicates via INSERT OR
        // IGNORE on their unique uuid indexes (migration 8→9), so we never
        // materialize those uuid sets. Glucose samples use INSERT OR IGNORE
        // on hc_record_id the same way.
        val existingFoodByUuid =
            if (mode == ImportMode.MERGE) {
                foodDao.getAll().associateBy { it.uuid }.toMutableMap()
            } else {
                mutableMapOf()
            }
        val existingChatByKey =
            if (mode == ImportMode.MERGE) {
                chatMessageDao.getAll().associateBy { chatKey(it) }
            } else {
                emptyMap()
            }
        val existingPendingKeys =
            if (mode == ImportMode.MERGE) {
                pendingAiQueryDao.getAll().map { pendingKey(it) }.toHashSet()
            } else {
                emptySet()
            }
        val existingInsightKeys =
            if (mode == ImportMode.MERGE) {
                insightDao.getAll().map { insightKey(it) }.toHashSet()
            } else {
                emptySet()
            }

        // Original ids map to the ids they receive (or already have) on this
        // device. Both maps are bounded by small tables and only built on merge.
        val foodIdMap = HashMap<Long, Long>()
        val chatIdMap = HashMap<Long, Long>()
        var actual = BackupCounts()
        val expectedTotal = header.counts.total().coerceAtLeast(1)
        fun reportImportProgress() {
            _progress.value = (actual.total().toFloat() / expectedTotal).coerceIn(0f, 1f)
        }

        var name = firstArrayName
        while (name != null) {
            when (name) {
                "foods" -> {
                    actual = actual.copy(foods = streamFoods(reader, mode, existingFoodByUuid, foodIdMap))
                    reportImportProgress()
                }
                "entries" -> {
                    actual = actual.copy(entries = streamEntries(reader, mode, foodIdMap))
                    reportImportProgress()
                }
                "supplies" -> {
                    actual = actual.copy(supplies = streamSupplies(reader, mode))
                    reportImportProgress()
                }
                "glucoseSamples" -> {
                    actual = actual.copy(glucoseSamples = streamGlucoseSamples(reader, mode))
                    reportImportProgress()
                }
                "chat" -> {
                    actual = actual.copy(chat = streamChat(reader, mode, existingChatByKey, chatIdMap))
                    reportImportProgress()
                }
                "pendingAiQueries" -> {
                    actual = actual.copy(
                        pendingAiQueries = streamPendingQueries(reader, mode, existingPendingKeys, chatIdMap),
                    )
                    reportImportProgress()
                }
                "insights" -> {
                    actual = actual.copy(insights = streamInsights(reader, mode, existingInsightKeys))
                    reportImportProgress()
                }
                else -> throw BackupFormatException("Backup contains unsupported field '$name'.")
            }
            name = if (reader.hasNext()) reader.nextName() else null
        }
        reader.endObject()

        if (mode == ImportMode.MERGE) {
            // Derived data regenerated by the nightly worker; clear so it reflects
            // the merged set instead of the pre-import set.
            tagAnalyticDao.clear()
        }

        verifyCounts(header.counts, actual)
        _progress.value = 1f
    }

    private suspend fun wipeUserTables() {
        // No foreign keys in the schema, but clear dependents first anyway.
        entryDao.clear()
        pendingAiQueryDao.clear()
        glucoseSampleDao.clear()
        supplyDao.clear()
        insightDao.clear()
        chatMessageDao.clear()
        foodDao.clear()
        // Derived data regenerated by the nightly worker; never imported.
        tagAnalyticDao.clear()
    }

    private suspend fun streamFoods(
        reader: JsonReader,
        mode: ImportMode,
        existingFoodByUuid: MutableMap<String, FoodEntity>,
        foodIdMap: MutableMap<Long, Long>,
    ): Int = reader.forEachInArray(BackupFood.serializer(), "foods", IMPORT_BATCH_SIZE) { batch ->
        when (mode) {
            ImportMode.REPLACE -> foodDao.insertAll(batch.map { it.toEntity() })
            ImportMode.MERGE -> {
                for (food in batch) {
                    val existing = existingFoodByUuid[food.uuid]
                    if (existing != null) {
                        foodIdMap[food.id] = existing.id
                        continue
                    }
                    val entity = food.toEntity().copy(id = 0L)
                    val insertedId = foodDao.insertIgnore(entity)
                    val resolvedId = if (insertedId != -1L) {
                        insertedId
                    } else {
                        foodDao.getByUuid(food.uuid)?.id
                            ?: food.barcode?.let { foodDao.getByBarcode(it)?.id }
                            ?: continue
                    }
                    foodIdMap[food.id] = resolvedId
                    if (insertedId != -1L) {
                        existingFoodByUuid[food.uuid] = entity.copy(id = resolvedId)
                    }
                }
            }
        }
    }

    private suspend fun streamEntries(
        reader: JsonReader,
        mode: ImportMode,
        foodIdMap: Map<Long, Long>,
    ): Int = reader.forEachInArray(BackupEntry.serializer(), "entries", IMPORT_BATCH_SIZE) { batch ->
        when (mode) {
            ImportMode.REPLACE -> entryDao.insertAll(batch.map { it.toEntity() })
            ImportMode.MERGE -> {
                val remapped = batch.map { entry ->
                    val remappedFoodId = entry.foodId?.let { foodIdMap[it] ?: it }
                    entry.toEntity().copy(id = 0L, foodId = remappedFoodId)
                }
                entryDao.insertIgnoreAll(remapped)
            }
        }
    }

    private suspend fun streamSupplies(
        reader: JsonReader,
        mode: ImportMode,
    ): Int = reader.forEachInArray(BackupSupply.serializer(), "supplies", IMPORT_BATCH_SIZE) { batch ->
        when (mode) {
            ImportMode.REPLACE -> supplyDao.insertAll(batch.map { it.toEntity() })
            ImportMode.MERGE -> {
                supplyDao.insertIgnoreAll(batch.map { it.toEntity().copy(id = 0L) })
            }
        }
    }

    private suspend fun streamGlucoseSamples(
        reader: JsonReader,
        mode: ImportMode,
    ): Int = reader.forEachInArray(BackupGlucoseSample.serializer(), "glucoseSamples", IMPORT_BATCH_SIZE) { batch ->
        when (mode) {
            ImportMode.REPLACE -> glucoseSampleDao.insertAll(batch.map { it.toEntity() })
            // INSERT OR IGNORE: glucose_samples has a unique index on
            // hc_record_id, so re-importing an existing sample is a no-op and
            // merge stays idempotent without holding every existing id in memory.
            ImportMode.MERGE -> glucoseSampleDao.upsertAll(batch.map { it.toEntity().copy(id = 0L) })
        }
    }

    private suspend fun streamChat(
        reader: JsonReader,
        mode: ImportMode,
        existingChatByKey: Map<ChatKey, ChatMessageEntity>,
        chatIdMap: MutableMap<Long, Long>,
    ): Int = reader.forEachInArray(BackupChatMessage.serializer(), "chat", IMPORT_BATCH_SIZE) { batch ->
        when (mode) {
            ImportMode.REPLACE -> chatMessageDao.insertAll(batch.map { it.toEntity() })
            ImportMode.MERGE -> {
                for (chat in batch) {
                    val key = backupChatKey(chat)
                    val existing = existingChatByKey[key]
                    chatIdMap[chat.id] = if (existing != null) {
                        existing.id
                    } else {
                        chatMessageDao.insert(chat.toEntity().copy(id = 0L))
                    }
                }
            }
        }
    }

    private suspend fun streamPendingQueries(
        reader: JsonReader,
        mode: ImportMode,
        existingPendingKeys: Set<PendingKey>,
        chatIdMap: Map<Long, Long>,
    ): Int = reader.forEachInArray(BackupPendingQuery.serializer(), "pendingAiQueries", IMPORT_BATCH_SIZE) { batch ->
        when (mode) {
            ImportMode.REPLACE -> pendingAiQueryDao.insertAll(batch.map { it.toEntity() })
            ImportMode.MERGE -> {
                val newPending = ArrayList<PendingAiQueryEntity>(batch.size)
                for (pending in batch) {
                    val remappedUserMessageId = chatIdMap[pending.userMessageId] ?: pending.userMessageId
                    val candidate = pending.toEntity().copy(
                        id = 0L,
                        userMessageId = remappedUserMessageId,
                    )
                    if (pendingKey(candidate) in existingPendingKeys) continue
                    newPending.add(candidate)
                }
                pendingAiQueryDao.insertAll(newPending)
            }
        }
    }

    private suspend fun streamInsights(
        reader: JsonReader,
        mode: ImportMode,
        existingInsightKeys: Set<InsightKey>,
    ): Int = reader.forEachInArray(BackupInsight.serializer(), "insights", IMPORT_BATCH_SIZE) { batch ->
        when (mode) {
            ImportMode.REPLACE -> insightDao.insertAll(batch.map { it.toEntity() })
            ImportMode.MERGE -> {
                val newInsights = ArrayList<InsightCardEntity>(batch.size)
                for (insight in batch) {
                    val candidate = insight.toEntity().copy(id = 0L)
                    if (insightKey(candidate) in existingInsightKeys) continue
                    newInsights.add(candidate)
                }
                insightDao.insertAll(newInsights)
            }
        }
    }

    private suspend fun restoreProfileAndSettings(
        profile: BackupProfile,
        settings: BackupSettings,
    ) {
        settingsDataStore.setProfileTarget(profile.profileTarget)
        settingsDataStore.setProfileName(profile.name)
        settingsDataStore.setProfileAge(profile.age)
        settingsDataStore.setProfileDiabetesType(profile.diabetesType)
        settingsDataStore.setProfileHeightCm(profile.heightCm)
        settingsDataStore.setProfileWeightKg(profile.weightKg)

        settingsDataStore.setThemeMode(settings.themeMode)
        settingsDataStore.setAccent(settings.accent)
        settingsDataStore.setUnit(settings.unit)
        settingsDataStore.setUse24HourTime(settings.use24HourTime)
        settingsDataStore.setTargetRange(settings.targetLowMgdl, settings.targetHighMgdl)
        settingsDataStore.setIsHeroAiEnabled(settings.isHeroAiEnabled)
        settingsDataStore.setShowAdvancedMacros(settings.showAdvancedMacros)
        settingsDataStore.setPostMealRemindersEnabled(settings.postMealRemindersEnabled)
        // Provider first; it resets base URL + model to presets, then the
        // backed-up values override them.
        settingsDataStore.setAiProvider(settings.aiProvider)
        settingsDataStore.setAiBaseUrl(settings.aiBaseUrl)
        settingsDataStore.setAiModel(settings.aiModel)
        val restoredProfile = settings.restoredDosingProfile()
        when (val validated = restoredProfile.validate()) {
            is DosingProfileValidation.Valid ->
                settingsDataStore.setDosingProfile(validated.profile)
            is DosingProfileValidation.Invalid -> {
                settingsDataStore.setDiaHours(settings.diaHours)
                settingsDataStore.setCirRatio(settings.cirRatio)
                settingsDataStore.setIsfMgdl(settings.isfMgdl)
                settingsDataStore.setTargetGlucoseMgdl(settings.targetGlucoseMgdl)
            }
        }
        settingsDataStore.setBarcodeLookupEnabled(settings.barcodeLookupEnabled)
        settingsDataStore.setHealthConnectSyncEnabled(settings.healthConnectSyncEnabled)
        settingsDataStore.setGlucoseImportEnabled(settings.glucoseImportEnabled)
        settingsDataStore.setNutritionImportEnabled(settings.nutritionImportEnabled)
        settingsDataStore.setExerciseImportEnabled(settings.exerciseImportEnabled)
        settingsDataStore.setSleepImportEnabled(settings.sleepImportEnabled)
        settingsDataStore.setCycleImportEnabled(settings.cycleImportEnabled)
        settingsDataStore.setHealthConnectInitialImportRange(
            settings.healthConnectInitialImportRange,
        )
    }

    private suspend fun writePreImportSnapshot() {
        val dir = File(context.filesDir, SNAPSHOT_DIR).apply { mkdirs() }
        val file = File(dir, "$SNAPSHOT_PREFIX${System.currentTimeMillis()}-${System.nanoTime()}.json")
        FileOutputStream(file).use { writeBackup(it) { } }
        prunePreImportSnapshots(dir, SNAPSHOT_KEEP)
        _hasPreImportSnapshot.value = latestPreImportSnapshot() != null
    }

    private fun preImportSnapshotFiles(): List<File> {
        val dir = File(context.filesDir, SNAPSHOT_DIR)
        return dir.listFiles { file ->
            file.isFile &&
                file.name.startsWith(SNAPSHOT_PREFIX) &&
                file.name.endsWith(".json")
        }?.toList().orEmpty()
    }

    private fun pruneTreeBackups(treeUri: Uri, keep: Int) {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val documents = mutableListOf<Pair<Long, Uri>>()

        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val modifiedIdx =
                cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val mime = cursor.getString(mimeIdx)
                if (mime == "application/json" || mime == "application/octet-stream") {
                    val docId = cursor.getString(idIdx)
                    val modified = cursor.getLong(modifiedIdx)
                    documents.add(modified to DocumentsContract.buildDocumentUriUsingTree(treeUri, docId))
                }
            }
        }

        documents.sortedByDescending { it.first }
            .drop(keep)
            .forEach { (_, uri) ->
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            }
    }

    private data class ChatKey(val role: ChatRole, val content: String, val timestamp: Long)
    private data class PendingKey(val userMessageId: Long, val prompt: String, val createdAt: Long)
    private data class InsightKey(
        val title: String,
        val description: String,
        val severityLevel: Int,
        val createdAt: Long,
    )

    private fun chatKey(entity: ChatMessageEntity) =
        ChatKey(entity.role, entity.content, entity.timestamp)

    private fun backupChatKey(backup: BackupChatMessage) =
        ChatKey(backup.role, backup.content, backup.timestamp)

    private fun pendingKey(entity: PendingAiQueryEntity) =
        PendingKey(entity.userMessageId, entity.prompt, entity.createdAt)

    private fun insightKey(entity: InsightCardEntity) =
        InsightKey(entity.title, entity.description, entity.severityLevel, entity.createdAt)

    private companion object {
        const val EXPORT_PAGE_SIZE = 500
        const val IMPORT_BATCH_SIZE = 500
        const val SNAPSHOT_DIR = "backups"
        const val SNAPSHOT_PREFIX = "pre-import-"
        const val SNAPSHOT_KEEP = 2
        const val AUTO_BACKUP_KEEP = 5

        val FILE_TIMESTAMP: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault())
    }
}

/**
 * Keeps only the [keep] newest `pre-import-*.json` files in [dir]. Other
 * files in the directory are left alone.
 */
internal fun prunePreImportSnapshots(dir: File, keep: Int) {
    dir.listFiles { file ->
        file.isFile &&
            file.name.startsWith("pre-import-") &&
            file.name.endsWith(".json")
    }
        ?.sortedByDescending { it.lastModified() }
        ?.drop(keep)
        ?.forEach { it.delete() }
}

/** android.util.JsonWriter adapter for [streamBackupEnvelope]. */
private class AndroidJsonSink(private val writer: JsonWriter) : BackupJsonSink {
    override fun beginObject() {
        writer.beginObject()
    }

    override fun endObject() {
        writer.endObject()
    }

    override fun beginArray() {
        writer.beginArray()
    }

    override fun endArray() {
        writer.endArray()
    }

    override fun name(name: String) {
        writer.name(name)
    }

    override fun value(string: String) {
        writer.value(string)
    }

    override fun value(number: Long) {
        writer.value(number)
    }

    override fun value(number: Int) {
        writer.value(number.toLong())
    }

    override fun nullValue() {
        writer.nullValue()
    }

    override fun rawValue(json: String) {
        writeElement(AppJson.parseToJsonElement(json))
    }

    override fun flush() {
        writer.flush()
    }

    private fun writeElement(element: kotlinx.serialization.json.JsonElement) {
        when (element) {
            is JsonNull -> writer.nullValue()
            is JsonPrimitive -> writePrimitive(element)
            is JsonObject -> {
                writer.beginObject()
                for ((key, value) in element) {
                    writer.name(key)
                    writeElement(value)
                }
                writer.endObject()
            }
            is JsonArray -> {
                writer.beginArray()
                for (value in element) writeElement(value)
                writer.endArray()
            }
        }
    }

    private fun writePrimitive(primitive: JsonPrimitive) {
        if (primitive.isString) {
            writer.value(primitive.content)
            return
        }
        primitive.booleanOrNull?.let { writer.value(it); return }
        val content = primitive.content
        if (content.contains('.') || content.contains('e', ignoreCase = true)) {
            writer.value(primitive.double)
        } else {
            writer.value(primitive.long)
        }
    }
}

internal data class BackupHeader(
    val format: String,
    val formatVersion: Int,
    val appVersion: String,
    val databaseVersion: Int,
    val exportedAt: Long,
    val counts: BackupCounts,
    val profile: BackupProfile,
    val settings: BackupSettings,
    val earliestEntry: Long?,
    val latestEntry: Long?,
)

/**
 * Reads the leading envelope fields (everything before the arrays) without
 * materializing any array. Returns the header plus the name of the first array
 * field already consumed by this read, so the streaming import can resume from
 * the correct position.
 */
internal fun readHeader(reader: JsonReader): Pair<BackupHeader, String?> {
    reader.beginObject()
    var format = ""
    var formatVersion = 0
    var appVersion = ""
    var databaseVersion = 0
    var exportedAt = 0L
    var counts = BackupCounts()
    var profile = BackupProfile()
    var settings = BackupSettings()
    var earliestEntry: Long? = null
    var latestEntry: Long? = null
    var firstArrayName: String? = null
    while (reader.hasNext() && firstArrayName == null) {
        when (val name = reader.nextName()) {
            "format" -> format = reader.nextString()
            "formatVersion" -> formatVersion = reader.nextInt()
            "appVersion" -> appVersion = reader.nextString()
            "databaseVersion" -> databaseVersion = reader.nextInt()
            "exportedAt" -> exportedAt = reader.nextLong()
            "counts" -> counts = reader.decodeRaw("counts")
            "profile" -> profile = reader.decodeRaw("profile")
            "settings" -> settings = reader.decodeRaw("settings")
            "earliestEntry" -> earliestEntry = reader.nextNullableLong()
            "latestEntry" -> latestEntry = reader.nextNullableLong()
            else -> {
                if (name in ExportWhitelist.arrayKeys) {
                    firstArrayName = name
                } else {
                    throw BackupFormatException("Backup contains unsupported field '$name'.")
                }
            }
        }
    }
    return BackupHeader(
        format, formatVersion, appVersion, databaseVersion, exportedAt,
        counts, profile, settings, earliestEntry, latestEntry,
    ) to firstArrayName
}

private fun JsonReader.nextNullableLong(): Long? =
    if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        nextLong()
    }

/** Decodes the next complete JSON value by first capturing its raw text. */
private inline fun <reified T> JsonReader.decodeRaw(fieldName: String): T =
    decodeWhitelisted(serializer(), readRawValue(this), fieldName)

/** Copies the next complete JSON value into a string so it can be decoded lazily. */
private fun readRawValue(reader: JsonReader): String {
    val out = StringBuilder(256)
    copyValue(reader, out)
    return out.toString()
}

private fun copyValue(reader: JsonReader, out: StringBuilder) {
    when (reader.peek()) {
        JsonToken.BEGIN_ARRAY -> {
            out.append('[')
            reader.beginArray()
            var first = true
            while (reader.hasNext()) {
                if (!first) out.append(',')
                first = false
                copyValue(reader, out)
            }
            reader.endArray()
            out.append(']')
        }
        JsonToken.BEGIN_OBJECT -> {
            out.append('{')
            reader.beginObject()
            var first = true
            while (reader.hasNext()) {
                if (!first) out.append(',')
                first = false
                out.append('"').append(escapeJsonString(reader.nextName())).append("\":")
                copyValue(reader, out)
            }
            reader.endObject()
            out.append('}')
        }
        JsonToken.STRING -> out.append('"').append(escapeJsonString(reader.nextString())).append('"')
        JsonToken.NUMBER -> out.append(reader.nextString())
        JsonToken.BOOLEAN -> out.append(reader.nextBoolean())
        JsonToken.NULL -> {
            reader.nextNull()
            out.append("null")
        }
        else -> throw BackupFormatException("Unexpected JSON token: ${reader.peek()}")
    }
}

private fun escapeJsonString(value: String): String {
    val escaped = StringBuilder(value.length + 16)
    for (ch in value) {
        when (ch) {
            '"' -> escaped.append("\\\"")
            '\\' -> escaped.append("\\\\")
            '\n' -> escaped.append("\\n")
            '\r' -> escaped.append("\\r")
            '\t' -> escaped.append("\\t")
            '\b' -> escaped.append("\\b")
            '\u000C' -> escaped.append("\\f")
            else -> {
                if (ch.code < 0x20) {
                    escaped.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    escaped.append(ch)
                }
            }
        }
    }
    return escaped.toString()
}

/**
 * Streams one array element at a time, flushing decoded rows in bounded
 * batches. Returns the number of decoded elements so the caller can verify the
 * header count against what was actually read.
 */
private suspend fun <T : Any> JsonReader.forEachInArray(
    serializer: KSerializer<T>,
    fieldName: String,
    batchSize: Int,
    onBatch: suspend (List<T>) -> Unit,
): Int {
    beginArray()
    var count = 0
    val batch = ArrayList<T>(batchSize)
    while (hasNext()) {
        batch.add(decodeWhitelisted(serializer, readRawValue(this), fieldName))
        count++
        if (batch.size >= batchSize) {
            onBatch(batch)
            batch.clear()
        }
    }
    endArray()
    if (batch.isNotEmpty()) onBatch(batch)
    return count
}
