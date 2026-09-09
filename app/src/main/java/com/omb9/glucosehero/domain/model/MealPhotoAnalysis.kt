package com.omb9.glucosehero.domain.model

import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One visible food (or dish component) estimated from a meal photo.
 *
 * [portionGrams] is the edible weight the model assigned to that item, not
 * the weight of the plate or packaging.
 */
@Serializable
data class MealPhotoPortion(
    val name: String,
    @SerialName("portion_label")
    val portionLabel: String? = null,
    @SerialName("portion_grams")
    val portionGrams: Double? = null,
    @SerialName("carbs_grams")
    val carbsGrams: Double? = null,
    @SerialName("fiber_grams")
    val fiberGrams: Double? = null,
    @SerialName("protein_grams")
    val proteinGrams: Double? = null,
    @SerialName("fat_grams")
    val fatGrams: Double? = null,
)

/**
 * Nutrition estimate returned by Hero AI for a captured meal photo.
 *
 * Totals are the sum across [portions] for the plate as photographed. Fat is
 * estimated because dietary fat delays gastric emptying and carbohydrate
 * absorption, which can shift the glucose peak later (a late spike) rather
 * than lowering the eventual rise. The source photo bytes are never part of
 * this value, so nothing image-related is persisted locally.
 *
 * Legacy keys (`carbs_grams`, `fat_grams`) are accepted so older cached
 * replies still decode.
 */
@Serializable
data class MealPhotoAnalysis(
    val description: String? = null,
    val portions: List<MealPhotoPortion> = emptyList(),
    @SerialName("total_carbs_grams")
    val totalCarbsGrams: Double? = null,
    @SerialName("carbs_grams")
    val carbsGramsLegacy: Double? = null,
    @SerialName("dietary_fiber_grams")
    val dietaryFiberGrams: Double? = null,
    @SerialName("protein_grams")
    val proteinGrams: Double? = null,
    @SerialName("dietary_fat_grams")
    val dietaryFatGrams: Double? = null,
    @SerialName("fat_grams")
    val fatGramsLegacy: Double? = null,
    @SerialName("fat_delays_carb_absorption")
    val fatDelaysCarbAbsorption: Boolean? = null,
    @SerialName("late_spike_note")
    val lateSpikeNote: String? = null,
) {
    val carbsGrams: Double?
        get() = totalCarbsGrams ?: carbsGramsLegacy

    val fatGrams: Double?
        get() = dietaryFatGrams ?: fatGramsLegacy

    val carbsGramsInt: Int?
        get() = carbsGrams?.roundToInt()

    val proteinGramsInt: Int?
        get() = proteinGrams?.roundToInt()

    val fatGramsInt: Int?
        get() = fatGrams?.roundToInt()

    val fiberGramsInt: Int?
        get() = dietaryFiberGrams?.roundToInt()

    val isEmpty: Boolean
        get() = description.isNullOrBlank() &&
            portions.isEmpty() &&
            carbsGrams == null &&
            dietaryFiberGrams == null &&
            proteinGrams == null &&
            fatGrams == null
}
