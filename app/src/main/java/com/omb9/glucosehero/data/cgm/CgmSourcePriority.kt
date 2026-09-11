package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource

/**
 * Ranking used when two sources report the same physiological reading inside
 * the collapse window.
 *
 * Why this exists: a user running xDrip+ while Health Connect also mirrors
 * that stream will insert two rows with different `(source, external_id)`
 * pairs. The unique index cannot collapse them. Direct device broadcast is
 * preferred over cloud mirrors, which are preferred over Health Connect
 * copies, which are preferred over one-shot file imports.
 *
 * Assumptions: a higher [rank] always wins, including exact-timestamp ties.
 * Equal rank keeps the sample already stored (or accepted earlier in the
 * same batch).
 *
 * FEATURE: cgm-direct-ingest
 */
object CgmSourcePriority {

    fun rank(source: GlucoseSampleSource): Int = when (source) {
        GlucoseSampleSource.XDRIP_BROADCAST -> 4
        GlucoseSampleSource.NIGHTSCOUT -> 3
        GlucoseSampleSource.LIBRE_LINK_UP -> 2
        GlucoseSampleSource.HEALTH_CONNECT -> 1
        GlucoseSampleSource.MANUAL_IMPORT -> 0
    }

    fun isHigherThan(left: GlucoseSampleSource, right: GlucoseSampleSource): Boolean =
        rank(left) > rank(right)
}
