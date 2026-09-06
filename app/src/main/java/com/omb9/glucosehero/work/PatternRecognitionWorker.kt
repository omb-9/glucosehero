package com.omb9.glucosehero.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.db.InsightDao
import com.omb9.glucosehero.data.local.entity.InsightCardEntity
import com.omb9.glucosehero.data.repository.AnalyticsRepository
import com.omb9.glucosehero.domain.model.GlucosePointRow
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException

/**
 * Nightly metabolic-pattern detector.
 *
 * The worker is deliberately database-only: it reads the last 14 days of
 * glucose readings through [EntryDao] aggregate queries, runs pure Kotlin
 * heuristics on the returned rows, and persists any detected patterns as
 * [InsightCardEntity] rows. It never touches UI or notification code.
 */
@HiltWorker
class PatternRecognitionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val entryDao: EntryDao,
    private val insightDao: InsightDao,
    private val analyticsRepository: AnalyticsRepository,
    private val database: GlucoseHeroDatabase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val since = now - ANALYSIS_WINDOW_MS

            insightDao.deleteOlderThan(now - INSIGHT_RETENTION_MS)

            val existingTitles = insightDao.getAll().map { it.title }.toSet()
            val newInsights = detectPatterns(since, now)
                .filter { it.title !in existingTitles }

            newInsights.forEach { insightDao.insert(it) }

            refreshTagAnalytics(since, now)

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Rebuilds the `tag_analytics` cache wholesale. A failure here must never
     * break the insight cards the rest of this worker already produced, so it
     * is caught, logged, and swallowed rather than converted to a retry.
     */
    private suspend fun refreshTagAnalytics(since: Long, now: Long) {
        try {
            val analytics = analyticsRepository.computeTagAnalytics(since = since, now = now)
            database.tagAnalyticDao().replaceAll(analytics)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Tag analytics computation failed", e)
        }
    }

    private suspend fun detectPatterns(since: Long, now: Long): List<InsightCardEntity> {
        val insights = mutableListOf<InsightCardEntity>()

        // 1) Overnight low episodes below the threshold. Hourly averages alone
        // can hide a dangerous 2-4 AM pattern if other readings pull the mean up.
        val points = entryDao.glucoseReadingPointsSince(since)
        val overnightLowCount = countOvernightLowEpisodes(points)
        val hasOvernightLowInsight = overnightLowCount >= MIN_OVERNIGHT_LOW_EVENTS
        if (hasOvernightLowInsight) {
            insights += InsightCardEntity(
                title = "Frequent overnight lows",
                description = "Glucose dropped below ${LOW_THRESHOLD_MGDL.toInt()} mg/dL " +
                    "$overnightLowCount times between ${formatHour(OVERNIGHT_LOW_START_HOUR)} " +
                    "and ${formatHour(OVERNIGHT_LOW_END_HOUR)} over the last 14 days.",
                severityLevel = InsightCardEntity.SEVERITY_CRITICAL,
                createdAt = now,
            )
        }

        // 2) Recurring time-based lows/highs from hour-of-day averages.
        val hourlyAverages = entryDao.hourlyAveragesSince(since)
            .filter { it.readings >= MIN_READINGS_PER_HOUR }

        val lowestHour = hourlyAverages.minByOrNull { it.avgMgdl }
        if (lowestHour != null && lowestHour.avgMgdl < LOW_THRESHOLD_MGDL) {
            val alreadyCoveredByOvernight = hasOvernightLowInsight &&
                lowestHour.hour in OVERNIGHT_LOW_START_HOUR..OVERNIGHT_LOW_END_HOUR
            if (!alreadyCoveredByOvernight) {
                insights += InsightCardEntity(
                    title = "Recurring low around ${formatHour(lowestHour.hour)}",
                    description = "Average glucose was " +
                        "${formatMgdl(lowestHour.avgMgdl)} during the " +
                        "${formatHour(lowestHour.hour)} hour over the last 14 days.",
                    severityLevel = InsightCardEntity.SEVERITY_CRITICAL,
                    createdAt = now,
                )
            }
        }

        val highestHour = hourlyAverages.maxByOrNull { it.avgMgdl }
        if (highestHour != null && highestHour.avgMgdl > HIGH_THRESHOLD_MGDL) {
            insights += InsightCardEntity(
                title = "Recurring high around ${formatHour(highestHour.hour)}",
                description = "Average glucose was " +
                    "${formatMgdl(highestHour.avgMgdl)} during the " +
                    "${formatHour(highestHour.hour)} hour over the last 14 days.",
                severityLevel = InsightCardEntity.SEVERITY_WARNING,
                createdAt = now,
            )
        }

        // 3) High-variance periods. Room returns avg(x) and avg(x*x), so the
        // per-hour standard deviation is computed in Kotlin as sqrt(E[x^2]-E[x]^2).
        val spikiestHour = entryDao.hourlyVarianceSince(since)
            .filter { it.readings >= MIN_READINGS_PER_HOUR }
            .mapNotNull { row ->
                val variance = (row.avgSqMgdl - row.avgMgdl * row.avgMgdl).coerceAtLeast(0.0)
                val standardDeviation = sqrt(variance)
                if (standardDeviation >= HIGH_VARIANCE_SD_MGDL) {
                    row to standardDeviation
                } else {
                    null
                }
            }
            .maxByOrNull { it.second }

        if (spikiestHour != null) {
            val (row, standardDeviation) = spikiestHour
            insights += InsightCardEntity(
                title = "High variability around ${formatHour(row.hour)}",
                description = "Glucose swung with a standard deviation of " +
                    "${formatMgdl(standardDeviation)} during the ${formatHour(row.hour)} hour " +
                    "over the last 14 days.",
                severityLevel = InsightCardEntity.SEVERITY_WARNING,
                createdAt = now,
            )
        }

        return insights
    }

    /**
     * Collapses below-threshold points into overnight-low episodes. CGM samples
     * arrive every few minutes, so a single sustained low would otherwise be
     * counted as three or four separate events. Points separated by more than
     * [LOW_EPISODE_GAP_MS] are treated as distinct episodes.
     */
    private fun countOvernightLowEpisodes(points: List<GlucosePointRow>): Int {
        val overnightLows = points.filter { point ->
            val hour = point.timestamp.localHour()
            hour in OVERNIGHT_LOW_START_HOUR..OVERNIGHT_LOW_END_HOUR &&
                point.glucoseMgdl < LOW_THRESHOLD_MGDL
        }
        if (overnightLows.isEmpty()) return 0

        var episodes = 1
        var previousTimestamp = overnightLows.first().timestamp
        for (point in overnightLows.drop(1)) {
            if (point.timestamp - previousTimestamp > LOW_EPISODE_GAP_MS) {
                episodes++
            }
            previousTimestamp = point.timestamp
        }
        return episodes
    }

    private fun Long.localHour(): Int =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).hour

    private fun formatHour(hour: Int): String {
        val hour12 = when (hour % 12) {
            0 -> 12
            else -> hour % 12
        }
        val suffix = if (hour < 12) "AM" else "PM"
        return "$hour12 $suffix"
    }

    private fun formatMgdl(value: Double): String =
        String.format(Locale.US, "%.0f mg/dL", value)

    companion object {
        private const val TAG = "PatternRecognitionWorker"

        const val UNIQUE_NAME = "pattern_recognition_worker"

        private const val ANALYSIS_WINDOW_MS = 14L * 24L * 60L * 60L * 1000L
        private const val INSIGHT_RETENTION_MS = ANALYSIS_WINDOW_MS
        private const val LOW_THRESHOLD_MGDL = 70.0
        private const val HIGH_THRESHOLD_MGDL = 180.0
        private const val HIGH_VARIANCE_SD_MGDL = 60.0
        private const val MIN_READINGS_PER_HOUR = 3
        private const val MIN_OVERNIGHT_LOW_EVENTS = 3
        private const val LOW_EPISODE_GAP_MS = 15L * 60L * 1000L
        private const val OVERNIGHT_LOW_START_HOUR = 2
        private const val OVERNIGHT_LOW_END_HOUR = 4
    }
}
