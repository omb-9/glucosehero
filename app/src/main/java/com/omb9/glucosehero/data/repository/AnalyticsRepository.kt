package com.omb9.glucosehero.data.repository

import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.entity.TagAnalyticEntity
import com.omb9.glucosehero.domain.model.TagKind
import com.omb9.glucosehero.util.Percentiles
import com.omb9.glucosehero.util.TagExtractor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Computes per-tag glucose analytics from logged entries.
 *
 * The "glucose delta" for a tagged event is the event's own baseline glucose
 * reading subtracted from the glucose reading found approximately 2 hours
 * after its timestamp. Follow-up readings are resolved by a single correlated
 * subquery in [EntryDao], so the cost is one database round trip rather than
 * one per entry. Aggregation (median/quartiles rather than a mean) happens in
 * Kotlin on [Dispatchers.IO] so the main thread is never blocked.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val entryDao: EntryDao,
) {

    /**
     * Builds a [TagAnalyticEntity] for every tag seen on a qualifying entry
     * since [since].
     *
     * [now] is stamped into each row as `computedAt`; it should match the
     * caller's notion of "the current run" so a nightly refresh is internally
     * consistent.
     *
     * Entries whose follow-up reading is missing are dropped before delta
     * grouping, so a tag only appears once at least one of its entries yields
     * a computable delta. Every such tag is emitted regardless of how many
     * occurrences it has.
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
                val baseline = row.baselineMgdl ?: continue
                val followUp = row.followUpMgdl ?: continue
                val delta = followUp - baseline

                val tags = TagExtractor.extract(
                    note = row.note,
                    mealDescription = row.mealDescription,
                    foodId = row.foodId,
                    foodName = row.foodName,
                )

                for (tag in tags) {
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

    private companion object {
        /** Target time-to-peak after a tagged event. */
        const val POST_EVENT_WINDOW_MILLIS = 2 * 60 * 60 * 1000L

        /** ± tolerance for locating the follow-up reading. */
        const val TOLERANCE_MILLIS = 30 * 60 * 1000L
    }
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
