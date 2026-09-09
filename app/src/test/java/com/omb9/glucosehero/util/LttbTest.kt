package com.omb9.glucosehero.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LttbTest {

    @Test
    fun `empty input stays empty`() {
        val empty = emptyList<Lttb.Point>()
        assertTrue(Lttb.downsample(empty, 100).isEmpty())
        assertSame(empty, Lttb.downsample(empty, 0))
    }

    @Test
    fun `tiny series shorter than the threshold is returned unchanged`() {
        val one = listOf(Lttb.Point(0.0, 100.0))
        val two = listOf(Lttb.Point(0.0, 100.0), Lttb.Point(1.0, 110.0))
        val three = listOf(
            Lttb.Point(0.0, 100.0),
            Lttb.Point(1.0, 110.0),
            Lttb.Point(2.0, 90.0),
        )

        assertSame(one, Lttb.downsample(one, 100))
        assertSame(two, Lttb.downsample(two, 50))
        assertSame(three, Lttb.downsample(three, 10))
        assertEquals(1, Lttb.downsample(two, 1).size)
        assertEquals(two.first(), Lttb.downsample(two, 2).first())
        assertEquals(two.last(), Lttb.downsample(two, 2).last())
    }

    @Test
    fun `threshold equal to n is identity`() {
        val points = (0 until 40).map { Lttb.Point(it.toDouble(), it.toDouble()) }
        val sampled = Lttb.downsample(points, 40)
        assertSame(points, sampled)
    }

    @Test
    fun `non-positive threshold returns the original list`() {
        val points = (0 until 10).map { Lttb.Point(it.toDouble(), 80.0) }
        assertSame(points, Lttb.downsample(points, 0))
        assertSame(points, Lttb.downsample(points, -4))
    }

    @Test
    fun `monotonic series keeps endpoints and a strictly increasing x`() {
        val points = (0 until 100).map { Lttb.Point(it.toDouble(), it.toDouble()) }
        val sampled = Lttb.downsample(points, 10)

        assertEquals(10, sampled.size)
        assertEquals(0.0, sampled.first().x, 0.0)
        assertEquals(99.0, sampled.last().x, 0.0)
        for (i in 1 until sampled.size) {
            assertTrue(sampled[i].x > sampled[i - 1].x)
            assertTrue(sampled[i].y > sampled[i - 1].y)
        }
    }

    @Test
    fun `a narrow spike is kept when a matching stride would skip it`() {
        val spikeIndex = 73
        val points = (0..200).map { i ->
            Lttb.Point(i.toDouble(), if (i == spikeIndex) 400.0 else 100.0)
        }
        val threshold = 20
        val strideStep = points.size / threshold
        val stride = points.filterIndexed { i, _ -> i % strideStep == 0 }

        assertTrue(
            "precondition: naive stride must miss the spike at $spikeIndex",
            stride.none { it.y == 400.0 },
        )

        val sampled = Lttb.downsample(points, threshold)
        assertEquals(threshold, sampled.size)
        assertTrue(sampled.any { it.y == 400.0 })
        assertEquals(0.0, sampled.first().x, 0.0)
        assertEquals(200.0, sampled.last().x, 0.0)
    }

    @Test
    fun `generic downsample preserves the original spike object`() {
        data class Row(val timestamp: Long, val mgdl: Double)

        val spike = Row(73_000L, 250.0)
        val rows = (0..120).map { i ->
            if (i == 73) spike else Row(i * 1_000L, 100.0)
        }

        val sampled = Lttb.downsample(rows, 15, { it.timestamp.toDouble() }, { it.mgdl })
        assertTrue(sampled.any { it === spike })
        assertEquals(15, sampled.size)
    }
}
