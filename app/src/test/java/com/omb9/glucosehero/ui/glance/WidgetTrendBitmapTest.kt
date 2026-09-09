package com.omb9.glucosehero.ui.glance

import com.omb9.glucosehero.domain.model.GlucosePointRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTrendBitmapTest {

    private val now = 1_000_000L

    @Test
    fun `limits payload to last 12 readings inside one hour`() {
        val points = (0 until 40).map { i ->
            GlucosePointRow(
                timestamp = now - (40 - i) * 60_000L,
                glucoseMgdl = 100.0 + i,
            )
        }
        val limited = WidgetTrendBitmap.limitReadings(points, now)
        assertEquals(WidgetTrendBitmap.MAX_TREND_POINTS, limited.size)
        assertTrue(limited.all { now - it.timestamp <= WidgetTrendBitmap.TREND_WINDOW_MS })
        assertEquals(points.takeLast(12), limited)
    }

    @Test
    fun `drops readings older than one hour`() {
        val points = listOf(
            GlucosePointRow(now - 2 * WidgetTrendBitmap.TREND_WINDOW_MS, 90.0),
            GlucosePointRow(now - 10 * 60_000L, 110.0),
            GlucosePointRow(now - 5 * 60_000L, 112.0),
        )
        val limited = WidgetTrendBitmap.limitReadings(points, now)
        assertEquals(2, limited.size)
        assertEquals(110.0, limited.first().glucoseMgdl, 0.0)
    }

    @Test
    fun `encoded PNG has fixed dimensions and stays under binder budget`() {
        val points = (0 until 12).map { i ->
            GlucosePointRow(
                timestamp = now - (12 - i) * 5 * 60_000L,
                glucoseMgdl = 90.0 + i * 3.0,
            )
        }
        val png = WidgetTrendBitmap.encodePng(points)
        assertTrue(png.isNotEmpty())
        assertEquals(WidgetTrendBitmap.WIDTH_PX, WidgetTrendBitmap.pngWidth(png))
        assertEquals(WidgetTrendBitmap.HEIGHT_PX, WidgetTrendBitmap.pngHeight(png))
        assertTrue(
            "PNG ${png.size} exceeded ${WidgetTrendBitmap.MAX_PNG_BYTES}",
            png.size <= WidgetTrendBitmap.MAX_PNG_BYTES,
        )
        val estimated = WidgetTrendBitmap.estimateBinderBytes(png.size)
        assertTrue("Estimated payload $estimated exceeded 1MB", estimated < 1_000_000)
        assertTrue(
            "Estimated payload $estimated exceeded ${WidgetTrendBitmap.MAX_PAYLOAD_BYTES}",
            estimated <= WidgetTrendBitmap.MAX_PAYLOAD_BYTES,
        )
        assertTrue(WidgetTrendBitmap.isWithinBinderBudget(png.size))
    }

    @Test
    fun `uncompressed ARGB of the fixed bitmap is far below 1MB`() {
        val argbBytes = WidgetTrendBitmap.WIDTH_PX * WidgetTrendBitmap.HEIGHT_PX * 4
        assertEquals(17_280, argbBytes)
        assertTrue(argbBytes < 1_000_000)
        assertTrue(argbBytes < WidgetTrendBitmap.MAX_PAYLOAD_BYTES)
    }

    @Test
    fun `single point produces no PNG so Glance never parcels a list of readings`() {
        val png = WidgetTrendBitmap.encodePng(
            listOf(GlucosePointRow(now, 120.0)),
        )
        assertEquals(0, png.size)
    }
}
