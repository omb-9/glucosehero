package com.omb9.glucosehero.ui.stats

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.data.export.ExportManager
import com.omb9.glucosehero.data.local.db.InsightDao
import com.omb9.glucosehero.data.local.entity.InsightCardEntity
import com.omb9.glucosehero.domain.model.Ea1cConfidence
import com.omb9.glucosehero.domain.model.ExportFormat
import com.omb9.glucosehero.domain.model.ExportedFile
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.Supply
import com.omb9.glucosehero.domain.model.SupplyType
import com.omb9.glucosehero.domain.model.TimeRange
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.domain.repository.SupplyRepository
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.util.SupplyCalculator
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import kotlin.math.abs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class TrendDirection { UP, DOWN, FLAT, UNKNOWN }

private data class MetricDelta(
    val direction: TrendDirection,
    val text: String,
)

@Immutable
data class ActiveSupplyUi(
    val id: Long,
    val type: SupplyType,
    val startedAt: Long,
    val daysRemaining: Double,
    val hoursRemaining: Double,
    val progressPercentage: Float,
    val isExpired: Boolean,
)

@Immutable
data class StatsUiState(
    val range: TimeRange = TimeRange.DAYS_14,
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    val hasData: Boolean = false,
    val loadFailed: Boolean = false,
    val avgDisplay: String = "–",
    val tirDisplay: String = "–",
    val avgTrend: TrendDirection = TrendDirection.UNKNOWN,
    val avgDeltaDisplay: String? = null,
    val tirTrend: TrendDirection = TrendDirection.UNKNOWN,
    val tirDeltaDisplay: String? = null,
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

/** One-shot events consumed by the Stats screen to launch the share sheet. */
sealed interface ExportEvent {
    data class Ready(val file: ExportedFile) : ExportEvent
    data class Failed(val message: String) : ExportEvent
}

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
    private val supplyRepository: SupplyRepository,
    private val insightDao: InsightDao,
    private val exportManager: ExportManager,
) : ViewModel() {

    /** Owned by the ViewModel so chart data survives recomposition. */
    val chartModelProducer = ChartEntryModelProducer()

    /** Continuous daily-logging streak, emitted reactively from Room. */
    val currentStreakDays: StateFlow<Int> = entryRepository.observeCurrentStreak()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Latest generated pattern-recognition insights, newest first. */
    val insights: StateFlow<List<InsightCardEntity>> = insightDao.observeLatest(INSIGHT_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Active supply cards, with lifecycle math evaluated on each emission and
     * a 60-second ticker so remaining counts advance without a DB emission. */
    val activeSupplies: StateFlow<ImmutableList<ActiveSupplyUi>> = combine(
        supplyRepository.observeActiveSupplies(),
        flow {
            while (true) {
                emit(Unit)
                delay(60_000L)
            }
        },
    ) { supplies, _ -> supplies.map { it.toUiModel() }.toImmutableList() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            persistentListOf(),
        )

    private val selectedRange = MutableStateFlow(TimeRange.DAYS_14)

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportEvents = MutableSharedFlow<ExportEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val exportEvents: SharedFlow<ExportEvent> = _exportEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            selectedRange
                .flatMapLatest { range ->
                    val since = Formatters.daysAgoMillis(range.days)
                    combine(
                        entryRepository.observeGlucosePoints(since),
                        settingsRepository.settings,
                    ) { entries, settings -> Triple(range, entries, settings) }
                }
                .collectLatest { (range, entries, settings) ->
                    try {
                        withContext(Dispatchers.Default) {
                            val unit = settings.unit
                            val sinceMillis = Formatters.daysAgoMillis(range.days)

                            val points = entries.mapNotNull { entry ->
                                // Non-finite values (a NaN/Infinity that reached
                                // storage) would poison the y-axis domain below.
                                val mgdl = entry.glucoseMgdl.takeIf { it.isFinite() }
                                    ?: return@mapNotNull null
                                val x = (entry.timestamp - sinceMillis).toFloat() / MILLIS_PER_DAY
                                entryOf(x, Formatters.toDisplayValue(mgdl, unit).toFloat())
                            }
                            chartModelProducer.setEntries(points)

                            val values = entries.mapNotNull { e ->
                                e.glucoseMgdl.takeIf { it.isFinite() }
                            }
                            val currentAvg = values.takeIf { it.isNotEmpty() }?.average()
                            val currentTir = if (values.isEmpty()) null else
                                values.count {
                                    it in settings.targetLowMgdl.toDouble()..settings.targetHighMgdl.toDouble()
                                }.toDouble() / values.size

                            // Sequential comparison window: the same length as the
                            // selected range, ending exactly where the current
                            // window begins (e.g. days 15–28 for the 14-day view).
                            val previousStartMillis =
                                sinceMillis - range.days * SupplyCalculator.MILLIS_PER_DAY
                            val previousAvg = entryRepository.averageGlucoseBetween(
                                previousStartMillis,
                                sinceMillis,
                            )
                            val previousTir = entryRepository.timeInRangeBetween(
                                previousStartMillis,
                                sinceMillis,
                                settings.targetLowMgdl.toDouble(),
                                settings.targetHighMgdl.toDouble(),
                            )
                            val avgDelta = glucoseDelta(currentAvg, previousAvg, unit)
                            val tirDelta = tirDelta(currentTir, previousTir)

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

                            _uiState.update { current ->
                                current.copy(
                                    range = range,
                                    unit = unit,
                                    hasData = points.isNotEmpty(),
                                    loadFailed = false,
                                    avgDisplay = currentAvg?.let { Formatters.glucose(it, unit) } ?: "–",
                                    tirDisplay = currentTir?.let { "%.0f%%".format(it * 100) } ?: "–",
                                    avgTrend = avgDelta?.direction ?: TrendDirection.UNKNOWN,
                                    avgDeltaDisplay = avgDelta?.text,
                                    tirTrend = tirDelta?.direction ?: TrendDirection.UNKNOWN,
                                    tirDeltaDisplay = tirDelta?.text,
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
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _uiState.update { it.copy(loadFailed = true) }
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

    fun logSupply(type: SupplyType, expectedLifespanDays: Int) {
        viewModelScope.launch {
            supplyRepository.addSupply(
                type = type,
                startedAt = System.currentTimeMillis(),
                expectedLifespanDays = expectedLifespanDays,
            )
        }
    }

    fun export(format: ExportFormat) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val file = exportManager.generate(format)
                _isExporting.value = false
                _exportEvents.tryEmit(ExportEvent.Ready(file))
            } catch (e: Exception) {
                _isExporting.value = false
                _exportEvents.tryEmit(
                    ExportEvent.Failed(e.message ?: "Couldn't generate the export"),
                )
            } finally {
                _isExporting.value = false
            }
        }
    }

    private fun glucoseDelta(
        currentMgdl: Double?,
        previousMgdl: Double?,
        unit: GlucoseUnit,
    ): MetricDelta? {
        if (currentMgdl == null || previousMgdl == null) return null
        val current = Formatters.toDisplayValue(currentMgdl, unit)
        val previous = Formatters.toDisplayValue(previousMgdl, unit)
        val delta = current - previous
        val magnitude = abs(delta)
        return MetricDelta(
            direction = directionFor(delta),
            text = when (unit) {
                GlucoseUnit.MGDL -> "%.0f".format(magnitude)
                GlucoseUnit.MMOL -> "%.1f".format(magnitude)
            } + " " + unit.label,
        )
    }

    private fun tirDelta(currentTir: Double?, previousTir: Double?): MetricDelta? {
        if (currentTir == null || previousTir == null) return null
        val deltaPoints = (currentTir - previousTir) * 100.0
        return MetricDelta(
            direction = directionFor(deltaPoints),
            text = "%.0f%%".format(abs(deltaPoints)),
        )
    }

    private fun directionFor(delta: Double): TrendDirection = when {
        delta > 0.0005 -> TrendDirection.UP
        delta < -0.0005 -> TrendDirection.DOWN
        else -> TrendDirection.FLAT
    }

    private fun Supply.toUiModel(): ActiveSupplyUi {
        val status = SupplyCalculator.status(this, Instant.now())
        return ActiveSupplyUi(
            id = id,
            type = type,
            startedAt = startedAt,
            daysRemaining = status.daysRemaining,
            hoursRemaining = status.hoursRemaining,
            progressPercentage = status.progressPercentage,
            isExpired = status.isExpired,
        )
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
        const val INSIGHT_LIMIT = 5
    }
}
