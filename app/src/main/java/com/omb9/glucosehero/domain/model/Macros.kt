package com.omb9.glucosehero.domain.model

/**
 * Macro-nutrient totals for a portion of food.
 *
 * [carbsGrams] is always present because it feeds [com.omb9.glucosehero.util.BolusCalculator]
 * directly; protein, fat, and calories are optional because Open Food Facts is
 * crowdsourced and any of those fields may be missing on a given product.
 */
data class Macros(
    val carbsGrams: Double,
    val proteinGrams: Double?,
    val fatGrams: Double?,
    val kcal: Double?,
)
