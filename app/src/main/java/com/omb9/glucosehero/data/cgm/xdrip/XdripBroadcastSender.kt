package com.omb9.glucosehero.data.cgm.xdrip

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Sending-package identity for a CGM broadcast.
 *
 * [Unknown] is not the same as [KnownNotAllowlisted]. On API 26–33 the
 * platform does not expose the sender, so identity is [Unknown] and
 * [XdripBroadcastAllowlist.acceptsSender] may accept the broadcast when an
 * allowlisted app is installed. A UID that resolves to packages, none of
 * which are allowlisted, is [KnownNotAllowlisted] and must be dropped even
 * if xDrip+ or AAPS is also installed. Collapsing that case into [Unknown]
 * would treat a positively unallowlisted shared-UID sender more leniently
 * than a single unknown package.
 *
 * FEATURE: cgm-direct-ingest
 */
sealed class XdripSenderIdentity {
    /** Platform did not expose a sender (API 26–33, or UID with no packages). */
    data object Unknown : XdripSenderIdentity()

    /** A specific package was resolved. May or may not be allowlisted. */
    data class Known(val packageName: String) : XdripSenderIdentity()

    /**
     * UID mapped to multiple packages and none of them is allowlisted.
     * Distinct from [Unknown] so the allowlist cannot accept this sender.
     */
    data object KnownNotAllowlisted : XdripSenderIdentity()
}

/**
 * Resolves the sending package of a CGM broadcast.
 *
 * API 34+ exposes [BroadcastReceiver.getSentFromPackage] /
 * [BroadcastReceiver.getSentFromUid]. Older platform versions do not;
 * callers must treat [XdripSenderIdentity.Unknown] as unknown and apply
 * [XdripBroadcastAllowlist.acceptsSender].
 *
 * FEATURE: cgm-direct-ingest
 */
object XdripBroadcastSender {

    fun identity(receiver: BroadcastReceiver, context: Context): XdripSenderIdentity {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return XdripSenderIdentity.Unknown
        }
        return identityFromPlatform(
            sdkInt = Build.VERSION.SDK_INT,
            sentFromPackage = receiver.sentFromPackage,
            sentFromUid = receiver.sentFromUid,
            uidPackages = {
                context.packageManager.getPackagesForUid(receiver.sentFromUid)
            },
        )
    }

    /**
     * Pure resolution used by unit tests. [uidPackages] is only invoked when
     * API 34+ has a real sending UID and [sentFromPackage] is blank.
     */
    internal fun identityFromPlatform(
        sdkInt: Int,
        sentFromPackage: String?,
        sentFromUid: Int,
        uidPackages: () -> Array<String>?,
    ): XdripSenderIdentity {
        if (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return XdripSenderIdentity.Unknown
        }
        sentFromPackage?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return XdripSenderIdentity.Known(it)
        }
        if (sentFromUid > 0 && sentFromUid != Process.SYSTEM_UID) {
            return identityFromUidPackages(uidPackages())
        }
        return XdripSenderIdentity.Unknown
    }

    internal fun identityFromUidPackages(packages: Array<String>?): XdripSenderIdentity {
        if (packages.isNullOrEmpty()) return XdripSenderIdentity.Unknown
        packages.firstOrNull { XdripBroadcastAllowlist.isAllowedPackage(it) }?.let {
            return XdripSenderIdentity.Known(it)
        }
        val single = packages.singleOrNull()
        return if (single != null) {
            XdripSenderIdentity.Known(single)
        } else {
            XdripSenderIdentity.KnownNotAllowlisted
        }
    }

    internal fun reportedPackage(identity: XdripSenderIdentity): String? =
        (identity as? XdripSenderIdentity.Known)?.packageName
}
