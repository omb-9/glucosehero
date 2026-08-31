package com.omb9.glucosehero.ui.entrydetail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.EntryType
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.glance.WidgetRefresher
import com.omb9.glucosehero.ui.log.DraftEventState
import com.omb9.glucosehero.ui.log.toLogEvent
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Editable form state for the log-detail screen.
 *
 * [activeCategories] is the source of truth for which sub-categories the user
 * wants on this entry (Glucose, Insulin, Meal, Activity). Note is not a
 * removable sub-category — it is a free-form field that is always editable.
 */
@Immutable
data class EntryDetailFormState(
    val isSeeded: Boolean = false,
    val loadFailed: Boolean = false,
    val isEditing: Boolean = false,
    val timestamp: Long = 0L,
    val activeCategories: ImmutableSet<EntryType> = persistentSetOf(),
    val glucose: String = "",
    val mealContext: MealContext = MealContext.NONE,
    val insulinBasal: String = "",
    val insulinBolus: String = "",
    val carbs: String = "",
    val protein: String = "",
    val fat: String = "",
    val mealDescription: String = "",
    val exerciseMinutes: String = "",
    val exerciseIntensity: ActivityIntensity = ActivityIntensity.MODERATE,
    val note: String = "",
    val isSaving: Boolean = false,
)

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val entryRepository: EntryRepository,
    settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {

    private val entryId: Long = checkNotNull(savedStateHandle["entryId"])

    val entry: StateFlow<LogEvent?> = entryRepository.observeEntry(entryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _form = MutableStateFlow(EntryDetailFormState())
    val form: StateFlow<EntryDetailFormState> = _form.asStateFlow()

    val canSave: StateFlow<Boolean> = combine(_form, entry, settings) { form, current, settings ->
        current != null && form.isSeeded &&
            form.toDraft().toLogEvent(settings, form.timestamp) != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _saveErrors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    /** One-shot save failures surfaced to the UI. */
    val saveErrors: SharedFlow<Throwable> = _saveErrors.asSharedFlow()

    init {
        // Seed the editable form exactly once from the Room StateFlow. The VM
        // survives recomposition/rotation, so the user never loses in-progress
        // edits when the screen is rebuilt.
        viewModelScope.launch {
            val loaded = withTimeoutOrNull(5_000) { entry.filterNotNull().first() }
            if (loaded == null) {
                _form.update { it.copy(loadFailed = true) }
            } else {
                seed(loaded, settings.value)
            }
        }
    }

    private fun seed(event: LogEvent, settings: UserSettings) {
        _form.value = EntryDetailFormState(
            isSeeded = true,
            timestamp = event.timestamp,
            activeCategories = event.presentCategories(),
            glucose = event.glucoseMgdl?.let { Formatters.glucose(it, settings.unit) } ?: "",
            mealContext = event.mealContext ?: MealContext.NONE,
            insulinBasal = event.insulinBasalUnits?.let(::trimDouble) ?: "",
            insulinBolus = event.insulinBolusUnits?.let(::trimDouble) ?: "",
            carbs = event.carbsGrams?.toString() ?: "",
            protein = event.proteinGrams?.toString() ?: "",
            fat = event.fatGrams?.toString() ?: "",
            mealDescription = event.mealDescription.orEmpty(),
            exerciseMinutes = event.exerciseMinutes?.toString() ?: "",
            exerciseIntensity = event.exerciseIntensity ?: ActivityIntensity.MODERATE,
            note = event.note.orEmpty(),
        )
    }

    fun onEditToggle() {
        _form.update { it.copy(isEditing = !it.isEditing) }
    }

    fun onTimestampChange(timestamp: Long) {
        _form.update { it.copy(timestamp = timestamp) }
    }

    fun onAddCategory(type: EntryType) {
        _form.update { it.copy(activeCategories = (it.activeCategories + type).toImmutableSet()) }
    }

    fun onRemoveCategory(type: EntryType) {
        _form.update { state ->
            state.copy(
                activeCategories = (state.activeCategories - type).toImmutableSet(),
                glucose = if (type == EntryType.GLUCOSE) "" else state.glucose,
                mealContext = if (type == EntryType.GLUCOSE) MealContext.NONE else state.mealContext,
                insulinBasal = if (type == EntryType.INSULIN) "" else state.insulinBasal,
                insulinBolus = if (type == EntryType.INSULIN) "" else state.insulinBolus,
                carbs = if (type == EntryType.MEAL) "" else state.carbs,
                protein = if (type == EntryType.MEAL) "" else state.protein,
                fat = if (type == EntryType.MEAL) "" else state.fat,
                mealDescription = if (type == EntryType.MEAL) "" else state.mealDescription,
                exerciseMinutes = if (type == EntryType.ACTIVITY) "" else state.exerciseMinutes,
                exerciseIntensity = if (type == EntryType.ACTIVITY) {
                    ActivityIntensity.MODERATE
                } else {
                    state.exerciseIntensity
                },
            )
        }
    }

    fun onGlucoseChange(value: String) { _form.update { it.copy(glucose = value) } }
    fun onMealContextChange(value: MealContext) { _form.update { it.copy(mealContext = value) } }
    fun onInsulinBasalChange(value: String) { _form.update { it.copy(insulinBasal = value) } }
    fun onInsulinBolusChange(value: String) { _form.update { it.copy(insulinBolus = value) } }
    fun onCarbsChange(value: String) { _form.update { it.copy(carbs = value) } }
    fun onProteinChange(value: String) { _form.update { it.copy(protein = value) } }
    fun onFatChange(value: String) { _form.update { it.copy(fat = value) } }
    fun onMealDescriptionChange(value: String) { _form.update { it.copy(mealDescription = value) } }
    fun onExerciseMinutesChange(value: String) { _form.update { it.copy(exerciseMinutes = value) } }
    fun onExerciseIntensityChange(value: ActivityIntensity) {
        _form.update { it.copy(exerciseIntensity = value) }
    }
    fun onNoteChange(value: String) { _form.update { it.copy(note = value) } }

    /**
     * Builds the domain event this form would persist, or null when the form
     * is not seed yet, the underlying entity is missing, or a typed value is
     * unparsable. This is the same validator [com.omb9.glucosehero.ui.log.toLogEvent]
     * used by the new-entry sheet, so detail edits and new entries can never
     * disagree about what is savable.
     */
    fun buildUpdatedEvent(): LogEvent? {
        val current = entry.value ?: return null
        if (!_form.value.isSeeded) return null
        return _form.value.toDraft().toLogEvent(settings.value, _form.value.timestamp)
            ?.copy(id = current.id)
    }

    fun save(onDone: () -> Unit) {
        val current = entry.value ?: return
        val formState = _form.value
        if (formState.isSaving || !formState.isSeeded) return

        val updated = formState.toDraft()
            .toLogEvent(settings.value, formState.timestamp)
            ?.copy(id = current.id)
            ?: return

        _form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            var saved = false
            try {
                entryRepository.update(updated)
                if (updated.glucoseMgdl != null) {
                    reminderScheduler.cancelPostMealCheck()
                }
                reminderScheduler.schedulePostMealCheck(updated)
                saved = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveErrors.emit(e)
            } finally {
                _form.update { it.copy(isSaving = false) }
            }
            if (saved) {
                widgetRefresher.refresh()
                onDone()
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                entry.value?.let { reminderScheduler.cancelIfTriggering(it) }
                entryRepository.delete(entryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _saveErrors.emit(e)
                return@launch
            }
            widgetRefresher.refresh()
            onDone()
        }
    }

    private fun EntryDetailFormState.toDraft(): DraftEventState = DraftEventState(
        activeCategory = EntryType.GLUCOSE,
        glucose = if (EntryType.GLUCOSE in activeCategories) glucose else "",
        mealContext = mealContext,
        insulinBasal = if (EntryType.INSULIN in activeCategories) insulinBasal else "",
        insulinBolus = if (EntryType.INSULIN in activeCategories) insulinBolus else "",
        carbsGrams = if (EntryType.MEAL in activeCategories) carbs else "",
        proteinGrams = if (EntryType.MEAL in activeCategories) protein else "",
        fatGrams = if (EntryType.MEAL in activeCategories) fat else "",
        mealDescription = if (EntryType.MEAL in activeCategories) mealDescription else "",
        exerciseMinutes = if (EntryType.ACTIVITY in activeCategories) exerciseMinutes else "",
        exerciseIntensity = exerciseIntensity,
        note = note,
    )
}

/** Which removable sub-categories this event currently carries data for. */
private fun LogEvent.presentCategories(): ImmutableSet<EntryType> = buildSet {
    if (glucoseMgdl != null) add(EntryType.GLUCOSE)
    if (insulinBasalUnits != null || insulinBolusUnits != null) add(EntryType.INSULIN)
    if (carbsGrams != null || proteinGrams != null || fatGrams != null ||
        !mealDescription.isNullOrBlank()
    ) add(EntryType.MEAL)
    if (exerciseMinutes != null) add(EntryType.ACTIVITY)
}.toImmutableSet()

private fun trimDouble(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
