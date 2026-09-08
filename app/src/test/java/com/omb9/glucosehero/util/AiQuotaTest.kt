package com.omb9.glucosehero.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiQuotaTest {

    @Test
    fun `daily limits per tier`() {
        assertEquals(10, AiQuota.dailyLimit(AiTier.FREE))
        assertEquals(50, AiQuota.dailyLimit(AiTier.PRO))
        assertNull(AiQuota.dailyLimit(AiTier.BYOK))
    }

    @Test
    fun `free exhausts exactly at the limit and not one below`() {
        assertFalse(AiQuota.isExhausted(AiTier.FREE, 9))
        assertTrue(AiQuota.isExhausted(AiTier.FREE, 10))
        assertTrue(AiQuota.isExhausted(AiTier.FREE, 11))
    }

    @Test
    fun `pro exhausts exactly at the limit and not one below`() {
        assertFalse(AiQuota.isExhausted(AiTier.PRO, 49))
        assertTrue(AiQuota.isExhausted(AiTier.PRO, 50))
        assertTrue(AiQuota.isExhausted(AiTier.PRO, 51))
    }

    @Test
    fun `byok never exhausts at any count`() {
        assertFalse(AiQuota.isExhausted(AiTier.BYOK, 0))
        assertFalse(AiQuota.isExhausted(AiTier.BYOK, 10))
        assertFalse(AiQuota.isExhausted(AiTier.BYOK, 50))
        assertFalse(AiQuota.isExhausted(AiTier.BYOK, Int.MAX_VALUE))
    }

    @Test
    fun `remaining counts down and floors at zero`() {
        assertEquals(10, AiQuota.remaining(AiTier.FREE, 0))
        assertEquals(1, AiQuota.remaining(AiTier.FREE, 9))
        assertEquals(0, AiQuota.remaining(AiTier.FREE, 10))
        assertEquals(0, AiQuota.remaining(AiTier.FREE, 999))
    }

    @Test
    fun `byok remaining is always null`() {
        assertNull(AiQuota.remaining(AiTier.BYOK, 0))
        assertNull(AiQuota.remaining(AiTier.BYOK, Int.MAX_VALUE))
    }

    @Test
    fun `day boundary resets only when stored day differs`() {
        assertTrue(AiQuota.shouldReset(null, "2026-07-31"))
        assertTrue(AiQuota.shouldReset("2026-07-30", "2026-07-31"))
        assertFalse(AiQuota.shouldReset("2026-07-31", "2026-07-31"))
    }
}
