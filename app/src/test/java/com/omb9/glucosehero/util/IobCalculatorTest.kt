package com.omb9.glucosehero.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class IobCalculatorTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `newly delivered bolus is fully active`() {
        val boluses = listOf(IobCalculator.BolusEntry(now.toEpochMilli(), 4.0))
        assertEquals(4.0, IobCalculator.activeInsulinOnBoard(boluses, diaHours = 4.0, now = now), 1e-9)
    }

    @Test
    fun `halfway through DIA yields half the bolus`() {
        val delivered = now.minusSeconds(2 * 3600L).toEpochMilli()
        val boluses = listOf(IobCalculator.BolusEntry(delivered, 4.0))
        assertEquals(2.0, IobCalculator.activeInsulinOnBoard(boluses, diaHours = 4.0, now = now), 1e-9)
    }

    @Test
    fun `bolus delivered at DIA boundary is fully absorbed`() {
        val delivered = now.minusSeconds(4 * 3600L).toEpochMilli()
        val boluses = listOf(IobCalculator.BolusEntry(delivered, 4.0))
        assertEquals(0.0, IobCalculator.activeInsulinOnBoard(boluses, diaHours = 4.0, now = now), 1e-9)
    }

    @Test
    fun `bolus older than DIA contributes nothing`() {
        val delivered = now.minusSeconds(5 * 3600L).toEpochMilli()
        val boluses = listOf(IobCalculator.BolusEntry(delivered, 4.0))
        assertEquals(0.0, IobCalculator.activeInsulinOnBoard(boluses, diaHours = 4.0, now = now), 1e-9)
    }

    @Test
    fun `multiple boluses sum their remaining activity`() {
        val boluses = listOf(
            IobCalculator.BolusEntry(now.toEpochMilli(), 3.0),                           // 3.0 active
            IobCalculator.BolusEntry(now.minusSeconds(2 * 3600L).toEpochMilli(), 4.0),   // 2.0 active
            IobCalculator.BolusEntry(now.minusSeconds(5 * 3600L).toEpochMilli(), 6.0),   // 0.0 expired
        )
        assertEquals(5.0, IobCalculator.activeInsulinOnBoard(boluses, diaHours = 4.0, now = now), 1e-9)
    }

    @Test
    fun `empty list yields zero`() {
        assertEquals(0.0, IobCalculator.activeInsulinOnBoard(emptyList(), diaHours = 4.0, now = now), 1e-9)
    }
}