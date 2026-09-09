package com.omb9.glucosehero.clinical

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.EntryDao
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.util.AppJson
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

@Immutable
data class ClinicalTestUiState(
    val kind: ClinicalTestKind = ClinicalTestKind.OVERNIGHT_BASAL,
    val session: ClinicalTestSession? = null,
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    val mealCarbsInput: String = "",
    val statusMessage: String? = null,
)

@HiltViewModel
class ClinicalTestViewModel @Inject constructor(
    private val entryDao: EntryDao,
    private val settingsDataStore: SettingsDataStore,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _kind = MutableStateFlow(ClinicalTestKind.OVERNIGHT_BASAL)
    private val _mealCarbsInput = MutableStateFlow("")
    private val _statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ClinicalTestUiState> = combine(
        _kind,
        settingsDataStore.clinicalTestSessionJson,
        settingsRepository.settings,
        _mealCarbsInput,
        _statusMessage,
    ) { kind, sessionJson, settings, carbs, message ->
        ClinicalTestUiState(
            kind = kind,
            session = decodeSession(sessionJson),
            unit = settings.unit,
            mealCarbsInput = carbs,
            statusMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClinicalTestUiState())

    init {
        viewModelScope.launch {
            settingsDataStore.clinicalTestSessionJson.collect { json ->
                val session = decodeSession(json) ?: return@collect
                if (session.status == ClinicalTestStatus.RUNNING) {
                    evaluate(session)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                val session = decodeSession(settingsDataStore.clinicalTestSessionJson.first())
                if (session?.status == ClinicalTestStatus.RUNNING) {
                    evaluate(session)
                }
                kotlinx.coroutines.delay(30_000L)
            }
        }
    }

    fun selectKind(kind: ClinicalTestKind) {
        _kind.value = kind
    }

    fun onMealCarbsChange(value: String) {
        _mealCarbsInput.value = value
    }

    fun startTest() {
        viewModelScope.launch {
            val now = Instant.now()
            val kind = _kind.value
            val hours = if (kind == ClinicalTestKind.OVERNIGHT_BASAL) {
                ClinicalTestEngine.DEFAULT_BASAL_HOURS
            } else {
                ClinicalTestEngine.DEFAULT_MEAL_HOURS
            }
            val latest = entryDao.latestGlucoseReading()
            if (kind == ClinicalTestKind.MEAL_CARB_RATIO) {
                val carbs = _mealCarbsInput.value.trim().toIntOrNull()
                if (carbs == null || carbs <= 0) {
                    _statusMessage.value = "Enter the meal carbohydrate grams before starting."
                    return@launch
                }
                if (latest == null) {
                    _statusMessage.value = "Need a current glucose reading to start a meal test."
                    return@launch
                }
            }
            val session = ClinicalTestSession(
                kind = kind,
                status = ClinicalTestStatus.RUNNING,
                startedAtMillis = now.toEpochMilli(),
                plannedEndMillis = now.plusMillis(hours * 3_600_000L).toEpochMilli(),
                preMealGlucoseMgdl = latest?.glucoseMgdl,
                carbsGrams = _mealCarbsInput.value.trim().toIntOrNull(),
                mealBolusUnits = recentMealBolus(now.toEpochMilli()),
            )
            persist(session)
            _statusMessage.value = null
            evaluate(session)
        }
    }

    fun cancelTest() {
        viewModelScope.launch {
            settingsDataStore.setClinicalTestSessionJson(null)
            _statusMessage.value = null
        }
    }

    private suspend fun recentMealBolus(nowMillis: Long): Double? {
        val entries = entryDao.entriesSince(nowMillis - 20L * 60_000L)
        return entries.mapNotNull { it.insulinBolusUnits }.lastOrNull()
    }

    private suspend fun evaluate(session: ClinicalTestSession) {
        val now = System.currentTimeMillis()
        val end = minOf(now, session.plannedEndMillis)
        val events = entryDao.entriesSince(session.startedAtMillis - ClinicalTestEngine.BASAL_PRE_WINDOW_MILLIS)
            .map {
                InterferingEvent(
                    timestampMillis = it.timestamp,
                    hasCarbs = (it.carbsGrams ?: 0) > 0,
                    hasBolus = (it.insulinBolusUnits ?: 0.0) > 0.0 &&
                        it.timestamp > session.startedAtMillis + 60_000L,
                )
            }
        val reason = ClinicalTestEngine.interferingReason(
            kind = session.kind,
            startMillis = session.startedAtMillis,
            endMillis = end,
            events = events,
        )
        if (reason != null) {
            persist(
                session.copy(
                    status = ClinicalTestStatus.INVALID,
                    invalidReason = reason,
                ),
            )
            return
        }
        if (now < session.plannedEndMillis) return
        val observations = entryDao.glucoseReadingPointsBetween(
            session.startedAtMillis,
            session.plannedEndMillis,
        ).map { GlucoseObservation(it.timestamp, it.glucoseMgdl) }
        val result = when (session.kind) {
            ClinicalTestKind.OVERNIGHT_BASAL -> ClinicalTestEngine.analyzeBasal(
                observations, session.startedAtMillis, session.plannedEndMillis,
            )
            ClinicalTestKind.MEAL_CARB_RATIO -> {
                val pre = session.preMealGlucoseMgdl ?: observations.firstOrNull()?.glucoseMgdl
                if (pre == null) null
                else ClinicalTestEngine.analyzeMealRatio(
                    observations,
                    session.startedAtMillis,
                    session.plannedEndMillis,
                    pre,
                    session.mealBolusUnits,
                )
            }
        }
        if (result == null) {
            persist(
                session.copy(
                    status = ClinicalTestStatus.INVALID,
                    invalidReason = "not_enough_readings",
                ),
            )
        } else {
            persist(
                session.copy(
                    status = ClinicalTestStatus.COMPLETE,
                    result = result,
                ),
            )
        }
    }

    private suspend fun persist(session: ClinicalTestSession) {
        settingsDataStore.setClinicalTestSessionJson(AppJson.encodeToString(session))
    }

    private fun decodeSession(raw: String?): ClinicalTestSession? {
        if (raw.isNullOrBlank()) return null
        return runCatching { AppJson.decodeFromString<ClinicalTestSession>(raw) }.getOrNull()
    }
}
