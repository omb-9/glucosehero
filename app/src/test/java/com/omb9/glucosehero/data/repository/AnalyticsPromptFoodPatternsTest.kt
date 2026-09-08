package com.omb9.glucosehero.data.repository

import com.omb9.glucosehero.data.local.entity.TagAnalyticEntity
import com.omb9.glucosehero.domain.model.TagKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AnalyticsPromptFoodPatternsTest {

    @Test
    fun `drops tags below display floors including n=1 noise`() {
        val selected = selectPromptFoodPatterns(
            tags = listOf(
                entity("once", TagKind.FOOD, occurrences = 1, median = 90.0),
                entity("oatmeal", TagKind.FOOD, occurrences = 3, median = 12.0),
                entity("sandwich", TagKind.DESCRIPTION, occurrences = 4, median = 40.0),
                entity("pasta", TagKind.DESCRIPTION, occurrences = 5, median = 20.0),
            ),
            limit = 6,
        )

        assertEquals(listOf("pasta", "oatmeal"), selected.map { it.tag })
    }

    @Test
    fun `omits mood and windowed tags and dismissed identities`() {
        val selected = selectPromptFoodPatterns(
            tags = listOf(
                entity("#pizza", TagKind.HASHTAG, occurrences = 11, median = 85.0),
                entity("mood:low", TagKind.MOOD, occurrences = 8, median = 50.0),
                entity("#sleep", TagKind.SLEEP, occurrences = 8, median = 30.0),
                entity("#cycle", TagKind.CYCLE, occurrences = 8, median = 25.0),
                entity("rice", TagKind.FOOD, occurrences = 6, median = 18.0),
            ),
            limit = 6,
            excludedTags = setOf("rice"),
        )

        assertEquals(listOf("#pizza"), selected.map { it.tag })
    }

    @Test
    fun `ranks by absolute median then occurrence and respects limit`() {
        val selected = selectPromptFoodPatterns(
            tags = listOf(
                entity("a", TagKind.FOOD, occurrences = 3, median = 10.0),
                entity("b", TagKind.FOOD, occurrences = 8, median = -40.0),
                entity("c", TagKind.HASHTAG, occurrences = 5, median = 40.0),
                entity("d", TagKind.FOOD, occurrences = 3, median = 5.0),
                entity("e", TagKind.FOOD, occurrences = 6, median = 8.0),
                entity("f", TagKind.FOOD, occurrences = 9, median = 7.0),
                entity("g", TagKind.FOOD, occurrences = 4, median = 6.0),
            ),
            limit = 6,
        )

        assertEquals(listOf("b", "c", "a", "e", "f", "g"), selected.map { it.tag })
    }

    private fun entity(
        tag: String,
        kind: TagKind,
        occurrences: Int,
        median: Double,
    ) = TagAnalyticEntity(
        tag = tag,
        kind = kind,
        occurrences = occurrences,
        medianDeltaMgdl = median,
        p25DeltaMgdl = median - 5,
        p75DeltaMgdl = median + 5,
        avgCarbsGrams = 40.0,
        avgBolusUnits = 4.2,
        lastSeenAt = 1L,
        computedAt = 2L,
    )
}
