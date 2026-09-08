package com.omb9.glucosehero.data.repository

import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.TagAnalyticDao
import com.omb9.glucosehero.data.local.entity.TagAnalyticEntity
import com.omb9.glucosehero.domain.model.TagKind
import com.omb9.glucosehero.domain.model.isWindowed
import com.omb9.glucosehero.util.CrisisDetector
import com.omb9.glucosehero.util.Percentiles
import com.omb9.glucosehero.util.TagExtractor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Computes per-tag glucose analytics from logged entries.
 *
 * Food, hashtag, description, and mood tags use a 2-hour post-event delta: the
 * event's own baseline glucose reading subtracted from the reading found about
 * two hours later (see [computeTagAnalytics]). Sleep and cycle tags are
 * interval records without their own glucose reading, so their impact is a
 * window delta instead — the average glucose during the interval minus a
 * baseline just before it (see [computeWindowTagAnalytics]).
 *
 * Aggregation (median/quartiles rather than a mean of the per-event deltas)
 * happens in Kotlin on [Dispatchers.IO] so the main thread is never blocked.
 *
 * [topFoodPatternsForPrompt] reads the cached `tag_analytics` rows (not a
 * live recompute) and returns a short list for the Hero system prompt.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val tagAnalyticDao: TagAnalyticDao,
) {

    /**
     * Builds a [TagAnalyticEntity] for every non-windowed tag seen on a
     * qualifying entry since [since].
     *
     * [now] is stamped into each row as `computedAt`; it should match the
     * caller's notion of "the current run" so a nightly refresh is internally
     * consistent.
     *
     * Entries whose note matches [CrisisDetector] are dropped before tag
     * extraction, so a crisis entry contributes no tags of any kind.
     *
     * Entries whose follow-up reading is missing are dropped before delta
     * grouping, so a tag only appears once at least one of its entries yields
     * a computable delta. Every such tag is emitted regardless of how many
     * occurrences it has; [TagExtractor.minOccurrences] is applied at display
     * time so the "still building" UI can still show tags below the floor.
     */
    suspend fun computeTagAnalytics(since: Long, now: Long): List<TagAnalyticEntity> =
        withContext(Dispatchers.IO) {
            val rows = entryDao.tagAnalyticsRows(
                since = since,
                windowStart = POST_EVENT_WINDOW_MILLIS - TOLERANCE_MILLIS,
                windowEnd = POST_EVENT_WINDOW_MILLIS + TOLERANCE_MILLIS,
                target = POST_EVENT_WINDOW_MILLIS,
            )

            val accumulators = LinkedHashMap<String, Accumulator>()

            for (row in rows) {
                if (CrisisDetector.isCrisis(row.note)) continue

                val baseline = row.baselineMgdl ?: continue
                val followUp = row.followUpMgdl ?: continue
                val delta = followUp - baseline

                val tags = TagExtractor.extract(
                    note = row.note,
                    mealDescription = row.mealDescription,
                    foodId = row.foodId,
                    foodName = row.foodName,
                    mood = row.moodLabel,
                )

                for (tag in tags) {
                    // Window tags (sleep/cycle) are computed separately from
                    // their intervals; never emit them here or they would
                    // collide on the unique `tag` column with the window path.
                    if (tag.kind.isWindowed) continue

                    val accumulator = accumulators.getOrPut(tag.tag) {
                        Accumulator(kind = tag.kind, foodId = tag.foodId)
                    }
                    accumulator.add(
                        delta = delta,
                        carbsGrams = row.carbsGrams,
                        bolusUnits = row.bolusUnits,
                        timestamp = row.timestamp,
                    )
                }
            }

            accumulators
                .map { (tag, accumulator) -> accumulator.toEntity(tag = tag, computedAt = now) }
                .sortedBy { it.tag }
        }

    /**
     * Builds a [TagAnalyticEntity] for the `SLEEP` and `CYCLE` tags from
     * Health Connect interval entries since [since].
     *
     * Each interval contributes a single delta:
     * - `SLEEP`: the average glucose during the session minus the last reading
     *   within [SLEEP_BASELINE_LOOKBACK_MILLIS] before it.
     * - `CYCLE`: the average glucose during the period minus the average over
     *   [CYCLE_BASELINE_LOOKBACK_MILLIS] before it.
     *
     * Intervals without an end timestamp or without enough surrounding glucose
     * readings are skipped.
     */
    suspend fun computeWindowTagAnalytics(since: Long, now: Long): List<TagAnalyticEntity> =
        withContext(Dispatchers.IO) {
            val events = entryDao.windowedEntriesSince(since)
            if (events.isEmpty()) return@withContext emptyList()

            val points = entryDao.glucoseReadingPointsBetween(
                startMillis = since - WINDOW_BASELINE_MARGIN_MILLIS,
                endMillis = now,
            )
            if (points.isEmpty()) return@withContext emptyList()

            val timestamps = LongArray(points.size) { points[it].timestamp }
            val values = DoubleArray(points.size) { points[it].glucoseMgdl }

            val accumulators = LinkedHashMap<String, Accumulator>()

            for (event in events) {
                val start = event.timestamp
                val end = event.endTime ?: continue
                if (end <= start) continue

                val kind = TagExtractor.extract(
                    note = event.note,
                    mealDescription = null,
                    foodId = null,
                    foodName = null,
                ).firstOrNull { it.kind.isWindowed }?.kind ?: continue

                val (baseline, windowAverage) = when (kind) {
                    TagKind.SLEEP -> {
                        val base = lastReadingBetween(
                            start - SLEEP_BASELINE_LOOKBACK_MILLIS,
                            start,
                            timestamps,
                            values,
                        )
                        val average = averageBetween(start, end, timestamps, values)
                        if (base != null && average != null) base to average else null
                    }

                    TagKind.CYCLE -> {
                        val base = averageBetween(
                            start - CYCLE_BASELINE_LOOKBACK_MILLIS,
                            start,
                            timestamps,
                            values,
                        )
                        val average = averageBetween(start, end, timestamps, values)
                        if (base != null && average != null) base to average else null
                    }

                    else -> null
                } ?: continue

                val tag = if (kind == TagKind.SLEEP) TagExtractor.SLEEP_TAG else TagExtractor.CYCLE_TAG
                val accumulator = accumulators.getOrPut(tag) { Accumulator(kind = kind, foodId = null) }
                accumulator.add(
                    delta = windowAverage - baseline,
                    carbsGrams = null,
                    bolusUnits = null,
                    timestamp = start,
                )
            }

            accumulators
                .map { (tag, accumulator) -> accumulator.toEntity(tag = tag, computedAt = now) }
                .sortedBy { it.tag }
        }

    /**
     * Cached food, hashtag, and description tags that meet display floors,
     * ranked for the Hero system prompt.
     *
     * Ranking is |median 2h delta| descending, then occurrence count, so the
     * handful of rows we spend tokens on are the user's strongest observed
     * patterns rather than n=1 noise. Mood and windowed lifestyle tags are
     * omitted. [excludedTags] is typically the user's dismissed food-impact list.
     */
    suspend fun topFoodPatternsForPrompt(
        limit: Int = PROMPT_FOOD_PATTERN_LIMIT,
        excludedTags: Set<String> = emptySet(),
    ): List<TagAnalyticEntity> = withContext(Dispatchers.IO) {
        selectPromptFoodPatterns(
            tags = tagAnalyticDao.observeAll().first(),
            limit = limit,
            excludedTags = excludedTags,
        )
    }

    private data class Accumulator(
        val kind: TagKind,
        val foodId: Long?,
        val deltas: MutableList<Double> = mutableListOf(),
        val carbsGrams: MutableList<Double> = mutableListOf(),
        val bolusUnits: MutableList<Double> = mutableListOf(),
        var lastSeenAt: Long = Long.MIN_VALUE,
    ) {
        fun add(delta: Double, carbsGrams: Int?, bolusUnits: Double?, timestamp: Long) {
            deltas.add(delta)
            carbsGrams?.let { this.carbsGrams.add(it.toDouble()) }
            bolusUnits?.let { this.bolusUnits.add(it) }
            if (timestamp > lastSeenAt) lastSeenAt = timestamp
        }

        fun toEntity(tag: String, computedAt: Long): TagAnalyticEntity {
            val (p25, median, p75) = Percentiles.quartiles(deltas)
            return TagAnalyticEntity(
                tag = tag,
                kind = kind,
                foodId = foodId,
                occurrences = deltas.size,
                medianDeltaMgdl = median,
                p25DeltaMgdl = p25,
                p75DeltaMgdl = p75,
                avgCarbsGrams = carbsGrams.averageOrNull(),
                avgBolusUnits = bolusUnits.averageOrNull(),
                lastSeenAt = lastSeenAt,
                computedAt = computedAt,
            )
        }
    }

    /** Last glucose reading with timestamp in [start, endExclusive); null if none. */
    private fun lastReadingBetween(
        start: Long,
        endExclusive: Long,
        timestamps: LongArray,
        values: DoubleArray,
    ): Double? {
        val from = lowerBound(timestamps, start)
        val to = upperBound(timestamps, endExclusive) - 1
        return if (to >= from) values[to] else null
    }

    /** Arithmetic mean of glucose readings with timestamp in [start, endExclusive); null if none. */
    private fun averageBetween(
        start: Long,
        endExclusive: Long,
        timestamps: LongArray,
        values: DoubleArray,
    ): Double? {
        val from = lowerBound(timestamps, start)
        val to = upperBound(timestamps, endExclusive)
        if (from >= to) return null
        var sum = 0.0
        for (i in from until to) sum += values[i]
        return sum / (to - from)
    }

    /** First index whose value is >= [key]. */
    private fun lowerBound(a: LongArray, key: Long): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (a[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** First index whose value is > [key]. */
    private fun upperBound(a: LongArray, key: Long): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (a[mid] <= key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private companion object {
        /** Target time-to-peak after a tagged event. */
        const val POST_EVENT_WINDOW_MILLIS = 2 * 60 * 60 * 1000L

        /** ± tolerance for locating the follow-up reading. */
        const val TOLERANCE_MILLIS = 30 * 60 * 1000L

        /** Lookback before a sleep session used to locate its pre-sleep baseline reading. */
        const val SLEEP_BASELINE_LOOKBACK_MILLIS = 3 * 60 * 60 * 1000L

        /** Lookback before a cycle period used to compute its pre-period baseline average. */
        const val CYCLE_BASELINE_LOOKBACK_MILLIS = 7 * 24 * 60 * 60 * 1000L

        /** Extra readings fetched behind the `since` bound so baseline windows near the window edge resolve. */
        const val WINDOW_BASELINE_MARGIN_MILLIS = CYCLE_BASELINE_LOOKBACK_MILLIS

        /** Tags appended to the Hero prompt: enough signal, few enough tokens. */
        const val PROMPT_FOOD_PATTERN_LIMIT = 6
    }
}

/**
 * Filters, ranks, and truncates cached tag analytics for the Hero prompt.
 * Visible for tests.
 */
internal fun selectPromptFoodPatterns(
    tags: List<TagAnalyticEntity>,
    limit: Int,
    excludedTags: Set<String> = emptySet(),
): List<TagAnalyticEntity> =
    tags.asSequence()
        .filter { it.kind != TagKind.MOOD && !it.kind.isWindowed }
        .filter { TagExtractor.meetsOccurrenceThreshold(it.kind, it.occurrences) }
        .filter { it.tag !in excludedTags }
        .sortedWith(
            compareByDescending<TagAnalyticEntity> { abs(it.medianDeltaMgdl) }
                .thenByDescending { it.occurrences },
        )
        .take(limit)
        .toList()

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()