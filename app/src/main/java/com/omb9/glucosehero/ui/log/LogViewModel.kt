package com.omb9.glucosehero.ui.log

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.local.entity.toDomain
import com.omb9.glucosehero.data.remote.off.OffProduct
import com.omb9.glucosehero.data.remote.off.OpenFoodFactsApi
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.util.PortionCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.EntryType
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.HeroAiPrefill
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.EntryRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.glance.WidgetRefresher
import com.omb9.glucosehero.util.BolusCalculator
import com.omb9.glucosehero.util.CrisisDetector
import com.omb9.glucosehero.util.Formatters
import com.omb9.glucosehero.util.IobCalculator
import com.omb9.glucosehero.util.StreakCalculator
import com.omb9.glucosehero.work.HealthConnectSyncWorker
import com.omb9.glucosehero.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
    val source: EntrySource = EntrySource.MANUAL,
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
@Immutable
data class StreakReward(
    val previousStreak: Int,
    val currentStreak: Int,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val entryDao: EntryDao,
    settingsRepository: SettingsRepository,
    private val heroAiPrefillCoordinator: HeroAiPrefillCoordinator,
    private val reminderScheduler: ReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val database: GlucoseHeroDatabase,
    private val openFoodFactsApi: OpenFoodFactsApi,
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    /** Minute-grain ticker so the IOB value keeps decaying without a DB emission. */
    private val timeTick: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(IOB_TICK_MILLIS)
        }
    }

    /** Live insulin-on-board (units) for the home dashboard. */
    val activeInsulin: StateFlow<Double> = combine(
        settingsRepository.bolusSettings,
        entryRepository.observeEntries(System.currentTimeMillis() - ACTIVE_INSULIN_WINDOW_MILLIS),
        timeTick,
    ) { bolus, entries, _ ->
        val boluses = entries
            .filter { it.insulinBolusUnits != null }
            .map { IobCalculator.BolusEntry(it.timestamp, it.insulinBolusUnits!!) }
        IobCalculator.activeInsulinOnBoard(
            boluses = boluses,
            diaHours = bolus.diaHours.toDouble(),
            now = Instant.now(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    private val _draft = MutableStateFlow(DraftEventState())
    val draft: StateFlow<DraftEventState> = _draft.asStateFlow()

    /** Food currently backing the draft, or null when the meal was typed by hand. */
    private val _selectedFood = MutableStateFlow<FoodEntity?>(null)
    val selectedFood: StateFlow<FoodEntity?> = _selectedFood.asStateFlow()

    /** Free-text query for the Log list; debounced before hitting the DAO. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Single-day filter, or null to show the rolling list. */
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    /**
     * Debounced log search results. Kept separate from the rolling list so a
     * keystroke never triggers a query per character and the unbounded entries
     * table is never streamed through Kotlin.
     */
    private val searchResults: StateFlow<List<LogEvent>> = _searchQuery
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .mapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.isBlank()) emptyList()
            else entryDao.searchEntries(trimmed).map { it.toLogEvent() }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recently used foods for the one-tap chips in the Add Entry sheet. */
    val recentFoods: StateFlow<List<FoodEntity>> = flow {
        emit(database.foodDao().recent())
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _foodSearchQuery = MutableStateFlow("")
    val foodSearchQuery: StateFlow<String> = _foodSearchQuery.asStateFlow()

    val foodSearchResults: StateFlow<List<FoodEntity>> = _foodSearchQuery
        .debounce(300)
        .mapLatest { query ->
            if (query.isBlank()) emptyList() else database.foodDao().search(query.trim())
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _foodLookupState = MutableStateFlow<FoodLookupState>(FoodLookupState.Idle)
    val foodLookupState: StateFlow<FoodLookupState> = _foodLookupState.asStateFlow()

    /**
     * Recommended bolus for the in-progress draft, or null until the user has
     * typed both a carb count and a glucose value. Recomputed reactively as
     * either field, the dosing parameters, or the current IOB changes.
     */
    val suggestedBolus: StateFlow<Double?> = combine(
        _draft,
        settingsRepository.bolusSettings,
        settings,
        activeInsulin,
    ) { draft, bolus, userSettings, iob ->
        val carbs = draft.carbsGrams.trim().toIntOrNull()?.takeIf { it > 0 }
            ?: return@combine null
        val glucoseDisplay = Formatters.parseDecimal(draft.glucose)?.takeIf { it > 0 }
            ?: return@combine null
        val glucoseMgdl = Formatters.displayToMgdl(glucoseDisplay, userSettings.unit)
        BolusCalculator.recommend(
            currentGlucoseMgdl = glucoseMgdl,
            targetGlucoseMgdl = bolus.targetGlucoseMgdl.toDouble(),
            carbsGrams = carbs.toDouble(),
            carbRatio = bolus.cirRatio.toDouble(),
            insulinSensitivityMgdl = bolus.isfMgdl.toDouble(),
            insulinOnBoard = iob,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Pending Hero AI prefill waiting for the Log screen to consume it. */
    val pendingHeroAiPrefill: StateFlow<HeroAiPrefill?> = heroAiPrefillCoordinator.pendingPrefill

    private val _streakReward = MutableStateFlow<StreakReward?>(null)
    /** Set once after a save that extends the streak; consumed by the UI. */
    val streakReward: StateFlow<StreakReward?> = _streakReward.asStateFlow()

    private val _showCrisisSupport = MutableStateFlow(false)
    /** True when the last saved journal text matched [CrisisDetector]. */
    val showCrisisSupport: StateFlow<Boolean> = _showCrisisSupport.asStateFlow()

    fun dismissCrisisSupport() {
        _showCrisisSupport.value = false
    }

    private val _saveErrors = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    /** One-shot save failures surfaced to the UI. */
    val saveErrors: SharedFlow<Throwable> = _saveErrors.asSharedFlow()

    private val _isRefreshing = MutableStateFlow(false)
    /** True while the pull-to-refresh indicator is animating. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    val uiState: StateFlow<LogUiState> =
        combine(
            entryRepository.observeEntries(Formatters.daysAgoMillis(WINDOW_DAYS)),
            settingsRepository.settings,
            _searchQuery,
            searchResults,
            _selectedDate,
        ) { entries, settings, query, search, selectedDate ->
            val source = if (query.isBlank()) entries else search
            val filtered = selectedDate?.let { date ->
                source.filter { Formatters.localDate(it.timestamp) == date }
            } ?: source
            LogUiState(
                isLoading = false,
                days = groupByDay(filtered, settings),
                unit = settings.unit,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState())

    val canSave: StateFlow<Boolean> = combine(_draft, settings) { draft, settings ->
        draft.toLogEvent(settings, 0L) != null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Pull-to-refresh entry point. The log list is already reactive via Room's
     * [Flow], so there is no re-fetch to perform — this only enforces a minimum
     * duration so the indicator animation always plays fully.
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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun onDateSelected(date: LocalDate) {
        _selectedDate.value = date
    }

    fun clearDateFilter() {
        _selectedDate.value = null
    }

    // ---- Draft setters: each is a pure copy/update — no clearing, no side-effects ----

    fun onCategorySelected(type: EntryType) {
        _draft.update { it.copy(activeCategory = type) }
    }

    fun onGlucoseChange(value: String) { _draft.update { it.copy(glucose = value) } }
    fun onMealContextChange(context: MealContext) { _draft.update { it.copy(mealContext = context) } }
    fun onInsulinBasalChange(value: String) { _draft.update { it.copy(insulinBasal = value) } }
    fun onInsulinBolusChange(value: String) { _draft.update { it.copy(insulinBolus = value) } }

    /** Populates the bolus field with the current smart-bolus suggestion. */
    fun useSuggestedBolus() {
        val suggestion = suggestedBolus.value ?: return
        onInsulinBolusChange(trim(suggestion))
    }
    fun onCarbsChange(value: String) { _draft.update { it.copy(carbsGrams = value) } }
    fun onProteinChange(value: String) { _draft.update { it.copy(proteinGrams = value) } }
    fun onFatChange(value: String) { _draft.update { it.copy(fatGrams = value) } }
    fun onMealDescriptionChange(value: String) { _draft.update { it.copy(mealDescription = value) } }
    fun onExerciseMinutesChange(value: String) { _draft.update { it.copy(exerciseMinutes = value) } }
    fun onExerciseIntensityChange(value: ActivityIntensity) {
        _draft.update { it.copy(exerciseIntensity = value) }
    }
    fun onNoteChange(value: String) { _draft.update { it.copy(note = value) } }

    fun onMoodScoreChange(value: Int?) {
        _draft.update {
            it.copy(
                moodScore = value,
                moodLabel = if (value == null) null else it.moodLabel,
            )
        }
    }

    fun onMoodLabelChange(value: String?) {
        _draft.update { it.copy(moodLabel = value) }
    }

    fun onPostMealReminderChange(enabled: Boolean) {
        _draft.update { it.copy(postMealReminderEnabled = enabled) }
    }

    fun onFoodSelected(food: FoodEntity) = applyFood(food)

    fun onFoodSearchQueryChange(query: String) {
        _foodSearchQuery.value = query
    }

    fun onBarcodeScanned(barcode: String?) {
        val code = barcode?.trim()?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            _foodLookupState.value = FoodLookupState.Loading
            try {
                // Cache first: a known barcode resolves fully offline.
                val cached = database.foodDao().getByBarcode(code)
                if (cached != null) {
                    applyFood(cached)
                    _foodLookupState.value = FoodLookupState.Idle
                    return@launch
                }

                // Cache miss: respect the privacy toggle before any network call.
                if (!settingsDataStore.barcodeLookupEnabled.first()) {
                    _foodLookupState.value = FoodLookupState.ManualEntry(code)
                    return@launch
                }

                val response = openFoodFactsApi.product(code)
                if (response.code() == 404) {
                    _foodLookupState.value = FoodLookupState.ManualEntry(code)
                    return@launch
                }

                val body = response.body()
                val product = body?.product
                when {
                    response.isSuccessful && body != null && body.status == 1 && product != null ->
                        _foodLookupState.value = FoodLookupState.ConfirmOff(product.toOffFoodDraft(code))

                    response.isSuccessful && (body == null || body.status == 0 || product == null) ->
                        _foodLookupState.value = FoodLookupState.ManualEntry(code)

                    else ->
                        _foodLookupState.value = FoodLookupState.Error("Couldn't look up that barcode.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _foodLookupState.value = FoodLookupState.Error("Couldn't reach Open Food Facts.")
            }
        }
    }

    fun dismissFoodLookup() {
        _foodLookupState.value = FoodLookupState.Idle
    }

    fun confirmOffFood(draft: OffFoodDraft) {
        val carbs = Formatters.parseDecimal(draft.carbs)?.takeIf { it > 0 } ?: return
        val name = draft.name.trim().ifBlank { return }
        val food = FoodEntity(
            name = name,
            brand = draft.brand.trim().ifBlank { null },
            barcode = draft.barcode.trim().ifBlank { null },
            carbsGrams = carbs,
            proteinGrams = Formatters.parseDecimal(draft.protein)?.takeIf { it > 0 },
            fatGrams = Formatters.parseDecimal(draft.fat)?.takeIf { it > 0 },
            kcal = draft.kcal,
            servingGrams = draft.servingGrams,
            servingLabel = draft.servingLabel.trim().ifBlank { null },
            source = FoodSource.OPEN_FOOD_FACTS,
            offFetchedAt = System.currentTimeMillis(),
            userCorrected = true,
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            val id = database.foodDao().insert(food)
            applyFood(food.copy(id = id))
            _foodLookupState.value = FoodLookupState.Idle
        }
    }

    fun saveAsFood() {
        val current = _draft.value
        val carbs = current.carbsGrams.trim().toIntOrNull()?.takeIf { it > 0 } ?: return
        val name = current.mealDescription.trim().ifBlank { return }
        val food = FoodEntity(
            name = name,
            brand = null,
            barcode = null,
            carbsGrams = carbs.toDouble(),
            proteinGrams = current.proteinGrams.trim().toIntOrNull()?.takeIf { it > 0 }?.toDouble(),
            fatGrams = current.fatGrams.trim().toIntOrNull()?.takeIf { it > 0 }?.toDouble(),
            kcal = null,
            servingGrams = null,
            servingLabel = null,
            source = FoodSource.FROM_ENTRY,
            userCorrected = false,
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            val id = database.foodDao().insert(food)
            _selectedFood.value = food.copy(id = id)
        }
    }

    private fun applyFood(food: FoodEntity) {
        _draft.update {
            it.copy(
                activeCategory = EntryType.MEAL,
                carbsGrams = trim(food.carbsGrams),
                proteinGrams = food.proteinGrams?.let(::trim) ?: "",
                fatGrams = food.fatGrams?.let(::trim) ?: "",
                mealDescription = food.name,
            )
        }
        _selectedFood.value = food
    }

    private fun OffProduct.toOffFoodDraft(barcode: String): OffFoodDraft {
        val n = nutriments
        val servingGrams = PortionCalculator.parseServingGrams(servingSize)

        fun scaled(serving: Double?, per100: Double?): Double? =
            serving
                ?: if (per100 != null && servingGrams != null) per100 * servingGrams / 100.0
                else per100

        val servingLabel = servingSize?.trim()?.takeIf { it.isNotBlank() }
            ?: if (servingGrams != null) "${trim(servingGrams)} g" else "100 g"

        return OffFoodDraft(
            barcode = barcode.trim(),
            name = productName?.trim().orEmpty(),
            brand = brands?.trim().orEmpty(),
            servingLabel = servingLabel,
            servingGrams = servingGrams,
            carbs = scaled(n?.carbsServing, n?.carbs100g)?.let(::trim) ?: "",
            protein = scaled(n?.proteinServing, n?.protein100g)?.let(::trim) ?: "",
            fat = scaled(n?.fatServing, n?.fat100g)?.let(::trim) ?: "",
            kcal = scaled(n?.kcalServing, n?.kcal100g),
        )
    }

    /** Starts a fresh draft with the user's saved reminder default applied. */
    fun openNewDraft(postMealReminderEnabled: Boolean) {
        _streakReward.value = null
        _showCrisisSupport.value = false
        _selectedFood.value = null
        _foodLookupState.value = FoodLookupState.Idle
        _foodSearchQuery.value = ""
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
        _selectedFood.value = null
        _foodLookupState.value = FoodLookupState.Idle
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
        _showCrisisSupport.value = false
        _draft.update { it.copy(isSaving = true) }
        val event = _draft.value.toLogEvent(settings.value, System.currentTimeMillis())
        if (event == null) {
            _draft.update { it.copy(isSaving = false) }
            return
        }
        val isCrisis = CrisisDetector.isCrisis(event.note)
        val reminderEnabled = _draft.value.postMealReminderEnabled
        viewModelScope.launch {
            var saved = false
            try {
                val loggedDays = entryRepository.distinctLoggedDays().toMutableSet()
                val before = StreakCalculator.currentStreak(loggedDays)
                entryRepository.add(event)
                _selectedFood.value?.let { food ->
                    database.foodDao().recordUse(food.id, event.timestamp)
                }
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
                _selectedFood.value = null
                _foodLookupState.value = FoodLookupState.Idle
                if (isCrisis) {
                    _showCrisisSupport.value = true
                } else if (after > before) {
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
        _showCrisisSupport.value = false
        _selectedFood.value = null
        _foodLookupState.value = FoodLookupState.Idle
        _foodSearchQuery.value = ""
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
            source = source,
        )
    }

    private fun EntryEntity.toLogEvent(): LogEvent = toDomain().copy(source = source)

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
        event.moodLabel?.takeIf { it.isNotBlank() }?.let { tokens += it }

        // If only a note, the title serves as the label and the subtitle
        // stays null so the row falls back to `note` in `toItem`.
        if (tokens.isEmpty()) return if (event.moodScore != null) "Mood" to null else "Note" to null

        // Derive a compact title from which metrics are present.
        val titleParts = mutableListOf<String>()
        if (event.glucoseMgdl != null) titleParts += "Glucose"
        if (event.insulinBasalUnits != null || event.insulinBolusUnits != null) titleParts += "Insulin"
        if (event.carbsGrams != null || event.proteinGrams != null || event.fatGrams != null ||
            !event.mealDescription.isNullOrBlank()
        ) titleParts += "Meal"
        if (event.exerciseMinutes != null) titleParts += "Activity"
        if (event.moodScore != null) titleParts += "Mood"
        val title = titleParts.joinToString(" · ").ifBlank {
            if (event.moodScore != null) "Mood" else "Note"
        }

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
        const val REFRESH_MIN_MILLIS = 600L

        /** Debounce window for log search input. */
        const val SEARCH_DEBOUNCE_MILLIS = 300L

        /** Bolus lookback window — generous enough for any realistic DIA. */
        const val ACTIVE_INSULIN_WINDOW_MILLIS = 6 * IobCalculator.MILLIS_PER_HOUR
        const val IOB_TICK_MILLIS = 60_000L
    }
}

/** State machine for barcode → food resolution in the Add Entry sheet. */
sealed interface FoodLookupState {
    data object Idle : FoodLookupState
    data object Loading : FoodLookupState
    data class ConfirmOff(val draft: OffFoodDraft) : FoodLookupState
    data class ManualEntry(val barcode: String) : FoodLookupState
    data class Error(val message: String) : FoodLookupState
}

/** Editable, human-reviewed snapshot of an Open Food Facts product. */
data class OffFoodDraft(
    val barcode: String,
    val name: String,
    val brand: String,
    val servingLabel: String,
    val servingGrams: Double?,
    val carbs: String,
    val protein: String,
    val fat: String,
    val kcal: Double?,
)
