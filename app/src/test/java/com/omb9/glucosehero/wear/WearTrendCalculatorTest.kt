package com.omb9.glucosehero.wear

import com.omb9.glucosehero.domain.model.GlucosePointRow
import org.junit.Assert.assertEquals
import org.junit.Test

class WearTrendCalculatorTest {

    @Test
    fun `single point is unknown`() {
        val points = listOf(GlucosePointRow(timestamp = 10 * 60_000L, glucoseMgdl = 120.0))
        assertEquals(WearTrend.UNKNOWN, WearTrendCalculator.from(points, nowMillis = 12 * 60_000L))
    }

    @Test
    fun `plus 20 mgdl over 10 minutes is single up`() {
        val points = listOf(
            GlucosePointRow(timestamp = 0L, glucoseMgdl = 100.0),
            GlucosePointRow(timestamp = 10 * 60_000L, glucoseMgdl = 120.0),
        )
        assertEquals(WearTrend.SINGLE_UP, WearTrendCalculator.from(points, nowMillis = 10 * 60_000L))
    }

    @Test
    fun `plus 35 mgdl over 10 minutes is double up`() {
        val points = listOf(
            GlucosePointRow(timestamp = 0L, glucoseMgdl = 100.0),
            GlucosePointRow(timestamp = 10 * 60_000L, glucoseMgdl = 135.0),
        )
        assertEquals(WearTrend.DOUBLE_UP, WearTrendCalculator.from(points, nowMillis = 10 * 60_000L))
    }

    @Test
    fun `minus 25 mgdl over 10 minutes is single down`() {
        val points = listOf(
            GlucosePointRow(timestamp = 0L, glucoseMgdl = 140.0),
            GlucosePointRow(timestamp = 10 * 60_000L, glucoseMgdl = 115.0),
        )
        assertEquals(WearTrend.SINGLE_DOWN, WearTrendCalculator.from(points, nowMillis = 10 * 60_000L))
    }

    @Test
    fun `small change is flat`() {
        val points = listOf(
            GlucosePointRow(timestamp = 0L, glucoseMgdl = 110.0),
            GlucosePointRow(timestamp = 10 * 60_000L, glucoseMgdl = 115.0),
        )
        assertEquals(WearTrend.FLAT, WearTrendCalculator.from(points, nowMillis = 10 * 60_000L))
    }

    @Test
    fun `points outside the window are ignored`() {
        val points = listOf(
            GlucosePointRow(timestamp = 0L, glucoseMgdl = 80.0),
            GlucosePointRow(timestamp = 40 * 60_000L, glucoseMgdl = 180.0),
        )
        assertEquals(WearTrend.UNKNOWN, WearTrendCalculator.from(points, nowMillis = 40 * 60_000L))
    }
}
