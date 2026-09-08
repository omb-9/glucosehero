package com.omb9.glucosehero.data.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.testing.populatedWithTestValues
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Mass
import com.omb9.glucosehero.BuildConfig
import com.omb9.glucosehero.data.local.datastore.InitialImportRange
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.GlucoseSampleDao
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.data.local.db.HourlyGlucoseAverageRow
import com.omb9.glucosehero.data.local.db.HourlyGlucoseVarianceRow
import com.omb9.glucosehero.data.local.db.LoggedDayRow
import com.omb9.glucosehero.data.local.db.TagAnalyticsRow
import com.omb9.glucosehero.data.local.db.TimeInRangeCounts
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.domain.model.DailyGlucoseSummary
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.EntryType
import com.omb9.glucosehero.domain.model.GlucoseStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

class HealthConnectRepositoryTest {

    private val now = Instant.parse("2026-01-15T12:00:00Z")

    // ---------- Range Filter Tests ----------

    @Test
    fun `30 day range maps to an after filter 30 days back`() {
        assertEquals(
            TimeRangeFilter.after(now.minus(30, ChronoUnit.DAYS)),
            HealthConnectRepository.timeRangeFilterFor(InitialImportRange.DAYS_30, now),
        )
    }

    @Test
    fun `90 day range maps to an after filter 90 days back`() {
        assertEquals(
            TimeRangeFilter.after(now.minus(90, ChronoUnit.DAYS)),
            HealthConnectRepository.timeRangeFilterFor(InitialImportRange.DAYS_90, now),
        )
    }

    @Test
    fun `all range maps to a before filter with no lower bound`() {
        assertEquals(
            TimeRangeFilter.before(now),
            HealthConnectRepository.timeRangeFilterFor(InitialImportRange.ALL, now),
        )
    }

    // ---------- Paginated Import & Dedup Tests ----------

    @Test
    fun `importGlucose paginates with pageSize 1000 following pageToken until null`() = runBlocking {
        val page1Records = listOf(
            createGlucoseRecord(id = "g-1", packageName = "com.dexcom.g7", mgdl = 110.0),
            createGlucoseRecord(id = "g-2", packageName = "com.dexcom.g7", mgdl = 115.0),
        )
        val page2Records = listOf(
            createGlucoseRecord(id = "g-3", packageName = "com.dexcom.g7", mgdl = 120.0),
        )

        val client = TestHealthConnectClient { request ->
            when (request.pageToken) {
                null -> createReadRecordsResponse(page1Records, pageToken = "token-page-2")
                "token-page-2" -> createReadRecordsResponse(page2Records, pageToken = null)
                else -> error("Unexpected page token: ${request.pageToken}")
            }
        }

        val glucoseDao = FakeGlucoseSampleDao()
        val entryDao = FakeEntryDao()
        val repo = HealthConnectRepository(client, glucoseDao, entryDao)

        val importedCount = repo.importGlucose(since = now)

        assertEquals(3, importedCount)
        assertEquals(3, glucoseDao.inserted.size)
        assertEquals(2, client.recordedRequests.size)

        // Verify request pagination configuration
        val req1 = client.recordedRequests[0]
        assertEquals(1_000, req1.pageSize)
        assertNull(req1.pageToken)
        assertEquals(TimeRangeFilter.after(now), req1.timeRangeFilter)

        val req2 = client.recordedRequests[1]
        assertEquals(1_000, req2.pageSize)
        assertEquals("token-page-2", req2.pageToken)
        assertEquals(TimeRangeFilter.after(now), req2.timeRangeFilter)
    }

