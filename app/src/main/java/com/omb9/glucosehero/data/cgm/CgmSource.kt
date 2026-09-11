package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource

/**
 * One CGM vendor/path that can supply [GlucoseSampleEntity] rows.
 *
 * Why this exists: Health Connect, Nightscout, xDrip+ broadcasts, and
 * LibreLinkUp all produce the same physiological stream. Mapping stays in
 * each source; persistence, unit conversion, clamping, cross-source
 * collapse, and post-write fan-out live in [CgmIngestService] so later
 * phases do not fork write paths.
 *
 * Assumptions:
 * - Every source is **opt-in and default off**. [CgmIngestService.pull] and
 *   Phase 2–4 callers must check the per-source SettingsDataStore flags
 *   before fetching or registering a receiver.
 * - Pull sources override [fetch]. Push/broadcast sources override
 *   [registerPush] / [unregisterPush] and deliver into
 *   [CgmIngestService.ingest] (typically via [CgmIngestService.bindPush]).
 * - [fetch] should return samples with `timestamp > since`, where [since] is
 *   the newest local row for this [key] (0 if none).
 * - Canonical storage is mg/dL. Sources may pass mmol/L in
 *   [GlucoseSampleEntity.glucoseMgdl] only when they also tell ingest the
 *   unit; the funnel converts and range-gates.
 * - Credentials never appear on this type. Nightscout tokens and Libre
 *   passwords belong in KeystoreManager (Phase 3/4), not here.
 *
 * Implementations must live in this package (sealed). Phase 2 adds xDrip;
 * Phase 3 adds Nightscout. Do not add network clients or receivers here in
 * Phase 1.
 *
 * FEATURE: cgm-direct-ingest
 */
sealed interface CgmSource {
    val key: GlucoseSampleSource

    /**
     * Incremental pull. Broadcast sources leave the default empty list.
     *
     * @param since epoch millis of the newest stored sample for [key], or 0
     */
    suspend fun fetch(since: Long): List<GlucoseSampleEntity> = emptyList()

    /**
     * Register a broadcast/push listener that delivers mapped samples to
     * [sink]. Pull sources leave the default no-op.
     */
    fun registerPush(sink: CgmPushSink) {}

    fun unregisterPush() {}
}

/**
 * Callback a [CgmSource] push path uses to hand samples to [CgmIngestService].
 *
 * FEATURE: cgm-direct-ingest
 */
fun interface CgmPushSink {
    suspend fun accept(samples: List<GlucoseSampleEntity>)
}
