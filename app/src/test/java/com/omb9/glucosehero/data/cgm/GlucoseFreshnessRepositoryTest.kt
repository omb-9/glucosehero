package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.domain.model.GlucosePointRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseFreshnessRepositoryTest {

    private val epoch = 1_700_000_000_000L

    @Test
    fun `long-lived subscription keeps a bounded Room window`() = runTest {
        val sinceValues = mutableListOf<Long>()
        val points = MutableStateFlow(emptyList<GlucosePointRow>())
        val repo = GlucoseFreshnessRepository(
            observePoints = { since ->
                sinceValues += since
                points
            },
            loadLatest = { GlucosePointRow(epoch - 3L * 24L * 60L * 60L * 1000L, 100.0) },
        )
        val job = launch {
            repo.observe { epoch + testScheduler.currentTime }.collect { }
        }
        testScheduler.runCurrent()
        val firstSince = sinceValues.first()
        testScheduler.advanceTimeBy(3L * 60L * 60L * 1000L)
        testScheduler.runCurrent()
        val lastSince = sinceValues.last()
        val now = epoch + testScheduler.currentTime
        assertTrue(lastSince > firstSince)
        assertTrue(
            now - lastSince <= GlucoseFreshnessRepository.LIVE_LOOKBACK_MILLIS +
                GlucoseFreshnessRepository.WINDOW_SLIDE_MILLIS,
        )
        assertTrue(
            lastSince >= now - GlucoseFreshnessRepository.LIVE_LOOKBACK_MILLIS -
                GlucoseFreshnessRepository.WINDOW_SLIDE_MILLIS,
        )
        job.cancel()
    }

    @Test
    fun `empty window caches the latest-row fallback across ticks`() = runTest {
        var latestQueries = 0
        val points = MutableStateFlow(emptyList<GlucosePointRow>())
        val staleTs = epoch - 3L * 24L * 60L * 60L * 1000L
        val repo = GlucoseFreshnessRepository(
            observePoints = { points },
            loadLatest = {
                latestQueries++
                GlucosePointRow(staleTs, 100.0)
            },
        )
        val emissions = mutableListOf<GlucoseFreshness>()
        val job = launch {
            repo.observe { epoch + testScheduler.currentTime }.collect { emissions.add(it) }
        }
        testScheduler.runCurrent()
        assertEquals(1, latestQueries)
        testScheduler.advanceTimeBy(5L * GlucoseFreshnessRepository.TICK_MILLIS)
        testScheduler.runCurrent()
        assertEquals(1, latestQueries)
        assertTrue(emissions.last() is GlucoseFreshness.Stale)
        job.cancel()
    }

    @Test
    fun `live window does not query the latest-row fallback`() = runTest {
        var latestQueries = 0
        val points = MutableStateFlow(listOf(GlucosePointRow(epoch, 110.0)))
        val repo = GlucoseFreshnessRepository(
            observePoints = { since ->
                points.map { list -> list.filter { it.timestamp >= since } }
            },
            loadLatest = {
                latestQueries++
                null
            },
        )
        val job = launch {
            repo.observe { epoch + testScheduler.currentTime }.collect { }
        }
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(5L * GlucoseFreshnessRepository.TICK_MILLIS)
        testScheduler.runCurrent()
        assertEquals(0, latestQueries)
        job.cancel()
    }

    @Test
    fun `ticker flips Fresh to Stale with no new rows`() = runTest {
        val points = MutableStateFlow(listOf(GlucosePointRow(epoch, 110.0)))
        val repo = GlucoseFreshnessRepository(
            observePoints = { points },
            loadLatest = { error("live window should not fall back") },
        )
        val emissions = mutableListOf<GlucoseFreshness>()
        val job = launch {
            repo.observe { epoch + testScheduler.currentTime }.collect { emissions.add(it) }
        }
        testScheduler.runCurrent()
        assertEquals(GlucoseFreshness.Fresh, emissions.last())
        testScheduler.advanceTimeBy(
            GlucoseFreshness.FRESH_MAX_AGE_MILLIS + GlucoseFreshnessRepository.TICK_MILLIS,
        )
        testScheduler.runCurrent()
        assertTrue(emissions.last() is GlucoseFreshness.Stale)
        job.cancel()
    }

    @Test
    fun `null latest fallback stays NoData without repeating the query`() = runTest {
        var latestQueries = 0
        val points = MutableStateFlow(emptyList<GlucosePointRow>())
        val repo = GlucoseFreshnessRepository(
            observePoints = { points },
            loadLatest = {
                latestQueries++
                null
            },
        )
        val emissions = mutableListOf<GlucoseFreshness>()
        val job = launch {
            repo.observe { epoch + testScheduler.currentTime }.collect { emissions.add(it) }
        }
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(3L * GlucoseFreshnessRepository.TICK_MILLIS)
        testScheduler.runCurrent()
        assertEquals(1, latestQueries)
        assertEquals(GlucoseFreshness.NoData, emissions.last())
        job.cancel()
    }
}
