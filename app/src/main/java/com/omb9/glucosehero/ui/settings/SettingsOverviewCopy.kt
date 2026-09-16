package com.omb9.glucosehero.ui.settings

import com.omb9.glucosehero.domain.model.AccentColor
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.util.Formatters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Subtitle copy for the main Settings list. Pure so unit tests can pin the
 * live-state strings without Compose or DataStore.
 */
object SettingsOverviewCopy {

    fun profileSubtitle(name: String): String =
        name.trim().ifBlank { "Not set" }

    fun glucoseTargetsSubtitle(
        unit: GlucoseUnit,
        lowMgdl: Float,
        highMgdl: Float,
    ): String {
        val low = Formatters.glucose(lowMgdl.toDouble(), unit)
        val high = Formatters.glucose(highMgdl.toDouble(), unit)
        return "$low-$high ${unit.label}"
    }

    fun appearanceSubtitle(themeMode: ThemeMode, accent: AccentColor): String =
        "${themeModeLabel(themeMode)} · ${accent.label}"

    fun heroAiSubtitle(enabled: Boolean): String = if (enabled) "On" else "Off"

    fun dataSourcesSubtitle(connectedCount: Int): String = when {
        connectedCount <= 0 -> "None connected"
        connectedCount == 1 -> "1 connected"
        else -> "$connectedCount connected"
    }

    fun backupSubtitle(lastBackupMillis: Long?): String {
        if (lastBackupMillis == null) return "No backup yet"
        val date = Instant.ofEpochMilli(lastBackupMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val formatted = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(date)
        return "Last backup $formatted"
    }

    fun themeModeLabel(mode: ThemeMode): String = when (mode) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.AMOLED -> "AMOLED"
    }

    fun latestBackupMillis(localMillis: Long?, cloudMillis: Long?): Long? =
        listOfNotNull(localMillis, cloudMillis).maxOrNull()

    fun countConnectedSources(
        xdripEnabled: Boolean,
        nightscoutEnabled: Boolean,
        healthConnectConnected: Boolean,
    ): Int {
        var count = 0
        if (xdripEnabled) count++
        if (nightscoutEnabled) count++
        if (healthConnectConnected) count++
        return count
    }
}
