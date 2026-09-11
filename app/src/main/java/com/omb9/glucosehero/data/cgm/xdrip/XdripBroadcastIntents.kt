package com.omb9.glucosehero.data.cgm.xdrip

/**
 * Intent contract for local CGM broadcasts from xDrip+ and AndroidAPS.
 *
 * Verified 2026-09-09 against current upstream sources, not memory. Extra
 * names have changed historically; keep this file aligned with GitHub.
 *
 * xDrip+ local broadcast (Inter-app settings → Broadcast locally):
 * - [ACTION_NEW_BG_ESTIMATE] from
 *   [BroadcastGlucose.sendLocalBroadcast](https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/utilitymodels/BroadcastGlucose.java)
 *   via [SendXdripBroadcast](https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/utilitymodels/SendXdripBroadcast.java)
 * - Extra keys from
 *   [Intents](https://github.com/NightscoutFoundation/xDrip/blob/master/app/src/main/java/com/eveningoutpost/dexdrip/utilitymodels/Intents.java)
 * - [EXTRA_BG_ESTIMATE] is mg/dL (`dg.mgdl` / `BgReading.calculated_value`).
 *   [EXTRA_DISPLAY_UNITS] is UI-only and must not drive conversion.
 * - Sender may `sendBroadcast(intent, RECEIVER_PERMISSION)` unless the user
 *   enables "without permission". Receivers must hold
 *   [XDRIP_RECEIVER_PERMISSION]. Identify-receiver uses `setPackage` and
 *   `FLAG_INCLUDE_STOPPED_PACKAGES`.
 *
 * AndroidAPS-compatible:
 * - AAPS *listens* on the same [ACTION_NEW_BG_ESTIMATE] extras
 *   ([Intents.kt](https://github.com/nightscout/AndroidAPS/blob/master/core/interfaces/src/main/kotlin/app/aaps/core/interfaces/receivers/Intents.kt),
 *   [XdripSourcePlugin](https://github.com/nightscout/AndroidAPS/blob/master/plugins/source/src/main/kotlin/app/aaps/plugins/source/XdripSourcePlugin.kt)).
 * - AAPS *sends* CGM via the xDrip plugin as [ACTION_NEW_SGV] with string
 *   extra [EXTRA_SGVS] (JSON array of `{mills, mgdl, direction}`)
 *   ([XdripPlugin.sendEntries](https://github.com/nightscout/AndroidAPS/blob/master/plugins/sync/src/main/kotlin/app/aaps/plugins/sync/xdrip/XdripPlugin.kt)).
 *   That plugin queries manifest receivers and `setPackage`s each match, so
 *   a manifest filter for [ACTION_NEW_SGV] is required for AAPS delivery.
 *
 * FEATURE: cgm-direct-ingest
 */
object XdripBroadcastIntents {

    const val ACTION_NEW_BG_ESTIMATE = "com.eveningoutpost.dexdrip.BgEstimate"

    const val ACTION_NEW_BG_ESTIMATE_NO_DATA = "com.eveningoutpost.dexdrip.BgEstimateNoData"

    const val ACTION_STATUS_UPDATE = "com.eveningoutpost.dexdrip.StatusUpdate"

    /** AAPS xDrip plugin → registered local receivers. */
    const val ACTION_NEW_SGV = "info.nightscout.client.NEW_SGV"

    /** True for the two actions this app ingests. Boot and status are not. */
    fun isCgmAction(action: String?): Boolean =
        action == ACTION_NEW_BG_ESTIMATE || action == ACTION_NEW_SGV

    const val XDRIP_RECEIVER_PERMISSION =
        "com.eveningoutpost.dexdrip.permissions.RECEIVE_BG_ESTIMATE"

    /**
     * Declared by GlucoseHero. Not attached to the receiver: xDrip+ / AAPS
     * do not request it, and requiring it would drop their broadcasts.
     * Package allowlist in [XdripBroadcastAllowlist] is the sender gate.
     */
    const val SEND_PERMISSION = "com.omb9.glucosehero.permission.SEND_XDRIP_CGM"

    const val EXTRA_BG_ESTIMATE = "com.eveningoutpost.dexdrip.Extras.BgEstimate"
    const val EXTRA_BG_SLOPE = "com.eveningoutpost.dexdrip.Extras.BgSlope"
    const val EXTRA_BG_SLOPE_NAME = "com.eveningoutpost.dexdrip.Extras.BgSlopeName"
    const val EXTRA_TIMESTAMP = "com.eveningoutpost.dexdrip.Extras.Time"
    const val EXTRA_DISPLAY_UNITS = "com.eveningoutpost.dexdrip.Extras.Display.Units"
    const val EXTRA_SENDER = "com.eveningoutpost.dexdrip.Extras.Sender"

    /** JSON array extra used by AAPS [ACTION_NEW_SGV]. */
    const val EXTRA_SGVS = "sgvs"
}
