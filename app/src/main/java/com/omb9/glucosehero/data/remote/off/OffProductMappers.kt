package com.omb9.glucosehero.data.remote.off

import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.util.PortionCalculator

/** True when OFF supplied any carbohydrate figure (per-serving or per-100g). */
val OffProduct.hasCarbs: Boolean
    get() = nutriments?.carbsServing != null || nutriments?.carbs100g != null

fun OffProduct.scaledCarbs(): Double? = scaled(nutriments?.carbsServing, nutriments?.carbs100g)
fun OffProduct.scaledProtein(): Double? = scaled(nutriments?.proteinServing, nutriments?.protein100g)
fun OffProduct.scaledFat(): Double? = scaled(nutriments?.fatServing, nutriments?.fat100g)
fun OffProduct.scaledKcal(): Double? = scaled(nutriments?.kcalServing, nutriments?.kcal100g)

fun OffProduct.resolvedServingGrams(): Double? = PortionCalculator.parseServingGrams(servingSize)

fun OffProduct.resolvedServingLabel(): String {
    val grams = resolvedServingGrams()
    return servingSize?.trim()?.takeIf { it.isNotBlank() }
        ?: if (grams != null) "${formatNumber(grams)} g" else "100 g"
}

/** Maps an OFF product into the local foods table for indefinite Room caching. */
fun OffProduct.toFoodEntity(barcode: String, now: Long = System.currentTimeMillis()): FoodEntity {
    val servingGrams = resolvedServingGrams()
    return FoodEntity(
        name = productName?.trim().orEmpty().ifBlank { "Unknown product" },
        brand = brands?.trim()?.takeIf { it.isNotBlank() },
        barcode = barcode.trim(),
        carbsGrams = scaledCarbs() ?: FoodEntity.CARBS_MISSING,
        proteinGrams = scaledProtein(),
        fatGrams = scaledFat(),
        kcal = scaledKcal(),
        servingGrams = servingGrams,
        servingLabel = servingSize?.trim()?.takeIf { it.isNotBlank() }
            ?: servingGrams?.let { "${formatNumber(it)} g" },
        source = FoodSource.OPEN_FOOD_FACTS,
        offFetchedAt = now,
        userCorrected = false,
        createdAt = now,
    )
}

/**
 * Prefer the per-serving value; fall back to scaling the per-100g value by the
 * parsed serving weight, and finally to the raw per-100g value when no serving
 * weight can be determined.
 */
private fun OffProduct.scaled(serving: Double?, per100: Double?): Double? =
    serving
        ?: if (per100 != null) {
            val grams = resolvedServingGrams()
            if (grams != null) per100 * grams / 100.0 else per100
        } else {
            null
        }

private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
