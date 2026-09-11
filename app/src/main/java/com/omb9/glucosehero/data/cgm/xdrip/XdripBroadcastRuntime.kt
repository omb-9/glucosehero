package com.omb9.glucosehero.data.cgm.xdrip

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime registration for xDrip+ / AAPS broadcasts plus enable/disable of
 * the manifest receiver component.
 *
 * Why this exists: API 26+ drops non-exempt implicit broadcasts for
 * manifest-declared receivers. xDrip+ only `setPackage`s when Identify
 * receiver is filled; an empty destination is an implicit send. AAPS
 * `queryBroadcastReceivers` then `setPackage`s each match, so the manifest
 * filter still matters for discovery. Both paths are kept.
 *
 * Explicit `setPackage` sends match both receivers. Dedup lives in
 * [XdripBroadcastHandler] (action + timestamp, one CGM interval) rather
 * than dropping explicit sends from the runtime receiver: the platform
 * does not reliably preserve "this was implicit" on the receiving side,
 * and a missed implicit xDrip+ send (Identify receiver blank) would
 * silently starve ingest.
 *
 * The runtime receiver is [ContextCompat.RECEIVER_EXPORTED] because another
 * app must send to us. [XdripBroadcastAllowlist] is the sender gate.
 *
 * [setComponentEnabledSetting] persists across reboot, so explicit Identify
 * / AAPS sends still wake a stopped process. The runtime receiver does not
 * persist. [XdripGlucoseBroadcastReceiver] also listens for
 * [Intent.ACTION_BOOT_COMPLETED] (no-op for ingest) so that when the toggle
 * is on, boot starts this process and [XdripBroadcastBootstrap] re-registers
 * the implicit path. Fill Identify receiver anyway: that is the path that
 * works while the process is stopped between readings.
 *
 * FEATURE: cgm-direct-ingest
 */
@Singleton
class XdripBroadcastRuntime internal constructor(
    private val platform: XdripBroadcastPlatform,
    private val handler: XdripBroadcastHandler,
    private val installedAppsCache: XdripInstalledAppsCache,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        handler: XdripBroadcastHandler,
        installedAppsCache: XdripInstalledAppsCache,
    ) : this(
        platform = AndroidXdripBroadcastPlatform(context),
        handler = handler,
        installedAppsCache = installedAppsCache,
    )

    private val lock = Any()
    private var runtimeRegistered = false
    private var manifestEnabledThisProcess = false

    private val runtimeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            handler.onReceive(this, ctx, intent)
        }
    }

    private val packageMonitor = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            // Invalidate only. Do not log the package. Next get() re-probes.
            installedAppsCache.invalidate()
        }
    }

    fun register() {
        synchronized(lock) {
            enableManifestOnce()
            installedAppsCache.refresh()
            if (runtimeRegistered) return
            platform.registerRuntime(runtimeReceiver)
            platform.registerPackageMonitor(packageMonitor)
            runtimeRegistered = true
        }
    }

    fun unregister() {
        synchronized(lock) {
            if (runtimeRegistered) {
                runCatching { platform.unregister(runtimeReceiver) }
                runCatching { platform.unregister(packageMonitor) }
                runtimeRegistered = false
            }
            disableManifest()
        }
    }

    internal fun isRuntimeRegistered(): Boolean = synchronized(lock) { runtimeRegistered }

    internal fun isManifestEnabledThisProcess(): Boolean =
        synchronized(lock) { manifestEnabledThisProcess }

    private fun enableManifestOnce() {
        if (manifestEnabledThisProcess) return
        platform.setManifestReceiverEnabled(true)
        manifestEnabledThisProcess = true
    }

    private fun disableManifest() {
        platform.setManifestReceiverEnabled(false)
        manifestEnabledThisProcess = false
    }
}

/**
 * PackageManager / [Context] operations [XdripBroadcastRuntime] needs,
 * extracted so unit tests can count registrations without Robolectric.
 *
 * FEATURE: cgm-direct-ingest
 */
internal interface XdripBroadcastPlatform {
    fun registerRuntime(receiver: BroadcastReceiver)
    fun registerPackageMonitor(receiver: BroadcastReceiver)
    fun unregister(receiver: BroadcastReceiver)
    fun setManifestReceiverEnabled(enabled: Boolean)
}

private class AndroidXdripBroadcastPlatform(
    private val context: Context,
) : XdripBroadcastPlatform {

    override fun registerRuntime(receiver: BroadcastReceiver) {
        val filter = IntentFilter().apply {
            addAction(XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE)
            addAction(XdripBroadcastIntents.ACTION_NEW_SGV)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun registerPackageMonitor(receiver: BroadcastReceiver) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun unregister(receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    override fun setManifestReceiverEnabled(enabled: Boolean) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, XdripGlucoseBroadcastReceiver::class.java),
            state,
            PackageManager.DONT_KILL_APP,
        )
    }
}
