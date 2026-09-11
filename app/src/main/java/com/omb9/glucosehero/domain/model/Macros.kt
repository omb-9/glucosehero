package com.omb9.glucosehero.domain.model

/**
 * Macro-nutrient totals for a portion of food.
 *
 * [carbsGrams] is always present because meal logging and the glucose forecast
 * need it; protein, fat, and calories are optional because Open Food Facts is
 * crowdsourced and any of those fields may be missing on a given product.
 */
data class Macros(
    val carbsGrams: Double,
    val proteinGrams: Double?,
    val fatGrams: Double?,
    val kcal: Double?,
)
