package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.TagKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagImpactCopyTest {

    @Test
    fun `observation names the tag, median, sample size, and bolus`() {
        val copy = TagImpactCopy.observation(
            tag = "#pizza",
            medianDeltaMgdl = 85.0,
            occurrences = 11,
            avgBolusUnits = 4.2,
            unit = GlucoseUnit.MGDL,
        )

        assertEquals(
            "After meals tagged #pizza, your glucose was typically 85 mg/dL " +
                "higher two hours later, across 11 occurrences with an average bolus of 4.2 units.",
            copy,
        )
        assertFalse(copy.contains("cause", ignoreCase = true))
        assertFalse(copy.contains("raises"))
    }

    @Test
    fun `observation uses one decimal for mmol per liter`() {
        val copy = TagImpactCopy.observation(
            tag = "#pizza",
            medianDeltaMgdl = 85.0,
            occurrences = 11,
            avgBolusUnits = 4.2,
            unit = GlucoseUnit.MMOL,
        )

        assertTrue(copy.contains("4.7 mmol/L"))
        assertFalse(copy.contains("85"))
    }

    @Test
    fun `observation omits bolus clause when none was logged`() {
        val copy = TagImpactCopy.observation(
            tag = "oatmeal",
            medianDeltaMgdl = -12.0,
            occurrences = 3,
            avgBolusUnits = null,
            unit = GlucoseUnit.MGDL,
        )

        assertEquals(
            "After meals tagged oatmeal, your glucose was typically 12 mg/dL " +
                "lower two hours later, across 3 occurrences.",
            copy,
        )
    }

    @Test
    fun `confounder line uses Formatters for carbs and bolus`() {
        assertEquals(
            "avg 62 g carbs, 4.2 units bolus",
            TagImpactCopy.confounder(avgCarbsGrams = 62.4, avgBolusUnits = 4.2),
        )
        assertEquals(
            "avg n/a carbs, n/a bolus",
            TagImpactCopy.confounder(avgCarbsGrams = null, avgBolusUnits = null),
        )
    }

    @Test
    fun `building copy states current count versus needed count`() {
        assertEquals(
            "You have 3 of the 5 occurrences needed to show patterns for #pizza.",
            TagImpactCopy.buildingProgress(tag = "#pizza", occurrences = 3, needed = 5),
        )
        assertEquals(
            "You have 1 of the 3 occurrences needed to show patterns for oatmeal.",
            TagImpactCopy.buildingProgress(tag = "oatmeal", occurrences = 1, needed = 3),
        )
    }

    @Test
    fun `provisional copy asks for five occurrences for a stable pattern`() {
        assertEquals(
            "You have 3 of the 5 occurrences needed for a stable pattern.",
            TagImpactCopy.provisionalProgress(3),
        )
    }

    @Test
    fun `empty copy says meals tags and repeats are missing`() {
        val copy = TagImpactCopy.emptyFoodImpact()
        assertTrue(copy.contains("food"))
        assertTrue(copy.contains("hashtag"))
        assertTrue(copy.contains("description"))
        assertTrue(copy.contains("repeats"))
    }

    @Test
    fun `food tags are provisional at three and four, confirmed at five`() {
        assertFalse(TagImpactCopy.isProvisional(TagKind.FOOD, 2))
        assertTrue(TagImpactCopy.isProvisional(TagKind.FOOD, 3))
        assertTrue(TagImpactCopy.isProvisional(TagKind.HASHTAG, 4))
        assertFalse(TagImpactCopy.isProvisional(TagKind.FOOD, 5))
    }

    @Test
    fun `description tags are never provisional because their floor is five`() {
        assertFalse(TagImpactCopy.isProvisional(TagKind.DESCRIPTION, 3))
        assertFalse(TagImpactCopy.isProvisional(TagKind.DESCRIPTION, 4))
        assertFalse(TagImpactCopy.isProvisional(TagKind.DESCRIPTION, 5))
    }
}
