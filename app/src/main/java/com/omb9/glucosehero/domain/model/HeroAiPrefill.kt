package com.omb9.glucosehero.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured payload the Hero AI returns when it invokes the
 * `prefill_log_draft` function. Every field is optional so the model can
 * extract exactly the metrics the user mentioned and leave the rest blank.
 *
 * Glucose is always received canonically in mg/dL regardless of the user's
 * display unit, matching the canonical storage convention of [LogEvent].
 */
@Serializable
data class HeroAiPrefill(
    @SerialName("insulin_basal_units")
    val insulinBasalUnits: Double? = null,
    @SerialName("insulin_bolus_units")
    val insulinBolusUnits: Double? = null,
    @SerialName("carbs_grams")
    val carbsGrams: Int? = null,
    @SerialName("glucose_mgdl")
    val glucoseMgdl: Int? = null,
    @SerialName("meal_description")
    val mealDescription: String? = null,
    @SerialName("exercise_minutes")
    val exerciseMinutes: Int? = null,
) {
    val isEmpty: Boolean
        get() = insulinBasalUnits == null && insulinBolusUnits == null &&
            carbsGrams == null && glucoseMgdl == null &&
            mealDescription.isNullOrBlank() && exerciseMinutes == null
}