    @Test
    fun `importGlucose filters out records originating from BuildConfig APPLICATION_ID to prevent echo`() = runBlocking {
        val records = listOf(
            createGlucoseRecord(id = "self-1", packageName = BuildConfig.APPLICATION_ID, mgdl = 140.0),
            createGlucoseRecord(id = "dex-1", packageName = "com.dexcom.g7", mgdl = 115.0),
            createGlucoseRecord(id = "self-2", packageName = BuildConfig.APPLICATION_ID, mgdl = 150.0),
            createGlucoseRecord(id = "dex-2", packageName = "com.dexcom.g7", mgdl = 118.0),
        )

        val client = TestHealthConnectClient {
            createReadRecordsResponse(records, pageToken = null)
        }
        val glucoseDao = FakeGlucoseSampleDao()
        val entryDao = FakeEntryDao()
        val repo = HealthConnectRepository(client, glucoseDao, entryDao)

        val importedCount = repo.importGlucose(since = now)

        assertEquals(2, importedCount)
        assertEquals(listOf("dex-1", "dex-2"), glucoseDao.inserted.map { it.hcRecordId })
        assertTrue(glucoseDao.inserted.none { it.sourcePackage == BuildConfig.APPLICATION_ID })
    }

    @Test
    fun `importGlucose relies on OnConflictStrategy IGNORE on hc_record_id for idempotent re-import`() = runBlocking {
        val records = listOf(
            createGlucoseRecord(id = "g-1", packageName = "com.dexcom.g7", mgdl = 110.0),
            createGlucoseRecord(id = "g-2", packageName = "com.dexcom.g7", mgdl = 120.0),
        )

        val client = TestHealthConnectClient {
            createReadRecordsResponse(records, pageToken = null)
        }
        val glucoseDao = FakeGlucoseSampleDao()
        val entryDao = FakeEntryDao()
        val repo = HealthConnectRepository(client, glucoseDao, entryDao)

        // First import inserts 2 records
        val firstRunCount = repo.importGlucose(since = now)
        assertEquals(2, firstRunCount)
        assertEquals(2, glucoseDao.inserted.size)

        // Re-importing identical records: OnConflictStrategy.IGNORE returns -1L for all conflicts
        val secondRunCount = repo.importGlucose(since = now)
        assertEquals(0, secondRunCount)
        assertEquals(2, glucoseDao.inserted.size) // Table remains deduplicated
    }

    @Test
    fun `importNutrition paginates, filters echo, and handles dedup idempotently`() = runBlocking {
        val page1 = listOf(
            createNutritionRecord(id = "meal-echo", packageName = BuildConfig.APPLICATION_ID, name = "Echo Meal"),
            createNutritionRecord(id = "meal-1", packageName = "com.myfitnesspal.android", name = "Salad"),
        )
        val page2 = listOf(
            createNutritionRecord(id = "meal-2", packageName = "com.myfitnesspal.android", name = "Chicken"),
        )

        val client = TestHealthConnectClient { request ->
            when (request.pageToken) {
                null -> createReadRecordsResponse(page1, pageToken = "nutr-page-2")
                "nutr-page-2" -> createReadRecordsResponse(page2, pageToken = null)
                else -> error("Unexpected page token: ${request.pageToken}")
            }
        }

        val entryDao = FakeEntryDao()
        val repo = HealthConnectRepository(client, FakeGlucoseSampleDao(), entryDao)

        val imported = repo.importNutrition(since = now)

        assertEquals(2, imported)
        assertEquals(2, entryDao.inserted.size)
        assertEquals(listOf("meal-1", "meal-2"), entryDao.inserted.map { it.hcRecordId })
        assertEquals(EntrySource.HEALTH_CONNECT, entryDao.inserted[0].source)

        // Re-import is idempotent
        val reimport = repo.importNutrition(since = now)
        assertEquals(0, reimport)
        assertEquals(2, entryDao.inserted.size)
    }

    @Test
    fun `importExercise paginates, filters echo, and handles dedup idempotently`() = runBlocking {
        val page1 = listOf(
            createExerciseRecord(id = "run-echo", packageName = BuildConfig.APPLICATION_ID, title = "Echo Run"),
            createExerciseRecord(id = "run-1", packageName = "com.strava", title = "Morning Jog"),
        )
        val page2 = listOf(
            createExerciseRecord(id = "run-2", packageName = "com.garmin.android", title = "Bike Ride"),
        )

        val client = TestHealthConnectClient { request ->
            when (request.pageToken) {
                null -> createReadRecordsResponse(page1, pageToken = "ex-page-2")
                "ex-page-2" -> createReadRecordsResponse(page2, pageToken = null)
                else -> error("Unexpected page token: ${request.pageToken}")
            }
        }

        val entryDao = FakeEntryDao()
        val repo = HealthConnectRepository(client, FakeGlucoseSampleDao(), entryDao)

        val imported = repo.importExercise(since = now)

        assertEquals(2, imported)
        assertEquals(2, entryDao.inserted.size)
        assertEquals(listOf("run-1", "run-2"), entryDao.inserted.map { it.hcRecordId })
        assertEquals(EntrySource.HEALTH_CONNECT, entryDao.inserted[0].source)

        // Re-import is idempotent
        val reimport = repo.importExercise(since = now)
        assertEquals(0, reimport)
        assertEquals(2, entryDao.inserted.size)
    }

