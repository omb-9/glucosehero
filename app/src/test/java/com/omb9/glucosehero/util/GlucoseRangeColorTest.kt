package com.omb9.glucosehero.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.ThemeMode
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseRangeColorTest {

    @Test
    fun `below 54 is very low`() {
        assertEquals(RangeCategory.VERY_LOW, GlucoseRangeColor.forValue(53f, 80f, 180f))
        assertEquals(RangeCategory.VERY_LOW, GlucoseRangeColor.forValue(10f, 70f, 180f))
    }

    @Test
    fun `exactly 54 is low not very low`() {
        assertEquals(RangeCategory.LOW, GlucoseRangeColor.forValue(54f, 80f, 180f))
    }

    @Test
    fun `below target low but at least 54 is low`() {
        assertEquals(RangeCategory.LOW, GlucoseRangeColor.forValue(69f, 80f, 180f))
    }

    @Test
    fun `value at target low is in range`() {
        assertEquals(RangeCategory.IN_RANGE, GlucoseRangeColor.forValue(80f, 80f, 180f))
    }

    @Test
    fun `value between targets is in range`() {
        assertEquals(RangeCategory.IN_RANGE, GlucoseRangeColor.forValue(120f, 80f, 180f))
    }

    @Test
    fun `value at target high is in range`() {
        assertEquals(RangeCategory.IN_RANGE, GlucoseRangeColor.forValue(180f, 80f, 180f))
    }

    @Test
    fun `above target high up to 250 is high`() {
        assertEquals(RangeCategory.HIGH, GlucoseRangeColor.forValue(181f, 80f, 180f))
        assertEquals(RangeCategory.HIGH, GlucoseRangeColor.forValue(250f, 80f, 180f))
    }

    @Test
    fun `above 250 is very high`() {
        assertEquals(RangeCategory.VERY_HIGH, GlucoseRangeColor.forValue(251f, 80f, 180f))
    }

    @Test
    fun `reads its parameters rather than hardcoded 70 and 180`() {
        // Non-default targets prove forValue actually reads targetLow/targetHigh.
        assertEquals(RangeCategory.LOW, GlucoseRangeColor.forValue(99f, 100f, 200f))
        assertEquals(RangeCategory.IN_RANGE, GlucoseRangeColor.forValue(100f, 100f, 200f))
        assertEquals(RangeCategory.IN_RANGE, GlucoseRangeColor.forValue(200f, 100f, 200f))
        assertEquals(RangeCategory.HIGH, GlucoseRangeColor.forValue(201f, 100f, 200f))
    }

    @Test
    fun `five range colors are internally distinct per theme`() {
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.AMOLED)) {
            val colors = RangeCategory.entries.map { GlucoseRangeColor.colorFor(it, theme) }
            assertEquals(5, colors.distinct().size)
        }
    }

    @Test
    fun `range colors remain distinguishable from all six accents in both themes`() {
        val accents = AccentColor.entries.map { Color(it.argb) }
        for (theme in listOf(ThemeMode.LIGHT, ThemeMode.AMOLED)) {
            for (category in RangeCategory.entries) {
                val range = GlucoseRangeColor.colorFor(category, theme)
                for (accent in accents) {
                    assertTrue(
                        "range $category/$theme too close to accent ${accent.toArgb()}",
                        rgbDistance(range, accent) >= MIN_ACCENT_DISTANCE,
                    )
                }
            }
        }
    }

    private fun rgbDistance(a: Color, b: Color): Double {
        val ar = a.toArgb()
        val br = b.toArgb()
        fun r(c: Int) = (c shr 16) and 0xFF
        fun g(c: Int) = (c shr 8) and 0xFF
        fun bl(c: Int) = c and 0xFF
        val dr = r(ar) - r(br)
        val dg = g(ar) - g(br)
        val db = bl(ar) - bl(br)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    private companion object {
        /**
         * RGB-distance floor (0-441 range) between any range color and any accent color. This is
         * intentionally a floor, not a WCAG contrast claim: it catches exact/near matches while
         * allowing the established blue/cyan distinction (the closest pair is ~25).
         */
        const val MIN_ACCENT_DISTANCE = 20.0
    }
}
