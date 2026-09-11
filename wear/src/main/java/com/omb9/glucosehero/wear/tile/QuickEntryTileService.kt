package com.omb9.glucosehero.wear.tile

import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.TimelineBuilders.TimeInterval
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.TimelineBuilders.TimelineEntry
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import com.omb9.glucosehero.wear.data.GlucoseFreshness
import com.omb9.glucosehero.wear.data.WearFreshnessAlarmReceiver
import com.omb9.glucosehero.wear.data.WearFreshnessPolicy
import com.omb9.glucosehero.wear.data.WearGlucoseSnapshot
import com.omb9.glucosehero.wear.data.WearGlucoseStore
import com.omb9.glucosehero.wear.data.WearPhoneMessenger
import com.omb9.glucosehero.wear.data.compactText
import com.omb9.glucosehero.wear.data.freshness
import com.omb9.glucosehero.wear.protocol.WearQuickEntryType
import com.omb9.glucosehero.wear.protocol.WearSyncProtocol

/**
 * Wear OS tile for one-tap water / carbs / insulin logging. Taps send a
 * MessageClient payload to the phone; the next tile render shows the last
 * logged action from [WearGlucoseStore].
 *
 * Provider class (tile ID):
 * `com.omb9.glucosehero.wear.tile.QuickEntryTileService`
 *
 * Freshness interval is a hint (often stretched by the system). While the
 * reading is Fresh, the timeline includes a second entry that becomes valid
 * at the 8-minute Stale boundary so the caption can flip without a wake.
 *
 * FEATURE: cgm-direct-ingest
 */
class QuickEntryTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        when (requestParams.currentState.lastClickableId) {
            WearSyncProtocol.CLICK_WATER -> WearPhoneMessenger.sendQuickEntry(
                this@QuickEntryTileService,
                WearQuickEntryType.WATER,
                WearSyncProtocol.DEFAULT_WATER_ML,
            )
            WearSyncProtocol.CLICK_CARBS -> WearPhoneMessenger.sendQuickEntry(
                this@QuickEntryTileService,
                WearQuickEntryType.CARBS,
                WearSyncProtocol.DEFAULT_CARBS_G,
            )
            WearSyncProtocol.CLICK_INSULIN -> WearPhoneMessenger.sendQuickEntry(
                this@QuickEntryTileService,
                WearQuickEntryType.INSULIN,
                WearSyncProtocol.DEFAULT_INSULIN_U,
            )
        }
        val snapshot = WearGlucoseStore.get(this@QuickEntryTileService).latest()
        val now = System.currentTimeMillis()
        WearFreshnessAlarmReceiver.scheduleCrossing(this@QuickEntryTileService, snapshot, now)
        val freshness = snapshot.freshness(now)
        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(WearFreshnessPolicy.tileFreshnessIntervalMillis(freshness))
            .setTileTimeline(tileTimeline(snapshot, freshness, now))
            .build()
    }

    private fun MaterialScope.tileTimeline(
        snapshot: WearGlucoseSnapshot,
        freshness: GlucoseFreshness,
        now: Long,
    ): Timeline {
        val staleAt = if (snapshot.hasReading) {
            WearFreshnessPolicy.staleCrossingAtMillis(snapshot.timestampMillis, now)
        } else {
            null
        }
        if (freshness is GlucoseFreshness.Fresh && staleAt != null) {
            val staleFreshness = snapshot.freshness(staleAt)
            return Timeline.Builder()
                .addTimelineEntry(timedEntry(now, staleAt, snapshot, freshness))
                .addTimelineEntry(
                    timedEntry(
                        startMillis = staleAt,
                        endMillis = staleAt + SIX_HOURS_MS,
                        snapshot = snapshot,
                        freshness = staleFreshness,
                    ),
                )
                .build()
        }
        return Timeline.fromLayoutElement(quickEntryLayout(snapshot, freshness))
    }

    private fun MaterialScope.timedEntry(
        startMillis: Long,
        endMillis: Long,
        snapshot: WearGlucoseSnapshot,
        freshness: GlucoseFreshness,
    ): TimelineEntry = TimelineEntry.Builder()
        .setLayout(
            Layout.Builder()
                .setRoot(quickEntryLayout(snapshot, freshness))
                .build(),
        )
        .setValidity(
            TimeInterval.Builder()
                .setStartMillis(startMillis)
                .setEndMillis(endMillis)
                .build(),
        )
        .build()

    private fun MaterialScope.quickEntryLayout(
        snapshot: WearGlucoseSnapshot,
        freshness: GlucoseFreshness,
    ) = primaryLayout(
        titleSlot = {
            val title = if (snapshot.hasReading) {
                "${snapshot.displayValue} ${snapshot.trend.arrow}".trim()
            } else {
                freshness.compactText(this@QuickEntryTileService)
            }
            text(title.layoutString)
        },
        mainSlot = {
            buttonGroup {
                buttonGroupItem {
                    textButton(
                        onClick = clickable(id = WearSyncProtocol.CLICK_WATER),
                        labelContent = { text("H2O".layoutString) },
                    )
                }
                buttonGroupItem {
                    textButton(
                        onClick = clickable(id = WearSyncProtocol.CLICK_CARBS),
                        labelContent = { text("15g".layoutString) },
                    )
                }
                buttonGroupItem {
                    textButton(
                        onClick = clickable(id = WearSyncProtocol.CLICK_INSULIN),
                        labelContent = { text("1U".layoutString) },
                    )
                }
            }
        },
        bottomSlot = {
            val caption = when {
                freshness is GlucoseFreshness.Stale || freshness is GlucoseFreshness.NoData ->
                    freshness.compactText(this@QuickEntryTileService)
                snapshot.lastQuickEntry.isNotBlank() -> snapshot.lastQuickEntry
                else -> freshness.compactText(this@QuickEntryTileService)
            }
            text(caption.layoutString)
        },
    )

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val SIX_HOURS_MS = 6L * 60L * 60L * 1000L
    }
}