    @Test
    fun `initialImport aggregates imported records across glucose, nutrition, and exercise`() = runBlocking {
        val client = TestHealthConnectClient { request ->
            when (request.recordType) {
                BloodGlucoseRecord::class -> createReadRecordsResponse(
                    listOf(createGlucoseRecord("g-1", "com.dexcom.g7", 100.0)),
                    pageToken = null,
                )
                NutritionRecord::class -> createReadRecordsResponse(
                    listOf(createNutritionRecord("n-1", "com.fatsecret.android", "Oatmeal")),
                    pageToken = null,
                )
                ExerciseSessionRecord::class -> createReadRecordsResponse(
                    listOf(createExerciseRecord("e-1", "com.strava", "Cycling")),
                    pageToken = null,
                )
                else -> createReadRecordsResponse(emptyList(), pageToken = null)
            }
        }

        val glucoseDao = FakeGlucoseSampleDao()
        val entryDao = FakeEntryDao()
        val repo = HealthConnectRepository(client, glucoseDao, entryDao)

        val total = repo.initialImport(since = now)

        assertEquals(3, total)
        assertEquals(1, glucoseDao.inserted.size)
        assertEquals(2, entryDao.inserted.size)
    }

    @Test
    fun `clearImportedGlucoseData clears glucoseSampleDao without touching entryDao`() = runBlocking {
        val glucoseDao = FakeGlucoseSampleDao().apply {
            upsertAll(
                listOf(
                    GlucoseSampleEntity(
                        id = 1L,
                        timestamp = now.toEpochMilli(),
                        glucoseMgdl = 120.0,
                        sourcePackage = "com.dexcom.g7",
                        recordingMethod = 1,
                        hcRecordId = "g-1",
                        importedAt = now.toEpochMilli(),
                    )
                )
            )
        }
        val entryDao = FakeEntryDao().apply {
            upsertAll(
                listOf(
                    EntryEntity(
                        id = 1L,
                        timestamp = now.toEpochMilli(),
                        carbsGrams = 45,
                        mealDescription = "Breakfast",
                    )
                )
            )
        }
        val repo = HealthConnectRepository(
            client = null,
            glucoseSampleDao = glucoseDao,
            entryDao = entryDao,
        )

        assertEquals(1, glucoseDao.count())
        assertEquals(1, entryDao.countAll())

        repo.clearImportedGlucoseData()

        assertEquals(0, glucoseDao.count())
        assertEquals(1, entryDao.countAll())
    }

    // ---------- Write-Back Tests (Phase 2) ----------

