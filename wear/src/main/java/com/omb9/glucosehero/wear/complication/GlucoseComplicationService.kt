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
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.omb9.glucosehero.wear.R
import com.omb9.glucosehero.wear.data.WearGlucoseSnapshot
import com.omb9.glucosehero.wear.data.WearGlucoseStore
import com.omb9.glucosehero.wear.protocol.WearTrend
import com.omb9.glucosehero.wear.ui.WearMainActivity

/**
 * Complication data source: most recent glucose reading plus trend arrow.
 *
 * Data source ID:
 * `com.omb9.glucosehero.wear.complication.GlucoseComplicationService`
 *
 * Supported types: SHORT_TEXT, LONG_TEXT, RANGED_VALUE.
 */
class GlucoseComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val preview = WearGlucoseSnapshot(
            hasReading = true,
            glucoseMgdl = 120f,
            timestampMillis = System.currentTimeMillis(),
            trend = WearTrend.FLAT,
        )
        return dataFor(type, preview)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val snapshot = WearGlucoseStore.get(this).latest()
        return dataFor(request.complicationType, snapshot)
            ?: ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("--").build(),
                contentDescription = PlainComplicationText.Builder("No glucose reading").build(),
            ).setTapAction(tapAction()).build()
    }

    private fun dataFor(type: ComplicationType, snapshot: WearGlucoseSnapshot): ComplicationData? {
        val tap = tapAction()
        val short = shortText(snapshot)
        val description = contentDescription(snapshot)
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder(short).build(),
                contentDescription = PlainComplicationText.Builder(description).build(),
            )
                .setTitle(PlainComplicationText.Builder(snapshot.trend.arrow.ifBlank { "BG" }).build())
                .setMonochromaticImage(
                    MonochromaticImage.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_complication)).build(),
                )
                .setTapAction(tap)
                .build()

            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(longText(snapshot)).build(),
                contentDescription = PlainComplicationText.Builder(description).build(),
            )
                .setTitle(PlainComplicationText.Builder("Glucose").build())
                .setTapAction(tap)
                .build()

            ComplicationType.RANGED_VALUE -> {
                val min = snapshot.targetLowMgdl
                val max = snapshot.targetHighMgdl.coerceAtLeast(min + 1f)
                val value = if (snapshot.hasReading) {
                    snapshot.glucoseMgdl.coerceIn(min, max)
                } else {
                    min
                }
                RangedValueComplicationData.Builder(
                    value = value,
                    min = min,
                    max = max,
                    contentDescription = PlainComplicationText.Builder(description).build(),
                )
                    .setText(PlainComplicationText.Builder(short).build())
                    .setTitle(PlainComplicationText.Builder(snapshot.trend.arrow.ifBlank { "BG" }).build())
                    .setTapAction(tap)
                    .build()
            }

            else -> null
        }
    }

    private fun shortText(snapshot: WearGlucoseSnapshot): String {
        if (!snapshot.hasReading) return "--"
        val arrow = snapshot.trend.arrow
        return if (arrow.isEmpty()) snapshot.displayValue else "${snapshot.displayValue} $arrow"
    }

    private fun longText(snapshot: WearGlucoseSnapshot): String {
        if (!snapshot.hasReading) return "No glucose yet"
        val arrow = snapshot.trend.arrow
        return if (arrow.isEmpty()) {
            "${snapshot.displayValue} ${snapshot.unitLabel}"
        } else {
            "${snapshot.displayValue} ${snapshot.unitLabel} $arrow"
        }
    }

    private fun contentDescription(snapshot: WearGlucoseSnapshot): String =
        if (!snapshot.hasReading) {
            "No glucose reading"
        } else {
            "Glucose ${snapshot.displayValue} ${snapshot.unitLabel} ${snapshot.trend.arrow} ${snapshot.ageLabel}"
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
