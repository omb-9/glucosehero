package com.omb9.glucosehero.crisis

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AlarmManager is the durable SOS timeout backstop. The foreground service
 * poll loop must not be the only path that can call [HypoSosManager.onTimeout].
 */
@RunWith(AndroidJUnit4::class)
class HypoSosTimeoutAlarmInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        HypoSosEntryPoint::class.java,
    )
    private val manager = entryPoint.hypoSosManager()
    private val store = entryPoint.settingsDataStore()

    private var previousEnabled = false
    private var previousContacts = emptyList<CaregiverContact>()
    private var previousPending: String? = null

    @Before
    fun setUp() = runBlocking {
        previousEnabled = store.hypoSosEnabled.first()
        previousContacts = store.caregiverContactsSnapshot()
        previousPending = store.hypoSosPendingJson.first()
        store.setHypoSosEnabled(false)
        store.setCaregiverContacts(emptyList())
        HypoSosManager.testSkipForegroundService = true
        HypoSosManager.testSkipDispatch = true
        HypoSosForegroundService.pollLoopTimeoutCount.set(0)
        manager.cancelPendingForTest()
        context.stopService(Intent(context, HypoSosForegroundService::class.java))
        waitUntil("foreground service stopped") { !HypoSosForegroundService.isRunning }
    }

    @After
    fun tearDown() = runBlocking {
        HypoSosManager.testOnTimeout = null
        HypoSosManager.testSkipDispatch = false
        HypoSosManager.testSkipForegroundService = false
        manager.cancelPendingForTest()
        store.setCaregiverContacts(previousContacts)
        store.setHypoSosPendingJson(previousPending)
        store.setHypoSosEnabled(previousEnabled)
        HypoSosForegroundService.pollLoopTimeoutCount.set(0)
    }

    @Test
    fun exactAlarmPermissionIsDeclaredAndGranted() {
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toList()
            .orEmpty()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertTrue(
                "API 33+ must declare USE_EXACT_ALARM, got $requested",
                Manifest.permission.USE_EXACT_ALARM in requested,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            assertTrue(
                "SOS timeout backstop requires canScheduleExactAlarms(); " +
                    "inexact setAndAllowWhileIdle is Doze-deferrable",
                alarmManager.canScheduleExactAlarms(),
            )
        }
    }

    @Test
    fun timeoutAlarmFiresWithForegroundServiceStopped() = runBlocking {
        assertFalse(
            "poll loop must be stopped; it previously masked a broken alarm",
            HypoSosForegroundService.isRunning,
        )
        val fired = CountDownLatch(1)
        HypoSosManager.testOnTimeout = { fired.countDown() }

        val timeoutAt = System.currentTimeMillis() + 4_000L
        manager.armTimeoutBackstopForTest(timeoutAt)
        assertTrue(
            "timeout PendingIntent must exist after schedule",
            manager.timeoutAlarmTokenExists(),
        )
        assertPendingBatch(expectPresent = true)
        assertFalse(HypoSosForegroundService.isRunning)

        assertTrue(
            "AlarmManager must deliver HYPO_SOS_TIMEOUT with the FGS stopped",
            fired.await(25, TimeUnit.SECONDS),
        )
        assertFalse(
            "FGS must stay stopped so this is not the 1-second poll",
            HypoSosForegroundService.isRunning,
        )
        assertTrue(
            "poll loop must not have called onTimeout",
            HypoSosForegroundService.pollLoopTimeoutCount.get() == 0,
        )
        manager.hydrate()
        assertTrue(
            "onTimeout must clear pending after the alarm",
            manager.pending.value == null,
        )
    }

    @Test
    fun cancelRemovesPendingBatchNotJustDumpsysHistory() = runBlocking {
        val fired = CountDownLatch(1)
        HypoSosManager.testOnTimeout = { fired.countDown() }

        val timeoutAt = System.currentTimeMillis() + 8_000L
        manager.armTimeoutBackstopForTest(timeoutAt)
        assertTrue(manager.timeoutAlarmTokenExists())
        assertPendingBatch(expectPresent = true)

        manager.cancelPendingForTest()
        assertFalse(
            "cancelTimeoutAlarm must drop the PendingIntent token",
            manager.timeoutAlarmTokenExists(),
        )
        assertPendingBatch(expectPresent = false)

        delay(10_000L)
        assertFalse(
            "stale timeout alarm must not re-enter onTimeout after cancel",
            fired.await(0, TimeUnit.SECONDS),
        )
    }

    private fun dumpsysAlarm(): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .executeShellCommand("dumpsys alarm"),
        ).bufferedReader().use { it.readText() }

    private suspend fun assertPendingBatch(expectPresent: Boolean) {
        waitUntil("dumpsys alarm readable") { dumpsysAlarm().isNotBlank() }
        val dump = dumpsysAlarm()
        if ("Pending alarm batches" !in dump) {
            throw AssertionError(
                "dumpsys alarm has no 'Pending alarm batches' section, so a " +
                    "HYPO_SOS_TIMEOUT line cannot be classified as live vs history. " +
                    "First 2000 chars:\n${dump.take(2000)}",
            )
        }
        if (expectPresent) {
            waitUntil("HYPO_SOS_TIMEOUT in pending alarm batches") {
                HypoSosAlarmDump.actionInPendingBatches(dumpsysAlarm(), HypoSosManager.ACTION_TIMEOUT)
            }
        } else {
            waitUntil("HYPO_SOS_TIMEOUT gone from pending batches") {
                !HypoSosAlarmDump.actionInPendingBatches(
                    dumpsysAlarm(),
                    HypoSosManager.ACTION_TIMEOUT,
                )
            }
        }
    }

    private suspend fun waitUntil(label: String, condition: () -> Boolean) {
        try {
            withTimeout(8_000) {
                while (!condition()) delay(100)
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError(label, e)
        }
    }
}
