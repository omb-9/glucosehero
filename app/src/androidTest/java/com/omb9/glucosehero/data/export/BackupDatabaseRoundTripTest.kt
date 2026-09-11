package com.omb9.glucosehero.data.export

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.omb9.glucosehero.data.backup.EncryptedBackupCipher
import com.omb9.glucosehero.data.backup.KeystoreDataKeyWrapper
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.entity.ChatMessageEntity
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.data.local.entity.InsightCardEntity
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity
import com.omb9.glucosehero.data.local.entity.SupplyEntity
import com.omb9.glucosehero.data.security.KeystoreManager
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.domain.model.SupplyType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupDatabaseRoundTripTest {

    private lateinit var context: Context
    private lateinit var db: GlucoseHeroDatabase
    private lateinit var backupManager: BackupManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "backups").deleteRecursively()
        db = Room.inMemoryDatabaseBuilder(context, GlucoseHeroDatabase::class.java).build()
        backupManager = BackupManager(
            context,
            db,
            SettingsDataStore(context),
            EncryptedBackupCipher(KeystoreDataKeyWrapper(KeystoreManager())),
        )
    }

    @After
    fun tearDown() {
        db.close()
        File(context.filesDir, "backups").deleteRecursively()
    }

    @Test
    fun replaceRoundTrip_preservesEveryTableRowForRow() = runBlocking {
        val seed = seed()

        val bytes = ByteArrayOutputStream().also { backupManager.exportTo(it) }.toByteArray()

        // Simulate a wiped device, then restore.
        db.entryDao().clear()
        db.foodDao().clear()
        db.supplyDao().clear()
        db.glucoseSampleDao().clear()
        db.chatMessageDao().clear()
        db.pendingAiQueryDao().clear()
        db.insightDao().clear()

        backupManager.importFrom(ByteArrayInputStream(bytes), ImportMode.REPLACE)

        assertEquals(listOf(seed.entry), db.entryDao().getAll())
        assertEquals(listOf(seed.food), db.foodDao().getAll())
        assertEquals(listOf(seed.supply), db.supplyDao().getAll())
        assertEquals(listOf(seed.sample), db.glucoseSampleDao().getAll())
        assertEquals(listOf(seed.chat), db.chatMessageDao().getAll())
        assertEquals(listOf(seed.pending), db.pendingAiQueryDao().getAll())
        assertEquals(listOf(seed.insight), db.insightDao().getAll())
    }

    @Test
    fun encryptedRoundTrip_decryptsOnThisDeviceAndRestoresRows() = runBlocking {
        val seed = seed()
        val bytes = ByteArrayOutputStream().also { backupManager.exportEncryptedTo(it) }.toByteArray()
        assertTrue(bytes.size >= 4)
        assertEquals('G'.code.toByte(), bytes[0])
        assertEquals('H'.code.toByte(), bytes[1])
        assertEquals('Z'.code.toByte(), bytes[2])
        assertEquals('K'.code.toByte(), bytes[3])

        db.entryDao().clear()
        db.foodDao().clear()
        db.supplyDao().clear()
        db.glucoseSampleDao().clear()
        db.chatMessageDao().clear()
        db.pendingAiQueryDao().clear()
        db.insightDao().clear()

        backupManager.importFrom(ByteArrayInputStream(bytes), ImportMode.REPLACE)

        assertEquals(listOf(seed.entry), db.entryDao().getAll())
        assertEquals(listOf(seed.food), db.foodDao().getAll())
        assertEquals(listOf(seed.sample), db.glucoseSampleDao().getAll())
    }

    @Test
    fun mergeImport_isIdempotent() = runBlocking {
        seed()

        val bytes = ByteArrayOutputStream().also { backupManager.exportTo(it) }.toByteArray()

        backupManager.importFrom(ByteArrayInputStream(bytes), ImportMode.MERGE)
        val afterFirst = counts()
        backupManager.importFrom(ByteArrayInputStream(bytes), ImportMode.MERGE)
        val afterSecond = counts()

        assertEquals(afterFirst, afterSecond)
        assertEquals(1, afterSecond.entries)
        assertEquals(1, afterSecond.foods)
        assertEquals(1, afterSecond.supplies)
        assertEquals(1, afterSecond.glucoseSamples)
        assertEquals(1, afterSecond.chat)
        assertEquals(1, afterSecond.pendingAiQueries)
        assertEquals(1, afterSecond.insights)
    }

    @Test
    fun preview_reportsRealDateRangeWithoutDecodingArrays() = runBlocking {
        seed()

        val bytes = ByteArrayOutputStream().also { backupManager.exportTo(it) }.toByteArray()

        val preview = backupManager.previewFrom(ByteArrayInputStream(bytes))

        assertEquals(1_000L, preview.earliestEntry)
        assertEquals(1_000L, preview.latestEntry)
        assertEquals(1, preview.counts.entries)
        assertEquals(1, preview.currentEntryCount)
        assertEquals(1, preview.currentCounts.entries)
        assertEquals(1, preview.currentCounts.foods)
    }

    @Test
    fun preview_readsHeaderOnly_whenArraysAreTruncated() = runBlocking {
        val json = """
            {
              "format": "glucosehero.backup",
              "formatVersion": 2,
              "appVersion": "1.0.0",
              "databaseVersion": 13,
              "exportedAt": 1234567,
              "counts": {"entries": 99, "glucoseSamples": 0, "foods": 0, "supplies": 0, "insights": 0, "chat": 0, "pendingAiQueries": 0},
              "profile": {},
              "settings": {},
              "earliestEntry": 1000,
              "latestEntry": 2000,
              "foods": [{"this array is intentionally truncated
        """.trimIndent()

        val preview = backupManager.previewFrom(ByteArrayInputStream(json.toByteArray()))

        assertEquals(1_000L, preview.earliestEntry)
        assertEquals(2_000L, preview.latestEntry)
        assertEquals(99, preview.counts.entries)
        assertEquals(0, preview.currentEntryCount)
    }

    @Test
    fun mergeImport_skipsDuplicateUuidAndInsertsNew() = runBlocking {
        seed()

        val bytes = ByteArrayOutputStream().also {
            streamBackupEnvelope(
                output = it,
                profile = BackupProfile(),
                settings = BackupSettings(),
                counts = BackupCounts(entries = 2, foods = 1),
                appVersion = "1.0.0",
                exportedAt = 1_234_567L,
                earliestEntry = 1_000L,
                latestEntry = 3_000L,
                foods = { offset, limit ->
                    page(
                        listOf(
                            BackupFood(
                                id = 1L,
                                uuid = "food-uuid-1",
                                name = "Oats",
                                carbsGrams = 27.0,
                                source = FoodSource.MANUAL,
                                createdAt = 900L,
                            ),
                        ),
                        offset,
                        limit,
                    )
                },
                entries = { offset, limit ->
                    page(
                        listOf(
                            BackupEntry(
                                id = 10L,
                                timestamp = 1_000L,
                                glucoseMgdl = 120.0,
                                uuid = "entry-uuid-1",
                            ),
                            BackupEntry(
                                id = 11L,
                                timestamp = 3_000L,
                                glucoseMgdl = 95.0,
                                uuid = "entry-uuid-new",
                            ),
                        ),
                        offset,
                        limit,
                    )
                },
                supplies = { _, _ -> emptyList() },
                glucoseSamples = { _, _ -> emptyList() },
                chat = { _, _ -> emptyList() },
                pendingAiQueries = { _, _ -> emptyList() },
                insights = { _, _ -> emptyList() },
            )
        }.toByteArray()

        backupManager.importFrom(ByteArrayInputStream(bytes), ImportMode.MERGE)
        assertEquals(2, db.entryDao().countAll())
        assertEquals(1, db.foodDao().countAll())

        backupManager.importFrom(ByteArrayInputStream(bytes), ImportMode.MERGE)
        assertEquals(2, db.entryDao().countAll())
        val uuids = db.entryDao().getAll().map { it.uuid }.toSet()
        assertEquals(setOf("entry-uuid-1", "entry-uuid-new"), uuids)
    }

    @Test
    fun preImportSnapshot_retainsTwoNewestFilesInFilesDir() = runBlocking {
        seed()
        val bytes = ByteArrayOutputStream().also { backupManager.exportTo(it) }.toByteArray()

        repeat(3) {
            backupManager.importFrom(ByteArrayInputStream(bytes), ImportMode.MERGE)
        }

        val dir = File(context.filesDir, "backups")
        val snapshots = dir.listFiles { file ->
            file.isFile && file.name.startsWith("pre-import-") && file.name.endsWith(".json")
        } ?: emptyArray()

        assertEquals(2, snapshots.size)
        snapshots.forEach { assertTrue(it.absolutePath.startsWith(context.filesDir.absolutePath)) }
        assertNotNull(backupManager.latestPreImportSnapshot())
    }

    @Test
    fun countMismatch_rollsBackTransaction() = runBlocking {
        seed()
        val envelope = BackupEnvelope(
            counts = BackupCounts(entries = 5),
            entries = listOf(
                BackupEntry(id = 1L, timestamp = 9_000L, glucoseMgdl = 80.0, uuid = "tampered"),
            ),
        )
        val bytes = ByteArrayOutputStream().also { encodeEnvelope(envelope, it) }.toByteArray()

        val error = try {
            backupManager.importFrom(ByteArrayInputStream(bytes), ImportMode.REPLACE)
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(error is BackupFormatException)
        assertEquals(1, db.entryDao().countAll())
        assertEquals("entry-uuid-1", db.entryDao().getAll().single().uuid)
        assertEquals(1, db.foodDao().countAll())
    }

    @Test
    fun preview_omitsDateRangeForOlderEnvelopeWithoutNewFields() = runBlocking {
        val olderJson = """
            {
              "format": "glucosehero.backup",
              "formatVersion": 1,
              "appVersion": "1.0.0",
              "databaseVersion": 10,
              "exportedAt": 1234567,
              "counts": {"entries": 0},
              "profile": {},
              "settings": {},
              "entries": []
            }
        """.trimIndent()

        val preview = backupManager.previewFrom(ByteArrayInputStream(olderJson.toByteArray()))

        assertEquals(null, preview.earliestEntry)
        assertEquals(null, preview.latestEntry)
        assertEquals(0, preview.counts.entries)
    }

    @Test
    fun versionRefusal_writesNothing() = runBlocking {
        seed()

        val futureEnvelope = BackupEnvelope(
            formatVersion = 99,
            counts = BackupCounts(entries = 0),
            entries = emptyList(),
        )
        val bytes = ByteArrayOutputStream().also { encodeEnvelope(futureEnvelope, it) }.toByteArray()

        val error = try {
            backupManager.importFrom(ByteArrayInputStream(bytes), ImportMode.REPLACE)
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(error is BackupFormatException)
        // Database is untouched.
        assertEquals(1, db.entryDao().countAll())
        assertEquals(1, db.foodDao().countAll())
    }

    @Test
    fun truncation_leavesDatabaseUntouched() = runBlocking {
        seed()

        val full = ByteArrayOutputStream().also { backupManager.exportTo(it) }.toByteArray()
        val truncated = full.copyOf(full.size / 2)

        val error = try {
            backupManager.importFrom(ByteArrayInputStream(truncated), ImportMode.REPLACE)
            null
        } catch (e: Exception) {
            e
        }

        assertTrue(error != null)
        assertEquals(1, db.entryDao().countAll())
        assertEquals(1, db.foodDao().countAll())
        assertEquals(1, db.glucoseSampleDao().count())
    }

    @Test
    fun largeImport_streamsAndMatchesRowCount() {
        runBlocking {
            val sampleCount = 50_000
            val bytes = syntheticBackupBytes(sampleCount)

            val start = System.nanoTime()
            backupManager.importFrom(ByteArrayInputStream(bytes), ImportMode.REPLACE)
            val elapsedMillis = (System.nanoTime() - start) / 1_000_000

            assertEquals(sampleCount, db.glucoseSampleDao().count())
            assertEquals(0, db.entryDao().countAll())
            Log.i("BackupDatabaseRoundTripTest", "Large import completed: $sampleCount samples in $elapsedMillis ms")
        }
    }

    private suspend fun syntheticBackupBytes(sampleCount: Int): ByteArray {
        val output = ByteArrayOutputStream()
        streamBackupEnvelope(
            output = output,
            profile = BackupProfile(),
            settings = BackupSettings(),
            counts = BackupCounts(glucoseSamples = sampleCount),
            appVersion = "9.9.9",
            exportedAt = 1_700_000_000_000L,
            pageSize = 500,
            foods = { _, _ -> emptyList() },
            entries = { _, _ -> emptyList() },
            supplies = { _, _ -> emptyList() },
            glucoseSamples = { offset, limit ->
                val end = minOf(offset + limit, sampleCount)
                (offset until end).map { index ->
                    BackupGlucoseSample(
                        timestamp = 1_700_000_000_000L + index,
                        glucoseMgdl = 90.0 + (index % 100),
                        hcRecordId = "hc-$index",
                        recordingMethod = 1,
                        importedAt = 1_700_000_000_000L + index,
                    )
                }
            },
            chat = { _, _ -> emptyList() },
            pendingAiQueries = { _, _ -> emptyList() },
            insights = { _, _ -> emptyList() },
        )
        return output.toByteArray()
    }

    private suspend fun seed(): Seed {
        val foodId = db.foodDao().insert(
            FoodEntity(
                uuid = "food-uuid-1",
                name = "Oats",
                brand = null,
                barcode = "500011",
                carbsGrams = 27.0,
                proteinGrams = 5.0,
                fatGrams = null,
                kcal = null,
                servingGrams = 40.0,
                servingLabel = "cup",
                source = FoodSource.MANUAL,
                offFetchedAt = null,
                userCorrected = false,
                useCount = 0,
                lastUsedAt = null,
                isFavorite = true,
                createdAt = 900L,
            )
        )
        val entryId = db.entryDao().insert(
            EntryEntity(
                timestamp = 1_000L,
                glucoseMgdl = 120.0,
                mealContext = MealContext.AFTER_MEAL,
                insulinBasalUnits = null,
                insulinBolusUnits = 4.5,
                carbsGrams = 40,
                proteinGrams = 20,
                fatGrams = 10,
                mealDescription = "Lunch",
                exerciseMinutes = null,
                exerciseIntensity = ActivityIntensity.MODERATE,
                note = "walk",
                source = EntrySource.HERO_AI,
                hcRecordId = null,
                foodId = foodId,
                uuid = "entry-uuid-1",
                moodScore = 4,
                moodLabel = "steady",
            )
        )
        val supplyId = db.supplyDao().insert(
            SupplyEntity(
                type = SupplyType.SENSOR,
                startedAt = 1_000L,
                expectedLifespanDays = 10,
                replacedAt = null,
                uuid = "supply-uuid-1",
            )
        )
        val chatId = db.chatMessageDao().insert(
            ChatMessageEntity(role = ChatRole.USER, content = "Hello", timestamp = 1_000L)
        )
        val pendingId = db.pendingAiQueryDao().insert(
            PendingAiQueryEntity(userMessageId = chatId, prompt = "avg?", createdAt = 2_000L)
        )
        val sampleId = db.glucoseSampleDao().insertAll(
            listOf(
                GlucoseSampleEntity(
                    timestamp = 1_000L,
                    glucoseMgdl = 96.0,
                    source = GlucoseSampleSource.HEALTH_CONNECT,
                    externalId = "hc-1",
                    hcRecordId = "hc-1",
                    sourcePackage = null,
                    recordingMethod = 1,
                    importedAt = 2_000L,
                )
            )
        ).first()
        val insightId = db.insightDao().insert(
            InsightCardEntity(
                title = "Late lows",
                description = "Lows cluster at 3 AM.",
                severityLevel = 2,
                createdAt = 3_000L,
            )
        )

        return Seed(
            entry = EntryEntity(
                id = entryId,
                timestamp = 1_000L,
                glucoseMgdl = 120.0,
                mealContext = MealContext.AFTER_MEAL,
                insulinBasalUnits = null,
                insulinBolusUnits = 4.5,
                carbsGrams = 40,
                proteinGrams = 20,
                fatGrams = 10,
                mealDescription = "Lunch",
                exerciseMinutes = null,
                exerciseIntensity = ActivityIntensity.MODERATE,
                note = "walk",
                source = EntrySource.HERO_AI,
                hcRecordId = null,
                foodId = foodId,
                uuid = "entry-uuid-1",
                moodScore = 4,
                moodLabel = "steady",
            ),
            food = FoodEntity(
                id = foodId,
                uuid = "food-uuid-1",
                name = "Oats",
                brand = null,
                barcode = "500011",
                carbsGrams = 27.0,
                proteinGrams = 5.0,
                fatGrams = null,
                kcal = null,
                servingGrams = 40.0,
                servingLabel = "cup",
                source = FoodSource.MANUAL,
                offFetchedAt = null,
                userCorrected = false,
                useCount = 0,
                lastUsedAt = null,
                isFavorite = true,
                createdAt = 900L,
            ),
            supply = SupplyEntity(
                id = supplyId,
                type = SupplyType.SENSOR,
                startedAt = 1_000L,
                expectedLifespanDays = 10,
                replacedAt = null,
                uuid = "supply-uuid-1",
            ),
            sample = GlucoseSampleEntity(
                id = sampleId,
                timestamp = 1_000L,
                glucoseMgdl = 96.0,
                source = GlucoseSampleSource.HEALTH_CONNECT,
                externalId = "hc-1",
                hcRecordId = "hc-1",
                sourcePackage = null,
                recordingMethod = 1,
                importedAt = 2_000L,
            ),
            chat = ChatMessageEntity(id = chatId, role = ChatRole.USER, content = "Hello", timestamp = 1_000L),
            pending = PendingAiQueryEntity(id = pendingId, userMessageId = chatId, prompt = "avg?", createdAt = 2_000L),
            insight = InsightCardEntity(
                id = insightId,
                title = "Late lows",
                description = "Lows cluster at 3 AM.",
                severityLevel = 2,
                createdAt = 3_000L,
            ),
        )
    }

    private fun <T> page(list: List<T>, offset: Int, limit: Int): List<T> =
        if (offset >= list.size) emptyList() else list.subList(offset, minOf(offset + limit, list.size))

    private suspend fun counts(): Counts = Counts(
        entries = db.entryDao().countAll(),
        foods = db.foodDao().countAll(),
        supplies = db.supplyDao().countAll(),
        glucoseSamples = db.glucoseSampleDao().count(),
        chat = db.chatMessageDao().countAll(),
        pendingAiQueries = db.pendingAiQueryDao().countAll(),
        insights = db.insightDao().countAll(),
    )

    private data class Counts(
        val entries: Int,
        val foods: Int,
        val supplies: Int,
        val glucoseSamples: Int,
        val chat: Int,
        val pendingAiQueries: Int,
        val insights: Int,
    )

    private data class Seed(
        val entry: EntryEntity,
        val food: FoodEntity,
        val supply: SupplyEntity,
        val sample: GlucoseSampleEntity,
        val chat: ChatMessageEntity,
        val pending: PendingAiQueryEntity,
        val insight: InsightCardEntity,
    )
}
