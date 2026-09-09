package com.omb9.glucosehero.data.export

import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.util.gmiPercentage
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgpReportCalculatorTest {

    private val thresholds = AgpRangeThresholds(targetLowMgdl = 70f, targetHighMgdl = 180f)

    @Test
    fun timeInRange_usesAppFiveBucketCutoffs() {
        val values = listOf(
            40.0,  // very low
            60.0,  // low
            100.0, // in range
            200.0, // high
            300.0, // very high
        )
        val tir = AgpReportCalculator.timeInRange(values, thresholds)
        assertEquals(20f, tir.veryLow, 0.01f)
        assertEquals(20f, tir.low, 0.01f)
        assertEquals(20f, tir.inRange, 0.01f)
        assertEquals(20f, tir.high, 0.01f)
        assertEquals(20f, tir.veryHigh, 0.01f)
        assertEquals(5, tir.readingCount)
    }

    @Test
    fun gmi_matchesConsensusFormula() {
        assertEquals(7.0, gmiPercentage(154.0), 0.05)
    }

    @Test
    fun cv_isSampleStdDevOverMean() {
        val values = listOf(100.0, 120.0, 140.0)
        val std = AgpReportCalculator.sampleStdDev(values)!!
        val cv = std / values.average() * 100.0
        assertEquals(20.0, std, 0.01)
        assertEquals(16.67, cv, 0.05)
    }

    @Test
    fun insufficientData_whenFewerThanMinReadings() {
        val now = 1_700_000_000_000L
        val points = (0 until 5).map { index ->
            GlucosePointRow(timestamp = now - index * 60_000L, glucoseMgdl = 110.0)
        }
        val report = AgpReportCalculator.build(
            points = points,
            thresholds = thresholds,
            cgmReadingCount = 0,
            manualReadingCount = 5,
            daily = emptyList(),
            windowEndMillis = now,
            zone = ZoneOffset.UTC,
        )
        assertFalse(report.sufficient)
        assertNotNull(report.insufficientReason)
        assertNotNull(report.meanMgdl)
        assertNotNull(report.gmiPercent)
    }

    @Test
    fun modalDay_groupsByClockTimeAndReportsPercentiles() {
        val zone = ZoneOffset.UTC
        val dayStart = 1_704_067_200_000L // 2024-01-01 00:00 UTC
        val points = (0 until 14).flatMap { day ->
            listOf(
                GlucosePointRow(dayStart + day * DAY + 6 * HOUR, 90.0 + day),
                GlucosePointRow(dayStart + day * DAY + 12 * HOUR, 140.0 + day),
            )
        }
        val bins = AgpReportCalculator.modalDay(points, zone)
        val sixAm = bins.first { it.minutesFromMidnight == 6 * 60 }
        val noon = bins.first { it.minutesFromMidnight == 12 * 60 }
        assertEquals(14, sixAm.sampleCount)
        assertEquals(14, noon.sampleCount)
        assertNotNull(sixAm.p10)
        assertNotNull(sixAm.p50)
        assertNotNull(sixAm.p90)
        assertTrue(sixAm.p50!! < noon.p50!!)
    }

    private companion object {
        const val HOUR = 60L * 60L * 1000L
        const val DAY = 24L * HOUR
    }
}
