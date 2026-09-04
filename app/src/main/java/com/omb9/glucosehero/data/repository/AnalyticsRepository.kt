package com.omb9.glucosehero.data.repository

import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.domain.model.TagAnalytic
import com.omb9.glucosehero.util.HashtagExtractor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Computes hashtag-level analytics from logged entries.
 *
 * The "glucose delta" for a tagged event is the event's own baseline glucose
 * reading subtracted from the glucose reading found approximately 2 hours
 * after its timestamp. Grouping and averaging happen in Kotlin on
 * [Dispatchers.IO] so the main thread is never blocked by the heavy
 * aggregation.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val entryDao: EntryDao,
) {

    /**
     * Returns per-hashtag analytics for every entry whose note carries a tag.
     *
     * For each tagged event, [HashtagExtractor] maps the note to its set of
     * hashtags; the event's baseline glucose is paired with the glucose
     * reading nearest to `timestamp + 2h` (±30 min) to form a delta, and
     * deltas are averaged per tag.
     */
    suspend fun tagAnalytics(): List<TagAnalytic> = withContext(Dispatchers.IO) {
        val tagged = entryDao.taggedEntries()

        // tag -> accumulated deltas (and count is tracked separately below).
        val deltas = HashMap<String, MutableList<Float>>()
        val counts = HashMap<String, Int>()

        for (entry in tagged) {
            val tags = HashtagExtractor.extract(entry.note)
            if (tags.isEmpty()) continue

            val delta = computeDelta(entry) ?: continue

            for (tag in tags) {
                deltas.getOrPut(tag) { mutableListOf() }.add(delta)
                counts[tag] = (counts[tag] ?: 0) + 1
            }
        }

        // Build results in a stable order (alphabetical) and include tags that
        // appeared but had no computable delta (count only, delta = 0).
        val allTags = deltas.keys + counts.keys
        allTags.sorted().map { tag ->
            val values = deltas[tag].orEmpty()
            TagAnalytic(
                tag = tag,
                count = counts[tag] ?: 0,
                averageDelta = if (values.isEmpty()) 0f else values.average().toFloat(),
            )
        }
    }

    /**
     * Glucose delta for a single tagged event: the glucose reading nearest to
     * `timestamp + 2h` (within ±30 min) minus the event's baseline glucose.
     * Returns null when the event has no baseline or no follow-up reading.
     */
    private suspend fun computeDelta(entry: EntryEntity): Float? {
        val baseline = entry.glucoseMgdl ?: return null
        val target = entry.timestamp + POST_EVENT_WINDOW_MILLIS
        val followUp = entryDao.glucoseReadingNearestTo(
            startMillis = target - TOLERANCE_MILLIS,
            endMillis = target + TOLERANCE_MILLIS,
            targetMillis = target,
        ) ?: return null
        val followUpGlucose = followUp.glucoseMgdl ?: return null

        return (followUpGlucose - baseline).toFloat()
    }

    private companion object {
        /** Target time-to-peak after a tagged event. */
        const val POST_EVENT_WINDOW_MILLIS = 2 * 60 * 60 * 1000L

        /** ± tolerance for locating the follow-up reading. */
        const val TOLERANCE_MILLIS = 30 * 60 * 1000L
    }
}
