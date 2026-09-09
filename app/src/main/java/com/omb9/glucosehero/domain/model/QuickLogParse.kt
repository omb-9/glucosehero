package com.omb9.glucosehero.domain.model

import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One food mention extracted from a natural-language or voice quick-log.
 *
 * Macros are for the spoken portion, not a library serving. [portionLabel]
 * keeps the user's wording ("2 eggs", "40 g oatmeal") so it can fill
 * [FoodEntity.servingLabel] / the meal description.
 */
@Serializable
data class QuickLogFood(
    val name: String,
    @SerialName("portion_label")
    val portionLabel: String? = null,
    @SerialName("portion_grams")
    val portionGrams: Double? = null,
    @SerialName("carbs_grams")
    val carbsGrams: Double? = null,
    @SerialName("protein_grams")
    val proteinGrams: Double? = null,
    @SerialName("fat_grams")
    val fatGrams: Double? = null,
)

/**
 * Structured extraction of a spoken or typed log line into the same slots
 * [com.omb9.glucosehero.data.local.entity.EntryEntity] and
 * [com.omb9.glucosehero.data.local.entity.FoodEntity] use.
 *
 * Glucose arrives as a raw value/unit pair, matching [HeroAiPrefill], so the
 * app can canonicalise to mg/dL with the user's display unit as fallback.
 */
@Serializable
data class QuickLogParseResult(
    @SerialName("glucose_value")
    val glucoseValue: Double? = null,
    @SerialName("glucose_unit")
    val glucoseUnit: String? = null,
    @SerialName("insulin_basal_units")
    val insulinBasalUnits: Double? = null,
    @SerialName("insulin_bolus_units")
    val insulinBolusUnits: Double? = null,
    @SerialName("carbs_grams")
    val carbsGrams: Double? = null,
    @SerialName("protein_grams")
    val proteinGrams: Double? = null,
    @SerialName("fat_grams")
    val fatGrams: Double? = null,
    @SerialName("meal_description")
    val mealDescription: String? = null,
    val foods: List<QuickLogFood> = emptyList(),
    @SerialName("exercise_minutes")
    val exerciseMinutes: Int? = null,
    val note: String? = null,
    @SerialName("minutes_ago")
    val minutesAgo: Int? = null,
) {
    val isEmpty: Boolean
        get() = glucoseValue == null &&
            insulinBasalUnits == null &&
            insulinBolusUnits == null &&
            carbsGrams == null &&
            proteinGrams == null &&
            fatGrams == null &&
            mealDescription.isNullOrBlank() &&
            foods.isEmpty() &&
            exerciseMinutes == null &&
            note.isNullOrBlank() &&
            minutesAgo == null

    val carbsGramsInt: Int?
        get() = carbsGrams?.roundToInt()

    val proteinGramsInt: Int?
        get() = proteinGrams?.roundToInt()

    val fatGramsInt: Int?
        get() = fatGrams?.roundToInt()

    fun toHeroAiPrefill(): HeroAiPrefill = HeroAiPrefill(
        insulinBasalUnits = insulinBasalUnits,
        insulinBolusUnits = insulinBolusUnits,
        carbsGrams = carbsGramsInt,
        glucoseValue = glucoseValue,
        glucoseUnit = glucoseUnit,
        mealDescription = mealDescription,
        exerciseMinutes = exerciseMinutes,
    )
}