    @Test
    fun `writeEntry writes blood glucose, nutrition, and exercise records with clientRecordId and clientRecordVersion`() = runBlocking {
        val allWritePermissions = setOf(
            HealthPermission.getWritePermission(BloodGlucoseRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
        val client = TestHealthConnectClient(grantedPermissionsSet = allWritePermissions)
        val repo = HealthConnectRepository(client, FakeGlucoseSampleDao(), FakeEntryDao())

        val entry = EntryEntity(
            id = 42L,
            timestamp = now.toEpochMilli(),
            glucoseMgdl = 135.0,
            carbsGrams = 45,
            mealDescription = "Lunch bowl",
            exerciseMinutes = 25,
            note = "Post-lunch walk",
            source = EntrySource.MANUAL,
        )

        val updatedAt = 1_700_555_000L
        val result = repo.writeEntry(entry, updatedAtMillis = updatedAt)

        assertEquals(3, result.size)
        assertEquals(3, client.insertedRecords.size)

        val bgRecord = client.insertedRecords.filterIsInstance<BloodGlucoseRecord>().first()
        assertEquals(135.0, bgRecord.level.inMilligramsPerDeciliter, 1e-6)
        assertEquals("glucosehero:42", bgRecord.metadata.clientRecordId)
        assertEquals(updatedAt, bgRecord.metadata.clientRecordVersion)

        val nutritionRecord = client.insertedRecords.filterIsInstance<NutritionRecord>().first()
        assertEquals(45.0, nutritionRecord.totalCarbohydrate!!.inGrams, 1e-6)
        assertEquals("Lunch bowl", nutritionRecord.name)
        assertEquals("glucosehero:42", nutritionRecord.metadata.clientRecordId)
        assertEquals(updatedAt, nutritionRecord.metadata.clientRecordVersion)

        val exerciseRecord = client.insertedRecords.filterIsInstance<ExerciseSessionRecord>().first()
        assertEquals(25 * 60L, java.time.Duration.between(exerciseRecord.startTime, exerciseRecord.endTime).seconds)
        assertEquals("Post-lunch walk", exerciseRecord.title)
        assertEquals("glucosehero:42", exerciseRecord.metadata.clientRecordId)
        assertEquals(updatedAt, exerciseRecord.metadata.clientRecordVersion)
    }

    @Test
    fun `writeEntry writes only record types for which write permission is granted`() = runBlocking {
        val onlyGlucoseWrite = setOf(HealthPermission.getWritePermission(BloodGlucoseRecord::class))
        val client = TestHealthConnectClient(grantedPermissionsSet = onlyGlucoseWrite)
        val repo = HealthConnectRepository(client, FakeGlucoseSampleDao(), FakeEntryDao())

        val entry = EntryEntity(
            id = 99L,
            timestamp = now.toEpochMilli(),
            glucoseMgdl = 120.0,
            carbsGrams = 30,
            exerciseMinutes = 20,
            source = EntrySource.MANUAL,
        )

        val result = repo.writeEntry(entry)

        assertEquals(1, result.size)
        assertEquals(1, client.insertedRecords.size)
        assertTrue(client.insertedRecords.first() is BloodGlucoseRecord)
        assertEquals("glucosehero:99", client.insertedRecords.first().metadata.clientRecordId)
    }

    @Test
    fun `writeEntry skips unpersisted entries and entries with HEALTH_CONNECT source`() = runBlocking {
        val allWritePermissions = setOf(
            HealthPermission.getWritePermission(BloodGlucoseRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
        val client = TestHealthConnectClient(grantedPermissionsSet = allWritePermissions)
        val repo = HealthConnectRepository(client, FakeGlucoseSampleDao(), FakeEntryDao())

        val unpersistedEntry = EntryEntity(
            id = 0L,
            timestamp = now.toEpochMilli(),
            glucoseMgdl = 110.0,
            source = EntrySource.MANUAL,
        )
        val hcImportedEntry = EntryEntity(
            id = 15L,
            timestamp = now.toEpochMilli(),
            glucoseMgdl = 115.0,
            source = EntrySource.HEALTH_CONNECT,
        )

        assertEquals(0, repo.writeEntry(unpersistedEntry).size)
        assertEquals(0, repo.writeEntry(hcImportedEntry).size)
        assertEquals(0, client.insertedRecords.size)
    }

    @Test
    fun `writeBack re-syncs local entries to Health Connect with clientRecordId for in-place upserts`() = runBlocking {
        val allWritePermissions = setOf(
            HealthPermission.getWritePermission(BloodGlucoseRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
        val client = TestHealthConnectClient(grantedPermissionsSet = allWritePermissions)
        val entryDao = FakeEntryDao().apply {
            upsertAll(
                listOf(
                    EntryEntity(id = 1L, timestamp = now.toEpochMilli(), glucoseMgdl = 100.0, source = EntrySource.MANUAL),
                    EntryEntity(id = 2L, timestamp = now.toEpochMilli(), carbsGrams = 25, source = EntrySource.MANUAL),
                    EntryEntity(id = 3L, timestamp = now.toEpochMilli(), glucoseMgdl = 140.0, source = EntrySource.HEALTH_CONNECT),
                )
            )
        }
        val repo = HealthConnectRepository(client, FakeGlucoseSampleDao(), entryDao)

        val writtenCount = repo.writeBack()

        // Only the 2 manual entries should be written back, not the HEALTH_CONNECT one
        assertEquals(2, writtenCount)
        assertEquals(2, client.insertedRecords.size)
        val clientRecordIds = client.insertedRecords.map { it.metadata.clientRecordId }.toSet()
        assertEquals(setOf("glucosehero:1", "glucosehero:2"), clientRecordIds)
    }

    @Test
    fun `deleteEntry deletes records by clientRecordId across all write-tracked record types`() = runBlocking {
        val allWritePermissions = setOf(
            HealthPermission.getWritePermission(BloodGlucoseRecord::class),
            HealthPermission.getWritePermission(NutritionRecord::class),
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        )
        val client = TestHealthConnectClient(grantedPermissionsSet = allWritePermissions)
        val repo = HealthConnectRepository(client, FakeGlucoseSampleDao(), FakeEntryDao())

        repo.deleteEntry(42L)

        assertEquals(listOf("glucosehero:42", "glucosehero:42", "glucosehero:42"), client.deletedClientRecordIds)
        val deletedTypes = client.deletedRecordTypes.toSet()
        assertEquals(
            setOf(BloodGlucoseRecord::class, NutritionRecord::class, ExerciseSessionRecord::class),
            deletedTypes,
        )
    }

    // ---------- Test Helpers & Fakes ----------

    private fun createGlucoseRecord(id: String, packageName: String, mgdl: Double): BloodGlucoseRecord =
        BloodGlucoseRecord(
            time = now,
            zoneOffset = null,
            metadata = Metadata.manualEntry().populatedWithTestValues(
                id = id,
                dataOrigin = DataOrigin(packageName),
            ),
            level = BloodGlucose.milligramsPerDeciliter(mgdl),
        )

    private fun createNutritionRecord(id: String, packageName: String, name: String): NutritionRecord =
        NutritionRecord(
            startTime = now,
            startZoneOffset = null,
            endTime = now.plusSeconds(300),
            endZoneOffset = null,
            metadata = Metadata.manualEntry().populatedWithTestValues(
                id = id,
                dataOrigin = DataOrigin(packageName),
            ),
            name = name,
            totalCarbohydrate = Mass.grams(30.0),
        )

    private fun createExerciseRecord(id: String, packageName: String, title: String): ExerciseSessionRecord =
        ExerciseSessionRecord(
            startTime = now,
            startZoneOffset = null,
            endTime = now.plusSeconds(1800),
            endZoneOffset = null,
            metadata = Metadata.manualEntry().populatedWithTestValues(
                id = id,
                dataOrigin = DataOrigin(packageName),
            ),
            title = title,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        )

    @Suppress("UNCHECKED_CAST")
    private fun <T : Record> createReadRecordsResponse(records: List<T>, pageToken: String?): ReadRecordsResponse<T> {
        val constructor = ReadRecordsResponse::class.java.declaredConstructors.first()
        constructor.isAccessible = true
        return constructor.newInstance(records, pageToken) as ReadRecordsResponse<T>
    }
}

private class TestHealthConnectClient(
    val grantedPermissionsSet: Set<String> = emptySet(),
    private val responseProvider: (ReadRecordsRequest<*>) -> ReadRecordsResponse<*> = {
        throw UnsupportedOperationException()
    },
) : HealthConnectClient {
    constructor(responseProvider: (ReadRecordsRequest<*>) -> ReadRecordsResponse<*>) : this(
        grantedPermissionsSet = emptySet(),
        responseProvider = responseProvider,
    )

    val recordedRequests = mutableListOf<ReadRecordsRequest<*>>()
    val insertedRecords = mutableListOf<Record>()
    val deletedRecordTypes = mutableListOf<KClass<out Record>>()
    val deletedClientRecordIds = mutableListOf<String>()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> {
        recordedRequests.add(request)
        return responseProvider(request) as ReadRecordsResponse<T>
    }

    override val permissionController: PermissionController
        get() = object : PermissionController {
            override suspend fun getGrantedPermissions(): Set<String> = grantedPermissionsSet
            override suspend fun revokeAllPermissions() {}
        }

    override suspend fun <T : Record> readRecord(recordType: KClass<T>, recordId: String): Nothing =
        throw UnsupportedOperationException()

    override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse {
        insertedRecords.addAll(records)
        val constructor = InsertRecordsResponse::class.java.declaredConstructors.first()
        constructor.isAccessible = true
        val ids = records.indices.map { "gen-id-$it" }
        return constructor.newInstance(ids) as InsertRecordsResponse
    }

    override suspend fun updateRecords(records: List<Record>): Nothing =
        throw UnsupportedOperationException()

    override suspend fun deleteRecords(
        recordType: KClass<out Record>,
        recordIdsList: List<String>,
        clientRecordIdsList: List<String>,
    ) {
        deletedRecordTypes.add(recordType)
        deletedClientRecordIds.addAll(clientRecordIdsList)
    }
    override suspend fun deleteRecords(recordType: KClass<out Record>, timeRangeFilter: TimeRangeFilter): Nothing =
        throw UnsupportedOperationException()
    override suspend fun aggregate(request: androidx.health.connect.client.request.AggregateRequest): Nothing =
        throw UnsupportedOperationException()
    override suspend fun aggregateGroupByDuration(request: androidx.health.connect.client.request.AggregateGroupByDurationRequest): Nothing =
        throw UnsupportedOperationException()
    override suspend fun aggregateGroupByPeriod(request: androidx.health.connect.client.request.AggregateGroupByPeriodRequest): Nothing =
        throw UnsupportedOperationException()
    override suspend fun getChangesToken(request: ChangesTokenRequest): Nothing =
        throw UnsupportedOperationException()
    override suspend fun getChanges(changesToken: String): Nothing =
        throw UnsupportedOperationException()
    override suspend fun getChanges(changesToken: String, pageSize: Int): androidx.health.connect.client.response.ChangesResponse =
        throw UnsupportedOperationException()
}

private class FakeGlucoseSampleDao : GlucoseSampleDao {
    val inserted = mutableListOf<GlucoseSampleEntity>()
    val existingHcRecordIds = mutableSetOf<String>()

    override suspend fun upsertAll(samples: List<GlucoseSampleEntity>): List<Long> {
        return samples.map { sample ->
            if (existingHcRecordIds.contains(sample.hcRecordId)) {
                -1L
            } else {
                existingHcRecordIds.add(sample.hcRecordId)
                inserted.add(sample)
                inserted.size.toLong()
            }
        }
    }

    override suspend fun deleteByHcRecordId(hcRecordId: String) {
        inserted.removeAll { it.hcRecordId == hcRecordId }
        existingHcRecordIds.remove(hcRecordId)
    }

    override suspend fun count(): Int = inserted.size
    override suspend fun clear() {
        inserted.clear()
        existingHcRecordIds.clear()
    }

    override suspend fun pageForExport(limit: Int, offset: Int): List<GlucoseSampleEntity> = emptyList()
    override suspend fun getAll(): List<GlucoseSampleEntity> = inserted
    override suspend fun insertAll(samples: List<GlucoseSampleEntity>): List<Long> = upsertAll(samples)
}

private class FakeEntryDao : EntryDao {
    val inserted = mutableListOf<EntryEntity>()
    val existingHcRecordIds = mutableSetOf<String>()

    override suspend fun upsertAll(entities: List<EntryEntity>): List<Long> {
        return entities.map { entity ->
            val hcId = entity.hcRecordId
            if (hcId != null && existingHcRecordIds.contains(hcId)) {
                -1L
            } else {
                if (hcId != null) existingHcRecordIds.add(hcId)
                inserted.add(entity)
                inserted.size.toLong()
            }
        }
    }

    override suspend fun deleteByHcRecordId(hcRecordId: String) {
        inserted.removeAll { it.hcRecordId == hcRecordId }
        existingHcRecordIds.remove(hcRecordId)
    }

    override fun observeEventsSince(since: Long): Flow<List<EntryEntity>> = throw UnsupportedOperationException()
    override fun observeGlucoseEventsSince(since: Long): Flow<List<EntryEntity>> = throw UnsupportedOperationException()
    override fun observeGlucoseReadingsPoints(since: Long): Flow<List<GlucosePointRow>> = throw UnsupportedOperationException()
    override fun observeById(id: Long): Flow<EntryEntity?> = throw UnsupportedOperationException()
    override suspend fun searchEntries(query: String): List<EntryEntity> = emptyList()
    override suspend fun insert(entity: EntryEntity): Long = 1L
    override suspend fun update(entity: EntryEntity) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun delete(entity: EntryEntity) {}
    override suspend fun averageGlucoseReadingsSince(since: Long): Double? = null
    override fun observeGlucoseStatsSince(since: Long): Flow<GlucoseStats> = throw UnsupportedOperationException()
    override suspend fun cgmReadingCountSince(since: Long): Int = 0
    override suspend fun manualReadingCountSince(since: Long): Int = 0
    override fun observeLoggedDays(since: Long): Flow<List<LoggedDayRow>> = throw UnsupportedOperationException()
    override suspend fun loggedDaysSince(since: Long): List<LoggedDayRow> = emptyList()
    override suspend fun timeInRangeSince(since: Long, low: Double, high: Double): Double? = null
    override suspend fun timeInRangeCountsSince(since: Long, low: Double, high: Double): TimeInRangeCounts =
        TimeInRangeCounts(0, 0, 0, 0, 0)
    override suspend fun averageGlucoseReadingsBetween(startMillis: Long, endMillis: Long): Double? = null
    override suspend fun dailySummaries(since: Long, limit: Int): List<DailyGlucoseSummary> = emptyList()
    override suspend fun recentEntries(limit: Int): List<EntryEntity> = emptyList()
    override suspend fun entriesSince(since: Long): List<EntryEntity> = emptyList()
    override suspend fun latestGlucoseReading(): GlucosePointRow? = null
    override suspend fun taggedEntries(): List<EntryEntity> = emptyList()
    override suspend fun tagAnalyticsRows(since: Long, windowStart: Long, windowEnd: Long, target: Long): List<TagAnalyticsRow> = emptyList()
    override suspend fun glucoseReadingPointsSince(since: Long): List<GlucosePointRow> = emptyList()
    override suspend fun windowedEntriesSince(since: Long): List<EntryEntity> = emptyList()
    override suspend fun glucoseReadingPointsBetween(startMillis: Long, endMillis: Long): List<GlucosePointRow> = emptyList()
    override suspend fun hourlyAveragesSince(since: Long): List<HourlyGlucoseAverageRow> = emptyList()
    override suspend fun hourlyVarianceSince(since: Long): List<HourlyGlucoseVarianceRow> = emptyList()
    override suspend fun pageForExport(limit: Int, offset: Int): List<EntryEntity> = emptyList()
    override suspend fun pageByTimestampForExport(limit: Int, offset: Int): List<EntryEntity> = emptyList()
    override suspend fun pageSinceByTimestampForExport(since: Long, limit: Int, offset: Int): List<EntryEntity> = emptyList()
    override suspend fun countAll(): Int = inserted.size
    override suspend fun getAll(): List<EntryEntity> = inserted
    override suspend fun allUuids(): List<String> = emptyList()
    override suspend fun clear() {
        inserted.clear()
        existingHcRecordIds.clear()
    }
    override suspend fun insertAll(entities: List<EntryEntity>): List<Long> = upsertAll(entities)

    override suspend fun insertIgnoreAll(entities: List<EntryEntity>): List<Long> {
        val existingUuids = inserted.map { it.uuid }.toHashSet()
        return entities.map { entity ->
            val uuidConflict = entity.uuid.isNotEmpty() && entity.uuid in existingUuids
            val hcId = entity.hcRecordId
            val hcConflict = hcId != null && existingHcRecordIds.contains(hcId)
            if (uuidConflict || hcConflict) {
                -1L
            } else {
                if (hcId != null) existingHcRecordIds.add(hcId)
                if (entity.uuid.isNotEmpty()) existingUuids.add(entity.uuid)
                inserted.add(entity)
                inserted.size.toLong()
            }
        }
    }
}
