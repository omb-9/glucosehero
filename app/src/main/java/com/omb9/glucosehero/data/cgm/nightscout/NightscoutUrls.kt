package com.omb9.glucosehero.data.cgm.nightscout

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Builds the Nightscout v1 SGV entries URL from a user-typed base.
 *
 * Why this exists: people paste `https://host`, `https://host/`, or a path
 * that already includes `/api/v1`. The poller always targets
 * `GET /api/v1/entries/sgv.json`. API v3 is not implemented.
 *
 * Assumptions: subdirectory installs (`https://host/ns`) keep their prefix.
 * Query parameters on the typed URL are dropped so a pasted `token=` is not
 * mixed with the Keystore-backed credential.
 *
 * FEATURE: cgm-direct-ingest
 */
object NightscoutUrls {

    const val V1_ENTRIES_PATH = "api/v1/entries/sgv.json"
    const val QUERY_FIND_DATE_GT = "find[date][\$gt]"
    const val QUERY_COUNT = "count"

    fun parseBase(raw: String): HttpUrl? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val withScheme = if (':' in trimmed) trimmed else "https://$trimmed"
        return withScheme.toHttpUrlOrNull()
    }

    fun entriesUrl(base: HttpUrl): HttpUrl {
        var path = base.encodedPath.trimEnd('/')
        path = path.removeSuffix("/api/v3/entries").removeSuffix("/api/v3")
        path = path.removeSuffix("/api/v1/entries/sgv.json")
            .removeSuffix("/api/v1/entries")
            .removeSuffix("/api/v1")
        val prefix = if (path == "" || path == "/") "" else path
        return base.newBuilder()
            .encodedPath("$prefix/$V1_ENTRIES_PATH")
            .query(null)
            .fragment(null)
            .build()
    }

    fun requestUrl(
        base: HttpUrl,
        sinceMillis: Long,
        count: Int,
    ): HttpUrl.Builder = entriesUrl(base).newBuilder()
        .setQueryParameter(QUERY_FIND_DATE_GT, sinceMillis.toString())
        .setQueryParameter(QUERY_COUNT, count.coerceIn(1, NightscoutLimits.MAX_ENTRY_COUNT).toString())
}
