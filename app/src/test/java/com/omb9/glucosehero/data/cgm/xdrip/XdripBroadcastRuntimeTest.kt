package com.omb9.glucosehero.data.cgm.xdrip

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XdripBroadcastRuntimeTest {

    @Test
    fun `register twice registers runtime once and enables the component once`() {
        val platform = FakePlatform()
        val runtime = runtime(platform)
        runtime.register()
        runtime.register()
        assertEquals(1, platform.runtimeRegisters)
        assertEquals(1, platform.packageMonitors)
        assertEquals(1, platform.manifestEnableCalls)
        assertEquals(0, platform.manifestDisableCalls)
        assertTrue(runtime.isRuntimeRegistered())
        assertTrue(runtime.isManifestEnabledThisProcess())
        assertTrue(platform.manifestEnabled == true)
    }

    @Test
    fun `unregister without register does not throw`() {
        val platform = FakePlatform()
        val runtime = runtime(platform)
        runtime.unregister()
        assertEquals(0, platform.runtimeUnregisters)
        assertEquals(1, platform.manifestDisableCalls)
        assertTrue(platform.manifestEnabled == false)
        assertFalse(runtime.isRuntimeRegistered())
    }

    @Test
    fun `register unregister register re-registers`() {
        val platform = FakePlatform()
        val runtime = runtime(platform)
        runtime.register()
        runtime.unregister()
        runtime.register()
        assertEquals(2, platform.runtimeRegisters)
        assertEquals(2, platform.packageMonitors)
        assertEquals(1, platform.runtimeUnregisters)
        assertEquals(1, platform.packageMonitorUnregisters)
        assertEquals(2, platform.manifestEnableCalls)
        assertEquals(1, platform.manifestDisableCalls)
        assertTrue(runtime.isRuntimeRegistered())
        assertTrue(runtime.isManifestEnabledThisProcess())
        assertTrue(platform.manifestEnabled == true)
    }

    @Test
    fun `manifest component tracks the toggle`() {
        val platform = FakePlatform()
        val runtime = runtime(platform)
        assertTrue(platform.manifestEnabled != true)
        runtime.register()
        assertTrue(platform.manifestEnabled == true)
        runtime.unregister()
        assertTrue(platform.manifestEnabled == false)
    }

    private fun runtime(platform: FakePlatform): XdripBroadcastRuntime {
        val handler = XdripBroadcastHandler(
            isEnabled = { true },
            ingest = {},
            resolveSender = { _, _ -> XdripSenderIdentity.Unknown },
            allowlistedInstalled = { true },
            scope = CoroutineScope(SupervisorJob()),
        )
        return XdripBroadcastRuntime(
            platform = platform,
            handler = handler,
            installedAppsCache = XdripInstalledAppsCache { true },
        )
    }

    private class FakePlatform : XdripBroadcastPlatform {
        var runtimeRegisters = 0
        var packageMonitors = 0
        var runtimeUnregisters = 0
        var packageMonitorUnregisters = 0
        var manifestEnableCalls = 0
        var manifestDisableCalls = 0
        var manifestEnabled: Boolean? = null
        private var runtimeReceiver: BroadcastReceiver? = null
        private var packageReceiver: BroadcastReceiver? = null

        override fun registerRuntime(receiver: BroadcastReceiver) {
            runtimeRegisters++
            runtimeReceiver = receiver
        }

        override fun registerPackageMonitor(receiver: BroadcastReceiver) {
            packageMonitors++
            packageReceiver = receiver
        }

        override fun unregister(receiver: BroadcastReceiver) {
            when (receiver) {
                runtimeReceiver -> {
                    runtimeUnregisters++
                    runtimeReceiver = null
                }
                packageReceiver -> {
                    packageMonitorUnregisters++
                    packageReceiver = null
                }
            }
        }

        override fun setManifestReceiverEnabled(enabled: Boolean) {
            manifestEnabled = enabled
            if (enabled) manifestEnableCalls++ else manifestDisableCalls++
        }
    }
}
