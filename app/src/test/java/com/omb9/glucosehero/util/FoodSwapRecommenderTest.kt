package com.omb9.glucosehero.util

import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.local.entity.TagAnalyticEntity
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.TagKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSwapRecommenderTest {

    @Test
    fun `empty when fewer than two foods have five occurrences`() {
        val swaps = FoodSwapRecommender.recommend(
            analytics = listOf(
                tag("pizza", TagKind.FOOD, occurrences = 8, median = 80.0, foodId = 1),
                tag("oatmeal", TagKind.FOOD, occurrences = 4, median = 10.0, foodId = 2),
            ),
            foods = listOf(food(1, "pizza", carbs = 60.0), food(2, "oatmeal", carbs = 40.0)),
        )
        assertTrue(swaps.isEmpty())
    }

    @Test
    fun `pairs a high median 2h spike with a lower spike similar-carb food`() {
        val swaps = FoodSwapRecommender.recommend(
            analytics = listOf(
                tag("pizza", TagKind.FOOD, occurrences = 8, median = 85.0, carbs = 62.0, foodId = 1),
                tag("oatmeal", TagKind.FOOD, occurrences = 6, median = 12.0, carbs = 41.0, foodId = 2),
                tag("mood:low", TagKind.MOOD, occurrences = 8, median = 50.0),
            ),
            foods = listOf(
                food(1, "pizza", carbs = 62.0, protein = 12.0, fat = 18.0),
                food(2, "oatmeal", carbs = 41.0, protein = 6.0, fat = 4.0),
            ),
            unit = GlucoseUnit.MGDL,
        )
        assertEquals(1, swaps.size)
        assertEquals("pizza", swaps.single().fromTag)
        assertEquals("oatmeal", swaps.single().toTag)
        assertEquals(73.0, swaps.single().improvementMgdl, 1e-6)
    }

    @Test
    fun `does not pair foods with very different carb loads`() {
        val swaps = FoodSwapRecommender.recommend(
            analytics = listOf(
                tag("pizza", TagKind.HASHTAG, occurrences = 8, median = 80.0, carbs = 70.0),
                tag("celery", TagKind.FOOD, occurrences = 8, median = 5.0, carbs = 3.0),
            ),
        )
        assertTrue(swaps.isEmpty())
    }

    @Test
    fun `dismissed high-spike food is not recommended as a from`() {
        val swaps = FoodSwapRecommender.recommend(
            analytics = listOf(
                tag("pizza", TagKind.FOOD, occurrences = 8, median = 85.0, carbs = 50.0),
                tag("oatmeal", TagKind.FOOD, occurrences = 6, median = 12.0, carbs = 45.0),
            ),
            dismissed = setOf("pizza"),
        )
        assertTrue(swaps.isEmpty())
    }

    @Test
    fun `absorption buffer score rises with protein and fat per carb`() {
        val buffered = food(1, "yogurt", carbs = 20.0, protein = 15.0, fat = 8.0)
        val plain = food(2, "juice", carbs = 20.0, protein = 0.0, fat = 0.0)
        assertTrue(buffered.absorptionBufferScore() > plain.absorptionBufferScore())
        assertEquals(0.0, food(3, "missing", carbs = FoodEntity.CARBS_MISSING).absorptionBufferScore(), 1e-9)
    }

    @Test
    fun `carbsCompatible allows missing carbs and similar loads`() {
        assertTrue(FoodSwapRecommender.carbsCompatible(null, 40.0))
        assertTrue(FoodSwapRecommender.carbsCompatible(62.0, 41.0))
        assertTrue(!FoodSwapRecommender.carbsCompatible(70.0, 3.0))
    }

    private fun tag(
        name: String,
        kind: TagKind,
        occurrences: Int,
        median: Double,
        carbs: Double? = 40.0,
        foodId: Long? = null,
    ) = TagAnalyticEntity(
        tag = name,
        kind = kind,
        foodId = foodId,
        occurrences = occurrences,
        medianDeltaMgdl = median,
        p25DeltaMgdl = median - 5,
        p75DeltaMgdl = median + 5,
        avgCarbsGrams = carbs,
        avgBolusUnits = 4.0,
        lastSeenAt = 1L,
        computedAt = 2L,
    )

    private fun food(
        id: Long,
        name: String,
        carbs: Double,
        protein: Double? = null,
        fat: Double? = null,
    ) = FoodEntity(
        id = id,
        name = name,
        carbsGrams = carbs,
        proteinGrams = protein,
        fatGrams = fat,
        source = FoodSource.MANUAL,
        createdAt = 1L,
    )
}
