package com.omb9.glucosehero.util

import androidx.compose.runtime.Immutable
import com.omb9.glucosehero.domain.model.GlucoseUnit
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Display-only rounding for a [BolusRecommendationBreakdown].
 *
 * **Why a separate helper.** The calculator must keep unrounded doubles so
 * stored suggestions and existing tests do not move. Three 1-decimal terms
 * do not reliably sum to a separately rounded total (0.25 + 0.25 displays
 * as 0.3 + 0.3 against a 0.5 total). This helper rounds for the screen and
 * records when a final rounding step or the zero floor must be shown, so the
 * UI never recomputes dosing math and never prints a self-contradicting sum.
 *
 * **Rule (display only).** Each term is half-up to one decimal via the same
 * `%.1f` conversion [Formatters.bolus] uses. The displayed headline total is
 * that 1-decimal form of [BolusRecommendationBreakdown.units], not the sum of
 * rounded terms. When those differ, [roundingStepShown] is true and the UI
 * must print "terms add to X, shown as Y". When [zeroFloorApplied] is true,
 * the UI must print the (rounded) raw net then "rounded up to 0 U".
 *
 * Glucose and ISF are converted from canonical mg/dL only here. Insulin
 * units are unit-system independent.
 *
 * Locale is [Locale.US] so 1-decimal strings are stable in tests and match
 * the ASCII decimal the rest of the logging UI already uses.
 */
object BolusDisplayFormatter {

    fun format(
        breakdown: BolusRecommendationBreakdown,
        unit: GlucoseUnit,
        segmentStart: LocalTime,
        segmentEnd: LocalTime,
        use24HourTime: Boolean,
    ): BolusDisplayBreakdown {
        val mealRounded = roundToOneDecimal(breakdown.mealDose)
        val correctionRounded = roundToOneDecimal(breakdown.correctionDose)
        val iobRounded = roundToOneDecimal(breakdown.insulinOnBoard)
        val termsNet = mealRounded + correctionRounded - iobRounded
        val termsNetRounded = roundToOneDecimal(termsNet)
        val totalRounded = roundToOneDecimal(breakdown.units)
        val zeroFloor = breakdown.zeroFloorApplied
        val roundingStep = !zeroFloor && termsNetRounded != totalRounded
        return BolusDisplayBreakdown(
            showMealLine = breakdown.carbsGrams > 0.0,
            carbsGramsText = breakdown.carbsGrams.roundToInt().toString(),
            cirText = formatScalar(breakdown.carbRatio),
            mealDoseText = oneDecimal(breakdown.mealDose),
            glucoseText = Formatters.glucose(breakdown.currentGlucoseMgdl, unit),
            targetText = Formatters.glucose(breakdown.targetGlucoseMgdl, unit),
            glucoseUnitLabel = unit.label,
            isfText = Formatters.isf(breakdown.insulinSensitivityMgdl, unit),
            isfWithUnit = Formatters.isfWithUnit(breakdown.insulinSensitivityMgdl, unit),
            correctionIsNegative = breakdown.correctionDose < 0.0,
            showCorrectionLine = breakdown.correctionDose != 0.0,
            correctionAbsText = oneDecimal(abs(breakdown.correctionDose)),
            correctionSignedText = oneDecimal(breakdown.correctionDose),
            iobText = oneDecimal(breakdown.insulinOnBoard),
            showIobLine = breakdown.insulinOnBoard != 0.0,
            termsNetText = oneDecimal(termsNet),
            rawNetText = oneDecimal(breakdown.rawTotal),
            totalText = oneDecimal(breakdown.units),
            zeroFloorApplied = zeroFloor,
            roundingStepShown = roundingStep,
            segmentStartText = Formatters.wallClock(segmentStart, use24HourTime),
            segmentEndText = Formatters.wallClock(segmentEnd, use24HourTime),
        )
    }

    fun oneDecimal(value: Double): String = "%.1f".format(Locale.US, value)

    fun roundToOneDecimal(value: Double): Double = oneDecimal(value).toDouble()

    fun formatScalar(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else oneDecimal(value)
}

/**
 * Already-rounded strings for the Smart Bolus explanation. Compose renders
 * these; it must not add meal/correction/IOB terms itself.
 */
@Immutable
data class BolusDisplayBreakdown(
    val showMealLine: Boolean,
    val carbsGramsText: String,
    val cirText: String,
    val mealDoseText: String,
    val glucoseText: String,
    val targetText: String,
    val glucoseUnitLabel: String,
    val isfText: String,
    val isfWithUnit: String,
    val correctionIsNegative: Boolean,
    val showCorrectionLine: Boolean,
    val correctionAbsText: String,
    val correctionSignedText: String,
    val iobText: String,
    val showIobLine: Boolean,
    val termsNetText: String,
    val rawNetText: String,
    val totalText: String,
    val zeroFloorApplied: Boolean,
    val roundingStepShown: Boolean,
    val segmentStartText: String,
    val segmentEndText: String,
) {
    /**
     * True when the visible arithmetic matches the visible total under the
     * chosen rounding rule, including an explicit floor or rounding step.
     */
    fun displayedTermsReconcileWithTotal(): Boolean {
        if (zeroFloorApplied) {
            return totalText == BolusDisplayFormatter.oneDecimal(0.0)
        }
        return if (roundingStepShown) {
            termsNetText != totalText
        } else {
            termsNetText == totalText
        }
    }

    /**
     * TalkBack sentence built from the same rounded terms the UI renders.
     * Connecting words are English so unit tests can assert a coherent
     * sentence without Android resources; Compose uses stringResource for
     * the on-screen copy and joins those clauses with [WhyThisNumberCopy].
     */
    fun spokenSentenceForTest(disclaimer: String): String {
        val clauses = buildList {
            if (showMealLine) {
                add("$carbsGramsText grams divided by carb ratio $cirText equals $mealDoseText units.")
            }
            if (showCorrectionLine) {
                if (correctionIsNegative) {
                    add("Minus correction: $targetText minus $glucoseText, divided by ISF $isfText, equals $correctionAbsText units.")
                } else {
                    add("Correction: $glucoseText minus $targetText, divided by ISF $isfText, equals $correctionAbsText units.")
                }
            }
            if (showIobLine) {
                add("Minus insulin on board $iobText units.")
            }
            when {
                zeroFloorApplied ->
                    add("The sum was $rawNetText units, rounded up to 0 units.")
                roundingStepShown ->
                    add("The terms add to $termsNetText units, shown as $totalText units.")
                else -> add("Suggested bolus $totalText units.")
            }
            add("This used your $segmentStartText to $segmentEndText block with ISF $isfWithUnit.")
            add(disclaimer)
        }
        return WhyThisNumberCopy.spokenSentence(clauses)
    }
}

/**
 * Joins already-localized clauses into one TalkBack string so an equation
 * split across [androidx.compose.material3.Text] nodes is not announced as
 * disconnected fragments.
 */
object WhyThisNumberCopy {
    fun spokenSentence(clauses: List<String>): String =
        clauses.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
}
