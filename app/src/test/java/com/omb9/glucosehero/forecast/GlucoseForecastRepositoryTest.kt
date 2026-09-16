package com.omb9.glucosehero.forecast

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.paging.PagingSource
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.HourlyGlucoseAverageRow
import com.omb9.glucosehero.data.local.db.HourlyGlucoseVarianceRow
import com.omb9.glucosehero.data.local.db.LoggedDayRow
import com.omb9.glucosehero.data.local.db.TagAnalyticsRow
import com.omb9.glucosehero.data.local.db.TimeInRangeCounts
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.domain.model.BolusSettings
import com.omb9.glucosehero.domain.model.DailyGlucoseSummary
import com.omb9.glucosehero.domain.model.DosingProfile
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.domain.model.GlucoseStats
import com.omb9.glucosehero.util.CarbAbsorptionCalculator
import com.omb9.glucosehero.util.IobCalculator
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseForecastRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val now = Instant.parse("2026-09-14T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `DIA 8 reports IOB for a 5U bolus logged 7 hours ago`() = runTest {
        val dao = RecordingEntryDao()
        dao.entries += EntryEntity(
            timestamp = now.minusSeconds(7 * 3600L).toEpochMilli(),
            insulinBolusUnits = 5.0,
        )
        withRepository(dao) { stores ->
            stores.settings.setDosingProfile(
                DosingProfile.single(BolusSettings(diaHours = 8.0f)),
            )
            val snapshot = stores.forecast.refresh(now)
            assertTrue(
                "7h-old bolus at DIA=8 was dropped by a 6h lookback; IOB was ${snapshot.iobUnits}",
                snapshot.iobUnits > 0.0,
            )
        }
    }

    @Test
    fun `glucose sample query stays at the engine 90 minute window`() = runTest {
        val dao = RecordingEntryDao()
        withRepository(dao) { stores ->
            stores.settings.setDosingProfile(DosingProfile.single(BolusSettings(diaHours = 8.0f)))
            stores.forecast.refresh(now)
            assertEquals(
                now.toEpochMilli() - GlucoseForecastEngine.LOOKBACK_MINUTES * 60_000L,
                dao.glucoseSinceValues.single(),
            )
            val sixHours = 6L * IobCalculator.MILLIS_PER_HOUR
            assertTrue(
                "glucose lookback must not widen to the old 6h conflated window",
                now.toEpochMilli() - dao.glucoseSinceValues.single() < sixHours,
            )
        }
    }

    @Test
    fun `entry lookback is the max of DIA bolus window and carb action`() = runTest {
        val dao = RecordingEntryDao()
        withRepository(dao) { stores ->
            stores.settings.setDosingProfile(DosingProfile.single(BolusSettings(diaHours = 8.0f)))
            stores.forecast.refresh(now)
            val expected = now.toEpochMilli() - maxOf(
                IobCalculator.lookbackMillis(8.0),
                (CarbAbsorptionCalculator.DEFAULT_ACTION_HOURS * IobCalculator.MILLIS_PER_HOUR)
                    .toLong(),
            )
            assertEquals(expected, dao.entrySinceValues.single())
        }
    }

    @Test
    fun `DIA 4 reports zero IOB for a bolus logged 5 hours ago`() = runTest {
        val dao = RecordingEntryDao()
        dao.entries += EntryEntity(
            timestamp = now.minusSeconds(5 * 3600L).toEpochMilli(),
            insulinBolusUnits = 5.0,
        )
        withRepository(dao) { stores ->
            stores.settings.setDosingProfile(DosingProfile.single(BolusSettings(diaHours = 4.0f)))
            val snapshot = stores.forecast.refresh(now)
            assertEquals(0.0, snapshot.iobUnits, 1e-9)
        }
    }

    private suspend fun TestScope.withRepository(
        dao: RecordingEntryDao,
        block: suspend (Stores) -> Unit,
    ) {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
        val settingsStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("settings.preferences_pb") },
        )
        val runtimeStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("runtime_state.preferences_pb") },
        )
        val settings = SettingsDataStore(
            clock = clock,
            settingsStore = settingsStore,
            runtimeStore = runtimeStore,
        )
        val forecast = GlucoseForecastRepository(dao, settings, clock)
        try {
            block(Stores(settings, forecast))
        } finally {
            scope.cancel()
        }
    }

    private data class Stores(
        val settings: SettingsDataStore,
        val forecast: GlucoseForecastRepository,
    )
}

private class RecordingEntryDao : EntryDao {
    val entries = mutableListOf<EntryEntity>()
    val points = mutableListOf<GlucosePointRow>()
    val glucoseSinceValues = mutableListOf<Long>()
    val entrySinceValues = mutableListOf<Long>()

