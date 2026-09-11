package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.domain.model.GlucosePointRow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach

/**
 * Observes [GlucoseFreshness] from the newest `glucose_readings` row.
 *
 * Why a repository: Log, Stats, and any later Data Sources screen need the
 * same classification without each ViewModel re-deriving lookback + tick.
 * The classifier itself stays a pure function; this only supplies the
 * newest timestamp and a clock.
 *
 * Live path: [EntryDao.observeGlucoseReadingsPoints] over a bounded window so
 * a new sample invalidates without loading full history. If that window is
 * empty (newest row is older than the window), falls back to
 * [EntryDao.latestGlucoseReading] so "3 days ago" is Stale, not NoData.
 *
 * `since` is recomputed from the 60-second ticker, not captured once at
 * subscribe time. Room is resubscribed only when that window start has
 * moved by [WINDOW_SLIDE_MILLIS], so a screen left open for hours does not
 * grow an unbounded query. Classification still ticks every minute so
 * Fresh can become Stale with no insert.
 *
 * The empty-window fallback is cached until Room emits again (a real table
 * change or a window slide). A user with no CGM does not pay a LIMIT 1
 * query every minute for as long as the screen is open.
 *
 * FEATURE: cgm-direct-ingest
 */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseFreshnessRepository(
    private val observePoints: (Long) -> Flow<List<GlucosePointRow>>,
    private val loadLatest: suspend () -> GlucosePointRow?,
) {

    @Inject
    constructor(entryDao: EntryDao) : this(
        observePoints = { since -> entryDao.observeGlucoseReadingsPoints(since) },
        loadLatest = { entryDao.latestGlucoseReading() },
    )

    fun observe(nowMillis: () -> Long = { System.currentTimeMillis() }): Flow<GlucoseFreshness> {
        var cachedFallbackTimestamp: Long? = null
        var fallbackResolved = false

        val ticks = ticker()
        val pointsFlow = ticks
            .map { nowMillis() - LIVE_LOOKBACK_MILLIS }
            .distinctUntilChanged { old, new -> abs(new - old) < WINDOW_SLIDE_MILLIS }
            .flatMapLatest { since ->
                observePoints(since).onEach { fallbackResolved = false }
            }

        return combine(pointsFlow, ticks) { points, _ -> points }
            .mapLatest { points ->
                val now = nowMillis()
                val newest = points.maxOfOrNull { it.timestamp } ?: run {
                    if (!fallbackResolved) {
                        cachedFallbackTimestamp = loadLatest()?.timestamp
                        fallbackResolved = true
                    }
                    cachedFallbackTimestamp
                }
                GlucoseFreshness.classify(newest, now)
            }
    }

    private fun ticker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    companion object {
        /**
         * Room observation window. Long enough that a typical stale gap still
         * arrives as a Flow emission; older newest-rows use the one-shot
         * latest query. Not a freshness threshold.
         */
        const val LIVE_LOOKBACK_MILLIS: Long = 6L * 60L * 60L * 1000L

        const val TICK_MILLIS: Long = 60_000L

        /**
         * How far the Room `since` parameter may drift before we resubscribe.
         * Smaller than [LIVE_LOOKBACK_MILLIS] so the query stays bounded;
         * larger than [TICK_MILLIS] so we do not open a new query every
         * minute. Classification still uses the 60-second tick.
         */
        const val WINDOW_SLIDE_MILLIS: Long = 30L * 60L * 1000L
    }
}
