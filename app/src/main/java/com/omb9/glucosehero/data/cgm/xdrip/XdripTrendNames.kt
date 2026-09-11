package com.omb9.glucosehero.data.cgm.xdrip

/**
 * Normalizes xDrip+ slope names and AAPS [TrendArrow] text to a compact
 * Nightscout-style token for [com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity.trendArrow].
 *
 * xDrip+ [BgReading.slopefromName](https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/models/BgReading.java)
 * accepts DoubleDown, SingleDown, FortyFiveDown, Flat, FortyFiveUp, SingleUp,
 * DoubleUp. Hide-slope broadcasts use `"9"`. Invalid names include
 * NOT_COMPUTABLE / NOT COMPUTABLE / OUT_OF_RANGE / NONE.
 *
 * AAPS [TrendArrow.text](https://github.com/nightscout/AndroidAPS/blob/master/core/data/src/main/kotlin/app/aaps/core/data/model/TrendArrow.kt)
 * adds TripleUp / TripleDown and uses the same PascalCase tokens.
 *
 * FEATURE: cgm-direct-ingest
 */
object XdripTrendNames {

    private val canonical = mapOf(
        "TRIPLEUP" to "TripleUp",
        "DOUBLEUP" to "DoubleUp",
        "SINGLEUP" to "SingleUp",
        "FORTYFIVEUP" to "FortyFiveUp",
        "FLAT" to "Flat",
        "FORTYFIVEDOWN" to "FortyFiveDown",
        "SINGLEDOWN" to "SingleDown",
        "DOUBLEDOWN" to "DoubleDown",
        "TRIPLEDOWN" to "TripleDown",
    )

    private val discarded = setOf(
        "9",
        "NONE",
        "NOTCOMPUTABLE",
        "OUTOFRANGE",
    )

    fun normalize(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed == "9") return null
        val compact = trimmed.replace(" ", "").replace("_", "").uppercase()
        if (compact in discarded) return null
        return canonical[compact]
    }
}
