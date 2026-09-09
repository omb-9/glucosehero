package com.omb9.glucosehero.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import com.omb9.glucosehero.exercise.LiveActiveCalories
import com.omb9.glucosehero.exercise.LiveExerciseSession
import com.omb9.glucosehero.exercise.LiveHeartRateSample
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.omb9.glucosehero.BuildConfig
import com.omb9.glucosehero.data.local.datastore.InitialImportRange
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.domain.model.EntrySource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Moves Health Connect data into local storage.
 *
 * High-frequency glucose samples land in [GlucoseSampleDao]'s table; the
 * human-scale nutrition, exercise, sleep, and cycle records land in the shared
 * entries log
 * with `source = HEALTH_CONNECT` and `hc_record_id` set, so the log screen's
 * source badge can mark them and the detail screen can refuse to edit them.
 *
 * Imports use a paginated [HealthConnectClient.readRecords] loop with `pageSize = 1000`,
 * following `response.pageToken` until null. Health Connect enforces per-app read rate limits
 * on real hardware, so an unpaginated 90-day pull of high-frequency samples will fail.
 *
 * Records whose originating package matches [BuildConfig.APPLICATION_ID] are filtered out
 * to prevent echo when write-back ships.
 *
 * Re-imports are idempotent: both glucose samples and entries rely on a unique
 * index on their respective `hc_record_id` columns with an [androidx.room.OnConflictStrategy.IGNORE]
 * insert, safely ignoring already-imported rows.
 */
