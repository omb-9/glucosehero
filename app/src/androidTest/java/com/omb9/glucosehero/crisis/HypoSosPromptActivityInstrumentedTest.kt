package com.omb9.glucosehero.crisis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.util.Formatters
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notification full-screen intent is suppressed while the app is
 * foregrounded, so [HypoSosManager.launchPromptUi] must be able to start
 * [HypoSosPromptActivity] from the same process.
 */
@RunWith(AndroidJUnit4::class)
class HypoSosPromptActivityInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        HypoSosEntryPoint::class.java,
    )
    private val manager = entryPoint.hypoSosManager()
    private val store = entryPoint.settingsDataStore()

    private var previousUnit = GlucoseUnit.MGDL
    private var previousPending: String? = null

    @Before
    fun setUp() = runBlocking {
        previousUnit = store.settings.first().unit
        previousPending = store.hypoSosPendingJson.first()
        store.setUnit(GlucoseUnit.MMOL)
        val now = System.currentTimeMillis()
        val pending = HypoSosPending(
            startedAtMillis = now,
            timeoutAtMillis = now + 120_000L,
            glucoseMgdl = 45.0,
            velocityMgdlPerMin = -1.0,
            trendLabel = "falling",
        )
        store.setHypoSosPendingJson(AppJson.encodeToString(pending))
        manager.hydrate()
    }

    @After
    fun tearDown() = runBlocking {
        manager.cancelPendingForTest()
        store.setHypoSosPendingJson(previousPending)
        store.setUnit(previousUnit)
    }

    @Test
    fun launchPromptUiPathOpensActivityWithMmolReading() {
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toList()
            .orEmpty()
        assertFalse(
            "RECORD_AUDIO must not be requested, got $requested",
            Manifest.permission.RECORD_AUDIO in requested,
        )

        val intent = Intent(context, HypoSosPromptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val startedAt = SystemClock.elapsedRealtime()
        val activity = instrumentation.startActivitySync(intent)
        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
        assertTrue(
            "onCreate must not block; startActivitySync took ${elapsedMs}ms",
            elapsedMs < 3_000L,
        )
        assertTrue(
            "launchPromptUi-equivalent start must open HypoSosPromptActivity",
            activity is HypoSosPromptActivity,
        )
        assertFalse(activity.isFinishing)

        instrumentation.waitForIdleSync()
        val expected = Formatters.glucoseWithUnit(45.0, GlucoseUnit.MMOL)
        assertTrue(
            "prompt body should show $expected when the display unit is mmol/L",
            waitForAccessibilityText(expected, timeoutMs = 5_000L),
        )

        activity.finish()
        instrumentation.waitForIdleSync()
    }

    private fun waitForAccessibilityText(needle: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null && nodeContains(root, needle)) return true
            Thread.sleep(150L)
        }
        return false
    }

    private fun nodeContains(node: AccessibilityNodeInfo, needle: String): Boolean {
        val text = node.text?.toString()
        val description = node.contentDescription?.toString()
        if (text?.contains(needle) == true || description?.contains(needle) == true) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (nodeContains(child, needle)) return true
        }
        return false
    }
}
