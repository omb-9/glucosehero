package com.omb9.glucosehero.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDownsampleTest {

    @Test
    fun `24h skips sql buckets so LTTB sees the raw day`() {
        assertNull(ChartDownsample.bucketMillisForRangeDays(1))
        assertEquals(400, ChartDownsample.memoryThreshold(1))
    }

    @Test
    fun `week and month use 5 and 15 minute buckets`() {
        assertEquals(ChartDownsample.FIVE_MIN_MILLIS, ChartDownsample.bucketMillisForRangeDays(7))
        assertEquals(ChartDownsample.FIFTEEN_MIN_MILLIS, ChartDownsample.bucketMillisForRangeDays(14))
        assertEquals(ChartDownsample.FIFTEEN_MIN_MILLIS, ChartDownsample.bucketMillisForRangeDays(30))
    }

    @Test
    fun `90 day window uses hourly buckets`() {
        assertEquals(ChartDownsample.ONE_HOUR_MILLIS, ChartDownsample.bucketMillisForRangeDays(90))
        assertEquals(800, ChartDownsample.memoryThreshold(90))
    }

    @Test
    fun `pixel budget is a few hundred for a phone-width 24h chart`() {
        val threshold = ChartDownsample.pixelThreshold(widthDp = 360f, rangeDays = 1)
        assertTrue(threshold in 120..400)
        assertEquals(400, ChartDownsample.pixelThreshold(widthDp = 360f, rangeDays = 1))
    }

    @Test
    fun `pixel budget never exceeds the memory cap for that range`() {
        val day = ChartDownsample.pixelThreshold(widthDp = 2000f, rangeDays = 1)
        val ninety = ChartDownsample.pixelThreshold(widthDp = 2000f, rangeDays = 90)
        assertEquals(ChartDownsample.memoryThreshold(1), day)
        assertEquals(ChartDownsample.memoryThreshold(90), ninety)
    }
}
