package com.omb9.glucosehero.data.cgm.xdrip

import com.omb9.glucosehero.data.cgm.CgmGlucose
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.util.AppJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Maps xDrip+ / AAPS broadcast extras to [GlucoseSampleEntity] rows.
 *
 * Why this exists: extra names and JSON shapes are vendor-defined and have
 * drifted. Parsing stays here so [com.omb9.glucosehero.data.cgm.CgmIngestService]
 * never sees raw Intents, and tests can feed a [XdripBroadcastPayload] without
 * a device.
 *
 * Assumptions:
 * - [XdripBroadcastIntents.EXTRA_BG_ESTIMATE] and AAPS `mgdl` are mg/dL.
 * - Identity is `xdrip:{timestampMillis}` so a repeat of the same reading is
 *   idempotent under `(source, external_id)`.
 * - Out-of-range / non-finite glucose is dropped here via [CgmGlucose].
 * - Missing or non-positive timestamp is dropped.
 * - [GlucoseSampleEntity.hcRecordId] stays null. [source] is always
 *   [GlucoseSampleSource.XDRIP_BROADCAST].
 *
 * FEATURE: cgm-direct-ingest
 */
object XdripBroadcastMapper {

    fun externalId(timestampMillis: Long): String = "xdrip:$timestampMillis"

    fun map(
        payload: XdripBroadcastPayload,
        sourcePackage: String? = null,
    ): List<GlucoseSampleEntity> {
        return when (payload.action) {
            XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE ->
                listOfNotNull(mapBgEstimate(payload.extras, sourcePackage))
            XdripBroadcastIntents.ACTION_NEW_SGV ->
                mapAapsSgvs(payload.extras[XdripBroadcastIntents.EXTRA_SGVS], sourcePackage)
            else -> emptyList()
        }
    }

    private fun mapBgEstimate(
        extras: Map<String, Any?>,
        sourcePackage: String?,
    ): GlucoseSampleEntity? {
        val timestamp = extraEpochMillis(extras[XdripBroadcastIntents.EXTRA_TIMESTAMP])
            ?: return null
        val mgdl = extraDouble(extras[XdripBroadcastIntents.EXTRA_BG_ESTIMATE])
            ?: return null
        return sample(
            timestampMillis = timestamp,
            glucoseMgdl = mgdl,
            trendRaw = extraString(extras[XdripBroadcastIntents.EXTRA_BG_SLOPE_NAME]),
            sourcePackage = sourcePackage,
        )
    }

    private fun mapAapsSgvs(
        raw: Any?,
        sourcePackage: String?,
    ): List<GlucoseSampleEntity> {
        val json = extraString(raw) ?: return emptyList()
        if (json.isEmpty()) return emptyList()
        val root = runCatching { AppJson.parseToJsonElement(json) }.getOrNull()
            ?: return emptyList()
        val array = root as? JsonArray ?: return emptyList()
        if (array.isEmpty()) return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val timestamp = jsonEpochMillis(obj, "mills")
                ?: jsonEpochMillis(obj, "date")
                ?: return@mapNotNull null
            val mgdl = jsonDouble(obj, "mgdl")
                ?: jsonDouble(obj, "sgv")
                ?: return@mapNotNull null
            sample(
                timestampMillis = timestamp,
                glucoseMgdl = mgdl,
                trendRaw = jsonString(obj, "direction"),
                sourcePackage = sourcePackage,
            )
        }
    }

    private fun sample(
        timestampMillis: Long,
        glucoseMgdl: Double,
        trendRaw: String?,
        sourcePackage: String?,
    ): GlucoseSampleEntity? {
        if (timestampMillis <= 0L) return null
        val canonical = CgmGlucose.toCanonicalMgdl(glucoseMgdl, GlucoseUnit.MGDL) ?: return null
        return GlucoseSampleEntity(
            timestamp = timestampMillis,
            glucoseMgdl = canonical,
            source = GlucoseSampleSource.XDRIP_BROADCAST,
            externalId = externalId(timestampMillis),
            hcRecordId = null,
            trendArrow = XdripTrendNames.normalize(trendRaw),
            sourcePackage = sourcePackage,
            recordingMethod = 0,
            importedAt = 0L,
        )
    }

    internal fun extraEpochMillis(raw: Any?): Long? {
        val value = extraLong(raw) ?: return null
        return value.takeIf { it > 0L }
    }

    internal fun extraDouble(raw: Any?): Double? = when (raw) {
        null -> null
        is Double -> raw.takeIf { it.isFinite() }
        is Float -> raw.toDouble().takeIf { it.isFinite() }
        is Number -> raw.toDouble().takeIf { it.isFinite() }
        is String -> raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

    internal fun extraLong(raw: Any?): Long? = when (raw) {
        null -> null
        is Long -> raw
        is Int -> raw.toLong()
        is Double -> raw.takeIf { it.isFinite() }?.toLong()
        is Float -> raw.toDouble().takeIf { it.isFinite() }?.toLong()
        is Number -> raw.toLong()
        is String -> raw.trim().toLongOrNull()
            ?: raw.trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
        else -> null
    }

    internal fun extraString(raw: Any?): String? = when (raw) {
        null -> null
        is String -> raw.trim().takeIf { it.isNotEmpty() }
        is JsonPrimitive -> raw.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun jsonEpochMillis(obj: JsonObject, key: String): Long? {
        val primitive = obj[key] as? JsonPrimitive ?: return null
        val fromLong = primitive.longOrNull
        if (fromLong != null) return fromLong.takeIf { it > 0L }
        val fromDouble = primitive.doubleOrNull?.takeIf { it.isFinite() }?.toLong()
        return fromDouble?.takeIf { it > 0L }
    }

    private fun jsonDouble(obj: JsonObject, key: String): Double? {
        val primitive = obj[key] as? JsonPrimitive ?: return null
        return primitive.doubleOrNull?.takeIf { it.isFinite() }
            ?: primitive.contentOrNull?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun jsonString(obj: JsonObject, key: String): String? {
        val primitive = obj[key] as? JsonPrimitive ?: return null
        return primitive.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }
}
