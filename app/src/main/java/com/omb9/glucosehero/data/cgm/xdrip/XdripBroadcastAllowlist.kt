package com.omb9.glucosehero.data.cgm.xdrip

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Packages allowed to supply glucose into the exported xDrip/AAPS receiver.
 *
 * Why this exists: the runtime receiver is [androidx.core.content.ContextCompat.RECEIVER_EXPORTED]
 * so another app can send. An open exported CGM receiver is a hypo-alarm
 * spoofing surface. Manifest `android:permission` cannot be our custom
 * permission: xDrip+ and AAPS do not hold it, and attaching it would drop
 * [XdripBroadcastIntents.ACTION_NEW_BG_ESTIMATE] / [XdripBroadcastIntents.ACTION_NEW_SGV].
 *
 * Allowed packages (exact, plus a `.debug` suffix used by some self-builds):
 * - `com.eveningoutpost.dexdrip` (xDrip+)
 * - `info.nightscout.androidaps` (AAPS full)
 * - `info.nightscout.aapsclient` / `info.nightscout.aapsclient2` (AAPS Client)
 * - `info.nightscout.aapspumpcontrol` (AAPS Pumpcontrol)
 *
 * Application IDs verified from
 * [AAPS app/build.gradle.kts](https://github.com/nightscout/AndroidAPS/blob/master/app/build.gradle.kts)
 * product flavors. xDrip+ package from
 * [SendXdripBroadcast](https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/utilitymodels/SendXdripBroadcast.java)
 * (`com.eveningoutpost.dexdrip`).
 *
 * Assumptions:
 * - Sender identity on API 34+ comes from [android.content.BroadcastReceiver.getSentFromPackage]
 *   (see [XdripSenderIdentity]).
 * - Below API 34 Android does not expose the sending package for broadcasts.
 *   [XdripSenderIdentity.Unknown] is accepted only when at least one
 *   allowlisted app is installed, so a device with neither xDrip nor AAPS
 *   cannot be used as a silent injection target. That is weaker than UID
 *   checks; keep the ingest toggle default **off**.
 * - [XdripSenderIdentity.KnownNotAllowlisted] is never accepted, even when
 *   an allowlisted app is installed. A shared-UID sender that resolved to
 *   no allowlisted package is not "unknown".
 *
 * FEATURE: cgm-direct-ingest
 */
object XdripBroadcastAllowlist {

    val PACKAGES: Set<String> = setOf(
        "com.eveningoutpost.dexdrip",
        "info.nightscout.androidaps",
        "info.nightscout.aapsclient",
        "info.nightscout.aapsclient2",
        "info.nightscout.aapspumpcontrol",
    )

    fun isAllowedPackage(packageName: String): Boolean {
        if (packageName in PACKAGES) return true
        if (packageName.endsWith(".debug")) {
            return packageName.removeSuffix(".debug") in PACKAGES
        }
        return false
    }

    /**
     * @param identity resolved sending identity from [XdripBroadcastSender]
     * @param allowlistedAppInstalled true when any [PACKAGES] entry is installed
     */
    fun acceptsSender(
        identity: XdripSenderIdentity,
        allowlistedAppInstalled: Boolean,
    ): Boolean {
        return when (identity) {
            XdripSenderIdentity.Unknown -> allowlistedAppInstalled
            is XdripSenderIdentity.Known -> isAllowedPackage(identity.packageName)
            XdripSenderIdentity.KnownNotAllowlisted -> false
        }
    }

    /**
     * Uncached PackageManager probe. The receive path uses
     * [XdripInstalledAppsCache] so a reading does not pay five IPCs.
     * The Data Sources hub still calls this on an explicit user test.
     */
    fun isAnyAllowlistedAppInstalled(context: Context): Boolean =
        PACKAGES.any { isInstalled(context, it) }

    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
