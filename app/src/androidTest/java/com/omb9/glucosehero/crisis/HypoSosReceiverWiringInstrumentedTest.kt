package com.omb9.glucosehero.crisis

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Manifest wiring for Hypo SOS receivers.
 *
 * `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED` cannot
 * verify this: BOOT_COMPLETED is a protected broadcast and shell (uid 2000)
 * gets a SecurityException on Android 15. Resolution uses
 * [PackageManager.queryBroadcastReceivers]; the boot path is exercised by
 * invoking the receiver under test directly.
 */
@RunWith(AndroidJUnit4::class)
class HypoSosReceiverWiringInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pm = context.packageManager

    @After
    fun tearDown() {
        HypoSosBootReceiver.testEvaluateLatest = null
    }

    @Test
    fun bootCompletedResolvesToHypoSosBootReceiver() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName)
        val resolved = pm.queryBroadcastReceivers(intent, 0)
            .filter { it.activityInfo.packageName == context.packageName }
        val names = resolved.map { it.activityInfo.name }

        assertTrue(
            "BOOT_COMPLETED must resolve to HypoSosBootReceiver, got $names",
            names.contains(HypoSosBootReceiver::class.java.name),
        )
        assertFalse(
            "HypoSosAlarmReceiver must not receive BOOT_COMPLETED, got $names",
            names.contains(HypoSosAlarmReceiver::class.java.name),
        )

        val bootInfo = resolved.first {
            it.activityInfo.name == HypoSosBootReceiver::class.java.name
        }.activityInfo
        assertTrue("HypoSosBootReceiver must be enabled", bootInfo.enabled)
        assertTrue("HypoSosBootReceiver must be exported for system boot", bootInfo.exported)

        val evaluated = CountDownLatch(1)
        HypoSosBootReceiver.testEvaluateLatest = { evaluated.countDown() }
        HypoSosBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(
            "Direct BOOT_COMPLETED invocation must run evaluateLatest",
            evaluated.await(5, TimeUnit.SECONDS),
        )
    }

    @Test
    fun hypoSosAlarmReceiverIsNotExported() {
        val info = pm.getReceiverInfo(
            ComponentName(context, HypoSosAlarmReceiver::class.java),
            PackageManager.GET_META_DATA,
        )
        assertFalse(
            "HypoSosAlarmReceiver must not be exported; any app could cancel a live SOS",
            info.exported,
        )
    }
}