    override suspend fun entriesSince(since: Long): List<EntryEntity> {
        entrySinceValues += since
        return entries.filter { it.timestamp >= since }
    }

    override suspend fun glucoseReadingPointsSince(since: Long): List<GlucosePointRow> {
        glucoseSinceValues += since
        return points.filter { it.timestamp >= since }
    }

    override fun observeEventsSince(since: Long): Flow<List<EntryEntity>> = unsupported()
    override fun observeEntriesBetween(startMillis: Long, endMillis: Long): Flow<List<EntryEntity>> =
        unsupported()
    override fun pagingSource(): PagingSource<Int, EntryEntity> = unsupported()
    override fun observeGlucoseEventsSince(since: Long): Flow<List<EntryEntity>> = unsupported()
    override fun observeGlucoseReadingsPoints(since: Long): Flow<List<GlucosePointRow>> = unsupported()
    override fun observeGlucoseReadingsMaxTimestamp(): Flow<Long?> = unsupported()
    override fun observeById(id: Long): Flow<EntryEntity?> = unsupported()
    override suspend fun searchEntries(query: String): List<EntryEntity> = emptyList()
    override suspend fun insert(entity: EntryEntity): Long = 1L
    override suspend fun update(entity: EntryEntity) {}
    override suspend fun deleteById(id: Long) {}
    override suspend fun delete(entity: EntryEntity) {}
    override suspend fun averageGlucoseReadingsSince(since: Long): Double? = null
    override fun observeGlucoseStatsSince(since: Long): Flow<GlucoseStats> = unsupported()
    override suspend fun cgmReadingCountSince(since: Long): Int = 0
    override suspend fun manualReadingCountSince(since: Long): Int = 0
    override fun observeLoggedDays(since: Long): Flow<List<LoggedDayRow>> = unsupported()
    override suspend fun loggedDaysSince(since: Long): List<LoggedDayRow> = emptyList()
    override suspend fun timeInRangeSince(since: Long, low: Double, high: Double): Double? = null
    override suspend fun timeInRangeCountsSince(since: Long, low: Double, high: Double): TimeInRangeCounts =
        TimeInRangeCounts(0, 0, 0, 0, 0)
    override suspend fun averageGlucoseReadingsBetween(startMillis: Long, endMillis: Long): Double? = null
    override suspend fun dailySummaries(since: Long, limit: Int): List<DailyGlucoseSummary> = emptyList()
    override suspend fun recentEntries(limit: Int): List<EntryEntity> = emptyList()
    override suspend fun latestGlucoseReading(): GlucosePointRow? = null
    override suspend fun taggedEntries(): List<EntryEntity> = emptyList()
    override suspend fun tagAnalyticsRows(
        since: Long,
        windowStart: Long,
        windowEnd: Long,
        target: Long,
    ): List<TagAnalyticsRow> = emptyList()
    override suspend fun windowedEntriesSince(since: Long): List<EntryEntity> = emptyList()
    override suspend fun glucoseReadingPointsBetween(
        startMillis: Long,
        endMillis: Long,
    ): List<GlucosePointRow> = emptyList()
    override suspend fun hourlyAveragesSince(since: Long): List<HourlyGlucoseAverageRow> = emptyList()
    override suspend fun hourlyVarianceSince(since: Long): List<HourlyGlucoseVarianceRow> = emptyList()
    override suspend fun pageForExport(lastId: Long, limit: Int): List<EntryEntity> = emptyList()
    override suspend fun pageByTimestampForExport(
        lastTimestamp: Long,
        lastId: Long,
        limit: Int,
    ): List<EntryEntity> = emptyList()
    override suspend fun pageSinceByTimestampForExport(
        since: Long,
        lastTimestamp: Long,
        lastId: Long,
        limit: Int,
    ): List<EntryEntity> = emptyList()
    override suspend fun countAll(): Int = entries.size
    override suspend fun getAll(): List<EntryEntity> = entries
    override suspend fun allUuids(): List<String> = emptyList()
    override suspend fun clear() {
        entries.clear()
        points.clear()
    }
    override suspend fun insertAll(entities: List<EntryEntity>): List<Long> = emptyList()
    override suspend fun insertIgnoreAll(entities: List<EntryEntity>): List<Long> = emptyList()
    override suspend fun upsertAll(entities: List<EntryEntity>): List<Long> = emptyList()
    override suspend fun deleteByHcRecordId(hcRecordId: String) {}

    private fun unsupported(): Nothing = throw UnsupportedOperationException()
}
