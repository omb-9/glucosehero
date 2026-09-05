package com.omb9.glucosehero.ui.stats

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.omb9.glucosehero.data.export.ExportManager
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.InsightDao
import com.omb9.glucosehero.data.local.entity.InsightCardEntity
import com.omb9.glucosehero.domain.model.Ea1cConfidence
import com.omb9.glucosehero.domain.model.ExportFormat
import com.omb9.glucosehero.domain.model.ExportedFile
import com.omb9.glucosehero.domain.model.GlucosePointRow
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.Supply
import com.omb9.glucosehero.domain.model.SupplyType
import com.omb9.glucosehero.domain.model.ThemeMode
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.model.TimeRange
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.domain.repository.SupplyRepository
import com.omb9.glucosehero.util.Ea1cFormula
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.util.GlucoseRangeColor
import com.omb9.glucosehero.util.RangeCategory
import com.omb9.glucosehero.util.SupplyCalculator
import com.omb9.glucosehero.util.adagPercentage
import com.omb9.glucosehero.util.gmiPercentage
import com.omb9.glucosehero.util.shouldUseGmi
import com.omb9.glucosehero.work.HealthConnectSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.first
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

private data class StatsPipelineInput(
    val range: TimeRange,
    val points: List<GlucosePointRow>,
    val events: List<LogEvent>,
    val settings: UserSettings,
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

/** A single glucose reading in chart coordinates (x = days since range start, y = display value). */
@Immutable
data class GlucoseChartPoint(
    val x: Double,
    val y: Float,
)

/** The revealable content categories a foreground marker can carry. */
enum class MarkerCategory(val label: String) {
    MEAL("Meal"),
    EXERCISE("Exercise"),
    NOTE("Note"),
}

/**
 * A sparse, tappable foreground marker. Unlike the dense background line, this is sourced only
 * from user-authored `entries` and carries a revealable category (meal/exercise/note).
 */
@Immutable
data class GlucoseMarker(
    val entryId: Long,
    val x: Double,
    val y: Float,
    val range: RangeCategory,
    val category: MarkerCategory,
    val note: String? = null,
    val mealDescription: String? = null,
)

@Immutable
data class StatsUiState(
    val range: TimeRange = TimeRange.DAYS_14,
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
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
    /** Which clinical formula produced [ea1cValue]; the UI labels the card with this. */
    val ea1cFormula: Ea1cFormula = Ea1cFormula.ADAG,
    val ea1cConfidence: Ea1cConfidence = Ea1cConfidence.INSUFFICIENT_DATA,
    val ea1cNeededDays: Int = 14,
    val ea1cNeededReadings: Int = 20,
    val minMaxDisplay: String = "–",
    /** Chart points in display-unit space; x is days since the window start. */
    val chartPoints: List<GlucoseChartPoint> = emptyList(),
    /** Sparse, tappable foreground markers (meal/exercise/note entries only). */
    val markers: List<GlucoseMarker> = emptyList(),
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
 * Spec §4: every heavy step — folding rows into chart points, averages,
 * TIR, min/max, shading bounds — runs inside the ViewModel on
 * Dispatchers.Default. The composable only ever renders finished values.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val entryDao: EntryDao,
    settingsRepository: SettingsRepository,
    private val supplyRepository: SupplyRepository,
    private val insightDao: InsightDao,
    private val exportManager: ExportManager,
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

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

    private val _enabledMarkerCategories = MutableStateFlow(MarkerCategory.entries.toSet())
    /** Session-scoped foreground-marker category filters. */
    val enabledMarkerCategories: StateFlow<Set<MarkerCategory>> = _enabledMarkerCategories.asStateFlow()

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    /** True while the pull-to-refresh indicator is animating. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
                        entryRepository.observeGlucose(since),
                        settingsRepository.settings,
                    ) { points, events, settings -> StatsPipelineInput(range, points, events, settings) }
                }
                .collectLatest { input ->
                    try {
                        withContext(Dispatchers.Default) {
                            val range = input.range
                            val settings = input.settings
                            val unit = settings.unit
                            val sinceMillis = Formatters.daysAgoMillis(range.days)

                            val points = input.points.mapNotNull { row ->
                                // Non-finite values (a NaN/Infinity that reached
                                // storage) would poison the y-axis domain below.
                                val mgdl = row.glucoseMgdl.takeIf { it.isFinite() }
                                    ?: return@mapNotNull null
                                val x = (row.timestamp - sinceMillis).toDouble() / MILLIS_PER_DAY.toDouble()
                                GlucoseChartPoint(x, Formatters.toDisplayValue(mgdl, unit).toFloat())
                            }

                            val markers = input.events.mapNotNull { event ->
                                val category = markerCategoryFor(event) ?: return@mapNotNull null
                                val mgdl = event.glucoseMgdl?.takeIf { it.isFinite() }
                                    ?: return@mapNotNull null
                                GlucoseMarker(
                                    entryId = event.id,
                                    x = (event.timestamp - sinceMillis).toDouble() / MILLIS_PER_DAY.toDouble(),
                                    y = Formatters.toDisplayValue(mgdl, unit).toFloat(),
                                    range = GlucoseRangeColor.forValue(
                                        mgdl.toFloat(),
                                        settings.targetLowMgdl,
                                        settings.targetHighMgdl,
                                    ),
                                    category = category,
                                    note = event.note,
                                    mealDescription = event.mealDescription,
                                )
                            }

                            val values = input.points.mapNotNull { e ->
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
                                    chartPoints = points,
                                    markers = markers,
                                    themeMode = settings.themeMode,
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
            ) { stats, _ -> stats }
                .collectLatest { stats ->
                    val avg = stats.avgMgdl?.takeIf { it.isFinite() }
                    val confidence = when {
                        stats.loggedDays < MIN_CONFIDENT_DAYS ||
                            stats.readingCount < MIN_CONFIDENT_READINGS ->
                            Ea1cConfidence.INSUFFICIENT_DATA
                        stats.loggedDays >= EA1C_WINDOW_DAYS ->
                            Ea1cConfidence.FULL_90_DAY_WINDOW
                        else -> Ea1cConfidence.BUILDING_ESTIMATE
                    }

                    val cgmCount = entryDao.cgmReadingCountSince(since)
                    val manualCount = entryDao.manualReadingCountSince(since)
                    val formula = if (shouldUseGmi(cgmCount, manualCount)) {
                        Ea1cFormula.GMI
                    } else {
                        Ea1cFormula.ADAG
                    }
                    val ea1c = avg?.let { meanMgdl ->
                        if (formula == Ea1cFormula.GMI) gmiPercentage(meanMgdl)
                        else adagPercentage(meanMgdl)
                    }

                    _uiState.update { current ->
                        current.copy(
                            ea1cValue = ea1c?.let { "%.1f".format(it) } ?: "–",
                            ea1cFormula = formula,
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

    fun toggleMarkerCategory(category: MarkerCategory) {
        _enabledMarkerCategories.update { current ->
            if (category in current) current - category else current + category
        }
    }

    /**
     * Pull-to-refresh entry point. The stats pipeline is already reactive via
     * Room flows (see init), so there is no re-fetch to perform — this only
     * enforces a minimum duration so the indicator animation always plays fully.
     */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                if (settingsDataStore.healthConnectSyncEnabled.first()) {
                    val request = OneTimeWorkRequestBuilder<HealthConnectSyncWorker>()
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    WorkManager.getInstance(context).enqueueUniqueWork(
                        HealthConnectSyncWorker.EXPEDITED_UNIQUE_NAME,
                        ExistingWorkPolicy.REPLACE,
                        request,
                    )
                }
                delay(REFRESH_MIN_MILLIS)
            } finally {
                _isRefreshing.value = false
            }
        }
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

    private fun markerCategoryFor(event: LogEvent): MarkerCategory? = when {
        event.carbsGrams != null || event.proteinGrams != null || event.fatGrams != null ||
            !event.mealDescription.isNullOrBlank() -> MarkerCategory.MEAL
        event.exerciseMinutes != null -> MarkerCategory.EXERCISE
        !event.note.isNullOrBlank() -> MarkerCategory.NOTE
        else -> null
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

    private fun trim(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)

    private companion object {
        const val MILLIS_PER_DAY = 24f * 60f * 60f * 1000f
        const val REFRESH_MIN_MILLIS = 600L
        const val EA1C_WINDOW_DAYS = 90
        const val MIN_CONFIDENT_DAYS = 14
        const val MIN_CONFIDENT_READINGS = 20
        const val INSIGHT_LIMIT = 5
    }
}
