package com.omb9.glucosehero.data.cgm

import androidx.compose.runtime.Immutable
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource

/**
 * Snapshot of CGM direct-ingest settings.
 *
 * Why this exists: Phase 2–5 need a single read of opt-in flags, the
 * cross-source collapse window, and last success/error per source without
 * collecting a dozen DataStore flows. Sources default **off**.
 *
 * Assumptions: last-error strings never contain credentials (callers store
 * a short class name or HTTP status, not tokens). Nightscout / Libre
 * secrets are not fields here; those belong in KeystoreManager in Phase 3/4.
 *
 * FEATURE: cgm-direct-ingest
 */
@Immutable
data class CgmIngestSettings(
    val dedupWindowMillis: Long = CgmGlucose.DEFAULT_DEDUP_WINDOW_MILLIS,
    val nightscoutEnabled: Boolean = false,
    val xdripBroadcastEnabled: Boolean = false,
    val libreLinkUpEnabled: Boolean = false,
    val nightscoutLastSuccessMillis: Long? = null,
    val xdripBroadcastLastSuccessMillis: Long? = null,
    val libreLinkUpLastSuccessMillis: Long? = null,
    val healthConnectLastSuccessMillis: Long? = null,
    val manualImportLastSuccessMillis: Long? = null,
    val nightscoutLastError: String? = null,
    val xdripBroadcastLastError: String? = null,
    val libreLinkUpLastError: String? = null,
    val healthConnectLastError: String? = null,
    val manualImportLastError: String? = null,
) {
    fun isEnabled(source: GlucoseSampleSource): Boolean = when (source) {
        GlucoseSampleSource.NIGHTSCOUT -> nightscoutEnabled
        GlucoseSampleSource.XDRIP_BROADCAST -> xdripBroadcastEnabled
        GlucoseSampleSource.LIBRE_LINK_UP -> libreLinkUpEnabled
        GlucoseSampleSource.HEALTH_CONNECT,
        GlucoseSampleSource.MANUAL_IMPORT,
        -> false
    }

    fun lastSuccessMillis(source: GlucoseSampleSource): Long? = when (source) {
        GlucoseSampleSource.NIGHTSCOUT -> nightscoutLastSuccessMillis
        GlucoseSampleSource.XDRIP_BROADCAST -> xdripBroadcastLastSuccessMillis
        GlucoseSampleSource.LIBRE_LINK_UP -> libreLinkUpLastSuccessMillis
        GlucoseSampleSource.HEALTH_CONNECT -> healthConnectLastSuccessMillis
        GlucoseSampleSource.MANUAL_IMPORT -> manualImportLastSuccessMillis
    }

    fun lastError(source: GlucoseSampleSource): String? = when (source) {
        GlucoseSampleSource.NIGHTSCOUT -> nightscoutLastError
        GlucoseSampleSource.XDRIP_BROADCAST -> xdripBroadcastLastError
        GlucoseSampleSource.LIBRE_LINK_UP -> libreLinkUpLastError
        GlucoseSampleSource.HEALTH_CONNECT -> healthConnectLastError
        GlucoseSampleSource.MANUAL_IMPORT -> manualImportLastError
    }
}
