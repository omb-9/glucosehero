package com.omb9.glucosehero.forecast

import androidx.compose.runtime.Immutable
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.parseSegmentStart
import com.omb9.glucosehero.util.BolusDisplayFormatter
import com.omb9.glucosehero.util.Formatters
import java.time.LocalTime

/**
 * Display-only forecast explanation built from [GlucoseForecastSnapshot]
 * fields the engine recorded. UI must not recompute insulin, carb, or
 * trend contributions, and must not invent a single ISF when
 * [GlucoseForecastSnapshot.horizonCrossedSegmentBoundary] is true.
 */
object ForecastDisplayFormatter {

    fun format(
        snapshot: GlucoseForecastSnapshot,
        unit: GlucoseUnit,
        use24HourTime: Boolean,
    ): ForecastDisplayExplanation {
        val used = snapshot.dosingSegmentsUsed
        val segments = used.mapIndexed { index, item ->
            val start = parseSegmentStart(item.startHhmm) ?: LocalTime.MIDNIGHT
            val nextHhmm = used.getOrNull(index + 1)?.startHhmm
                ?: used.firstOrNull()?.startHhmm
            val end = nextHhmm?.let { parseSegmentStart(it) } ?: LocalTime.MIDNIGHT
            ForecastSegmentDisplay(
                startText = Formatters.wallClock(start, use24HourTime),
                endText = Formatters.wallClock(end, use24HourTime),
                isfWithUnit = Formatters.isfWithUnit(item.isfMgdl, unit),
                cirText = formatCir(item.cirRatio),
            )
        }
        return ForecastDisplayExplanation(
            insufficientData = snapshot.insufficientData,
            dosingProfileInvalid = snapshot.dosingProfileInvalid,
            startGlucose = Formatters.glucoseWithUnit(snapshot.currentMgdl, unit),
            startTime = Formatters.time(snapshot.currentTimestampMillis, use24HourTime),
            trendContribution = Formatters.signedGlucoseWithUnit(snapshot.trendEffectMgdl60, unit),
            iobUnits = BolusDisplayFormatter.oneDecimal(snapshot.iobUnits),
            insulinDrop = Formatters.signedGlucoseWithUnit(-snapshot.insulinEffectMgdl60, unit),
            cobGrams = snapshot.cobGrams.toInt().toString(),
            carbRise = Formatters.signedGlucoseWithUnit(snapshot.carbEffectMgdl60, unit),
            clamp60 = clampLabel(
                clampMin = snapshot.clampMin60,
                clampMax = snapshot.clampMax60,
                unit = unit,
            ),
            clamp30 = clampLabel(
                clampMin = snapshot.clampMin30,
                clampMax = snapshot.clampMax30,
                unit = unit,
            ),
            horizonCrossedSegmentBoundary = snapshot.horizonCrossedSegmentBoundary,
            claimsSingleIsf = snapshot.claimsSingleIsf,
            segments = segments,
        )
    }

    private fun clampLabel(
        clampMin: Boolean,
        clampMax: Boolean,
        unit: GlucoseUnit,
    ): String? = when {
        clampMin -> Formatters.glucoseWithUnit(GlucoseForecastEngine.MIN_GLUCOSE_MGDL, unit)
        clampMax -> Formatters.glucoseWithUnit(GlucoseForecastEngine.MAX_GLUCOSE_MGDL, unit)
        else -> null
    }

    private fun formatCir(cirRatio: Double): String =
        if (cirRatio % 1.0 == 0.0) cirRatio.toInt().toString() else "%.1f".format(cirRatio)
}

@Immutable
data class ForecastSegmentDisplay(
    val startText: String,
    val endText: String,
    val isfWithUnit: String,
    val cirText: String,
)

@Immutable
data class ForecastDisplayExplanation(
    val insufficientData: Boolean,
    val dosingProfileInvalid: Boolean,
    val startGlucose: String,
    val startTime: String,
    val trendContribution: String,
    val iobUnits: String,
    val insulinDrop: String,
    val cobGrams: String,
    val carbRise: String,
    val clamp60: String?,
    val clamp30: String?,
    val horizonCrossedSegmentBoundary: Boolean,
    val claimsSingleIsf: Boolean,
    val segments: List<ForecastSegmentDisplay>,
) {
    fun spokenSentenceForTest(disclaimer: String): String {
        val clauses = buildList {
            if (dosingProfileInvalid) {
                add("Cannot project insulin or carb effects until the time-of-day dosing profile is valid.")
            } else if (insufficientData) {
                add("Need a few recent glucose readings to project ahead.")
            } else {
                add("Starts from $startGlucose at $startTime.")
                add("Recent readings suggest about $trendContribution over 60 minutes.")
                add("Insulin still working: $iobUnits U, about $insulinDrop.")
                add("Carbs still absorbing: $cobGrams g, about $carbRise.")
                clamp30?.let { add("The 30-minute value was limited to $it.") }
                clamp60?.let { add("The 60-minute value was limited to $it.") }
                if (horizonCrossedSegmentBoundary && segments.isNotEmpty()) {
                    val joined = segments.joinToString(", ") { seg ->
                        "${seg.startText}-${seg.endText} ISF ${seg.isfWithUnit} CIR ${seg.cirText}"
                    }
                    add(
                        "This hour covers more than one dosing block: $joined. " +
                            "There is not a single ISF for the whole forecast.",
                    )
                } else if (claimsSingleIsf && segments.size == 1) {
                    val seg = segments.first()
                    add("Used ISF ${seg.isfWithUnit}, your ${seg.startText}-${seg.endText} block.")
                }
            }
            add(disclaimer)
        }
        return com.omb9.glucosehero.util.WhyThisNumberCopy.spokenSentence(clauses)
    }
}
