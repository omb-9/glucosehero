package com.omb9.glucosehero.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.domain.model.TagKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies that crisis-flagged journal entries are excluded from tag
 * analytics before tag extraction, so a matched entry contributes no tags of
 * any kind — even when it also carries a food id and a meal description.
 *
 * Mirrors [com.omb9.glucosehero.data.export.BackupDatabaseRoundTripTest]'s use
 * of a real in-memory Room database, since `room-testing` is
 * `androidTestImplementation` only (issue 70).
 */
class AnalyticsRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: GlucoseHeroDatabase
    private lateinit var repository: AnalyticsRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, GlucoseHeroDatabase::class.java).build()
        repository = AnalyticsRepository(db.entryDao(), db.tagAnalyticDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun crisisEntry_contributesNoTags_evenWithFoodAndMealDescription() = runBlocking {
        val foodId = db.foodDao().insert(
            FoodEntity(
                name = "Crisis Pizza",
                carbsGrams = 40.0,
                source = FoodSource.MANUAL,
                createdAt = 900L,
            ),
        )
        // The exact failure case: a meal and journal in the same entry, where
        // the journal text triggers the crisis detector.
        db.entryDao().insert(
            EntryEntity(
                timestamp = 1_000_000L,
                glucoseMgdl = 120.0,
                mealDescription = "Pizza meal",
                note = "I want to die tonight",
                foodId = foodId,
            )
        )
        // A follow-up reading ~2h later so the entry would otherwise yield a
        // computable delta (and therefore a FOOD tag from the food id).
        seedFollowUp(recordId = "hc-followup-crisis", glucoseMgdl = 140.0)

        val result = repository.computeTagAnalytics(since = 0L, now = 9_000_000L)

        assertTrue("crisis entry must contribute no tags", result.isEmpty())
    }

    @Test
    fun normalEntry_withHashtag_stillProducesItsTag() = runBlocking {
        db.entryDao().insert(
            EntryEntity(
                timestamp = 1_000_000L,
                glucoseMgdl = 100.0,
                note = "#salad for lunch",
            )
        )
        seedFollowUp(recordId = "hc-followup-normal", glucoseMgdl = 130.0)

        val result = repository.computeTagAnalytics(since = 0L, now = 9_000_000L)

        assertEquals(listOf("#salad"), result.map { it.tag })
    }

    @Test
    fun skewedDeltas_useMedianNotMean_andPersistBolusAndCarbs() = runBlocking {
        val baseline = 100.0
        val followUps = listOf(110.0, 120.0, 300.0)
        followUps.forEachIndexed { index, followUp ->
            val start = 1_000_000L + index * 20_000_000L
            db.entryDao().insert(
                EntryEntity(
                    timestamp = start,
                    glucoseMgdl = baseline,
                    note = "#pizza",
                    carbsGrams = 40 + index * 10,
                    insulinBolusUnits = 3.0 + index,
                )
            )
            seedReading(
                recordId = "hc-pizza-$index",
                timestamp = start + 7_200_000L,
                glucoseMgdl = followUp,
            )
        }

        val result = repository.computeTagAnalytics(since = 0L, now = 80_000_000L)
        val pizza = result.single { it.tag == "#pizza" }

        // Deltas 10, 20, 200. Mean would be ~76.7; median/quartiles stay robust.
        assertEquals(3, pizza.occurrences)
        assertEquals(20.0, pizza.medianDeltaMgdl, 1e-6)
        assertEquals(15.0, pizza.p25DeltaMgdl, 1e-6)
        assertEquals(110.0, pizza.p75DeltaMgdl, 1e-6)
        assertEquals(50.0, pizza.avgCarbsGrams!!, 1e-6)
        assertEquals(4.0, pizza.avgBolusUnits!!, 1e-6)
    }

    @Test
    fun tagsBelowDisplayFloor_areStillComputed() = runBlocking {
        db.entryDao().insert(
            EntryEntity(
                timestamp = 1_000_000L,
                glucoseMgdl = 100.0,
                note = "#once",
                carbsGrams = 20,
                insulinBolusUnits = 2.0,
            )
        )
        seedFollowUp(recordId = "hc-followup-once", glucoseMgdl = 130.0)

        val result = repository.computeTagAnalytics(since = 0L, now = 9_000_000L)
        val tag = result.single { it.tag == "#once" }

        assertEquals(1, tag.occurrences)
        assertEquals(30.0, tag.medianDeltaMgdl, 1e-6)
        assertEquals(20.0, tag.avgCarbsGrams!!, 1e-6)
        assertEquals(2.0, tag.avgBolusUnits!!, 1e-6)
    }

    @Test
    fun sleepWindowEvent_producesSleepTagWithWindowDelta() = runBlocking {
        db.entryDao().insert(
            EntryEntity(
                timestamp = 10_000_000L,
                endTime = 10_000_000L + 28_800_000L,
                note = "#sleep",
                source = EntrySource.HEALTH_CONNECT,
                hcRecordId = "hc-sleep-1",
            )
        )
        seedReading(recordId = "hc-baseline", timestamp = 9_000_000L, glucoseMgdl = 100.0)
        seedReading(recordId = "hc-overnight", timestamp = 20_000_000L, glucoseMgdl = 140.0)

        val result = repository.computeWindowTagAnalytics(since = 0L, now = 50_000_000L)

        assertEquals(listOf("#sleep"), result.map { it.tag })
        assertEquals(TagKind.SLEEP, result.single().kind)
        assertEquals(40.0, result.single().medianDeltaMgdl, 1e-6)
    }

    private suspend fun seedFollowUp(recordId: String, glucoseMgdl: Double) {
        db.glucoseSampleDao().insertAll(
            listOf(
                GlucoseSampleEntity(
                    // ~2h after the seeded entry's timestamp, inside the
                    // ±30-minute follow-up window used by tagAnalyticsRows.
                    timestamp = 1_000_000L + 7_200_000L,
                    glucoseMgdl = glucoseMgdl,
                    source = GlucoseSampleSource.HEALTH_CONNECT,
                    externalId = recordId,
                    hcRecordId = recordId,
                    sourcePackage = null,
                    recordingMethod = 1,
                    importedAt = 2_000_000L,
                )
            )
        )
    }

    private suspend fun seedReading(recordId: String, timestamp: Long, glucoseMgdl: Double) {
        db.glucoseSampleDao().insertAll(
            listOf(
                GlucoseSampleEntity(
                    timestamp = timestamp,
                    glucoseMgdl = glucoseMgdl,
                    source = GlucoseSampleSource.HEALTH_CONNECT,
                    externalId = recordId,
                    hcRecordId = recordId,
                    sourcePackage = null,
                    recordingMethod = 1,
                    importedAt = 2_000_000L,
                )
            )
        )
    }
}
