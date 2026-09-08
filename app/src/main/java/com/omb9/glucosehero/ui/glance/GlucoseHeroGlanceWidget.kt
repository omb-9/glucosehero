package com.omb9.glucosehero.ui.glance

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omb9.glucosehero.MainActivity
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.repository.SettingsRepository
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
)

/**
 * Home-screen widget that surfaces the most recent glucose reading. The whole
 * surface deep-links into the Add Entry sheet with the Glucose tab selected,
 * exactly like the post-meal reminder notification.
 */
class GlucoseHeroGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = withContext(Dispatchers.IO) { loadSnapshot(context) }
        val openAddEntry = addGlucoseIntent(context)

        provideContent {
            GlucoseHeroWidgetContent(
                snapshot = snapshot,
                openAddEntry = actionStartActivity(openAddEntry),
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
    openAddEntry: Action,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground.asColorProvider())
            .clickable(openAddEntry)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
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
        }
    }
}

private suspend fun loadSnapshot(context: Context): GlucoseWidgetSnapshot {
    val appContext = context.applicationContext
    val entryPoint = EntryPointAccessors.fromApplication(
        appContext,
        GlucoseHeroWidgetEntryPoint::class.java,
    )
    val settings = entryPoint.settingsRepository().settings.first()
    val reading = entryPoint.entryRepository().latestGlucoseReading()

    val valueText = reading?.glucoseMgdl?.let { Formatters.glucose(it, settings.unit) }
    val timeText = reading?.let {
        val day = Formatters.dayHeader(Formatters.localDate(it.timestamp))
        val time = Formatters.time(it.timestamp, settings.use24HourTime)
        "$day · $time"
    }

    return GlucoseWidgetSnapshot(
        title = appContext.getString(R.string.glucose_widget_label_glucose),
        valueText = valueText,
        unitLabel = settings.unit.label,
        timeText = timeText,
        emptyText = appContext.getString(R.string.glucose_widget_empty),
    )
}

private fun addGlucoseIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_ADD_GLUCOSE)
    }
