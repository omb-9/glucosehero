package com.omb9.glucosehero.data.cgm

/**
 * Opt-in error surface for [CgmIngestService.pull].
 *
 * Why this exists: Nightscout URLs can embed an access token as a query
 * parameter, so [Exception.message] must never be persisted. A short code is
 * enough for Settings to show a plain-language sentence.
 *
 * Assumptions: [publicErrorCode] is a stable token such as `cleartext`, never
 * a host, URL, glucose value, or credential.
 *
 * FEATURE: cgm-direct-ingest
 */
interface CgmPublicError {
    val publicErrorCode: String
}
