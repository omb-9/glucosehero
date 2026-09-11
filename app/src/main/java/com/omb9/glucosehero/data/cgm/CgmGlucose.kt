package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.domain.model.GlucoseUnit
import kotlin.math.round

/**
 * Canonical mg/dL conversion and physiological range gate used by
 * [CgmIngestService].
 *
 * Why this exists: Nightscout, xDrip+, LibreLinkUp, and file imports each
 * speak a different unit (or none). Storage is always mg/dL, matching
 * [com.omb9.glucosehero.data.local.entity.EntryEntity]. Conversion belongs in
 * the ingest funnel, not at chart/display edges.
 *
 * Assumptions:
 * - mmol/L uses [GlucoseUnit.MGDL_PER_MMOL] (18.0182) and is rounded to a
 *   whole mg/dL, matching [com.omb9.glucosehero.domain.model.HeroAiPrefill].
 * - Bounds are 20–600 mg/dL, the same range [HeroAiPrefill] accepts for a
 *   typed glucose. Values outside that window are discarded, not coerced, so
 *   a mmol reading mis-tagged as mg/dL is not stored as 20.
 * - Non-finite values are discarded.
 *
 * FEATURE: cgm-direct-ingest
 */
object CgmGlucose {

    /** Inclusive lower bound, matching Hero AI prefill. */
    const val MIN_MGDL: Double = 20.0

    /** Inclusive upper bound, matching Hero AI prefill. Typical CGM range. */
    const val MAX_MGDL: Double = 600.0

    /**
     * Default cross-source collapse window: 150 seconds, half of a common
     * 5-minute CGM interval.
     */
    const val DEFAULT_DEDUP_WINDOW_MILLIS: Long = 150_000L

    /**
     * Upper clamp for the configurable dedup window so a bogus setting
     * cannot collapse an entire day of distinct readings.
     */
    const val MAX_DEDUP_WINDOW_MILLIS: Long = 600_000L

    /**
     * Converts [value] in [unit] to canonical mg/dL, or null when the result
     * is non-finite or outside [MIN_MGDL]..[MAX_MGDL].
     */
    fun toCanonicalMgdl(value: Double, unit: GlucoseUnit): Double? {
        if (!value.isFinite()) return null
        val mgdl = when (unit) {
            GlucoseUnit.MGDL -> value
            GlucoseUnit.MMOL -> round(value * GlucoseUnit.MGDL_PER_MMOL)
        }
        return mgdl.takeIf { it in MIN_MGDL..MAX_MGDL }
    }

    /**
     * Returns a copy of [sample] with glucose in mg/dL, or null when the
     * identity is unusable or the value fails the range gate.
     *
     * [importedAt] is applied when the incoming row still has the default 0.
     */
    fun canonicalize(
        sample: GlucoseSampleEntity,
        unit: GlucoseUnit = GlucoseUnit.MGDL,
        importedAt: Long = sample.importedAt,
    ): GlucoseSampleEntity? {
        val externalId = sample.externalId.trim()
        if (externalId.isEmpty()) return null
        if (sample.timestamp <= 0L) return null
        val mgdl = toCanonicalMgdl(sample.glucoseMgdl, unit) ?: return null
        val stamped = if (sample.importedAt == 0L) importedAt else sample.importedAt
        return sample.copy(
            glucoseMgdl = mgdl,
            externalId = externalId,
            importedAt = stamped,
        )
    }
}
