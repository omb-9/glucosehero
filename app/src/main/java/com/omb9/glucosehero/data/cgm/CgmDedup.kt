package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import kotlin.math.abs

/**
 * Identity used by [CgmDedup] so collapse can be unit-tested without Room.
 *
 * FEATURE: cgm-direct-ingest
 */
data class CgmDedupSample(
    val source: GlucoseSampleSource,
    val externalId: String,
    val timestamp: Long,
)

/**
 * Write-time plan produced by [CgmDedup.plan].
 *
 * [accept] is inserted via `upsertAll`. [delete] is removed first via
 * `deleteByExternalId` so a higher-priority source can replace a lower one.
 *
 * FEATURE: cgm-direct-ingest
 */
data class CgmDedupPlan(
    val accept: List<CgmDedupSample>,
    val delete: List<CgmDedupSample>,
)

/**
 * Cross-source collapse for CGM ingest.
 *
 * Why this exists: `(source, external_id)` uniqueness does not catch the
 * same physiological reading arriving from xDrip+ and Health Connect a
 * few seconds apart. Collapse happens at write time in [CgmIngestService]
 * so the `glucose_readings` view can stay a cheap UNION.
 *
 * Assumptions:
 * - Two samples match when `|t1 - t2| <= windowMillis` (inclusive).
 * - [CgmSourcePriority] decides the winner. Exact-timestamp ties: higher
 *   rank wins. Equal rank: the existing (or earlier-accepted) sample wins.
 * - Same `(source, external_id)` is idempotent: the incoming row is skipped
 *   and the stored row is left alone.
 * - Samples farther apart than the window are both kept.
 * - Incoming is ranked highest-first so a mixed batch collapses without
 *   depending on caller order.
 *
 * FEATURE: cgm-direct-ingest
 */
object CgmDedup {

    fun plan(
        incoming: List<CgmDedupSample>,
        existing: List<CgmDedupSample>,
        windowMillis: Long,
    ): CgmDedupPlan {
        if (incoming.isEmpty()) return CgmDedupPlan(accept = emptyList(), delete = emptyList())

        val window = windowMillis.coerceAtLeast(0L)
        val existingKeys = existing.map { it.key() }.toSet()
        val live = existing.toMutableList()
        val accept = ArrayList<CgmDedupSample>()
        val delete = ArrayList<CgmDedupSample>()

        val ordered = incoming
            .distinctBy { it.key() }
            .sortedWith(
                compareByDescending<CgmDedupSample> { CgmSourcePriority.rank(it.source) }
                    .thenBy { it.timestamp }
                    .thenBy { it.externalId },
            )

        for (sample in ordered) {
            val sameId = live.any { it.key() == sample.key() }
            if (sameId) continue

            val overlapping = live.filter { abs(it.timestamp - sample.timestamp) <= window }
            val blocked = overlapping.any {
                CgmSourcePriority.rank(it.source) >= CgmSourcePriority.rank(sample.source)
            }
            if (blocked) continue

            val lower = overlapping.filter {
                CgmSourcePriority.isHigherThan(sample.source, it.source)
            }
            for (row in lower) {
                live.removeAll { it.key() == row.key() }
                if (row.key() in existingKeys) {
                    delete += row
                }
            }
            live += sample
            accept += sample
        }

        return CgmDedupPlan(
            accept = accept,
            delete = delete.distinctBy { it.key() },
        )
    }

    private fun CgmDedupSample.key(): Pair<GlucoseSampleSource, String> = source to externalId
}
