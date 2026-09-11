package com.omb9.glucosehero.data.cgm.nightscout

import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.util.AppJson
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Maps Nightscout v1 `sgv.json` payloads into [GlucoseSampleEntity] rows.
 *
 * Why this exists: Nightscout speaks mg/dL (`sgv`), a vendor `_id`, and a
 * `direction` string. Storage is canonical mg/dL with `trend_arrow` copied
 * from `direction` and `external_id` from `_id`. Range gating happens later
 * in [com.omb9.glucosehero.data.cgm.CgmIngestService].
 *
 * Assumptions:
 * - A well-formed payload is a JSON array. An empty array is success with
 *   no rows. A non-array body is a parse failure (do not treat `{}` as
 *   empty: that is what a v3 envelope looks like).
 * - Individual objects that lack `_id`, `sgv`, or a millisecond timestamp
 *   are skipped. One bad row does not fail the batch.
 * - Glucose values are never logged.
 *
 * FEATURE: cgm-direct-ingest
 */
object NightscoutMapper {

    fun parseEntries(body: String): List<GlucoseSampleEntity> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) throw NightscoutParseException()
        val element = try {
            AppJson.parseToJsonElement(trimmed)
        } catch (_: SerializationException) {
            throw NightscoutParseException()
        } catch (_: IllegalArgumentException) {
            throw NightscoutParseException()
        }
        if (element !is JsonArray) throw NightscoutParseException()
        val dtos = try {
            AppJson.decodeFromJsonElement<List<NightscoutSgvDto>>(element)
        } catch (_: SerializationException) {
            throw NightscoutParseException()
        }
        return dtos.mapNotNull { it.toSample() }
    }

    internal fun NightscoutSgvDto.toSample(): GlucoseSampleEntity? {
        val externalId = id?.trim().orEmpty()
        if (externalId.isEmpty()) return null
        val mgdl = sgv ?: return null
        if (!mgdl.isFinite()) return null
        val timestamp = date ?: mills ?: return null
        if (timestamp <= 0L) return null
        val trend = direction?.trim()?.takeIf { it.isNotEmpty() }
        val pkg = device?.trim()?.takeIf { it.isNotEmpty() }
        return GlucoseSampleEntity(
            timestamp = timestamp,
            glucoseMgdl = mgdl,
            source = GlucoseSampleSource.NIGHTSCOUT,
            externalId = externalId,
            hcRecordId = null,
            trendArrow = trend,
            sourcePackage = pkg,
            recordingMethod = 0,
            importedAt = 0L,
        )
    }
}
