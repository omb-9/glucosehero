package com.omb9.glucosehero.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrisisDetectorTest {

    @Test
    fun `severe hypo is below 54 mgdl`() {
        assertTrue(CrisisDetector.isSevereHypoglycemia(53.9))
        assertFalse(CrisisDetector.isSevereHypoglycemia(54.0))
        assertFalse(CrisisDetector.isSevereHypoglycemia(null))
    }

    @Test
    fun `recovery is at or above 70 mgdl`() {
        assertTrue(CrisisDetector.isHypoRecovered(70.0))
        assertFalse(CrisisDetector.isHypoRecovered(69.9))
    }

    @Test
    fun `timeout is clamped to 5 through 10 minutes`() {
        assertEqualsClamped()
    }

    @Test
    fun `journal phrases still match`() {
        assertTrue(CrisisDetector.isCrisis("I want to die"))
        assertFalse(CrisisDetector.isCrisis("feeling tired"))
    }

    private fun assertEqualsClamped() {
        assertTrue(CrisisDetector.clampSosTimeoutMinutes(1) == 5)
        assertTrue(CrisisDetector.clampSosTimeoutMinutes(5) == 5)
        assertTrue(CrisisDetector.clampSosTimeoutMinutes(8) == 8)
        assertTrue(CrisisDetector.clampSosTimeoutMinutes(20) == 10)
    }
}
