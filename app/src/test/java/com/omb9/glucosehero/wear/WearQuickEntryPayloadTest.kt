package com.omb9.glucosehero.wear

import com.omb9.glucosehero.domain.model.Metric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearQuickEntryPayloadTest {

    @Test
    fun `water maps to a hashtag note`() {
        val event = WearQuickEntryPayload(
            type = WearQuickEntryType.WATER,
            amount = 250.0,
            timestampMillis = 1_000L,
        ).toLogEvent()
        assertEquals(1_000L, event.timestamp)
        assertEquals("#water 250 ml", event.note)
        assertTrue(Metric.NOTE in event.presentMetrics)
    }

    @Test
    fun `carbs maps to carbsGrams`() {
        val event = WearQuickEntryPayload(
            type = WearQuickEntryType.CARBS,
            amount = 15.4,
            timestampMillis = 2_000L,
        ).toLogEvent()
        assertEquals(15, event.carbsGrams)
        assertTrue(Metric.CARBS in event.presentMetrics)
    }

    @Test
    fun `insulin maps to bolus units`() {
        val event = WearQuickEntryPayload(
            type = WearQuickEntryType.INSULIN,
            amount = 1.5,
            timestampMillis = 3_000L,
        ).toLogEvent()
        assertEquals(1.5, event.insulinBolusUnits!!, 1e-6)
        assertTrue(Metric.INSULIN in event.presentMetrics)
    }

    @Test
    fun `json round trip matches the wear payload`() {
        val original = WearQuickEntryPayload(
            type = WearQuickEntryType.CARBS,
            amount = 15.0,
            timestampMillis = 42L,
        )
        val encoded = WearSyncProtocol.json.encodeToString(
            WearQuickEntryPayload.serializer(),
            original,
        )
        val decoded = WearSyncProtocol.json.decodeFromString(
            WearQuickEntryPayload.serializer(),
            encoded,
        )
        assertEquals(original, decoded)
    }
}
