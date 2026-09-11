package com.omb9.glucosehero.ui.glance

import com.omb9.glucosehero.data.cgm.GlucoseFreshness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetFreshnessPolicyTest {

    private val writtenAt = 1_700_000_000_000L
    private val glucoseMgdl = 112.0
    private val unitName = "MGDL"

    @Test
    fun `reading that was Fresh at write time is Stale nine minutes later with no new data`() {
        val atWrite = WidgetFreshnessPolicy.renderKey(
            glucoseMgdl = glucoseMgdl,
            timestampMillis = writtenAt,
            nowMillis = writtenAt,
            unitName = unitName,
        )
        val nineMinutesLater = writtenAt + 9L * 60_000L
        val later = WidgetFreshnessPolicy.renderKey(
            glucoseMgdl = glucoseMgdl,
            timestampMillis = writtenAt,
            nowMillis = nineMinutesLater,
            unitName = unitName,
        )
        assertEquals(
            WidgetFreshnessPolicy.freshnessKey(GlucoseFreshness.Fresh),
            WidgetFreshnessPolicy.freshnessKey(
                GlucoseFreshness.classify(writtenAt, writtenAt),
            ),
        )
        assertTrue(atWrite.endsWith("|fresh"))
        assertTrue(later.contains("|stale:"))
        assertTrue(WidgetFreshnessPolicy.shouldUpdate(atWrite, later))
    }

    @Test
    fun `unchanged caption does not request a widget update`() {
        val key = WidgetFreshnessPolicy.renderKey(
            glucoseMgdl = glucoseMgdl,
            timestampMillis = writtenAt,
            nowMillis = writtenAt + 60_000L,
            unitName = unitName,
        )
        assertFalse(WidgetFreshnessPolicy.shouldUpdate(key, key))
        assertTrue(WidgetFreshnessPolicy.shouldUpdate(null, key))
    }

    @Test
    fun `one-shot delay is remaining Fresh time plus one millisecond`() {
        val now = writtenAt + 60_000L
        assertEquals(
            GlucoseFreshness.FRESH_MAX_AGE_MILLIS - 60_000L + 1L,
            WidgetFreshnessPolicy.millisUntilStaleCaption(writtenAt, now),
        )
        assertNull(
            WidgetFreshnessPolicy.millisUntilStaleCaption(
                writtenAt,
                writtenAt + GlucoseFreshness.FRESH_MAX_AGE_MILLIS + 1L,
            ),
        )
        assertNull(WidgetFreshnessPolicy.millisUntilStaleCaption(null, now))
    }
}
