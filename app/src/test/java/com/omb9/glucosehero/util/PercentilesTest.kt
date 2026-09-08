package com.omb9.glucosehero.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PercentilesTest {

    @Test
    fun `median of an even-length list interpolates`() {
        assertEquals(2.5, Percentiles.median(listOf(1.0, 2.0, 3.0, 4.0)), 1e-9)
    }

    @Test
    fun `median of an odd-length list returns the middle value`() {
        assertEquals(3.0, Percentiles.median(listOf(1.0, 2.0, 3.0, 4.0, 5.0)), 1e-9)
    }

    @Test
    fun `median of a single element is that element`() {
        assertEquals(42.0, Percentiles.median(listOf(42.0)), 1e-9)
    }

    @Test
    fun `median of identical values is that value`() {
        assertEquals(5.0, Percentiles.median(listOf(5.0, 5.0, 5.0)), 1e-9)
    }

    @Test
    fun `median sorts its input internally`() {
        assertEquals(2.5, Percentiles.median(listOf(4.0, 1.0, 3.0, 2.0)), 1e-9)
    }

    @Test
    fun `quartiles return p25 median and p75`() {
        val (p25, median, p75) = Percentiles.quartiles(listOf(1.0, 2.0, 3.0, 4.0, 5.0))

        assertEquals(2.0, p25, 1e-9)
        assertEquals(3.0, median, 1e-9)
        assertEquals(4.0, p75, 1e-9)
    }

    @Test
    fun `percentile interpolates between the closest ranks`() {
        val sorted = listOf(1.0, 2.0, 3.0, 4.0)

        assertEquals(1.75, Percentiles.percentile(sorted, 0.25), 1e-9)
        assertEquals(2.5, Percentiles.percentile(sorted, 0.5), 1e-9)
        assertEquals(3.25, Percentiles.percentile(sorted, 0.75), 1e-9)
    }
}
