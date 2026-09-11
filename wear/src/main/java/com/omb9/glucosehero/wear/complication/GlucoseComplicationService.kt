package com.omb9.glucosehero.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeRange
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.omb9.glucosehero.wear.R
import com.omb9.glucosehero.wear.data.WearFreshnessAlarmReceiver
import com.omb9.glucosehero.wear.data.WearFreshnessPolicy
import com.omb9.glucosehero.wear.data.WearGlucoseSnapshot
import com.omb9.glucosehero.wear.data.WearGlucoseStore
import com.omb9.glucosehero.wear.data.compactText
import com.omb9.glucosehero.wear.data.freshness
import com.omb9.glucosehero.wear.protocol.WearTrend
import com.omb9.glucosehero.wear.ui.WearMainActivity
import java.time.Instant

/**
 * Complication data source: most recent glucose reading plus trend arrow.
 *
 * Data source ID:
 * `com.omb9.glucosehero.wear.complication.GlucoseComplicationService`
 *
 * Supported types: SHORT_TEXT, LONG_TEXT, RANGED_VALUE.
 *
 * Manifest UPDATE_PERIOD_SECONDS is [WearFreshnessPolicy.COMPLICATION_UPDATE_PERIOD_SECONDS]
 * (10 minutes). The platform treats that as advisory and may only honor ~30
 * minutes. Fresh-to-Stale is therefore [WearFreshnessAlarmReceiver] plus
 * [TimeRange] on the payload so a silent CGM stream cannot leave a bare
 * number on the watch face.
 *
 * FEATURE: cgm-direct-ingest
 */
class GlucoseComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val preview = WearGlucoseSnapshot(
            hasReading = true,
            glucoseMgdl = 120f,
            timestampMillis = System.currentTimeMillis(),
            trend = WearTrend.FLAT,
        )
        return dataFor(type, preview, scheduleAlarm = false)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val snapshot = WearGlucoseStore.get(this).latest()
        return dataFor(request.complicationType, snapshot, scheduleAlarm = true)
            ?: ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("--").build(),
                contentDescription = PlainComplicationText.Builder(
                    getString(R.string.freshness_compact_no_data),
                ).build(),
            ).setTapAction(tapAction()).build()
    }

    private fun dataFor(
        type: ComplicationType,
        snapshot: WearGlucoseSnapshot,
        scheduleAlarm: Boolean,
    ): ComplicationData? {
        val now = System.currentTimeMillis()
        if (scheduleAlarm) {
            WearFreshnessAlarmReceiver.scheduleCrossing(this, snapshot, now)
        }
        val tap = tapAction()
        val freshness = snapshot.freshness(now)
        val short = shortText(snapshot)
        val compactAge = freshness.compactText(this)
        val description = GlucoseComplicationCopy.contentDescription(
            label = getString(R.string.complication_label),
            snapshot = snapshot,
            compactAge = compactAge,
        )
        val title = GlucoseComplicationCopy.title(
            freshness = freshness,
            compactAge = compactAge,
            trendArrow = snapshot.trend.arrow,
            fallbackLabel = getString(R.string.complication_label),
        )
        val validUntil = WearFreshnessPolicy.complicationValidUntilMillis(snapshot, now)
        val validity = validUntil?.let { TimeRange.before(Instant.ofEpochMilli(it)) }
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                val builder = ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(short).build(),
                    contentDescription = PlainComplicationText.Builder(description).build(),
                )
                    .setTitle(PlainComplicationText.Builder(title).build())
                    .setMonochromaticImage(
                        MonochromaticImage.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_complication)).build(),
                    )
                    .setTapAction(tap)
                if (validity != null) builder.setValidTimeRange(validity)
                builder.build()
            }

            ComplicationType.LONG_TEXT -> {
                val builder = LongTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(
                        GlucoseComplicationCopy.longText(
                            hasReading = snapshot.hasReading,
                            valueLine = longValueLine(snapshot),
                            compactAge = compactAge,
                        ),
                    ).build(),
                    contentDescription = PlainComplicationText.Builder(description).build(),
                )
                    .setTitle(PlainComplicationText.Builder(getString(R.string.complication_label)).build())
                    .setTapAction(tap)
                if (validity != null) builder.setValidTimeRange(validity)
                builder.build()
            }

            ComplicationType.RANGED_VALUE -> {
                val min = snapshot.targetLowMgdl
                val max = snapshot.targetHighMgdl.coerceAtLeast(min + 1f)
                val value = if (snapshot.hasReading) {
                    snapshot.glucoseMgdl.coerceIn(min, max)
                } else {
                    min
                }
                val builder = RangedValueComplicationData.Builder(
                    value = value,
                    min = min,
                    max = max,
                    contentDescription = PlainComplicationText.Builder(description).build(),
                )
                    .setText(PlainComplicationText.Builder(short).build())
                    .setTitle(PlainComplicationText.Builder(title).build())
                    .setTapAction(tap)
                if (validity != null) builder.setValidTimeRange(validity)
                builder.build()
            }

            else -> null
        }
    }

    private fun shortText(snapshot: WearGlucoseSnapshot): String {
        if (!snapshot.hasReading) return "--"
        val arrow = snapshot.trend.arrow
        return if (arrow.isEmpty()) snapshot.displayValue else "${snapshot.displayValue} $arrow"
    }

    private fun longValueLine(snapshot: WearGlucoseSnapshot): String {
        val arrow = snapshot.trend.arrow
        return if (arrow.isEmpty()) {
            "${snapshot.displayValue} ${snapshot.unitLabel}"
        } else {
            "${snapshot.displayValue} ${snapshot.unitLabel} $arrow"
        }
    }

    private fun tapAction(): PendingIntent {
        val intent = Intent(this, WearMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
