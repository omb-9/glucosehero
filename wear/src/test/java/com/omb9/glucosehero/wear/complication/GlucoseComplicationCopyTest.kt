package com.omb9.glucosehero.wear.complication

import com.omb9.glucosehero.wear.data.GlucoseFreshness
import com.omb9.glucosehero.wear.data.WearGlucoseSnapshot
import com.omb9.glucosehero.wear.protocol.WearTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseComplicationCopyTest {

    private val snapshot = WearGlucoseSnapshot(
        hasReading = true,
        glucoseMgdl = 112f,
        timestampMillis = 1_700_000_000_000L,
        trend = WearTrend.FLAT,
    )

    @Test
    fun `longText always carries age when Fresh`() {
        val text = GlucoseComplicationCopy.longText(
            hasReading = true,
            valueLine = "112 mg/dL →",
            compactAge = "Just now",
        )
        assertEquals("112 mg/dL → · Just now", text)
        assertTrue(text.contains("Just now"))
    }

    @Test
    fun `longText carries age when Stale`() {
        val text = GlucoseComplicationCopy.longText(
            hasReading = true,
            valueLine = "112 mg/dL →",
            compactAge = "40 min ago",
        )
        assertEquals("112 mg/dL → · 40 min ago", text)
    }

    @Test
    fun `longText is compact age when NoData`() {
        assertEquals(
            "No reading yet",
            GlucoseComplicationCopy.longText(
                hasReading = false,
                valueLine = "",
                compactAge = "No reading yet",
            ),
        )
    }

    @Test
    fun `contentDescription includes value and age when Fresh`() {
        val description = GlucoseComplicationCopy.contentDescription(
            label = "Glucose",
            snapshot = snapshot,
            compactAge = "Just now",
        )
        assertTrue(description.contains("112"))
        assertTrue(description.contains("Just now"))
    }

    @Test
    fun `contentDescription includes value and age when Stale`() {
        val description = GlucoseComplicationCopy.contentDescription(
            label = "Glucose",
            snapshot = snapshot,
            compactAge = "40 min ago",
        )
        assertTrue(description.contains("112"))
        assertTrue(description.contains("40 min ago"))
    }

    @Test
    fun `contentDescription is compact age when NoData`() {
        val empty = WearGlucoseSnapshot(hasReading = false)
        assertEquals(
            "No reading yet",
            GlucoseComplicationCopy.contentDescription(
                label = "Glucose",
                snapshot = empty,
                compactAge = "No reading yet",
            ),
        )
    }

    @Test
    fun `title uses compact age when Stale or NoData`() {
        assertEquals(
            "40 min ago",
            GlucoseComplicationCopy.title(
                freshness = GlucoseFreshness.Stale(40L * 60_000L),
                compactAge = "40 min ago",
                trendArrow = "→",
                fallbackLabel = "Glucose",
            ),
        )
        assertEquals(
            "No reading yet",
            GlucoseComplicationCopy.title(
                freshness = GlucoseFreshness.NoData,
                compactAge = "No reading yet",
                trendArrow = "",
                fallbackLabel = "Glucose",
            ),
        )
        assertFalse(
            GlucoseComplicationCopy.title(
                freshness = GlucoseFreshness.Fresh,
                compactAge = "Just now",
                trendArrow = "→",
                fallbackLabel = "Glucose",
            ).contains("Just now"),
        )
    }
}
