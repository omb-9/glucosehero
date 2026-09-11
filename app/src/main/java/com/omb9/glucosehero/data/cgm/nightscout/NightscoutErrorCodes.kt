package com.omb9.glucosehero.data.cgm.nightscout

/**
 * Stable last-error tokens stored in DataStore for Nightscout.
 *
 * Why this exists: Settings must show a plain-language sentence without
 * persisting URLs (which can carry `token=`) or HTTP bodies (which contain
 * glucose).
 *
 * FEATURE: cgm-direct-ingest
 */
object NightscoutErrorCodes {
    const val CLEARTEXT = "cleartext"
    const val HOST_UNACKNOWLEDGED = "host_unacknowledged"
    const val AUTH = "auth"
    const val V1_ONLY = "v1_only"
    const val NETWORK = "network"
    const val HTTP = "http"
    const val INVALID_URL = "invalid_url"
    const val MISSING_CREDENTIAL = "missing_credential"
    const val MISSING_URL = "missing_url"
    const val PARSE = "parse"
}
