package com.omb9.glucosehero.data.export

import com.omb9.glucosehero.domain.model.DailyGlucoseSummary
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.util.GlucoseRangeColor
import com.omb9.glucosehero.util.Percentiles
import com.omb9.glucosehero.util.RangeCategory
import com.omb9.glucosehero.util.gmiPercentage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

/**
 * Pure AGP math used by the clinical PDF renderer.
 *
 * Range buckets reuse the app's five-level classification: user target
 * [AgpRangeThresholds.targetLowMgdl] / [AgpRangeThresholds.targetHighMgdl]
 * plus the fixed clinical cutoffs 54 and 250 mg/dL from [GlucoseRangeColor].
 *
 * The 24-hour modal-day overlay bins readings by local clock time (default
 * 15-minute slots) and reports the 10th, 25th, 50th, 75th, and 90th
 * percentiles in each slot, matching the international AGP consensus plot.
 */
object AgpReportCalculator {

    const val WINDOW_DAYS = 14
    const val MIN_DAYS = 3
    const val MIN_READINGS = 20
    const val BIN_MINUTES = 15
    const val BINS_PER_DAY = 24 * 60 / BIN_MINUTES

    fun build(
        points: List<GlucosePointRow>,
        thresholds: AgpRangeThresholds,
        cgmReadingCount: Int,
        manualReadingCount: Int,
        daily: List<DailyGlucoseSummary>,
        windowEndMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): AgpReport {
        val windowStartMillis = windowEndMillis - WINDOW_DAYS * 24L * 60L * 60L * 1000L
        val inWindow = points.filter { it.timestamp in windowStartMillis..windowEndMillis }
        val values = inWindow.map { it.glucoseMgdl }.filter { it.isFinite() }
        val activeDays = inWindow
            .map { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
            .toSet()
            .size
        val mean = values.takeIf { it.isNotEmpty() }?.average()
        val stdDev = sampleStdDev(values)
        val cv = if (mean != null && mean > 0.0 && stdDev != null) {
            stdDev / mean * 100.0
        } else {
            null
        }
        val tir = if (values.isEmpty()) null else timeInRange(values, thresholds)
        val sufficient = activeDays >= MIN_DAYS && values.size >= MIN_READINGS
        val insufficientReason = when {
            values.isEmpty() ->
                "No glucose readings in the last $WINDOW_DAYS days."
            !sufficient ->
                insufficientMessage(activeDays, values.size)
            else -> null
        }
        return AgpReport(
            sufficient = sufficient,
            insufficientReason = insufficientReason,
            windowDays = WINDOW_DAYS,
            startMillis = windowStartMillis,
            endMillis = windowEndMillis,
            activeDays = activeDays,
            readingCount = values.size,
            cgmReadingCount = cgmReadingCount,
            manualReadingCount = manualReadingCount,
            meanMgdl = mean,
            gmiPercent = mean?.let { gmiPercentage(it) },
            cvPercent = cv,
            stdDevMgdl = stdDev,
            tir = tir,
            thresholds = thresholds,
            modalDay = modalDay(inWindow, zone),
            daily = daily.sortedBy { it.day },
        )
    }

    fun timeInRange(
        values: List<Double>,
        thresholds: AgpRangeThresholds,
    ): AgpTirPercents {
        require(values.isNotEmpty()) { "Cannot compute TIR of an empty list" }
        var veryLow = 0
        var low = 0
        var inRange = 0
        var high = 0
        var veryHigh = 0
        for (value in values) {
            when (
                GlucoseRangeColor.forValue(
                    value.toFloat(),
                    thresholds.targetLowMgdl,
                    thresholds.targetHighMgdl,
                )
            ) {
                RangeCategory.VERY_LOW -> veryLow++
                RangeCategory.LOW -> low++
                RangeCategory.IN_RANGE -> inRange++
                RangeCategory.HIGH -> high++
                RangeCategory.VERY_HIGH -> veryHigh++
            }
        }
        val total = values.size.toFloat()
        return AgpTirPercents(
            veryLow = veryLow * 100f / total,
            low = low * 100f / total,
            inRange = inRange * 100f / total,
            high = high * 100f / total,
            veryHigh = veryHigh * 100f / total,
            readingCount = values.size,
            veryLowCount = veryLow,
            lowCount = low,
            inRangeCount = inRange,
            highCount = high,
            veryHighCount = veryHigh,
        )
    }

    fun modalDay(
        points: List<GlucosePointRow>,
        zone: ZoneId,
        binMinutes: Int = BIN_MINUTES,
    ): List<ModalDayBin> {
        val binsPerDay = (24 * 60) / binMinutes
        val buckets = Array(binsPerDay) { ArrayList<Double>() }
        for (point in points) {
            if (!point.glucoseMgdl.isFinite()) continue
            val local = Instant.ofEpochMilli(point.timestamp).atZone(zone).toLocalTime()
            val minutes = local.toSecondOfDay() / 60
            val index = (minutes / binMinutes).coerceIn(0, binsPerDay - 1)
            buckets[index].add(point.glucoseMgdl)
        }
        return buckets.mapIndexed { index, values ->
            val minutesFromMidnight = index * binMinutes
            if (values.isEmpty()) {
                ModalDayBin(
                    minutesFromMidnight = minutesFromMidnight,
                    p10 = null,
                    p25 = null,
                    p50 = null,
                    p75 = null,
                    p90 = null,
                    sampleCount = 0,
                )
            } else {
                val sorted = values.sorted()
                ModalDayBin(
                    minutesFromMidnight = minutesFromMidnight,
                    p10 = Percentiles.percentile(sorted, 0.10),
                    p25 = Percentiles.percentile(sorted, 0.25),
                    p50 = Percentiles.percentile(sorted, 0.50),
                    p75 = Percentiles.percentile(sorted, 0.75),
                    p90 = Percentiles.percentile(sorted, 0.90),
                    sampleCount = values.size,
                )
            }
        }
    }

    fun sampleStdDev(values: List<Double>): Double? {
        if (values.size < 2) return null
        val mean = values.average()
        val sumSq = values.sumOf { val d = it - mean; d * d }
        return sqrt(sumSq / (values.size - 1))
    }

    private fun insufficientMessage(activeDays: Int, readings: Int): String {
        val daysNeeded = (MIN_DAYS - activeDays).coerceAtLeast(0)
        val readingsNeeded = (MIN_READINGS - readings).coerceAtLeast(0)
        val dayPart = when {
            daysNeeded <= 0 -> null
            daysNeeded == 1 -> "1 more calendar day"
            else -> "$daysNeeded more calendar days"
        }
        val readingPart = when {
            readingsNeeded <= 0 -> null
            readingsNeeded == 1 -> "1 more reading"
            else -> "$readingsNeeded more readings"
        }
        return when {
            dayPart != null && readingPart != null ->
                "Need $dayPart and $readingPart in the last $WINDOW_DAYS days to generate AGP charts."
            dayPart != null ->
                "Need $dayPart of glucose data in the last $WINDOW_DAYS days to generate AGP charts."
            readingPart != null ->
                "Need $readingPart in the last $WINDOW_DAYS days to generate AGP charts."
            else ->
                "Not enough glucose data in the last $WINDOW_DAYS days to generate AGP charts."
        }
    }
}

data class AgpRangeThresholds(
    val targetLowMgdl: Float,
    val targetHighMgdl: Float,
    val veryLowMgdl: Float = GlucoseRangeColor.VERY_LOW_MGDL,
    val veryHighMgdl: Float = GlucoseRangeColor.VERY_HIGH_MGDL,
)

data class AgpTirPercents(
    val veryLow: Float,
    val low: Float,
    val inRange: Float,
    val high: Float,
    val veryHigh: Float,
    val readingCount: Int,
    val veryLowCount: Int,
    val lowCount: Int,
    val inRangeCount: Int,
    val highCount: Int,
    val veryHighCount: Int,
)

data class ModalDayBin(
    val minutesFromMidnight: Int,
    val p10: Double?,
    val p25: Double?,
    val p50: Double?,
    val p75: Double?,
    val p90: Double?,
    val sampleCount: Int,
) {
    val hasPercentiles: Boolean
        get() = p10 != null && p25 != null && p50 != null && p75 != null && p90 != null
}

data class AgpReport(
    val sufficient: Boolean,
    val insufficientReason: String?,
    val windowDays: Int,
    val startMillis: Long,
    val endMillis: Long,
    val activeDays: Int,
    val readingCount: Int,
    val cgmReadingCount: Int,
    val manualReadingCount: Int,
    val meanMgdl: Double?,
    val gmiPercent: Double?,
    val cvPercent: Double?,
    val stdDevMgdl: Double?,
    val tir: AgpTirPercents?,
    val thresholds: AgpRangeThresholds,
    val modalDay: List<ModalDayBin>,
    val daily: List<DailyGlucoseSummary>,
) {
    val periodStartDate: LocalDate
        get() = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    val periodEndDate: LocalDate
        get() = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
}
