package com.omb9.glucosehero.wear.data

import com.omb9.glucosehero.wear.protocol.WearTrend
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearFreshnessPolicyTest {

    private val now = 1_700_000_000_000L
    private val snapshot = WearGlucoseSnapshot(
        hasReading = true,
        glucoseMgdl = 112f,
        timestampMillis = now,
        trend = WearTrend.FLAT,
    )

    @Test
    fun `no redundant update when rendered signature is unchanged`() {
        val signature = WearFreshnessPolicy.visualSignature(snapshot, now)
        assertFalse(WearFreshnessPolicy.shouldRequestUpdate(signature, signature))
        assertTrue(WearFreshnessPolicy.shouldRequestUpdate(null, signature))
    }

    @Test
    fun `Fresh to Stale changes the signature`() {
        val fresh = WearFreshnessPolicy.visualSignature(snapshot, now)
        val staleNow = now + 9L * 60_000L
        val stale = WearFreshnessPolicy.visualSignature(snapshot, staleNow)
        assertTrue(WearFreshnessPolicy.shouldRequestUpdate(fresh, stale))
        assertTrue(stale.contains("stale:"))
        assertTrue(fresh.endsWith("|fresh"))
    }

    @Test
    fun `stale crossing is one millisecond past the Fresh ceiling`() {
        assertEquals(
            now + GlucoseFreshness.FRESH_MAX_AGE_MILLIS + 1L,
            WearFreshnessPolicy.staleCrossingAtMillis(now, now),
        )
        assertNull(
            WearFreshnessPolicy.staleCrossingAtMillis(
                now,
                now + GlucoseFreshness.FRESH_MAX_AGE_MILLIS + 1L,
            ),
        )
    }

    @Test
    fun `manifest update period matches the documented constant`() {
        assertEquals(600, WearFreshnessPolicy.COMPLICATION_UPDATE_PERIOD_SECONDS)
        val manifest = wearManifestFile().readText()
        assertTrue(
            manifest.contains("android.support.wearable.complications.UPDATE_PERIOD_SECONDS"),
        )
        assertTrue(manifest.contains("android:value=\"600\""))
        assertFalse(manifest.contains("android:value=\"0\""))
    }

    private fun wearManifestFile(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("wear/src/main/AndroidManifest.xml"),
        )
        return candidates.first { it.exists() }
    }
}
