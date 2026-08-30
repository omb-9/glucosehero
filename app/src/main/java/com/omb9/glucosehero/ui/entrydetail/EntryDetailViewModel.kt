package com.omb9.glucosehero.ui.entrydetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.log.DraftEventState
import com.omb9.glucosehero.ui.log.toLogEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val entryRepository: EntryRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val entryId: Long = checkNotNull(savedStateHandle["entryId"])

    val entry: StateFlow<LogEvent?> = entryRepository.observeEntry(entryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun toDetailDraft(
        e: LogEvent,
        glucoseInput: String,
        insulinBasalInput: String,
        insulinBolusInput: String,
        carbsInput: String,
        mealDescriptionInput: String,
        exerciseInput: String,
        noteInput: String,
    ): DraftEventState = DraftEventState(
        activeCategory = com.omb9.glucosehero.domain.model.EntryType.GLUCOSE,
        glucose = if (e.glucoseMgdl != null) glucoseInput else "",
        mealContext = e.mealContext ?: com.omb9.glucosehero.domain.model.MealContext.NONE,
        insulinBasal = if (e.insulinBasalUnits != null) insulinBasalInput else "",
        insulinBolus = if (e.insulinBolusUnits != null) insulinBolusInput else "",
        carbsGrams = if (e.carbsGrams != null) carbsInput else "",
        mealDescription = if (e.mealDescription != null) mealDescriptionInput else "",
        exerciseMinutes = if (e.exerciseMinutes != null) exerciseInput else "",
        exerciseIntensity = e.exerciseIntensity ?: com.omb9.glucosehero.domain.model.ActivityIntensity.MODERATE,
        note = noteInput,
    )

    fun save(
        glucoseInput: String,
        insulinBasalInput: String,
        insulinBolusInput: String,
        carbsInput: String,
        mealDescriptionInput: String,
        exerciseInput: String,
        noteInput: String,
        onDone: () -> Unit,
    ) {
        val current = entry.value ?: return
        val draft = toDetailDraft(
            current, glucoseInput, insulinBasalInput, insulinBolusInput, carbsInput,
            mealDescriptionInput, exerciseInput, noteInput,
        )
        // Reuse the single source-of-truth validator; seeding from the loaded
        // LogEvent ensures unchanged metrics round-trip correctly.
        val updated = draft.toLogEvent(settings.value, current.timestamp) ?: return
        viewModelScope.launch {
            entryRepository.update(updated.copy(id = current.id))
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            entryRepository.delete(entryId)
            onDone()
        }
    }
}
