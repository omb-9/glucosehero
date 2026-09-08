package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.Macros
import java.util.Locale

/**
 * Pure, side-effect-free scaling and parsing for Open Food Facts portions.
 *
 * Values are returned unrounded. Rounding belongs to the UI layer, and the
 * unrounded carbohydrate figure must be what reaches [BolusCalculator].
 */
object PortionCalculator {

    /**
     * A gram weight anywhere in the free-text serving string.
     *
     * Matches `"30 g"`, `"30g"`, and `"30 grams"` (case-insensitive). It does
     * not match `"ml"`, `"mg"`, or `"kg"`, so volume or mass units other than
     * grams are not mistaken for gram weights.
     */
    private val GRAM_WEIGHT = Regex(
        pattern = """((?:\d+(?:\.\d+)?|\.\d+))\s*(?:grams|gram|g)\b""",
        option = RegexOption.IGNORE_CASE,
    )

    /** Scale per-serving macros by a number of servings. */
    fun scaleByServings(base: Macros, servings: Double): Macros {
        if (servings < 0.0) {
            return Macros(
                carbsGrams = 0.0,
                proteinGrams = base.proteinGrams?.let { 0.0 },
                fatGrams = base.fatGrams?.let { 0.0 },
                kcal = base.kcal?.let { 0.0 },
            )
        }
        return Macros(
            carbsGrams = base.carbsGrams * servings,
            proteinGrams = base.proteinGrams?.let { it * servings },
            fatGrams = base.fatGrams?.let { it * servings },
            kcal = base.kcal?.let { it * servings },
        )
    }

    /**
     * Scale base macros to an arbitrary gram weight.
     *
     * Scaling factor is `grams / servingGrams`.
     * If [servingGrams] is unstated, it defaults to 100.0 (per-100g base macros).
     */
    fun scaleByGrams(
        base: Macros,
        grams: Double,
        servingGrams: Double = 100.0,
    ): Macros {
        if (servingGrams <= 0.0 || grams < 0.0) {
            return Macros(
                carbsGrams = 0.0,
                proteinGrams = base.proteinGrams?.let { 0.0 },
                fatGrams = base.fatGrams?.let { 0.0 },
                kcal = base.kcal?.let { 0.0 },
            )
        }
        val factor = grams / servingGrams
        return Macros(
            carbsGrams = base.carbsGrams * factor,
            proteinGrams = base.proteinGrams?.let { it * factor },
            fatGrams = base.fatGrams?.let { it * factor },
            kcal = base.kcal?.let { it * factor },
        )
    }

    /**
     * Parse the free-text `serving_size` field from Open Food Facts.
     *
     * Returns the gram weight, or null when no gram weight can be determined
     * (e.g. "1 cup (240 ml)"). European decimal commas are normalised to
     * periods before parsing, so `"12,5 g"` parses as 12.5 rather than being
     * dropped or truncated.
     */
    fun parseServingGrams(servingSize: String?): Double? {
        if (servingSize.isNullOrBlank()) return null
        val normalized = servingSize.replace(',', '.').trim()
        return GRAM_WEIGHT.find(normalized)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /**
     * Parse the free-text `serving_size` field from Open Food Facts, falling back
     * to [fallbackGrams] (default 100.0g) when no gram weight could be parsed.
     */
    fun parseServingGramsOrDefault(servingSize: String?, fallbackGrams: Double = 100.0): Double =
        parseServingGrams(servingSize) ?: fallbackGrams

    /**
     * Rounds a macro figure once for UI display.
     * Integral values are formatted as integers (e.g. 15.0 -> "15"); non-integral
     * values are rounded to 1 decimal place (e.g. 15.34 -> "15.3").
     */
    fun roundForDisplay(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            "%.1f".format(Locale.US, value)
        }
}
