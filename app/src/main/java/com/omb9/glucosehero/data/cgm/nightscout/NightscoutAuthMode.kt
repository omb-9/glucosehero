package com.omb9.glucosehero.data.cgm.nightscout

/**
 * How GlucoseHero authenticates to a user-supplied Nightscout instance.
 *
 * Token query-parameter auth is preferred: Nightscout access tokens are
 * scoped and revocable. The legacy `API_SECRET` path hashes the secret with
 * SHA-1 and sends hex in the `api-secret` header, which is what Nightscout
 * v1 expects (the plaintext secret never goes on the wire).
 *
 * FEATURE: cgm-direct-ingest
 */
enum class NightscoutAuthMode {
    TOKEN,
    API_SECRET,
}
