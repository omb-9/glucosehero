package com.omb9.glucosehero.ui.insights

import com.omb9.glucosehero.data.local.entity.TagAnalyticEntity
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.TagKind
import com.omb9.glucosehero.util.TagExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodImpactGatingTest {

    @Test
    fun `food tags below three occurrences stay in building`() {
        val state = foodImpactUiState(
            entities = listOf(entity(tag = "oatmeal", kind = TagKind.FOOD, occurrences = 2)),
            unit = GlucoseUnit.MGDL,
            dismissed = emptySet(),
        )

        assertTrue(state.tags.isEmpty())
        assertEquals(1, state.building.size)
        assertEquals("oatmeal", state.building.single().tag)
        assertEquals(2, state.building.single().occurrences)
        assertEquals(MIN_DISPLAY_OCCURRENCES, state.building.single().neededOccurrences)
    }

    @Test
    fun `food tags with three or four occurrences are provisional cards`() {
        val state = foodImpactUiState(
            entities = listOf(entity(tag = "#pizza", kind = TagKind.HASHTAG, occurrences = 3)),
            unit = GlucoseUnit.MGDL,
            dismissed = emptySet(),
        )

        assertEquals(listOf("#pizza"), state.tags.map { it.tag })
        assertTrue(state.tags.single().isProvisional)
        assertTrue(state.building.isEmpty())
    }

    @Test
    fun `food tags with five occurrences display normally`() {
        val state = foodImpactUiState(
            entities = listOf(entity(tag = "#pizza", kind = TagKind.HASHTAG, occurrences = 5)),
            unit = GlucoseUnit.MGDL,
            dismissed = emptySet(),
        )

        assertEquals(listOf("#pizza"), state.tags.map { it.tag })
        assertFalse(state.tags.single().isProvisional)
    }

    @Test
    fun `description tags with three or four occurrences stay in building`() {
        val state = foodImpactUiState(
            entities = listOf(entity(tag = "sandwich", kind = TagKind.DESCRIPTION, occurrences = 4)),
            unit = GlucoseUnit.MGDL,
            dismissed = emptySet(),
        )

        assertTrue(state.tags.isEmpty())
        assertEquals("sandwich", state.building.single().tag)
        assertEquals(4, state.building.single().occurrences)
        assertEquals(TagExtractor.DESCRIPTION_MIN_OCCURRENCES, state.building.single().neededOccurrences)
    }

    @Test
    fun `description tags display normally only at five or more`() {
        val state = foodImpactUiState(
            entities = listOf(entity(tag = "sandwich", kind = TagKind.DESCRIPTION, occurrences = 5)),
            unit = GlucoseUnit.MGDL,
            dismissed = emptySet(),
        )

        assertEquals(listOf("sandwich"), state.tags.map { it.tag })
        assertFalse(state.tags.single().isProvisional)
        assertTrue(state.building.isEmpty())
    }

    @Test
    fun `dismissed tags stay hidden by identity even after a recompute with new stats`() {
        val before = entity(tag = "#pizza", kind = TagKind.HASHTAG, occurrences = 6, median = 40.0)
        val afterRecompute = entity(tag = "#pizza", kind = TagKind.HASHTAG, occurrences = 11, median = 85.0)
        val dismissed = setOf("#pizza")

        val first = foodImpactUiState(listOf(before), GlucoseUnit.MGDL, dismissed)
        val second = foodImpactUiState(listOf(afterRecompute), GlucoseUnit.MGDL, dismissed)

        assertTrue(first.tags.isEmpty())
        assertTrue(second.tags.isEmpty())
        assertEquals(listOf("#pizza"), first.dismissed.map { it.tag })
        assertEquals(listOf("#pizza"), second.dismissed.map { it.tag })
        assertEquals(11, second.dismissed.single().occurrences)
    }

    @Test
    fun `restoring a tag identity shows it again`() {
        val entities = listOf(entity(tag = "#pizza", kind = TagKind.HASHTAG, occurrences = 6))

        val hidden = foodImpactUiState(entities, GlucoseUnit.MGDL, dismissed = setOf("#pizza"))
        val restored = foodImpactUiState(entities, GlucoseUnit.MGDL, dismissed = emptySet())

        assertTrue(hidden.tags.isEmpty())
        assertEquals(listOf("#pizza"), restored.tags.map { it.tag })
        assertTrue(restored.dismissed.isEmpty())
    }

    @Test
    fun `n less than three is computed but never shown as a delta card`() {
        val visible = listOf(
            entity(tag = "keep", kind = TagKind.FOOD, occurrences = 1),
            entity(tag = "show", kind = TagKind.FOOD, occurrences = 5),
        ).toDisplayableTags(dismissed = emptySet(), unit = GlucoseUnit.MGDL)

        assertEquals(listOf("show"), visible.map { it.tag })
    }

    private fun entity(
        tag: String,
        kind: TagKind,
        occurrences: Int,
        median: Double = 10.0,
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
