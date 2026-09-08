package com.omb9.glucosehero.ui.insights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.entity.TagAnalyticEntity
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.TagKind
import com.omb9.glucosehero.domain.model.isWindowed
import com.omb9.glucosehero.util.TagExtractor
import com.omb9.glucosehero.util.TagImpactCopy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.abs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Minimum occurrences before a food-derived tag is shown at all. */
const val MIN_DISPLAY_OCCURRENCES = TagExtractor.FOOD_MIN_OCCURRENCES

/** Occurrences at which a tag stops being "provisional" and shows normally. */
const val MIN_CONFIDENT_OCCURRENCES = TagImpactCopy.MIN_CONFIDENT_OCCURRENCES

@Immutable
data class TagImpactUi(
    val tag: String,
    val kind: TagKind,
    val occurrences: Int,
    val medianDeltaMgdl: Double,
    val p25DeltaMgdl: Double,
    val p75DeltaMgdl: Double,
    val avgCarbsGrams: Double?,
    val avgBolusUnits: Double?,
    val unit: GlucoseUnit,
) {
    /**
     * Food/hashtag/mood/windowed tags with 3–4 occurrences render as provisional.
     * Description tags use a floor of 5, so they are never provisional on a card.
     */
    val isProvisional: Boolean
        get() = TagImpactCopy.isProvisional(kind, occurrences)
}

@Immutable
data class BuildingTagUi(
    val tag: String,
    val occurrences: Int,
    val neededOccurrences: Int,
)

@Immutable
data class FoodImpactUiState(
    val unit: GlucoseUnit = GlucoseUnit.MGDL,
    val tags: List<TagImpactUi> = emptyList(),
    val building: List<BuildingTagUi> = emptyList(),
    val dismissed: List<TagImpactUi> = emptyList(),
    val closestBuilding: BuildingTagUi? = null,
    val maxAbsDeltaMgdl: Double = 0.0,
    val moods: List<TagImpactUi> = emptyList(),
    val lifestyle: List<TagImpactUi> = emptyList(),
)

/** Shared display gating: only tags at/above the minimum, not dismissed, sorted by impact. */
fun List<TagAnalyticEntity>.toDisplayableTags(
    dismissed: Set<String>,
    unit: GlucoseUnit,
): List<TagImpactUi> =
    asSequence()
        .filter { TagExtractor.meetsOccurrenceThreshold(it.kind, it.occurrences) && it.tag !in dismissed }
        .sortedByDescending { abs(it.medianDeltaMgdl) }
        .map { it.toTagImpactUi(unit) }
        .toList()

internal fun TagAnalyticEntity.toTagImpactUi(unit: GlucoseUnit): TagImpactUi = TagImpactUi(
    tag = tag,
    kind = kind,
    occurrences = occurrences,
    medianDeltaMgdl = medianDeltaMgdl,
    p25DeltaMgdl = p25DeltaMgdl,
    p75DeltaMgdl = p75DeltaMgdl,
    avgCarbsGrams = avgCarbsGrams,
    avgBolusUnits = avgBolusUnits,
    unit = unit,
)

@HiltViewModel
class FoodImpactViewModel @Inject constructor(
    private val database: GlucoseHeroDatabase,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val tagAnalyticDao = database.tagAnalyticDao()

    val uiState: StateFlow<FoodImpactUiState> = combine(
        tagAnalyticDao.observeAll(),
        settingsDataStore.settings,
        settingsDataStore.dismissedFoodTags,
    ) { entities, settings, dismissed ->
        foodImpactUiState(entities, settings.unit, dismissed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodImpactUiState())

    fun dismiss(tag: String) {
        viewModelScope.launch { settingsDataStore.dismissFoodTag(tag) }
    }

    fun restore(tag: String) {
        viewModelScope.launch { settingsDataStore.restoreFoodTag(tag) }
    }
}

internal fun foodImpactUiState(
    entities: List<TagAnalyticEntity>,
    unit: GlucoseUnit,
    dismissed: Set<String>,
): FoodImpactUiState {
    val foodEntities = entities.filter { it.kind != TagKind.MOOD && !it.kind.isWindowed }
    val lifestyleEntities = entities.filter { it.kind.isWindowed }
    val moodEntities = entities.filter { it.kind == TagKind.MOOD }
    val visible = foodEntities.toDisplayableTags(dismissed, unit)
    val lifestyle = lifestyleEntities.toDisplayableTags(dismissed, unit)
    val moods = moodEntities
        .toDisplayableTags(dismissed, unit)
        .map { it.copy(tag = it.tag.removePrefix(TagExtractor.MOOD_TAG_PREFIX)) }
    val hidden = foodEntities
        .filter { TagExtractor.meetsOccurrenceThreshold(it.kind, it.occurrences) && it.tag in dismissed }
        .sortedByDescending { abs(it.medianDeltaMgdl) }
        .map { it.toTagImpactUi(unit) }
    val building = foodEntities
        .filter { !TagExtractor.meetsOccurrenceThreshold(it.kind, it.occurrences) && it.tag !in dismissed }
        .sortedByDescending { it.occurrences }
        .map {
            BuildingTagUi(
                tag = it.tag,
                occurrences = it.occurrences,
                neededOccurrences = TagExtractor.minOccurrences(it.kind),
            )
        }

    return FoodImpactUiState(
        unit = unit,
        tags = visible,
        building = building,
        dismissed = hidden,
        closestBuilding = building.firstOrNull(),
        maxAbsDeltaMgdl = visible.maxOfOrNull { abs(it.medianDeltaMgdl) } ?: 0.0,
        moods = moods,
        lifestyle = lifestyle,
    )
}
