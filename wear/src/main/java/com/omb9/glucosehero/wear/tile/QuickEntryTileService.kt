package com.omb9.glucosehero.wear.tile

import androidx.wear.protolayout.TimelineBuilders.Timeline
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
import com.omb9.glucosehero.wear.data.WearGlucoseSnapshot
import com.omb9.glucosehero.wear.data.WearGlucoseStore
import com.omb9.glucosehero.wear.data.WearPhoneMessenger
import com.omb9.glucosehero.wear.protocol.WearQuickEntryType
import com.omb9.glucosehero.wear.protocol.WearSyncProtocol

/**
 * Wear OS tile for one-tap water / carbs / insulin logging. Taps send a
 * MessageClient payload to the phone; the next tile render shows the last
 * logged action from [WearGlucoseStore].
 *
 * Provider class (tile ID):
 * `com.omb9.glucosehero.wear.tile.QuickEntryTileService`
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
        return Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_INTERVAL_MS)
            .setTileTimeline(Timeline.fromLayoutElement(quickEntryLayout(snapshot)))
            .build()
    }

    private fun MaterialScope.quickEntryLayout(snapshot: WearGlucoseSnapshot) = primaryLayout(
        titleSlot = {
            val title = if (snapshot.hasReading) {
                "${snapshot.displayValue} ${snapshot.trend.arrow}".trim()
            } else {
                "Glucose"
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
            val caption = snapshot.lastQuickEntry.ifBlank { snapshot.ageLabel }
            text(caption.layoutString)
        },
    )

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val FRESHNESS_INTERVAL_MS = 15 * 60 * 1000L
    }
}
