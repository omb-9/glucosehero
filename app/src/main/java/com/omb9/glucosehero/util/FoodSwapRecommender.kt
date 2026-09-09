package com.omb9.glucosehero.util

import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.local.entity.TagAnalyticEntity
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.TagKind
import com.omb9.glucosehero.domain.model.isWindowed
import kotlin.math.abs

/**
 * One recommended swap: a higher median 2-hour postprandial glucose change
 * versus a lower-change alternative with a similar logged carb load.
 *
 * Copy stays observational. It never claims a food causes a spike.
 */
data class FoodSwap(
    val fromTag: String,
    val toTag: String,
    val fromMedianDeltaMgdl: Double,
    val toMedianDeltaMgdl: Double,
    val fromOccurrences: Int,
    val toOccurrences: Int,
    val fromAvgCarbsGrams: Double?,
    val toAvgCarbsGrams: Double?,
    val fromBufferScore: Double?,
    val toBufferScore: Double?,
    val unit: GlucoseUnit,
) {
    val improvementMgdl: Double
        get() = fromMedianDeltaMgdl - toMedianDeltaMgdl
}

/**
 * Ranks [FoodEntity] rows for glycemic buffering.
 *
 * Protein and especially dietary fat slow gastric emptying, so grams of
 * protein+fat per gram of carbohydrate are a useful secondary score when two
 * foods have similar logged carbs. Missing or sentinel carbs yield 0.
 */
fun FoodEntity.absorptionBufferScore(): Double {
    if (hasMissingCarbs) return 0.0
    val carbs = carbsGrams.takeIf { it > 0.0 } ?: return 0.0
    val protein = proteinGrams ?: 0.0
    val fat = fatGrams ?: 0.0
    return (protein + fat) / carbs
}

/**
 * Builds low-spike food swaps from cached tag analytics (median 2-hour
 * post-meal glucose delta) joined to [FoodEntity] macros when a `food_id`
 * is present.
 *
 * A pair is emitted only when both sides meet [MIN_SWAP_OCCURRENCES], the
 * high-spike food is actually a rise, and the alternative improves the
 * median delta by at least [MIN_IMPROVEMENT_MGDL].
 */
object FoodSwapRecommender {

    /** Swaps wait for a stable pattern, not the 3-occurrence display floor. */
    const val MIN_SWAP_OCCURRENCES = TagImpactCopy.MIN_CONFIDENT_OCCURRENCES

    /** High-spike side must typically rise at least this much. */
    const val MIN_HIGH_SPIKE_MGDL = 15.0

    /** Alternative must improve the median 2h delta by at least this much. */
    const val MIN_IMPROVEMENT_MGDL = 15.0

    /** |fromCarbs - toCarbs| / max(from, to) must be at or below this. */
    const val MAX_CARB_RELATIVE_DIFF = 0.45

    const val MAX_SWAPS = 5

    fun recommend(
        analytics: List<TagAnalyticEntity>,
        foods: List<FoodEntity> = emptyList(),
        dismissed: Set<String> = emptySet(),
        unit: GlucoseUnit = GlucoseUnit.MGDL,
    ): List<FoodSwap> {
        val foodsById = foods.associateBy { it.id }
        val eligible = analytics.mapNotNull { row ->
            toCandidate(row, foodsById, dismissed)
        }
        if (eligible.size < 2) return emptyList()

        val highs = eligible
            .filter { it.medianDeltaMgdl >= MIN_HIGH_SPIKE_MGDL }
            .sortedByDescending { it.medianDeltaMgdl }

        val usedFrom = mutableSetOf<String>()
        val usedTo = mutableSetOf<String>()
        val swaps = mutableListOf<FoodSwap>()

        for (high in highs) {
            if (high.tag in usedFrom || high.tag in usedTo) continue
            val alternative = eligible
                .asSequence()
                .filter { it.tag != high.tag }
                .filter { it.tag !in usedFrom && it.tag !in usedTo }
                .filter { high.medianDeltaMgdl - it.medianDeltaMgdl >= MIN_IMPROVEMENT_MGDL }
                .filter { carbsCompatible(high.avgCarbsGrams, it.avgCarbsGrams) }
                .maxWithOrNull(
                    compareBy<SwapCandidate> { high.medianDeltaMgdl - it.medianDeltaMgdl }
                        .thenBy { it.medianDeltaMgdl }
                        .thenByDescending { it.bufferScore ?: 0.0 },
                ) ?: continue

            usedFrom += high.tag
            usedTo += alternative.tag
            swaps += FoodSwap(
                fromTag = high.tag,
                toTag = alternative.tag,
                fromMedianDeltaMgdl = high.medianDeltaMgdl,
                toMedianDeltaMgdl = alternative.medianDeltaMgdl,
                fromOccurrences = high.occurrences,
                toOccurrences = alternative.occurrences,
                fromAvgCarbsGrams = high.avgCarbsGrams,
                toAvgCarbsGrams = alternative.avgCarbsGrams,
                fromBufferScore = high.bufferScore,
                toBufferScore = alternative.bufferScore,
                unit = unit,
            )
            if (swaps.size >= MAX_SWAPS) break
        }
        return swaps
    }

    private fun toCandidate(
        row: TagAnalyticEntity,
        foodsById: Map<Long, FoodEntity>,
        dismissed: Set<String>,
    ): SwapCandidate? {
        if (row.kind == TagKind.MOOD || row.kind.isWindowed) return null
        if (row.tag in dismissed) return null
        if (row.occurrences < MIN_SWAP_OCCURRENCES) return null
        if (!TagExtractor.meetsOccurrenceThreshold(row.kind, row.occurrences)) return null
        val food = row.foodId?.let { foodsById[it] }
        return SwapCandidate(
            tag = row.tag,
            medianDeltaMgdl = row.medianDeltaMgdl,
            occurrences = row.occurrences,
            avgCarbsGrams = row.avgCarbsGrams ?: food?.takeUnless { it.hasMissingCarbs }?.carbsGrams,
            bufferScore = food?.absorptionBufferScore(),
        )
    }

    internal fun carbsCompatible(fromCarbs: Double?, toCarbs: Double?): Boolean {
        if (fromCarbs == null || toCarbs == null) return true
        if (fromCarbs <= 0.0 || toCarbs <= 0.0) return true
        val denom = maxOf(fromCarbs, toCarbs)
        return abs(fromCarbs - toCarbs) / denom <= MAX_CARB_RELATIVE_DIFF
    }

    private data class SwapCandidate(
        val tag: String,
        val medianDeltaMgdl: Double,
        val occurrences: Int,
        val avgCarbsGrams: Double?,
        val bufferScore: Double?,
    )
}
