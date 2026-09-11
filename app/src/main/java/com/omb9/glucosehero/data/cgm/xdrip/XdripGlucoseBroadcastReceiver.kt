package com.omb9.glucosehero.data.cgm.xdrip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Manifest-declared receiver for xDrip+ / AAPS local glucose broadcasts.
 *
 * Why this exists: when xDrip+ Identify receiver is `com.omb9.glucosehero`,
 * [SendXdripBroadcast] uses `setPackage` plus `FLAG_INCLUDE_STOPPED_PACKAGES`.
 * That explicit send reaches a manifest receiver even if our process is
 * stopped, which a runtime-only receiver cannot do.
 *
 * Implicit broadcasts of these custom actions do **not** reach this receiver
 * on API 26+. [XdripBroadcastRuntime] registers an exported twin while the
 * user toggle is on.
 *
 * Also receives [android.content.Intent.ACTION_BOOT_COMPLETED] so that when
 * the opt-in component is enabled, reboot starts this process and
 * [XdripBroadcastBootstrap] re-registers the runtime (implicit) path.
 * Boot is not a CGM action; this receiver returns immediately for
 * [android.content.Intent.ACTION_BOOT_COMPLETED] without goAsync.
 *
 * Security: this receiver is exported with no `android:permission`. xDrip+
 * and AAPS cannot hold [XdripBroadcastIntents.SEND_PERMISSION]. Sender checks
 * live in [XdripBroadcastHandler] via [XdripBroadcastAllowlist]. The component
 * is disabled until the opt-in flag is on.
 *
 * FEATURE: cgm-direct-ingest
 */
@AndroidEntryPoint
class XdripGlucoseBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var handler: XdripBroadcastHandler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) return
        handler.onReceive(this, context, intent)
    }
}