@Singleton
class HealthConnectRepository(
    private val client: HealthConnectClient?,
    private val glucoseSampleDao: GlucoseSampleDao,
    private val entryDao: EntryDao,
    private val settingsDataStore: SettingsDataStore? = null,
) {
    @Inject
    constructor(
        @Suppress("UNUSED_PARAMETER") @ApplicationContext context: Context,
        client: HealthConnectClient?,
        glucoseSampleDao: GlucoseSampleDao,
        entryDao: EntryDao,
        settingsDataStore: SettingsDataStore,
    ) : this(client, glucoseSampleDao, entryDao, settingsDataStore)

    val readPermissions = setOf(
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(MenstruationPeriodRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY,
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    /** Optional reads for exercise-fueling alerts. Not required for "connected". */
    val exerciseFuelingPermissions = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    val writePermissions = setOf(
        HealthPermission.getWritePermission(BloodGlucoseRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    val allPermissions = readPermissions + writePermissions + exerciseFuelingPermissions

    suspend fun grantedPermissions(): Set<String> =
        client?.permissionController?.getGrantedPermissions().orEmpty()

    /**
     * Recent heart-rate samples from Health Connect. Empty when the client is
     * missing, permission is denied, or no records exist.
     */
    suspend fun readRecentHeartRate(since: Instant): List<LiveHeartRateSample> {
        val hc = client ?: return emptyList()
        return try {
            readAllRecords<HeartRateRecord>(hc, TimeRangeFilter.after(since)).flatMap { record ->
                record.samples.map { sample ->
                    LiveHeartRateSample(
                        timestampMillis = sample.time.toEpochMilli(),
                        beatsPerMinute = sample.beatsPerMinute.toLong(),
                    )
                }
            }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Recent exercise sessions overlapping [since]. Empty on missing data or permission. */
    suspend fun readRecentExerciseSessions(since: Instant): List<LiveExerciseSession> {
        val hc = client ?: return emptyList()
        return try {
            readAllRecords<ExerciseSessionRecord>(hc, TimeRangeFilter.after(since)).map { record ->
                LiveExerciseSession(
                    startMillis = record.startTime.toEpochMilli(),
                    endMillis = record.endTime.toEpochMilli(),
                    title = record.title,
                )
            }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Active calories burned since [since]. Empty on missing data or permission. */
    suspend fun readRecentActiveCalories(since: Instant): List<LiveActiveCalories> {
        val hc = client ?: return emptyList()
        return try {
            readAllRecords<ActiveCaloriesBurnedRecord>(hc, TimeRangeFilter.after(since)).map { record ->
                LiveActiveCalories(
                    startMillis = record.startTime.toEpochMilli(),
                    endMillis = record.endTime.toEpochMilli(),
                    kcal = record.energy.inKilocalories,
                )
            }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend inline fun <reified T : Record> readAllRecords(
        hc: HealthConnectClient,
        filter: TimeRangeFilter,
    ): List<T> {
        var pageToken: String? = null
        val out = ArrayList<T>()
        do {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = T::class,
                    timeRangeFilter = filter,
                    pageSize = 1_000,
                    pageToken = pageToken,
                )
            )
            out.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)
        return out
    }

    suspend fun importGlucose(since: Instant): Int =
        importGlucose(TimeRangeFilter.after(since))

    suspend fun importGlucose(filter: TimeRangeFilter): Int {
        if (settingsDataStore?.glucoseImportEnabled?.first() == false) return 0
        val hc = client ?: return 0
        var pageToken: String? = null
        var imported = 0
        do {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = BloodGlucoseRecord::class,
                    timeRangeFilter = filter,
                    pageSize = 1_000,
                    pageToken = pageToken,
                )
            )
            val samples = response.records
                .filterNot { it.metadata.dataOrigin.packageName == BuildConfig.APPLICATION_ID }
                .map { with(HealthConnectMapper) { it.toSample(System.currentTimeMillis()) } }
            if (samples.isNotEmpty()) {
                imported += glucoseSampleDao.upsertAll(samples).count { it != -1L }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return imported
    }

    suspend fun importNutrition(since: Instant): Int =
        importNutrition(TimeRangeFilter.after(since))

    suspend fun importNutrition(filter: TimeRangeFilter): Int {
        if (settingsDataStore?.nutritionImportEnabled?.first() == false) return 0
        val hc = client ?: return 0
        var pageToken: String? = null
        var imported = 0
        do {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = NutritionRecord::class,
                    timeRangeFilter = filter,
                    pageSize = 1_000,
                    pageToken = pageToken,
                )
            )
            val entries = response.records
                .filterNot { it.metadata.dataOrigin.packageName == BuildConfig.APPLICATION_ID }
                .map { with(HealthConnectMapper) { it.toEntry() } }
            if (entries.isNotEmpty()) {
                imported += entryDao.upsertAll(entries).count { it != -1L }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return imported
    }

    suspend fun importExercise(since: Instant): Int =
        importExercise(TimeRangeFilter.after(since))

    suspend fun importExercise(filter: TimeRangeFilter): Int {
        if (settingsDataStore?.exerciseImportEnabled?.first() == false) return 0
        val hc = client ?: return 0
        var pageToken: String? = null
        var imported = 0
        do {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = filter,
                    pageSize = 1_000,
                    pageToken = pageToken,
                )
            )
            val entries = response.records
                .filterNot { it.metadata.dataOrigin.packageName == BuildConfig.APPLICATION_ID }
                .map { with(HealthConnectMapper) { it.toEntry() } }
            if (entries.isNotEmpty()) {
                imported += entryDao.upsertAll(entries).count { it != -1L }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return imported
    }

    suspend fun importSleep(since: Instant): Int =
        importSleep(TimeRangeFilter.after(since))

    suspend fun importSleep(filter: TimeRangeFilter): Int {
        if (settingsDataStore?.sleepImportEnabled?.first() == false) return 0
        val hc = client ?: return 0
        var pageToken: String? = null
        var imported = 0
        do {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = filter,
                    pageSize = 1_000,
                    pageToken = pageToken,
                )
            )
            val entries = response.records
                .filterNot { it.metadata.dataOrigin.packageName == BuildConfig.APPLICATION_ID }
                .map { with(HealthConnectMapper) { it.toEntry() } }
            if (entries.isNotEmpty()) {
                imported += entryDao.upsertAll(entries).count { it != -1L }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return imported
    }

    suspend fun importCycle(since: Instant): Int =
        importCycle(TimeRangeFilter.after(since))

    suspend fun importCycle(filter: TimeRangeFilter): Int {
        if (settingsDataStore?.cycleImportEnabled?.first() == false) return 0
        val hc = client ?: return 0
        var pageToken: String? = null
        var imported = 0
        do {
            val response = hc.readRecords(
                ReadRecordsRequest(
                    recordType = MenstruationPeriodRecord::class,
                    timeRangeFilter = filter,
                    pageSize = 1_000,
                    pageToken = pageToken,
                )
            )
            val entries = response.records
                .filterNot { it.metadata.dataOrigin.packageName == BuildConfig.APPLICATION_ID }
                .map { with(HealthConnectMapper) { it.toEntry() } }
            if (entries.isNotEmpty()) {
                imported += entryDao.upsertAll(entries).count { it != -1L }
            }
            pageToken = response.pageToken
        } while (pageToken != null)
        return imported
    }

    suspend fun initialImport(since: Instant): Int =
        initialImport(TimeRangeFilter.after(since))

    suspend fun initialImport(filter: TimeRangeFilter): Int =
        importGlucose(filter) + importNutrition(filter) + importExercise(filter) +
            importSleep(filter) + importCycle(filter)

    suspend fun syncChanges(): SyncResult {
        val hc = client ?: return SyncResult.Unavailable
        val storedRaw = settingsDataStore?.healthConnectChangesToken?.first()
        val storedToken = decodeStoredToken(storedRaw)
        var token = storedToken ?: hc.getChangesToken(
            ChangesTokenRequest(recordTypes = CHANGES_TOKEN_RECORD_TYPES)
        )

        // A stored token without the current scope signature was minted before
        // one of the newly-tracked record types joined the set. Replay those
        // types now; a token scoped to fewer record types would otherwise
        // silently drop every record that predates the switchover.
        if (storedToken == null && storedRaw != null) {
            importExercise(initialImportFilter())
            importSleep(initialImportFilter())
            importCycle(initialImportFilter())
        }

        while (true) {
            val response = hc.getChanges(token)

            if (response.changesTokenExpired) {
                // Recover inline: a fresh token only sees changes from "now",
                // so replay the configured initial import for every tracked
                // type before the caller has to think about it.
                importGlucose(initialImportFilter())
                importNutrition(initialImportFilter())
                importExercise(initialImportFilter())
                importSleep(initialImportFilter())
                importCycle(initialImportFilter())
                val freshToken = hc.getChangesToken(
                    ChangesTokenRequest(recordTypes = CHANGES_TOKEN_RECORD_TYPES)
                )
                settingsDataStore?.setHealthConnectChangesToken(encodeStoredToken(freshToken))
                return SyncResult.Success
            }

            response.changes.forEach { change ->
                when (change) {
                    is UpsertionChange -> handleUpsertion(change.record)
                    is DeletionChange -> {
                        // DeletionChange carries no record type, so target both
                        // tables. The id only exists in one of them in practice.
                        glucoseSampleDao.deleteByHcRecordId(change.recordId)
                        entryDao.deleteByHcRecordId(change.recordId)
                    }
                }
            }

            token = response.nextChangesToken
            if (!response.hasMore) break
        }

        settingsDataStore?.setHealthConnectChangesToken(encodeStoredToken(token))
        return SyncResult.Success
    }

    private suspend fun handleUpsertion(record: Record) {
        when (record) {
            is BloodGlucoseRecord -> {
                if (settingsDataStore?.glucoseImportEnabled?.first() != false &&
                    record.metadata.dataOrigin.packageName != BuildConfig.APPLICATION_ID
                ) {
                    val sample = with(HealthConnectMapper) {
                        record.toSample(System.currentTimeMillis())
                    }
                    glucoseSampleDao.upsertAll(listOf(sample))
                }
            }

            is NutritionRecord -> {
                if (settingsDataStore?.nutritionImportEnabled?.first() != false &&
                    record.metadata.dataOrigin.packageName != BuildConfig.APPLICATION_ID
                ) {
                    val entry = with(HealthConnectMapper) { record.toEntry() }
                    entryDao.upsertAll(listOf(entry))
                }
            }

            is ExerciseSessionRecord -> {
                if (settingsDataStore?.exerciseImportEnabled?.first() != false &&
                    record.metadata.dataOrigin.packageName != BuildConfig.APPLICATION_ID
                ) {
                    val entry = with(HealthConnectMapper) { record.toEntry() }
                    entryDao.upsertAll(listOf(entry))
                }
            }

            is SleepSessionRecord -> {
                if (settingsDataStore?.sleepImportEnabled?.first() != false &&
                    record.metadata.dataOrigin.packageName != BuildConfig.APPLICATION_ID
                ) {
                    val entry = with(HealthConnectMapper) { record.toEntry() }
                    entryDao.upsertAll(listOf(entry))
                }
            }

            is MenstruationPeriodRecord -> {
                if (settingsDataStore?.cycleImportEnabled?.first() != false &&
                    record.metadata.dataOrigin.packageName != BuildConfig.APPLICATION_ID
                ) {
                    val entry = with(HealthConnectMapper) { record.toEntry() }
                    entryDao.upsertAll(listOf(entry))
                }
            }
        }
    }

    /**
     * Writes a single user-authored [EntryEntity] to Health Connect.
     *
     * Constructs records with `Metadata.manualEntry(clientRecordId = "glucosehero:$entryId", clientRecordVersion = updatedAtMillis)`
     * using [HealthConnectMapper.createMetadata]. Health Connect upserts on `clientRecordId`, so edits update
     * existing records in place rather than creating duplicates.
     *
     * Only emits records for which the matching write permission is currently granted.
     * Records originating from Health Connect (`source == EntrySource.HEALTH_CONNECT`)
     * or with `id == 0L` are ignored to prevent echo and invalid IDs.
     */
    suspend fun writeEntry(
        entry: EntryEntity,
        updatedAtMillis: Long = System.currentTimeMillis(),
    ): List<String> {
        val hc = client ?: return emptyList()
        if (entry.id == 0L || entry.source == EntrySource.HEALTH_CONNECT) return emptyList()

        val granted = grantedPermissions()
        val metadata = HealthConnectMapper.createMetadata(entry.id, updatedAtMillis)
        val records = buildList {
            if (HealthPermission.getWritePermission(BloodGlucoseRecord::class) in granted) {
                with(HealthConnectMapper) { entry.toBloodGlucoseRecord(metadata = metadata) }?.let { add(it) }
            }
            if (HealthPermission.getWritePermission(NutritionRecord::class) in granted) {
                with(HealthConnectMapper) { entry.toNutritionRecord(metadata = metadata) }?.let { add(it) }
            }
            if (HealthPermission.getWritePermission(ExerciseSessionRecord::class) in granted) {
                with(HealthConnectMapper) { entry.toExerciseRecord(metadata = metadata) }?.let { add(it) }
            }
        }

        if (records.isEmpty()) return emptyList()
        return try {
            hc.insertRecords(records).recordIdsList
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * Writes multiple user-authored entries to Health Connect, chunked in batches of 1,000.
     * Uses `clientRecordId` so re-syncs update existing records in place rather than duplicating.
     */
    suspend fun writeEntries(
        entries: List<EntryEntity>,
        updatedAtMillis: Long = System.currentTimeMillis(),
    ): Int {
        val hc = client ?: return 0
        val eligibleEntries = entries.filter { it.id != 0L && it.source != EntrySource.HEALTH_CONNECT }
        if (eligibleEntries.isEmpty()) return 0

        val granted = grantedPermissions()
        val canWriteGlucose = HealthPermission.getWritePermission(BloodGlucoseRecord::class) in granted
        val canWriteNutrition = HealthPermission.getWritePermission(NutritionRecord::class) in granted
        val canWriteExercise = HealthPermission.getWritePermission(ExerciseSessionRecord::class) in granted

        if (!canWriteGlucose && !canWriteNutrition && !canWriteExercise) return 0

        val records = eligibleEntries.flatMap { entry ->
            val metadata = HealthConnectMapper.createMetadata(entry.id, updatedAtMillis)
            buildList {
                if (canWriteGlucose) {
                    with(HealthConnectMapper) { entry.toBloodGlucoseRecord(metadata = metadata) }?.let { add(it) }
                }
                if (canWriteNutrition) {
                    with(HealthConnectMapper) { entry.toNutritionRecord(metadata = metadata) }?.let { add(it) }
                }
                if (canWriteExercise) {
                    with(HealthConnectMapper) { entry.toExerciseRecord(metadata = metadata) }?.let { add(it) }
                }
            }
        }

        if (records.isEmpty()) return 0

        var written = 0
        records.chunked(1_000).forEach { chunk ->
            try {
                val response = hc.insertRecords(chunk)
                written += response.recordIdsList.size
            } catch (e: SecurityException) {
                // permission revoked mid-sync
            }
        }
        return written
    }

    /**
     * Re-syncs all local entries to Health Connect.
     *
     * Health Connect matches on `clientRecordId = "glucosehero:$entryId"` and updates
     * in place, safely re-syncing without duplicates.
     */
    suspend fun writeBack(): Int {
        val localEntries = entryDao.getAll().filter { it.source != EntrySource.HEALTH_CONNECT }
        return writeEntries(localEntries)
    }

    /**
     * Deletes Health Connect records matching a local entry ID.
     */
    suspend fun deleteEntry(entryId: Long) {
        val hc = client ?: return
        if (entryId == 0L) return
        val clientRecordId = "glucosehero:$entryId"
        val granted = grantedPermissions()
        try {
            if (HealthPermission.getWritePermission(BloodGlucoseRecord::class) in granted) {
                hc.deleteRecords(
                    recordType = BloodGlucoseRecord::class,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = listOf(clientRecordId),
                )
            }
            if (HealthPermission.getWritePermission(NutritionRecord::class) in granted) {
                hc.deleteRecords(
                    recordType = NutritionRecord::class,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = listOf(clientRecordId),
                )
            }
            if (HealthPermission.getWritePermission(ExerciseSessionRecord::class) in granted) {
                hc.deleteRecords(
                    recordType = ExerciseSessionRecord::class,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = listOf(clientRecordId),
                )
            }
        } catch (e: SecurityException) {
            // permission revoked
        }
    }

    suspend fun clearImportedGlucoseData() {
        glucoseSampleDao.clear()
        settingsDataStore?.setHealthConnectChangesToken(null)
    }

    /**
     * Reads the persisted [InitialImportRange] and returns the matching
     * [TimeRangeFilter]. "All" is expressed as [TimeRangeFilter.before] with
     * no lower bound; the 30/90-day ranges use [TimeRangeFilter.after] to
     * preserve the previous bounded-import behavior.
     */
    suspend fun initialImportFilter(now: Instant = Instant.now()): TimeRangeFilter =
        timeRangeFilterFor(
            settingsDataStore?.healthConnectInitialImportRange?.first() ?: InitialImportRange.DAYS_90,
            now,
        )

    companion object {
        fun timeRangeFilterFor(range: InitialImportRange, now: Instant): TimeRangeFilter = when (range) {
            InitialImportRange.DAYS_30 -> TimeRangeFilter.after(now.minus(30, ChronoUnit.DAYS))
            InitialImportRange.DAYS_90 -> TimeRangeFilter.after(now.minus(90, ChronoUnit.DAYS))
            InitialImportRange.ALL -> TimeRangeFilter.before(now)
        }

        private val CHANGES_TOKEN_RECORD_TYPES = setOf(
            BloodGlucoseRecord::class,
            NutritionRecord::class,
            ExerciseSessionRecord::class,
            SleepSessionRecord::class,
            MenstruationPeriodRecord::class,
        )

        private const val CHANGES_TOKEN_SCOPE_SIGNATURE = "glucose,nutrition,exercise,sleep,cycle"
        private const val CHANGES_TOKEN_DELIMITER = "|"

        private fun encodeStoredToken(token: String): String =
            "$CHANGES_TOKEN_SCOPE_SIGNATURE$CHANGES_TOKEN_DELIMITER$token"

        private fun decodeStoredToken(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val signature = raw.substringBefore(CHANGES_TOKEN_DELIMITER, "")
            val token = raw.substringAfter(CHANGES_TOKEN_DELIMITER, "")
            return token.takeIf { signature == CHANGES_TOKEN_SCOPE_SIGNATURE && it.isNotBlank() }
        }
    }
}

sealed interface SyncResult {
    data object Success : SyncResult
    data object NeedsFullResync : SyncResult
    data object Unavailable : SyncResult
}
