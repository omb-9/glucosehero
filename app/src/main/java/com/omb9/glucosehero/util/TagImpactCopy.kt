package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.TagKind
import kotlin.math.abs

/**
 * Observational copy for tag analytics.
 *
 * Glucose, bolus, and carbs always go through [Formatters] so mmol/L stays at
 * one decimal and unit suffixes stay consistent. Phrasing describes tagged
 * meals (sample size and logged insulin included); it never claims a food
 * causes a glucose change.
 */
object TagImpactCopy {

    /** Occurrences at which a displayed tag stops being marked provisional. */
    const val MIN_CONFIDENT_OCCURRENCES = 5

    fun isProvisional(kind: TagKind, occurrences: Int): Boolean {
        val minShow = TagExtractor.minOccurrences(kind)
        return occurrences >= minShow && occurrences < MIN_CONFIDENT_OCCURRENCES
    }

    fun deltaHeadline(
        medianDeltaMgdl: Double,
        unit: GlucoseUnit,
        whenPhrase: String = "two hours later",
    ): String {
        val magnitude = Formatters.glucoseWithUnit(abs(medianDeltaMgdl), unit)
        return when {
            medianDeltaMgdl > 0.0005 -> "Typically $magnitude higher $whenPhrase"
            medianDeltaMgdl < -0.0005 -> "Typically $magnitude lower $whenPhrase"
            else -> "Typically no change $whenPhrase"
        }
    }

    /**
     * Full observational sentence used on cards, in chat context, and in weekly
     * summary prompts. Example: "After meals tagged #pizza, your glucose was
     * typically 85 mg/dL higher two hours later, across 11 occurrences with an
     * average bolus of 4.2 units."
     */
    fun observation(
        tag: String,
        medianDeltaMgdl: Double,
        occurrences: Int,
        avgBolusUnits: Double?,
        unit: GlucoseUnit,
        whenPhrase: String = "two hours later",
    ): String {
        val magnitude = Formatters.glucoseWithUnit(abs(medianDeltaMgdl), unit)
        val typically = when {
            medianDeltaMgdl > 0.0005 -> "typically $magnitude higher $whenPhrase"
            medianDeltaMgdl < -0.0005 -> "typically $magnitude lower $whenPhrase"
            else -> "typically unchanged $whenPhrase"
        }
        val sample = if (occurrences == 1) "1 occurrence" else "$occurrences occurrences"
        val bolus = avgBolusUnits?.let { " with an average bolus of ${Formatters.bolus(it)}" }.orEmpty()
        return "After meals tagged $tag, your glucose was $typically, across $sample$bolus."
    }

    fun spread(p25DeltaMgdl: Double, p75DeltaMgdl: Double, unit: GlucoseUnit): String =
        "${Formatters.glucose(p25DeltaMgdl, unit)}–${Formatters.glucose(p75DeltaMgdl, unit)} ${unit.label}"

    fun confounder(avgCarbsGrams: Double?, avgBolusUnits: Double?): String {
        val carbs = avgCarbsGrams?.let { Formatters.carbs(it) } ?: "n/a"
        val bolus = avgBolusUnits?.let { Formatters.bolus(it) } ?: "n/a"
        return "avg $carbs carbs, $bolus bolus"
    }

    fun buildingProgress(tag: String, occurrences: Int, needed: Int): String =
        "You have $occurrences of the $needed occurrences needed to show patterns for $tag."

    fun provisionalProgress(occurrences: Int): String =
        "You have $occurrences of the $MIN_CONFIDENT_OCCURRENCES occurrences needed for a stable pattern."

    fun emptyFoodImpact(): String =
        "No food patterns yet. Log meals with a food, a hashtag, or a description, plus a glucose reading before and about two hours after. Patterns appear after enough repeats of the same tag."
}
