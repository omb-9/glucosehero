package com.omb9.glucosehero.data.cgm.xdrip

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.ui.settings.XdripSettingsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sends real xDrip+ / AAPS actions at the app's receivers and asserts a
 * single [GlucoseSampleSource.XDRIP_BROADCAST] row per reading.
 *
 * FEATURE: cgm-direct-ingest
 */
@RunWith(AndroidJUnit4::class)
class XdripBroadcastIngestInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val entryPoint: XdripSettingsEntryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        XdripSettingsEntryPoint::class.java,
    )
    private val store = entryPoint.settingsDataStore()
    private val dao = entryPoint.glucoseSampleDao()

    private val bgTimestamp = 4_000_000_000_000L
    private val sgvTimestamp = 4_000_000_000_100L
    private var previousEnabled = false

    @Before
    fun setUp() = runBlocking {
        previousEnabled = store.xdripBroadcastEnabledSnapshot()
        XdripBroadcastHandler.testIdentityOverride =
            XdripSenderIdentity.Known("com.eveningoutpost.dexdrip")
        XdripBroadcastHandler.testAllowlistedInstalledOverride = true
        dao.deleteByExternalId(
            GlucoseSampleSource.XDRIP_BROADCAST,
            XdripBroadcastMapper.externalId(bgTimestamp),
        )
        dao.deleteByExternalId(
            GlucoseSampleSource.XDRIP_BROADCAST,
            XdripBroadcastMapper.externalId(sgvTimestamp),
        )
        store.setXdripBroadcastEnabled(true)
        waitUntil("manifest receiver enabled") {
            context.packageManager.getComponentEnabledSetting(
                ComponentName(context, XdripGlucoseBroadcastReceiver::class.java),
            ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
    }

    @After
    fun tearDown() = runBlocking {
        XdripBroadcastHandler.testIdentityOverride = null
        XdripBroadcastHandler.testAllowlistedInstalledOverride = null
        dao.deleteByExternalId(
            GlucoseSampleSource.XDRIP_BROADCAST,
            XdripBroadcastMapper.externalId(bgTimestamp),
        )
        dao.deleteByExternalId(
            GlucoseSampleSource.XDRIP_BROADCAST,
            XdripBroadcastMapper.externalId(sgvTimestamp),
        )
        store.setXdripBroadcastEnabled(previousEnabled)
    }

    @Test
    fun broadcastProducesOneRowPerReadingAndIsIdempotent() = runBlocking {
        sendBgEstimate(bgTimestamp, 121.0)
        val bg = awaitRow(bgTimestamp)
        assertEquals(GlucoseSampleSource.XDRIP_BROADCAST, bg.source)
        assertEquals(121.0, bg.glucoseMgdl, 1e-9)
        assertEquals(XdripBroadcastMapper.externalId(bgTimestamp), bg.externalId)

        sendBgEstimate(bgTimestamp, 121.0)
        delay(500)
        val bgAgain = dao.samplesBetween(bgTimestamp, bgTimestamp)
            .filter { it.source == GlucoseSampleSource.XDRIP_BROADCAST }
        assertEquals(1, bgAgain.size)

        sendAapsSgv(sgvTimestamp, 110.0)
        val sgv = awaitRow(sgvTimestamp)
        assertEquals(GlucoseSampleSource.XDRIP_BROADCAST, sgv.source)
        assertEquals(110.0, sgv.glucoseMgdl, 1e-9)
        assertEquals(XdripBroadcastMapper.externalId(sgvTimestamp), sgv.externalId)

        sendAapsSgv(sgvTimestamp, 110.0)
        delay(500)
        val sgvAgain = dao.samplesBetween(sgvTimestamp, sgvTimestamp)
            .filter { it.source == GlucoseSampleSource.XDRIP_BROADCAST }
        assertEquals(1, sgvAgain.size)
    }

    private fun sendBgEstimate(timestampMillis: Long, mgdl: Double) {
        val intent = Intent(XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE).apply {
            setPackage(context.packageName)
            putExtra(XdripBroadcastIntents.EXTRA_TIMESTAMP, timestampMillis)
            putExtra(XdripBroadcastIntents.EXTRA_BG_ESTIMATE, mgdl)
            putExtra(XdripBroadcastIntents.EXTRA_BG_SLOPE_NAME, "Flat")
        }
        context.sendBroadcast(intent)
    }

    private fun sendAapsSgv(timestampMillis: Long, mgdl: Double) {
        val json = """[{"mills":$timestampMillis,"mgdl":$mgdl,"direction":"Flat"}]"""
        val intent = Intent(XdripBroadcastIntents.ACTION_NEW_SGV).apply {
            setPackage(context.packageName)
            putExtra(XdripBroadcastIntents.EXTRA_SGVS, json)
        }
        context.sendBroadcast(intent)
    }

    private suspend fun awaitRow(timestampMillis: Long) = withTimeout(15_000) {
        while (true) {
            val rows = dao.samplesBetween(timestampMillis, timestampMillis)
                .filter { it.source == GlucoseSampleSource.XDRIP_BROADCAST }
            if (rows.size == 1) return@withTimeout rows.single()
            delay(50)
        }
        error("unreachable")
    }

    private suspend fun waitUntil(label: String, condition: () -> Boolean) {
        withTimeout(10_000) {
            while (!condition()) {
                delay(50)
            }
        }
        check(condition()) { label }
    }
}
