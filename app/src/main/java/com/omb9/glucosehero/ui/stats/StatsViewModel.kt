package com.omb9.glucosehero.ui.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.domain.model.Ea1cConfidence
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.TimeRange
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.util.Formatters
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class StatsUiState(
    val range: TimeRange = TimeRange.DAYS_14,
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    val hasData: Boolean = false,
    val avgDisplay: String = "–",
    val tirDisplay: String = "–",
    val readingCount: Int = 0,
    /** Estimated A1c value (percentage only; the UI appends "%"). */
    val ea1cValue: String = "–",
    val ea1cConfidence: Ea1cConfidence = Ea1cConfidence.INSUFFICIENT_DATA,
    val ea1cNeededDays: Int = 14,
    val ea1cNeededReadings: Int = 20,
    val minMaxDisplay: String = "–",
    /** Epoch millis of the window start; the bottom axis maps x (days) → dates. */
    val rangeStartMillis: Long = 0L,
    /** Target range + y bounds, already converted to the display unit. */
    val targetLowDisplay: Float = 70f,
    val targetHighDisplay: Float = 180f,
    val chartMinY: Float = 40f,
    val chartMaxY: Float = 260f,
)

/**
 * Spec §4: every heavy step — folding rows into Vico chart entries, averages,
 * TIR, min/max, shading bounds — runs inside the ViewModel on
 * Dispatchers.Default. The composable only ever renders finished values.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Owned by the ViewModel so chart data survives recomposition. */
    val chartModelProducer = ChartEntryModelProducer()

    private val selectedRange = MutableStateFlow(TimeRange.DAYS_14)

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            selectedRange
                .flatMapLatest { range ->
                    val since = Formatters.daysAgoMillis(range.days)
                    combine(
                        entryRepository.observeGlucose(since),
                        settingsRepository.settings,
                    ) { entries, settings -> Triple(range, entries, settings) }
                }
                .collectLatest { (range, entries, settings) ->
                    withContext(Dispatchers.Default) {
                        val unit = settings.unit
                        val sinceMillis = Formatters.daysAgoMillis(range.days)

                        val points = entries.mapNotNull { entry ->
                            // Non-finite values (a NaN/Infinity that reached
                            // storage) would poison the y-axis domain below.
                            val mgdl = entry.glucoseMgdl?.takeIf { it.isFinite() }
                                ?: return@mapNotNull null
                            val x = (entry.timestamp - sinceMillis).toFloat() / MILLIS_PER_DAY
                            entryOf(x, Formatters.toDisplayValue(mgdl, unit).toFloat())
                        }
                        chartModelProducer.setEntries(points)

                        val values = entries.mapNotNull { e ->
                            e.glucoseMgdl?.takeIf { it.isFinite() }
                        }
                        val avg = entryRepository.averageGlucoseSince(sinceMillis)
                        val tir = entryRepository.timeInRangeSince(
                            sinceMillis,
                            settings.targetLowMgdl.toDouble(),
                            settings.targetHighMgdl.toDouble(),
                        )

                        val lowDisplay =
                            Formatters.toDisplayValue(settings.targetLowMgdl.toDouble(), unit)
                                .toFloat()
                        val highDisplay =
                            Formatters.toDisplayValue(settings.targetHighMgdl.toDouble(), unit)
                                .toFloat()
                        val dataMin = values.minOrNull()
                            ?.let { Formatters.toDisplayValue(it, unit).toFloat() }
                        val dataMax = values.maxOrNull()
                            ?.let { Formatters.toDisplayValue(it, unit).toFloat() }

                        val padding = (highDisplay - lowDisplay) * 0.25f

                        // The RangeSlider permits low == high; with all data
                        // at that exact value the y-domain collapses to zero
                        // height (minY == maxY) — undefined for axis/pixel
                        // math. Guarantee a positive span.
                        val rawMinY = minOf(dataMin ?: lowDisplay, lowDisplay) - padding
                        val rawMaxY = maxOf(dataMax ?: highDisplay, highDisplay) + padding
                        val safeMaxY = if (rawMaxY > rawMinY) rawMaxY else rawMinY + 1f

                        _uiState.value = StatsUiState(
                            range = range,
                            unit = unit,
                            hasData = points.isNotEmpty(),
                            avgDisplay = avg?.let { Formatters.glucose(it, unit) } ?: "–",
                            tirDisplay = tir?.let { "%.0f%%".format(it * 100) } ?: "–",
                            readingCount = values.size,
                            minMaxDisplay = if (dataMin != null && dataMax != null) {
                                "${trim(dataMin)} / ${trim(dataMax)}"
                            } else {
                                "–"
                            },
                            rangeStartMillis = sinceMillis,
                            targetLowDisplay = lowDisplay,
                            targetHighDisplay = highDisplay,
                            chartMinY = rawMinY,
                            chartMaxY = safeMaxY,
                        )
                    }
                }
        }

        viewModelScope.launch {
            val since = Formatters.daysAgoMillis(EA1C_WINDOW_DAYS)
            combine(
                entryRepository.observeGlucoseStats(since),
                settingsRepository.settings,
            ) { stats, settings -> stats to settings }
                .collectLatest { (stats, settings) ->
                    val avg = stats.avgMgdl?.takeIf { it.isFinite() }
                    val confidence = when {
                        stats.loggedDays < MIN_CONFIDENT_DAYS ||
                            stats.readingCount < MIN_CONFIDENT_READINGS ->
                            Ea1cConfidence.INSUFFICIENT_DATA
                        stats.loggedDays >= EA1C_WINDOW_DAYS ->
                            Ea1cConfidence.FULL_90_DAY_WINDOW
                        else -> Ea1cConfidence.BUILDING_ESTIMATE
                    }
                    _uiState.update { current ->
                        current.copy(
                            ea1cValue = avg
                                ?.let { "%.1f".format(estimatedA1c(it, settings.unit)) }
                                ?: "–",
                            ea1cConfidence = confidence,
                            ea1cNeededDays =
                                (MIN_CONFIDENT_DAYS - stats.loggedDays).coerceAtLeast(0),
                            ea1cNeededReadings =
                                (MIN_CONFIDENT_READINGS - stats.readingCount).coerceAtLeast(0),
                        )
                    }
                }
        }
    }

    fun selectRange(range: TimeRange) {
        selectedRange.value = range
    }

    /**
     * ADAG formula in the user's display unit. In practice this yields the
     * same percentage for both units because the stored value is canonical
     * mg/dL and the mmol/L path converts there and back, but keeping the
     * branch explicit makes the unit handling self-documenting.
     */
    private fun estimatedA1c(avgMgdl: Double, unit: GlucoseUnit): Double {
        val averageInDisplayUnit = when (unit) {
            GlucoseUnit.MGDL -> avgMgdl
            GlucoseUnit.MMOL -> avgMgdl / GlucoseUnit.MGDL_PER_MMOL
        }
        return when (unit) {
            GlucoseUnit.MGDL -> (averageInDisplayUnit + 46.7) / 28.7
            GlucoseUnit.MMOL ->
                ((averageInDisplayUnit * GlucoseUnit.MGDL_PER_MMOL) + 46.7) / 28.7
        }
    }

    private fun trim(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

    private companion object {
        const val MILLIS_PER_DAY = 24f * 60f * 60f * 1000f
        const val EA1C_WINDOW_DAYS = 90
        const val MIN_CONFIDENT_DAYS = 14
        const val MIN_CONFIDENT_READINGS = 20
    }
}
