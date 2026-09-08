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
import com.omb9.glucosehero.data.remote.off.resolvedServingGrams
import com.omb9.glucosehero.data.remote.off.resolvedServingLabel
import com.omb9.glucosehero.data.remote.off.scaledCarbs
import com.omb9.glucosehero.data.remote.off.scaledFat
import com.omb9.glucosehero.data.remote.off.scaledKcal
import com.omb9.glucosehero.data.remote.off.scaledProtein
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.EntryType
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.HeroAiPrefill
import com.omb9.glucosehero.domain.model.LogEvent
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.domain.model.MealPhotoAnalysis
import com.omb9.glucosehero.domain.model.UserSettings
import com.omb9.glucosehero.domain.repository.ChatRepository
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
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

/** State of the in-memory meal-photo analysis pipeline (no DB writes). */
sealed interface MealPhotoState {
    data object Idle : MealPhotoState
    data object Analyzing : MealPhotoState
    data class Failed(val message: String) : MealPhotoState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val entryDao: EntryDao,
    settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository,
    private val heroAiPrefillCoordinator: HeroAiPrefillCoordinator,
    private val reminderScheduler: ReminderScheduler,
    private val widgetRefresher: WidgetRefresher,
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val database: GlucoseHeroDatabase,
    private val openFoodFactsApi: OpenFoodFactsApi,
) : ViewModel() {

    private val foodDao = database.foodDao()

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    val isHealthConnectRevoked: StateFlow<Boolean> = settingsDataStore.healthConnectRevoked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val barcodeLookupEnabled: StateFlow<Boolean> = settingsDataStore.barcodeLookupEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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

    private val _mealPhotoState = MutableStateFlow<MealPhotoState>(MealPhotoState.Idle)
    /** Progress of the in-flight meal-photo analysis, surfaced to the sheet. */
    val mealPhotoState: StateFlow<MealPhotoState> = _mealPhotoState.asStateFlow()

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
        emit(foodDao.recent())
    }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _foodSearchQuery = MutableStateFlow("")
    val foodSearchQuery: StateFlow<String> = _foodSearchQuery.asStateFlow()

    /**
     * Debounced local-library search. Version 1 never hits the Open Food Facts
     * search endpoint (rate-limited to ~15 reads/min); barcode lookup is the
     * only network path, and repeat scans resolve from [foodDao].
     */
    val foodSearchResults: StateFlow<List<FoodEntity>> = _foodSearchQuery
        .debounce(300)
        .mapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.isBlank()) emptyList() else foodDao.search(trimmed)
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
        val carbs = Formatters.parseDecimal(draft.carbsGrams)?.takeIf { it > 0 }
            ?: return@combine null
        val glucoseDisplay = Formatters.parseDecimal(draft.glucose)?.takeIf { it > 0 }
            ?: return@combine null
        val glucoseMgdl = Formatters.displayToMgdl(glucoseDisplay, userSettings.unit)
        BolusCalculator.recommend(
            currentGlucoseMgdl = glucoseMgdl,
            targetGlucoseMgdl = bolus.targetGlucoseMgdl.toDouble(),
            carbsGrams = carbs,
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
                    HealthConnectSyncWorker.enqueueExpedited(context, ExistingWorkPolicy.REPLACE)
                }
                delay(REFRESH_MIN_MILLIS)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun dismissHealthConnectRevokedBanner() {
        viewModelScope.launch {
            settingsDataStore.setHealthConnectRevoked(false)
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

    fun onFoodSelected(food: FoodEntity) {
        applyFood(food)
    }

    fun onFoodSearchQueryChange(query: String) {
        _foodSearchQuery.value = query
    }

    fun onBarcodeScanned(barcode: String?) {
        val code = barcode?.trim()?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            _foodLookupState.value = FoodLookupState.Loading
            try {
                // Cache first: a known barcode resolves fully offline.
                val cached = foodDao.getByBarcode(code)
                if (cached != null) {
                    presentCachedFood(cached)
                    return@launch
                }

                // Cache miss: never contact Open Food Facts when the privacy toggle is off.
                if (!settingsDataStore.barcodeLookupEnabled.first()) {
                    _foodLookupState.value = FoodLookupState.ManualEntry(code)
                    return@launch
                }

                val response = openFoodFactsApi.product(code)
                if (response.code() == 404) {
                    _foodLookupState.value = FoodLookupState.NotFound(code)
                    return@launch
                }

                val body = response.body()
                val product = body?.product
                when {
                    response.isSuccessful && body != null && body.status == 1 && product != null -> {
                        val saved = foodDao.cacheOffProduct(product.toFoodEntity(code))
                        presentFetchedFood(saved, product.toOffFoodDraft(code))
                    }

                    response.isSuccessful && (body == null || body.status == 0 || product == null) ->
                        _foodLookupState.value = FoodLookupState.NotFound(code)

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

    private fun openManualEntryForMissingCarbs(draft: OffFoodDraft) {
        val description = buildString {
            if (draft.brand.isNotBlank()) append(draft.brand.trim()).append(" ")
            append(draft.name.trim())
        }.trim()
        _draft.update {
            it.copy(
                activeCategory = EntryType.MEAL,
                mealDescription = if (it.mealDescription.isBlank()) description else it.mealDescription,
                carbsGrams = "", // Never auto-fill carbs; manual entry required
                proteinGrams = if (it.proteinGrams.isBlank()) draft.protein else it.proteinGrams,
                fatGrams = if (it.fatGrams.isBlank()) draft.fat else it.fatGrams,
            )
        }
    }

    fun dismissFoodLookup() {
        _foodLookupState.value = FoodLookupState.Idle
    }

    fun confirmOffFood(draft: OffFoodDraft) {
        val carbs = Formatters.parseDecimal(draft.carbs)?.takeIf { it > 0 } ?: return
        val name = draft.name.trim().ifBlank { return }
        val barcode = draft.barcode.trim().ifBlank { null }
        val protein = Formatters.parseDecimal(draft.protein)?.takeIf { it > 0 }
        val fat = Formatters.parseDecimal(draft.fat)?.takeIf { it > 0 }
        val brand = draft.brand.trim().ifBlank { null }
        val servingLabel = draft.servingLabel.trim().ifBlank { null }
        viewModelScope.launch {
            val existing = barcode?.let { foodDao.getByBarcode(it) }
            val edited = existing != null && existing.differsFrom(
                name = name,
                brand = brand,
                carbsGrams = carbs,
                proteinGrams = protein,
                fatGrams = fat,
                kcal = draft.kcal,
                servingGrams = draft.servingGrams,
                servingLabel = servingLabel,
            )
            val food = FoodEntity(
                name = name,
                brand = brand,
                barcode = barcode,
                carbsGrams = carbs,
                proteinGrams = protein,
                fatGrams = fat,
                kcal = draft.kcal,
                servingGrams = draft.servingGrams,
                servingLabel = servingLabel,
                source = FoodSource.OPEN_FOOD_FACTS,
                offFetchedAt = existing?.offFetchedAt ?: System.currentTimeMillis(),
                userCorrected = existing?.userCorrected == true || edited,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            )
            val id = if (existing != null) {
                val updated = food.copy(
                    id = existing.id,
                    uuid = existing.uuid,
                    useCount = existing.useCount,
                    lastUsedAt = existing.lastUsedAt,
                    isFavorite = existing.isFavorite,
                    createdAt = existing.createdAt,
                )
                foodDao.update(updated)
                existing.id
            } else {
                foodDao.insert(food)
            }
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
            val id = foodDao.insert(food)
            _selectedFood.value = food.copy(id = id)
        }
    }

    private fun applyFood(food: FoodEntity) {
        if (food.hasMissingCarbs) {
            val draft = food.toOffFoodDraft()
            _selectedFood.value = food
            _foodLookupState.value = FoodLookupState.MissingCarbohydrates(food.barcode, draft)
            openManualEntryForMissingCarbs(draft)
            return
        }
        _draft.update {
            it.copy(
                activeCategory = EntryType.MEAL,
                carbsGrams = if (food.hasMissingCarbs) "" else trim(food.carbsGrams),
                proteinGrams = food.proteinGrams?.let(::trim) ?: "",
                fatGrams = food.fatGrams?.let(::trim) ?: "",
                mealDescription = food.name,
            )
        }
        _selectedFood.value = food
        _foodLookupState.value = FoodLookupState.Idle
    }

    /** Instant offline path: never hits the network, never auto-fills missing carbs. */
    private fun presentCachedFood(cached: FoodEntity) {
        applyFood(cached)
    }

    /**
     * First-time Open Food Facts hit: the product is already in [foods], and the
     * user still reviews it before those macros fill the draft.
     */
    private fun presentFetchedFood(saved: FoodEntity, draft: OffFoodDraft) {
        if (draft.carbs.isBlank() || saved.hasMissingCarbs) {
            _selectedFood.value = saved
            _foodLookupState.value = FoodLookupState.MissingCarbohydrates(saved.barcode, draft)
            openManualEntryForMissingCarbs(draft)
        } else {
            _foodLookupState.value = FoodLookupState.ConfirmOff(draft)
        }
    }

    private fun OffProduct.toOffFoodDraft(barcode: String): OffFoodDraft = OffFoodDraft(
        barcode = barcode.trim(),
        name = productName?.trim().orEmpty(),
        brand = brands?.trim().orEmpty(),
        servingLabel = resolvedServingLabel(),
        servingGrams = resolvedServingGrams(),
        carbs = scaledCarbs()?.let(::trim) ?: "",
        protein = scaledProtein()?.let(::trim) ?: "",
        fat = scaledFat()?.let(::trim) ?: "",
        kcal = scaledKcal(),
    )

    private fun OffProduct.toFoodEntity(barcode: String): FoodEntity {
        val servingGrams = resolvedServingGrams()
        return FoodEntity(
            name = productName?.trim().orEmpty().ifBlank { "Unknown product" },
            brand = brands?.trim()?.takeIf { it.isNotBlank() },
            barcode = barcode,
            carbsGrams = scaledCarbs() ?: FoodEntity.CARBS_MISSING,
            proteinGrams = scaledProtein(),
            fatGrams = scaledFat(),
            kcal = scaledKcal(),
            servingGrams = servingGrams,
            servingLabel = servingSize?.trim()?.takeIf { it.isNotBlank() }
                ?: servingGrams?.let { "${trim(it)} g" },
            source = FoodSource.OPEN_FOOD_FACTS,
            offFetchedAt = System.currentTimeMillis(),
            userCorrected = false,
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun FoodEntity.toOffFoodDraft(): OffFoodDraft = OffFoodDraft(
        barcode = barcode.orEmpty(),
        name = name,
        brand = brand.orEmpty(),
        servingLabel = servingLabel.orEmpty().ifBlank {
            servingGrams?.let { "${trim(it)} g" } ?: "100 g"
        },
        servingGrams = servingGrams,
        carbs = if (hasMissingCarbs) "" else trim(carbsGrams),
        protein = proteinGrams?.let(::trim).orEmpty(),
        fat = fatGrams?.let(::trim).orEmpty(),
        kcal = kcal,
    )

    private fun FoodEntity.differsFrom(
        name: String,
        brand: String?,
        carbsGrams: Double,
        proteinGrams: Double?,
        fatGrams: Double?,
        kcal: Double?,
        servingGrams: Double?,
        servingLabel: String?,
    ): Boolean =
        this.name != name ||
            this.brand != brand ||
            this.carbsGrams != carbsGrams ||
            this.proteinGrams != proteinGrams ||
            this.fatGrams != fatGrams ||
            this.kcal != kcal ||
            this.servingGrams != servingGrams ||
            this.servingLabel != servingLabel

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

    /**
     * Sends the captured meal photo (a `data:` URI, in memory only) to Hero AI
     * and pre-fills the draft with the returned macros + description. Gated on
     * the privacy toggle upstream in the UI; the image bytes are never persisted.
     */
    fun analyzeMealPhoto(imageDataUri: String) {
        if (_mealPhotoState.value is MealPhotoState.Analyzing) return
        _mealPhotoState.value = MealPhotoState.Analyzing
        viewModelScope.launch {
            try {
                val analysis = chatRepository.analyzeMealPhoto(imageDataUri)
                applyMealPhotoAnalysis(analysis)
                _mealPhotoState.value = MealPhotoState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _mealPhotoState.value = MealPhotoState.Failed(
                    e.message ?: "Couldn't estimate this meal."
                )
            }
        }
    }

    /** Merges a photo analysis into the draft, switching to the Meal category. */
    private fun applyMealPhotoAnalysis(analysis: MealPhotoAnalysis) {
        _selectedFood.value = null
        _foodLookupState.value = FoodLookupState.Idle
        _draft.update { current ->
            current.copy(
                activeCategory = EntryType.MEAL,
                carbsGrams = analysis.carbsGrams?.toString() ?: current.carbsGrams,
                proteinGrams = analysis.proteinGrams?.toString() ?: current.proteinGrams,
                fatGrams = analysis.fatGrams?.toString() ?: current.fatGrams,
                mealDescription = analysis.description?.trim()?.takeIf { it.isNotBlank() }
                    ?: current.mealDescription,
            )
        }
    }

    /** Resets any lingering photo-analysis status (e.g. when the sheet closes). */
    fun clearMealPhotoState() {
        _mealPhotoState.value = MealPhotoState.Idle
    }

    fun saveDraft(onSaved: () -> Unit) {
        if (_draft.value.isSaving) return
        _showCrisisSupport.value = false
        _draft.update { it.copy(isSaving = true) }
        val foodId = _selectedFood.value?.id?.takeIf { it > 0L }
        val event = _draft.value.toLogEvent(settings.value, System.currentTimeMillis())
            ?.copy(foodId = foodId)
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
                    foodDao.recordUse(food.id, event.timestamp)
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
    data class MissingCarbohydrates(val barcode: String?, val draft: OffFoodDraft) : FoodLookupState
    data class NotFound(val barcode: String) : FoodLookupState
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
