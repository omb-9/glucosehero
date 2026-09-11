package com.omb9.glucosehero.ui.settings

import androidx.annotation.StringRes
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutErrorCodes
import com.omb9.glucosehero.data.health.HealthConnectStatus
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource

/**
 * Plain-language copy helpers for the Data Sources hub.
 *
 * Why a pure object: the hub must map stored last-error tokens (never URLs
 * or glucose) onto string resources, and classify xDrip / Health Connect
 * connection tests, without Compose. Unit tests cover the mapping.
 *
 * Assumptions:
 * - Nightscout tokens match [NightscoutErrorCodes] (including `v1_only` on
 *   a 404 of `/api/v1/entries/sgv.json`).
 * - xDrip is local push, so a "test" is last-broadcast / waiting, not HTTP.
 * - Last successful poll is not [com.omb9.glucosehero.data.cgm.GlucoseFreshness].
 *
 * FEATURE: cgm-direct-ingest
 */
object DataSourcesCopy {

    const val SAMPLE_WINDOW_MILLIS: Long = 24L * 60L * 60L * 1000L

    const val HC_UNAVAILABLE: String = "hc_unavailable"
    const val HC_UPDATE: String = "hc_update"
    const val HC_NOT_CONNECTED: String = "hc_not_connected"
    const val HC_REVOKED: String = "hc_revoked"

    fun sampleWindowStart(nowMillis: Long): Long = nowMillis - SAMPLE_WINDOW_MILLIS

    /**
     * Rows whose timestamp is at least [sinceMillis], scoped to [source].
     * Mirrors [com.omb9.glucosehero.data.local.db.GlucoseSampleDao.countSince].
     */
    fun countSamplesSince(
        rows: List<Pair<GlucoseSampleSource, Long>>,
        source: GlucoseSampleSource,
        sinceMillis: Long,
    ): Int = rows.count { it.first == source && it.second >= sinceMillis }

    @StringRes
    fun nightscoutErrorStringRes(code: String): Int = when (code) {
        NightscoutErrorCodes.CLEARTEXT -> R.string.nightscout_cleartext_refused
        NightscoutErrorCodes.HOST_UNACKNOWLEDGED -> R.string.nightscout_error_host
        NightscoutErrorCodes.AUTH -> R.string.nightscout_error_auth
        NightscoutErrorCodes.V1_ONLY -> R.string.nightscout_error_v1_only
        NightscoutErrorCodes.NETWORK -> R.string.nightscout_error_network
        NightscoutErrorCodes.HTTP -> R.string.nightscout_error_http
        NightscoutErrorCodes.INVALID_URL -> R.string.nightscout_url_invalid
        NightscoutErrorCodes.MISSING_CREDENTIAL -> R.string.nightscout_error_credential
        NightscoutErrorCodes.MISSING_URL -> R.string.nightscout_error_url
        NightscoutErrorCodes.PARSE -> R.string.nightscout_error_parse
        else -> R.string.nightscout_error_generic
    }

    /**
     * Persistent last-error token → string resource. Null when there is
     * nothing to show. Never pass a URL or credential as [code].
     */
    @StringRes
    fun errorMessageRes(source: GlucoseSampleSource, code: String?): Int? {
        if (code.isNullOrBlank()) return null
        return when (source) {
            GlucoseSampleSource.NIGHTSCOUT -> nightscoutErrorStringRes(code)
            GlucoseSampleSource.XDRIP_BROADCAST -> R.string.data_sources_xdrip_error_generic
            GlucoseSampleSource.HEALTH_CONNECT -> when (code) {
                HC_UNAVAILABLE -> R.string.data_sources_hc_unavailable
                HC_UPDATE -> R.string.data_sources_hc_update
                HC_NOT_CONNECTED -> R.string.data_sources_hc_not_connected
                HC_REVOKED -> R.string.data_sources_hc_revoked
                else -> R.string.data_sources_hc_error_generic
            }
            GlucoseSampleSource.LIBRE_LINK_UP,
            GlucoseSampleSource.MANUAL_IMPORT,
            -> null
        }
    }

    /**
     * Health Connect status that should show as an error on the hub. "Not
     * connected yet" is setup state, not a failure, so it stays null unless
     * permissions were revoked after a previous grant.
     */
    fun healthConnectErrorCode(
        status: HealthConnectStatus,
        revoked: Boolean,
        lastError: String?,
    ): String? {
        if (!lastError.isNullOrBlank()) return lastError
        return when {
            status == HealthConnectStatus.UNAVAILABLE -> HC_UNAVAILABLE
            status == HealthConnectStatus.UPDATE_REQUIRED -> HC_UPDATE
            revoked -> HC_REVOKED
            else -> null
        }
    }

    fun classifyXdripListen(
        enabled: Boolean,
        allowlistedInstalled: Boolean,
        lastSuccessMillis: Long?,
    ): XdripListenResult {
        if (!enabled) return XdripListenResult.Disabled
        if (lastSuccessMillis != null) return XdripListenResult.Received(lastSuccessMillis)
        if (!allowlistedInstalled) return XdripListenResult.NoAllowlistedApp
        return XdripListenResult.Waiting
    }
}

/**
 * Real result of the xDrip hub test: last local broadcast, or waiting.
 *
 * FEATURE: cgm-direct-ingest
 */
sealed class XdripListenResult {
    data object Disabled : XdripListenResult()
    data object NoAllowlistedApp : XdripListenResult()
    data object Waiting : XdripListenResult()
    data class Received(val lastSuccessMillis: Long) : XdripListenResult()
}

/**
 * Ephemeral connection-test state for hub buttons that are not Nightscout.
 *
 * FEATURE: cgm-direct-ingest
 */
sealed interface DataSourceTestUiState {
    data object Idle : DataSourceTestUiState
    data object Running : DataSourceTestUiState
    data class Success(val messageRes: Int) : DataSourceTestUiState
    data class Failure(val messageRes: Int) : DataSourceTestUiState
}
