package com.omb9.glucosehero.ui.log

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.EntryType
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.HeroAiPrefill
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.glance.WidgetRefresher
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.util.StreakCalculator
import com.omb9.glucosehero.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Glucose value position relative to the user's target range. */
enum class GlucoseStatus { LOW, IN_RANGE, HIGH }

/**
 * Spec: every state class feeding the 90-day scrollable list is @Immutable and
 * backed by kotlinx immutable collections, so Kotlin 2.0 Strong Skipping can
 * prove stability and skip untouched rows during recomposition.
 */
@Immutable
data class LogEntryItem(
    val id: Long,
    val type: EntryType,
    val timeLabel: String,
    val title: String,
    val subtitle: String?,
    val glucoseDisplay: String?,
    val glucoseStatus: GlucoseStatus?,
)

@Immutable
data class LogDayGroup(
    val epochDay: Long,
    val header: String,
    val entries: ImmutableList<LogEntryItem>,
)

@Immutable
data class LogUiState(
    val isLoading: Boolean = true,
    val days: ImmutableList<LogDayGroup> = persistentListOf(),
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
)

/** One-shot reward payload surfaced to the Add Entry sheet after a streak-extending save. */
data class StreakReward(
    val previousStreak: Int,
    val currentStreak: Int,
)

@HiltViewModel
class LogViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    settingsRepository: SettingsRepository,
    private val heroAiPrefillCoordinator: HeroAiPrefillCoordinator,
    private val reminderScheduler: ReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _draft = MutableStateFlow(DraftEventState())
    val draft: StateFlow<DraftEventState> = _draft.asStateFlow()

    /** Pending Hero AI prefill waiting for the Log screen to consume it. */
    val pendingHeroAiPrefill: StateFlow<HeroAiPrefill?> = heroAiPrefillCoordinator.pendingPrefill

    private val _streakReward = MutableStateFlow<StreakReward?>(null)
    /** Set once after a save that extends the streak; consumed by the UI. */
    val streakReward: StateFlow<StreakReward?> = _streakReward.asStateFlow()

    private val _saveErrors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    /** One-shot save failures surfaced to the UI. */
    val saveErrors: SharedFlow<Throwable> = _saveErrors.asSharedFlow()

    val uiState: StateFlow<LogUiState> =
        combine(
            entryRepository.observeEntries(Formatters.daysAgoMillis(WINDOW_DAYS)),
            settingsRepository.settings,
        ) { entries, settings ->
            LogUiState(
                isLoading = false,
                days = groupByDay(entries, settings),
                unit = settings.unit,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())

    val canSave: StateFlow<Boolean> = combine(_draft, settings) { draft, settings ->
        draft.toLogEvent(settings, 0L) != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ---- Draft setters: each is a pure copy/update — no clearing, no side-effects ----

    fun onCategorySelected(type: EntryType) {
        _draft.update { it.copy(activeCategory = type) }
    }

    fun onGlucoseChange(value: String) { _draft.update { it.copy(glucose = value) } }
    fun onMealContextChange(context: MealContext) { _draft.update { it.copy(mealContext = context) } }
    fun onInsulinBasalChange(value: String) { _draft.update { it.copy(insulinBasal = value) } }
    fun onInsulinBolusChange(value: String) { _draft.update { it.copy(insulinBolus = value) } }
    fun onCarbsChange(value: String) { _draft.update { it.copy(carbsGrams = value) } }
    fun onProteinChange(value: String) { _draft.update { it.copy(proteinGrams = value) } }
    fun onFatChange(value: String) { _draft.update { it.copy(fatGrams = value) } }
    fun onMealDescriptionChange(value: String) { _draft.update { it.copy(mealDescription = value) } }
    fun onExerciseMinutesChange(value: String) { _draft.update { it.copy(exerciseMinutes = value) } }
    fun onExerciseIntensityChange(value: ActivityIntensity) {
        _draft.update { it.copy(exerciseIntensity = value) }
    }
    fun onNoteChange(value: String) { _draft.update { it.copy(note = value) } }

    fun onPostMealReminderChange(enabled: Boolean) {
        _draft.update { it.copy(postMealReminderEnabled = enabled) }
    }

    /** Starts a fresh draft with the user's saved reminder default applied. */
    fun openNewDraft(postMealReminderEnabled: Boolean) {
        _streakReward.value = null
        _draft.value = DraftEventState(postMealReminderEnabled = postMealReminderEnabled)
    }

    /** Applies a Hero AI `prefill_log_draft` payload to the draft state. */
    fun applyHeroAiPrefill(prefill: HeroAiPrefill) {
        val unit = settings.value.unit
        val activeCategory = when {
            prefill.glucoseMgdl != null -> EntryType.GLUCOSE
            prefill.insulinBasalUnits != null || prefill.insulinBolusUnits != null -> EntryType.INSULIN
            prefill.carbsGrams != null || !prefill.mealDescription.isNullOrBlank() -> EntryType.MEAL
            prefill.exerciseMinutes != null -> EntryType.ACTIVITY
            else -> _draft.value.activeCategory
        }
        _draft.value = DraftEventState(
            activeCategory = activeCategory,
            glucose = prefill.glucoseMgdl?.let { Formatters.glucose(it.toDouble(), unit) } ?: "",
            insulinBasal = prefill.insulinBasalUnits?.let(::trim) ?: "",
            insulinBolus = prefill.insulinBolusUnits?.let(::trim) ?: "",
            carbsGrams = prefill.carbsGrams?.toString() ?: "",
            mealDescription = prefill.mealDescription ?: "",
            exerciseMinutes = prefill.exerciseMinutes?.toString() ?: "",
            postMealReminderEnabled = settings.value.postMealRemindersEnabled,
        )
    }

    fun consumeHeroAiPrefill() {
        heroAiPrefillCoordinator.consumePrefill()
    }

    fun saveDraft(onSaved: () -> Unit) {
        if (_draft.value.isSaving) return
        _draft.update { it.copy(isSaving = true) }
        val event = _draft.value.toLogEvent(settings.value, System.currentTimeMillis())
        if (event == null) {
            _draft.update { it.copy(isSaving = false) }
            return
        }
        val reminderEnabled = _draft.value.postMealReminderEnabled
        viewModelScope.launch {
            var saved = false
            try {
                val loggedDays = entryRepository.distinctLoggedDays().toMutableSet()
                val before = StreakCalculator.currentStreak(loggedDays)
                entryRepository.add(event)
                val newLocalDate = Formatters.localDate(event.timestamp)
                val after = if (event.qualifiesForStreak()) {
                    loggedDays += newLocalDate
                    StreakCalculator.currentStreak(loggedDays)
                } else {
                    before
                }

                when {
                    event.glucoseMgdl != null -> reminderScheduler.cancelPostMealCheck()
                    reminderEnabled -> reminderScheduler.schedulePostMealCheck(event)
                }

                _draft.value = DraftEventState(
                    postMealReminderEnabled = settings.value.postMealRemindersEnabled,
                )
                if (after > before) {
                    _streakReward.value =
                        StreakReward(previousStreak = before, currentStreak = after)
                } else {
                    onSaved()
                }
                saved = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveErrors.emit(e)
            } finally {
                _draft.update { it.copy(isSaving = false) }
            }
            if (saved) widgetRefresher.refresh()
        }
    }

    fun consumeStreakReward() {
        _streakReward.value = null
    }

    fun discardDraft() {
        _streakReward.value = null
        _draft.value = DraftEventState(
            postMealReminderEnabled = settings.value.postMealRemindersEnabled,
        )
    }

    private fun groupByDay(
        entries: List<LogEvent>,
        settings: UserSettings,
    ): ImmutableList<LogDayGroup> =
        entries
            .groupBy { Formatters.localDate(it.timestamp) }
            .entries
            .sortedByDescending { it.key }
            .map { (date, dayEntries) ->
                LogDayGroup(
                    epochDay = date.toEpochDay(),
                    header = Formatters.dayHeader(date),
                    entries = dayEntries
                        .map { it.toItem(settings) }
                        .toImmutableList(),
                )
            }
            .toImmutableList()

    private fun LogEvent.toItem(settings: UserSettings): LogEntryItem {
        val unit = settings.unit
        val glucoseDisplay = glucoseMgdl?.let { Formatters.glucose(it, unit) }
        val status = glucoseMgdl?.let {
            when {
                it < settings.targetLowMgdl -> GlucoseStatus.LOW
                it > settings.targetHighMgdl -> GlucoseStatus.HIGH
                else -> GlucoseStatus.IN_RANGE
            }
        }
        val (title, subtitle) = describe(this, settings)
        return LogEntryItem(
            id = id,
            type = primaryType(this),
            timeLabel = Formatters.time(timestamp, settings.use24HourTime),
            title = title,
            subtitle = subtitle ?: note?.takeIf { it.isNotBlank() },
            glucoseDisplay = glucoseDisplay,
            glucoseStatus = status,
        )
    }

    private fun primaryType(event: LogEvent): EntryType = when {
        event.glucoseMgdl != null -> EntryType.GLUCOSE
        event.insulinBasalUnits != null || event.insulinBolusUnits != null -> EntryType.INSULIN
        event.carbsGrams != null || event.proteinGrams != null || event.fatGrams != null ||
            !event.mealDescription.isNullOrBlank() -> EntryType.MEAL
        event.exerciseMinutes != null -> EntryType.ACTIVITY
        else -> EntryType.NOTE
    }

    /**
     * Multi-metric subtitle: every present slot contributes a token, joined
     * with " · " — e.g. "120 mg/dL · 4u Long · 6u Rapid · 45 g · 30 min".
     */
    private fun describe(event: LogEvent, settings: UserSettings): Pair<String, String?> {
        val unit = settings.unit
        val tokens = mutableListOf<String>()

        event.glucoseMgdl?.let { tokens += "${Formatters.glucose(it, unit)} ${unit.label}" }
        event.insulinBasalUnits?.let { tokens += "${trim(it)}u Long" }
        event.insulinBolusUnits?.let { tokens += "${trim(it)}u Rapid" }
        event.carbsGrams?.let { tokens += "$it g" }
        event.proteinGrams?.let { tokens += "$it g protein" }
        event.fatGrams?.let { tokens += "$it g fat" }
        event.mealDescription?.takeIf { it.isNotBlank() }?.let { tokens += it }
        event.exerciseMinutes?.let { tokens += "$it min" }

        // If only a note, the title serves as the label and the subtitle
        // stays null so the row falls back to `note` in `toItem`.
        if (tokens.isEmpty()) return "Note" to null

        // Derive a compact title from which metrics are present.
        val titleParts = mutableListOf<String>()
        if (event.glucoseMgdl != null) titleParts += "Glucose"
        if (event.insulinBasalUnits != null || event.insulinBolusUnits != null) titleParts += "Insulin"
        if (event.carbsGrams != null || event.proteinGrams != null || event.fatGrams != null ||
            !event.mealDescription.isNullOrBlank()
        ) titleParts += "Meal"
        if (event.exerciseMinutes != null) titleParts += "Activity"
        val title = titleParts.joinToString(" · ").ifBlank { "Note" }

        return title to tokens.joinToString(" · ")
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

    private fun LogEvent.qualifiesForStreak(): Boolean =
        glucoseMgdl != null ||
            insulinBasalUnits != null ||
            insulinBolusUnits != null ||
            carbsGrams != null ||
            proteinGrams != null ||
            fatGrams != null ||
            !mealDescription.isNullOrBlank()

    private companion object {
        const val WINDOW_DAYS = 90
    }
}
