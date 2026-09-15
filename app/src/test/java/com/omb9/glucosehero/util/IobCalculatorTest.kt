package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.DosingBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class IobCalculatorTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val diaHours = 4.0

    @Test
    fun `newly delivered bolus is fully active`() {
        val boluses = listOf(IobCalculator.BolusEntry(now.toEpochMilli(), 4.0))
        assertEquals(4.0, IobCalculator.activeInsulinOnBoard(boluses, diaHours, now = now), 1e-9)
    }

    @Test
    fun `bolus delivered at DIA boundary is fully absorbed`() {
        val delivered = now.minusSeconds(4 * 3600L).toEpochMilli()
        val boluses = listOf(IobCalculator.BolusEntry(delivered, 4.0))
        assertEquals(0.0, IobCalculator.activeInsulinOnBoard(boluses, diaHours, now = now), 1e-9)
    }

    @Test
    fun `bolus older than DIA contributes nothing`() {
        val delivered = now.minusSeconds(5 * 3600L).toEpochMilli()
        val boluses = listOf(IobCalculator.BolusEntry(delivered, 4.0))
        assertEquals(0.0, IobCalculator.activeInsulinOnBoard(boluses, diaHours, now = now), 1e-9)
    }

    @Test
    fun `future dated bolus contributes nothing`() {
        val boluses = listOf(IobCalculator.BolusEntry(now.plusSeconds(60).toEpochMilli(), 4.0))
        assertEquals(0.0, IobCalculator.activeInsulinOnBoard(boluses, diaHours, now = now), 1e-9)
    }

    @Test
    fun `empty list yields zero`() {
        assertEquals(0.0, IobCalculator.activeInsulinOnBoard(emptyList(), diaHours, now = now), 1e-9)
    }

    @Test
    fun `remaining at 60 minutes is still high and above linear DIA`() {
        val remaining = IobCalculator.remainingFraction(60.0 * 60_000.0, diaHours)
        val linear = linearRemaining(elapsedHours = 1.0, dia = diaHours)
        assertTrue("Walsh remaining at 60m should stay high, was $remaining", remaining > 0.75)
        assertTrue("Walsh remaining at 60m ($remaining) should exceed linear ($linear)", remaining > linear)
        assertEquals(0.80, remaining, 1e-6)
    }

    @Test
    fun `remaining at 3 hours is below linear DIA`() {
        val remaining = IobCalculator.remainingFraction(3.0 * 3_600_000.0, diaHours)
        val linear = linearRemaining(elapsedHours = 3.0, dia = diaHours)
        assertTrue("Walsh remaining at 3h ($remaining) should be below linear ($linear)", remaining < linear)
        assertTrue("Walsh tail at 3h should be thin, was $remaining", remaining < 0.20)
        assertTrue(remaining > 0.0)
    }

    @Test
    fun `remaining at 4 hours is zero`() {
        assertEquals(0.0, IobCalculator.remainingFraction(4.0 * 3_600_000.0, diaHours), 1e-9)
    }

    @Test
    fun `activity peak is inside the 50 to 75 minute window for a 4 hour DIA`() {
        val peakMinutes = IobCalculator.peakMillis(diaHours) / 60_000.0
        assertEquals(IobCalculator.ACTIVITY_PEAK_MINUTES, peakMinutes, 1e-9)
        assertTrue(peakMinutes in 50.0..75.0)
    }

    @Test
    fun `IOB is monotonically decreasing across the DIA window`() {
        var previous = 1.0
        var t = 0.0
        while (t <= 4.0 * 3_600_000.0) {
            val remaining = IobCalculator.remainingFraction(t, diaHours)
            assertTrue("IOB rose at t=$t: $previous -> $remaining", remaining <= previous + 1e-9)
            previous = remaining
            t += 5.0 * 60_000.0
        }
        assertEquals(0.0, previous, 1e-9)
    }

    @Test
    fun `multiple boluses sum their remaining activity`() {
        val twoHoursAgo = 2.0 * 3_600_000.0
        val expectedTwoHour = 4.0 * IobCalculator.remainingFraction(twoHoursAgo, diaHours)
        val boluses = listOf(
            IobCalculator.BolusEntry(now.toEpochMilli(), 3.0),
            IobCalculator.BolusEntry(now.minusMillis(twoHoursAgo.toLong()).toEpochMilli(), 4.0),
            IobCalculator.BolusEntry(now.minusSeconds(5 * 3600L).toEpochMilli(), 6.0),
        )
        assertEquals(
            3.0 + expectedTwoHour,
            IobCalculator.activeInsulinOnBoard(boluses, diaHours, now = now),
            1e-9,
        )
    }

    @Test
    fun `insulin absorbed over the first hour is less than a linear quarter`() {
        val boluses = listOf(IobCalculator.BolusEntry(now.toEpochMilli(), 4.0))
        val absorbed = IobCalculator.insulinAbsorbedBetween(
            boluses = boluses,
            diaHours = diaHours,
            from = now,
            to = now.plusSeconds(3600),
        )
        val linearQuarter = 1.0
        assertTrue("First-hour absorption $absorbed should be below linear $linearQuarter", absorbed < linearQuarter)
        assertEquals(4.0 * (1.0 - 0.80), absorbed, 1e-6)
        assertTrue(absorbed > 0.0)
    }

    @Test
    fun `forecast window still sees decaying IOB between now and 60 minutes`() {
        val boluses = listOf(IobCalculator.BolusEntry(now.toEpochMilli(), 2.0))
        val start = IobCalculator.activeInsulinOnBoard(boluses, diaHours, now)
        val later = IobCalculator.activeInsulinOnBoard(boluses, diaHours, now.plusSeconds(3600))
        assertEquals(2.0, start, 1e-9)
        assertTrue(later < start)
        assertTrue(later > 0.0)
        val absorbed = IobCalculator.insulinAbsorbedBetween(boluses, diaHours, now, now.plusSeconds(3600))
        assertEquals(start - later, absorbed, 1e-9)
    }

    @Test
    fun `lookbackMillis is strictly greater than DIA across the configured range`() {
        for (dia in listOf(2.0, 4.0, 6.0, 8.0)) {
            val window = IobCalculator.lookbackMillis(dia)
            val diaMillis = (dia * IobCalculator.MILLIS_PER_HOUR).toLong()
            assertTrue(
                "lookback for DIA=$dia ($window) should exceed DIA millis ($diaMillis)",
                window > diaMillis,
            )
            assertEquals(diaMillis + IobCalculator.WINDOW_MARGIN_MILLIS, window)
        }
    }

    @Test
    fun `lookbackMillis clamps DIA below min and above max`() {
        assertEquals(
            IobCalculator.lookbackMillis(DosingBounds.MIN_DIA_HOURS.toDouble()),
            IobCalculator.lookbackMillis(0.0),
        )
        assertEquals(
            IobCalculator.lookbackMillis(DosingBounds.MAX_DIA_HOURS.toDouble()),
            IobCalculator.lookbackMillis(500.0),
        )
    }

    @Test
    fun `DIA 8 still reports IOB for a 5U bolus from 7 hours ago`() {
        val delivered = now.minusSeconds(7 * 3600L).toEpochMilli()
        val windowStart = now.toEpochMilli() - IobCalculator.lookbackMillis(8.0)
        assertTrue(
            "7h bolus must sit inside the DIA=8 lookback, not a 6h window",
            delivered >= windowStart,
        )
        val loaded = listOf(IobCalculator.BolusEntry(delivered, 5.0))
            .filter { it.timestampMillis >= windowStart }
        val iob = IobCalculator.activeInsulinOnBoard(loaded, 8.0, now = now)
        assertTrue("IOB for a 7h-old bolus at DIA=8 should be > 0, was $iob", iob > 0.0)
    }

    @Test
    fun `DIA 4 reports exactly zero IOB for a bolus from 5 hours ago`() {
        val delivered = now.minusSeconds(5 * 3600L).toEpochMilli()
        val windowStart = now.toEpochMilli() - IobCalculator.lookbackMillis(4.0)
        val loaded = listOf(IobCalculator.BolusEntry(delivered, 5.0))
            .filter { it.timestampMillis >= windowStart }
        assertEquals(
            0.0,
            IobCalculator.activeInsulinOnBoard(loaded, 4.0, now = now),
            1e-9,
        )
        val stillLoadedPastDia = listOf(
            IobCalculator.BolusEntry(
                now.minusMillis(
                    (4.0 * IobCalculator.MILLIS_PER_HOUR).toLong() +
                        IobCalculator.WINDOW_MARGIN_MILLIS / 2,
                ).toEpochMilli(),
                5.0,
            ),
        )
        assertEquals(
            0.0,
            IobCalculator.activeInsulinOnBoard(stillLoadedPastDia, 4.0, now = now),
            1e-9,
        )
    }

    private fun linearRemaining(elapsedHours: Double, dia: Double): Double =
        (1.0 - elapsedHours / dia).coerceIn(0.0, 1.0)
}
