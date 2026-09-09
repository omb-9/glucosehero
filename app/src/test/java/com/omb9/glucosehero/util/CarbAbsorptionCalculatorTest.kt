package com.omb9.glucosehero.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CarbAbsorptionCalculatorTest {

    private val now = Instant.parse("2026-09-08T12:00:00Z")

    @Test
    fun `fresh meal is fully on board`() {
        val meals = listOf(CarbAbsorptionCalculator.CarbEntry(now.toEpochMilli(), 30.0))
        assertEquals(30.0, CarbAbsorptionCalculator.carbsOnBoard(meals, actionHours = 3.0, now = now), 1e-9)
    }

    @Test
    fun `halfway through action window leaves half the carbs`() {
        val logged = now.minusSeconds((1.5 * 3600).toLong()).toEpochMilli()
        val meals = listOf(CarbAbsorptionCalculator.CarbEntry(logged, 30.0))
        assertEquals(15.0, CarbAbsorptionCalculator.carbsOnBoard(meals, actionHours = 3.0, now = now), 1e-9)
    }

    @Test
    fun `absorbed between now and one hour is the COB drop`() {
        val meals = listOf(CarbAbsorptionCalculator.CarbEntry(now.toEpochMilli(), 30.0))
        val absorbed = CarbAbsorptionCalculator.carbsAbsorbedBetween(
            meals = meals,
            actionHours = 3.0,
            from = now,
            to = now.plusSeconds(3600),
        )
        assertEquals(10.0, absorbed, 1e-9)
    }
}
