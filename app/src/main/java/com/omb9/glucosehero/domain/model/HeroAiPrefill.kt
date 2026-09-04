package com.omb9.glucosehero.domain.model

import kotlin.math.round
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Structured payload the Hero AI returns when it invokes the
 * `prefill_log_draft` function. Every field is optional so the model can
 * extract exactly the metrics the user mentioned and leave the rest blank.
 *
 * Glucose arrives as a raw `glucose_value` / `glucose_unit` pair — the value
 * exactly as the user stated it, in whatever unit they spoke in. Call
 * [toCanonical] before handing the payload to the rest of the app: it
 * resolves the pair (or a legacy `glucose_mgdl` value from older cached
 * conversations) into canonical mg/dL, matching [LogEvent].
 */
@Serializable
data class HeroAiPrefill(
    @SerialName("insulin_basal_units")
    val insulinBasalUnits: Double? = null,
    @SerialName("insulin_bolus_units")
    val insulinBolusUnits: Double? = null,
    @SerialName("carbs_grams")
    val carbsGrams: Int? = null,

    /**
     * Canonical glucose in mg/dL, matching the storage convention of
     * [LogEvent]. Populated by [toCanonical]; before that it only carries a
     * legacy `glucose_mgdl` value, which is already expressed in mg/dL.
     */
    @SerialName("glucose_mgdl")
    val glucoseMgdl: Double? = null,

    @SerialName("glucose_value")
    val glucoseValue: Double? = null,

    @SerialName("glucose_unit")
    val glucoseUnit: String? = null,

    @SerialName("meal_description")
    val mealDescription: String? = null,
    @SerialName("exercise_minutes")
    val exerciseMinutes: Int? = null,
) {
    val isEmpty: Boolean
        get() = insulinBasalUnits == null && insulinBolusUnits == null &&
            carbsGrams == null && glucoseMgdl == null && glucoseValue == null &&
            mealDescription.isNullOrBlank() && exerciseMinutes == null

    /**
     * Returns a copy whose [glucoseMgdl] is canonical mg/dL.
     *
     * The raw [glucoseValue]/[glucoseUnit] pair is cleared so downstream
     * consumers never re-interpret a display value. Values outside the
     * physiological range are dropped, leaving [glucoseMgdl] null.
     */
    fun toCanonical(displayUnit: GlucoseUnit): HeroAiPrefill =
        copy(
            glucoseMgdl = canonicalGlucoseMgdl(displayUnit),
            glucoseValue = null,
            glucoseUnit = null,
        )

    private fun canonicalGlucoseMgdl(displayUnit: GlucoseUnit): Double? {
        val value = glucoseValue
        val unit = glucoseUnit
        val canonical = when {
            value != null -> {
                val resolvedUnit = when (unit) {
                    GlucoseUnit.MGDL.label -> GlucoseUnit.MGDL
                    GlucoseUnit.MMOL.label -> GlucoseUnit.MMOL
                    else -> displayUnit
                }
                when (resolvedUnit) {
                    GlucoseUnit.MGDL -> value
                    GlucoseUnit.MMOL -> round(value * GlucoseUnit.MGDL_PER_MMOL)
                }
            }

            glucoseMgdl != null -> glucoseMgdl

            else -> null
        } ?: return null

        return canonical.takeIf { it in MIN_GLUCOSE_MGDL..MAX_GLUCOSE_MGDL }
    }

    private companion object {
        const val MIN_GLUCOSE_MGDL = 20.0
        const val MAX_GLUCOSE_MGDL = 600.0
    }
}
