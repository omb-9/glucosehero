package com.omb9.glucosehero.ui.glance

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.LocalContext
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omb9.glucosehero.MainActivity
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.cgm.GlucoseFreshness
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.cgm.compactText
import com.omb9.glucosehero.util.Formatters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Hilt entry point for the widget process. Glance receivers are instantiated
 * by the system, not by Hilt, so this is how the widget reaches the app's
 * singleton repositories and DataStore-backed settings.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GlucoseHeroWidgetEntryPoint {
    fun entryRepository(): EntryRepository
    fun settingsRepository(): SettingsRepository
    fun entryDao(): EntryDao
}

// Strict AMOLED black background per the app's minimalist aesthetic.
private val WidgetBackground = Color(0xFF000000)
private val WidgetAccent = Color(AccentColor.LIGHT_RED.argb)
private val WidgetOnSurfaceVariant = Color(0xFF9E9EA4)

private fun Color.asColorProvider(): ColorProvider = ColorProvider(this)

private data class GlucoseWidgetSnapshot(
    val title: String,
    val valueText: String?,
    val unitLabel: String,
    val timeText: String?,
    val emptyText: String,
    val trendPng: ByteArray?,
)

/**
 * Home-screen widget that surfaces the most recent glucose reading plus a
 * 1-hour mini-trend. The trend is a fixed [WidgetTrendBitmap] PNG so the
 * Glance / RemoteViews payload stays well under Binder's 1 MB limit. The
 * whole surface deep-links into the Add Entry sheet with the Glucose tab
 * selected, exactly like the post-meal reminder notification.
 *
 * Freshness is classified at draw time. [WidgetRefresher] must therefore
 * run on elapsed time as well as on new rows; otherwise a stopped CGM
 * stream leaves the last "Just now" caption on the home screen forever.
 *
 * @param omitTrendBitmap set by [WidgetRefresher] when a previous update hit
 *   [android.os.TransactionTooLargeException]; the retry is text-only.
 */
class GlucoseHeroGlanceWidget(
    private val omitTrendBitmap: Boolean = false,
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) {
            loadSnapshot(context, omitTrendBitmap = omitTrendBitmap)
        }
        val openGlucose = addGlucoseIntent(context)
        val openMeal = addMealIntent(context)
        val openBolus = addBolusIntent(context)

        provideContent {
            GlucoseHeroWidgetContent(
                snapshot = snapshot,
                openGlucose = actionStartActivity(openGlucose),
                openMeal = actionStartActivity(openMeal),
                openBolus = actionStartActivity(openBolus),
            )
        }
    }
}

class GlucoseHeroGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GlucoseHeroGlanceWidget()
}

@Composable
private fun GlucoseHeroWidgetContent(
    snapshot: GlucoseWidgetSnapshot,
    openGlucose: Action,
    openMeal: Action,
    openBolus: Action,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground.asColorProvider())
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val readingDescription = widgetReadingContentDescription(LocalContext.current, snapshot)
            Column(
                modifier = GlanceModifier
                    .clickable(openGlucose)
                    .semantics {
                        contentDescription = readingDescription
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = snapshot.title,
                    style = TextStyle(
                        color = WidgetOnSurfaceVariant.asColorProvider(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )

                if (snapshot.valueText != null) {
                    Text(
                        text = "${snapshot.valueText} ${snapshot.unitLabel}",
                        modifier = GlanceModifier.padding(top = 2.dp),
                        maxLines = 1,
                        style = TextStyle(
                            color = WidgetAccent.asColorProvider(),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                } else {
                    Text(
                        text = snapshot.emptyText,
                        modifier = GlanceModifier.padding(top = 2.dp),
                        maxLines = 2,
                        style = TextStyle(
                            color = WidgetAccent.asColorProvider(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                if (snapshot.timeText != null) {
                    Text(
                        text = snapshot.timeText,
                        modifier = GlanceModifier.padding(top = 2.dp),
                        maxLines = 1,
                        style = TextStyle(
                            color = WidgetOnSurfaceVariant.asColorProvider(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                    )
                }

                val png = snapshot.trendPng
                val trendBitmap = if (png != null && png.isNotEmpty()) {
                    BitmapFactory.decodeByteArray(png, 0, png.size)
                } else {
                    null
                }
                if (trendBitmap != null) {
                    Image(
                        provider = ImageProvider(trendBitmap),
                        contentDescription = "One hour glucose trend",
                        modifier = GlanceModifier
                            .padding(top = 4.dp)
                            .fillMaxWidth()
                            .height(24.dp),
                    )
                }
            }

            Spacer(GlanceModifier.height(6.dp))

            Row(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    text = "Log Meal",
                    onClick = openMeal,
                    modifier = GlanceModifier.padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = 4.dp,
                        bottom = 4.dp,
                    ),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = WidgetOnSurfaceVariant.asColorProvider(),
                        contentColor = WidgetBackground.asColorProvider(),
                    ),
                    maxLines = 1,
                )
                Spacer(GlanceModifier.width(8.dp))
                Button(
                    text = "Log Bolus",
                    onClick = openBolus,
                    modifier = GlanceModifier.padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = 4.dp,
                        bottom = 4.dp,
                    ),
                    style = TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = WidgetAccent.asColorProvider(),
                        contentColor = WidgetBackground.asColorProvider(),
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun widgetReadingContentDescription(
    context: Context,
    snapshot: GlucoseWidgetSnapshot,
): String {
    val value = snapshot.valueText
    val age = snapshot.timeText
    return if (value != null && age != null) {
        context.getString(
            R.string.glucose_widget_reading_a11y,
            value,
            snapshot.unitLabel,
            age,
        )
    } else {
        snapshot.emptyText
    }
}

private suspend fun loadSnapshot(
    context: Context,
    omitTrendBitmap: Boolean,
): GlucoseWidgetSnapshot {
    val appContext = context.applicationContext
    val entryPoint = EntryPointAccessors.fromApplication(
        appContext,
        GlucoseHeroWidgetEntryPoint::class.java,
    )
    val settings = entryPoint.settingsRepository().settings.first()
    val reading = entryPoint.entryRepository().latestGlucoseReading()

    val valueText = reading?.glucoseMgdl?.let { Formatters.glucose(it, settings.unit) }
    val now = System.currentTimeMillis()
    val freshness = GlucoseFreshness.classify(reading?.timestamp, now)
    val timeText = if (reading == null) {
        null
    } else {
        freshness.compactText(appContext)
    }

    val trendPng = if (omitTrendBitmap) {
        null
    } else {
        val raw = entryPoint.entryDao().glucoseReadingPointsSince(
            now - WidgetTrendBitmap.TREND_WINDOW_MS,
        )
        val points = WidgetTrendBitmap.limitReadings(raw, now)
        val png = WidgetTrendBitmap.encodePng(points)
        png.takeIf { it.isNotEmpty() && WidgetTrendBitmap.isWithinBinderBudget(it.size) }
    }

    return GlucoseWidgetSnapshot(
        title = appContext.getString(R.string.glucose_widget_label_glucose),
        valueText = valueText,
        unitLabel = settings.unit.label,
        timeText = timeText,
        emptyText = appContext.getString(R.string.glucose_widget_empty),
        trendPng = trendPng,
    )
}

private fun addGlucoseIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_ADD_GLUCOSE)
    }

private fun addMealIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_ADD_MEAL)
    }

private fun addBolusIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_ADD_BOLUS)
    }
