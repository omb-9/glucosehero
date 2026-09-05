package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.UnitSystem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {

    /**
     * Locale-bound [DateTimeFormatter]s.
     *
     * These must not be held in `private val`s initialised from
     * [Locale.getDefault] at class-init time: the default locale can change
     * while the process is alive (system Settings → Languages, or a per-app
     * language override), and a statically captured formatter would keep
     * rendering "3:05 PM" style output after the user switched to a locale
     * that expects "15:05".
     */
    private class LocalizedFormatters(val locale: Locale) {
        val time12: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", locale)
        val time24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
        val dayHeader: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
        val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d", locale)
    }

    // Cached so the hot path (formatting 90 days of log rows on every
    // recomposition-triggering emission) does not rebuild four formatters per
    // call; invalidated automatically when the default locale changes.
    @Volatile
    private var cached: LocalizedFormatters = LocalizedFormatters(Locale.getDefault())

    private fun formatters(): LocalizedFormatters {
        val current = Locale.getDefault()
        val existing = cached
        return if (existing.locale == current) {
            existing
        } else {
            LocalizedFormatters(current).also { cached = it }
        }
    }

    fun time(timestamp: Long, use24Hour: Boolean): String {
        val f = formatters()
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(if (use24Hour) f.time24 else f.time12)
    }

    fun localDate(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

    fun dayHeader(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(formatters().dayHeader)
        }
    }

    fun shortDate(date: LocalDate): String = date.format(formatters().shortDate)

    /** Canonical mg/dL → display string in the chosen unit. */
    fun glucose(mgdl: Double, unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MGDL -> "%.0f".format(mgdl)
        GlucoseUnit.MMOL -> "%.1f".format(mgdl / GlucoseUnit.MGDL_PER_MMOL)
    }

    fun glucoseWithUnit(mgdl: Double, unit: GlucoseUnit): String =
        "${glucose(mgdl, unit)} ${unit.label}"

    fun toDisplayValue(mgdl: Double, unit: GlucoseUnit): Double = when (unit) {
        GlucoseUnit.MGDL -> mgdl
        GlucoseUnit.MMOL -> mgdl / GlucoseUnit.MGDL_PER_MMOL
    }

    fun displayToMgdl(value: Double, unit: GlucoseUnit): Double = when (unit) {
        GlucoseUnit.MGDL -> value
        GlucoseUnit.MMOL -> value * GlucoseUnit.MGDL_PER_MMOL
    }

    fun daysAgoMillis(days: Int): Long =
        System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L

    /**
     * Parses user-typed decimal health values.
     *
     * - Accepts a comma as the decimal separator: comma-decimal locales
     *   (es/de/fr/pt…) show a comma key on the Decimal soft keyboard, and
     *   [glucose] itself emits "5,5" there — without this, mmol/L users in
     *   those locales cannot enter or re-save a single reading.
     * - Rejects non-finite values: "NaN"/"Infinity"/"1e999" all pass
     *   toDoubleOrNull() and would corrupt stored data and blow up the
     *   chart's y-axis domain.
     *
     * Returns null for anything unparsable or non-finite; callers apply
     * their own positivity bounds.
     */
    fun parseDecimal(raw: String): Double? =
        raw.trim()
            .replace(',', '.')
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() }

    // ---------- Height & weight display units ----------

    private const val CM_PER_INCH = 2.54
    private const val LB_PER_KG = 2.20462

    /** Canonical cm → display string in the chosen unit. Null/blank height → empty string. */
    fun formatHeight(cm: Float?, system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> cm?.let(::formatNumber) ?: ""
        UnitSystem.IMPERIAL -> cm?.let {
            val (feet, inches) = cmToFeetInches(it)
            "$feet'$inches\""
        } ?: ""
    }

    /** Canonical kg → display string in the chosen unit. Null/blank weight → empty string. */
    fun formatWeight(kg: Float?, system: UnitSystem): String = when (system) {
        UnitSystem.METRIC -> kg?.let(::formatNumber) ?: ""
        UnitSystem.IMPERIAL -> kg?.let { formatNumber(roundToOneDecimal(kgToLbs(it))) } ?: ""
    }

    /** feet/inches -> canonical cm. */
    fun feetInchesToCm(feet: Int, inches: Int): Float =
        ((feet * 12) + inches) * CM_PER_INCH.toFloat()

    /** canonical cm -> feet/inches for display. */
    fun cmToFeetInches(cm: Float): Pair<Int, Int> {
        val totalInches = (cm / CM_PER_INCH).roundToInt()
        return totalInches / 12 to totalInches % 12
    }

    fun lbsToKg(lbs: Float): Float = lbs / LB_PER_KG.toFloat()
    fun kgToLbs(kg: Float): Float = kg * LB_PER_KG.toFloat()

    private fun formatNumber(value: Float): String =
        if (value % 1.0f == 0.0f) value.toInt().toString() else value.toString()

    private fun roundToOneDecimal(value: Float): Float =
        (value * 10).roundToInt() / 10f
}
