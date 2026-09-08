package com.omb9.glucosehero.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ChartRevealTierTest {

    @Test
    fun `24h labels markers with values`() {
        assertEquals(ChartRevealTier.DOT_PLUS_VALUE, ChartRevealTier.forRangeDays(1))
    }

    @Test
    fun `7d labels markers with values`() {
        assertEquals(ChartRevealTier.DOT_PLUS_VALUE, ChartRevealTier.forRangeDays(7))
    }

    @Test
    fun `14d shows dots only`() {
        assertEquals(ChartRevealTier.DOT, ChartRevealTier.forRangeDays(14))
    }

    @Test
    fun `30d shows no markers`() {
        assertEquals(ChartRevealTier.LINE_ONLY, ChartRevealTier.forRangeDays(30))
    }

    @Test
    fun `90d shows no markers`() {
        assertEquals(ChartRevealTier.LINE_ONLY, ChartRevealTier.forRangeDays(90))
    }
}
