package com.omb9.glucosehero.wear

/**
 * GlucoseHero Wear OS companion.
 *
 * ## Open in Android Studio
 * 1. File → Open the repo root `GlucoseHero` (this Gradle project already
 *    includes `:wear` from `settings.gradle.kts`).
 * 2. Select the **wear** run configuration (or Edit Configurations → Android
 *    App → module `:wear`).
 * 3. Deploy to a Wear OS emulator (Wear OS 4+ recommended) or a physical watch
 *    that is paired with a phone running the `:app` module.
 *
 * Required SDK components: Android SDK Platform 37, a Wear OS 4/5 system image
 * for the emulator, and Google Play services on both phone and watch. The phone
 * APK does not embed this module (`wearApp()` is intentionally omitted) so a
 * missing Wear emulator image cannot fail `:app` builds.
 *
 * ## Surfaces
 * - Watch UI: [com.omb9.glucosehero.wear.ui.WearMainActivity] shows the latest
 *   glucose value, trend arrow, unit, and age of the reading.
 * - Complication data source ID:
 *   `com.omb9.glucosehero.wear.complication.GlucoseComplicationService`
 *   (SHORT_TEXT, LONG_TEXT, RANGED_VALUE). Label in the picker: "Glucose".
 * - Tile provider ID:
 *   `com.omb9.glucosehero.wear.tile.QuickEntryTileService`
 *   (water 250 ml, carbs 15 g, bolus 1.0 U). Label: "Quick log".
 *
 * ## Sync protocol (must match the phone `com.omb9.glucosehero.wear` package)
 *
 * Same `applicationId` (`com.omb9.glucosehero`) on phone and watch so Data Layer
 * nodes can see each other.
 *
 * Capabilities (res/values/wear.xml `android_wear_capabilities`):
 * - Watch advertises `glucosehero_wear`
 * - Phone advertises `glucosehero_phone`
 *
 * DataClient item path `/glucosehero/glucose/latest` (phone → watch), keys:
 * - `has_reading` Boolean
 * - `glucose_mgdl` Float (canonical mg/dL, same as Room)
 * - `timestamp_millis` Long
 * - `trend` String: DOUBLE_UP, SINGLE_UP, FORTY_FIVE_UP, FLAT,
 *   FORTY_FIVE_DOWN, SINGLE_DOWN, DOUBLE_DOWN, UNKNOWN
 * - `unit` String: MGDL or MMOL (display only; value stays mg/dL)
 * - `target_low_mgdl` / `target_high_mgdl` Float
 *
 * MessageClient paths:
 * - `/glucosehero/glucose/request` empty payload, watch asks the phone to
 *   republish `/glucosehero/glucose/latest`
 * - `/glucosehero/entry/quick` UTF-8 JSON
 *   `{ "type": "WATER"|"CARBS"|"INSULIN", "amount": number, "timestampMillis": long }`
 *   Phone writes a [com.omb9.glucosehero.domain.model.LogEvent]: WATER as a note
 *   (`#water {ml} ml`), CARBS as `carbsGrams`, INSULIN as `insulinBolusUnits`.
 *
 * ## Data flow
 * Phone [com.omb9.glucosehero.wear.WearGlucosePushController] watches Room
 * invalidation on `entries` and `glucose_samples`, then [WearSyncWorker] reads
 * `glucose_readings` (CGM samples plus manual entries), computes a Dexcom-style
 * rate-of-change arrow, and `putDataItem`s the snapshot. The watch
 * WearableListenerService writes [com.omb9.glucosehero.wear.data.WearGlucoseStore]
 * and requests complication + tile updates. Tile taps and the watch UI send
 * quick-entry messages the other way.
 *
 * Missing Play services or an unpaired watch is a no-op on the phone (settings
 * toggle plus a status line). This module stays free of Hilt and Horologist to
 * keep the APK small; Wear Compose Material 3 and ProtoLayout Material 3 cover
 * the UI, tile, and complication surfaces.
 */
object WearOsCompanion {
    const val MODULE_PATH: String = ":wear"
}
