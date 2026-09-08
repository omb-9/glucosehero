package com.omb9.glucosehero.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ea1cCalculatorTest {

    @Test
    fun `exactly 70 percent cgm returns true`() {
        assertTrue(shouldUseGmi(cgmReadingCount = 70, manualReadingCount = 30))
    }

    @Test
    fun `just above 70 percent cgm returns true`() {
        assertTrue(shouldUseGmi(cgmReadingCount = 71, manualReadingCount = 29))
    }

    @Test
    fun `just below 70 percent cgm returns false`() {
        assertFalse(shouldUseGmi(cgmReadingCount = 69, manualReadingCount = 31))
    }

    @Test
    fun `zero readings returns false without dividing by zero`() {
        assertFalse(shouldUseGmi(cgmReadingCount = 0, manualReadingCount = 0))
    }

    @Test
    fun `manual only readings returns false`() {
        assertFalse(shouldUseGmi(cgmReadingCount = 0, manualReadingCount = 5))
    }

    @Test
    fun `cgm only readings returns true`() {
        assertTrue(shouldUseGmi(cgmReadingCount = 5, manualReadingCount = 0))
    }

    @Test
    fun `gmi formula matches clinical constant`() {
        // 3.31 + 0.02392 * 120 = 6.1804
        assertEquals(6.1804, gmiPercentage(120.0), 1e-9)
    }

    @Test
    fun `adag formula matches clinical constant`() {
        // (120 + 46.7) / 28.7
        assertEquals((120.0 + 46.7) / 28.7, adagPercentage(120.0), 1e-9)
    }
}
