package com.omb9.glucosehero.util

import com.omb9.glucosehero.domain.model.TagKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagExtractorTest {

    @Test
    fun `hashtags delegate to HashtagExtractor`() {
        val tags = TagExtractor.extract(
            note = "Lunch #Pizza #Salad #pizza",
            mealDescription = null,
            foodId = null,
            foodName = null,
        )

        assertEquals(
            listOf(
                ExtractedTag("#pizza", TagKind.HASHTAG),
                ExtractedTag("#salad", TagKind.HASHTAG),
            ),
            tags,
        )
    }

    @Test
    fun `description tokens are lowercased and split`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = "Peanut Butter sandwich",
            foodId = null,
            foodName = null,
        )

        assertEquals(
            listOf(
                ExtractedTag("peanut", TagKind.DESCRIPTION),
                ExtractedTag("butter", TagKind.DESCRIPTION),
                ExtractedTag("sandwich", TagKind.DESCRIPTION),
            ),
            tags,
        )
    }

    @Test
    fun `description stopwords are dropped`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = "Bowl with chicken and rice",
            foodId = null,
            foodName = null,
        )

        assertEquals(
            listOf(
                ExtractedTag("bowl", TagKind.DESCRIPTION),
                ExtractedTag("chicken", TagKind.DESCRIPTION),
                ExtractedTag("rice", TagKind.DESCRIPTION),
            ),
            tags,
        )
    }

    @Test
    fun `description tokens are capped at three`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = "chicken broccoli rice pasta salad",
            foodId = null,
            foodName = null,
        )

        assertEquals(
            listOf(
                ExtractedTag("chicken", TagKind.DESCRIPTION),
                ExtractedTag("broccoli", TagKind.DESCRIPTION),
                ExtractedTag("rice", TagKind.DESCRIPTION),
            ),
            tags,
        )
    }

    @Test
    fun `foodId produces the food name and suppresses description tokens`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = "cheese pizza",
            foodId = 42L,
            foodName = "Pizza",
        )

        assertEquals(
            listOf(ExtractedTag("pizza", TagKind.FOOD, 42L)),
            tags,
        )
    }

    @Test
    fun `food name is lowercased without tokenizing`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = null,
            foodId = 42L,
            foodName = "Pepperoni Pizza",
        )

        assertEquals(
            listOf(ExtractedTag("pepperoni pizza", TagKind.FOOD, 42L)),
            tags,
        )
    }

    @Test
    fun `cross-source dedupe keeps the highest-priority kind`() {
        val tags = TagExtractor.extract(
            note = "#pizza",
            mealDescription = null,
            foodId = 42L,
            foodName = "#pizza",
        )

        assertEquals(
            listOf(ExtractedTag("#pizza", TagKind.FOOD, 42L)),
            tags,
        )
    }

    @Test
    fun `unicode letters survive description tokenization`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = "café crème",
            foodId = null,
            foodName = null,
        )

        assertEquals(
            listOf(
                ExtractedTag("café", TagKind.DESCRIPTION),
                ExtractedTag("crème", TagKind.DESCRIPTION),
            ),
            tags,
        )
    }

    @Test
    fun `emoji in notes are ignored`() {
        val tags = TagExtractor.extract(
            note = "Ate 🍕 #pizza 😋",
            mealDescription = null,
            foodId = null,
            foodName = null,
        )

        assertEquals(
            listOf(ExtractedTag("#pizza", TagKind.HASHTAG)),
            tags,
        )
    }

    @Test
    fun `null and empty inputs produce no tags`() {
        assertEquals(
            emptyList<ExtractedTag>(),
            TagExtractor.extract(null, null, null, null),
        )
        assertEquals(
            emptyList<ExtractedTag>(),
            TagExtractor.extract("", "   ", null, ""),
        )
    }

    @Test
    fun `mood produces a namespaced tag`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = null,
            foodId = null,
            foodName = null,
            mood = "Calm",
        )

        assertEquals(
            listOf(ExtractedTag("mood:calm", TagKind.MOOD)),
            tags,
        )
    }

    @Test
    fun `food and mood coexist on one entry`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = null,
            foodId = 42L,
            foodName = "Pizza",
            mood = "Happy",
        )

        assertEquals(
            listOf(
                ExtractedTag("pizza", TagKind.FOOD, 42L),
                ExtractedTag("mood:happy", TagKind.MOOD),
            ),
            tags,
        )
    }

    @Test
    fun `low mood and low description token coexist via namespacing`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = "low carb wrap",
            foodId = null,
            foodName = null,
            mood = "Low",
        )

        assertEquals(
            listOf(
                ExtractedTag("low", TagKind.DESCRIPTION),
                ExtractedTag("carb", TagKind.DESCRIPTION),
                ExtractedTag("wrap", TagKind.DESCRIPTION),
                ExtractedTag("mood:low", TagKind.MOOD),
            ),
            tags,
        )
    }

    @Test
    fun `description tags require more occurrences than food tags`() {
        assertTrue(TagExtractor.DESCRIPTION_MIN_OCCURRENCES > TagExtractor.FOOD_MIN_OCCURRENCES)
        assertEquals(TagExtractor.FOOD_MIN_OCCURRENCES, TagExtractor.minOccurrences(TagKind.FOOD))
        assertEquals(TagExtractor.FOOD_MIN_OCCURRENCES, TagExtractor.minOccurrences(TagKind.HASHTAG))
        assertEquals(TagExtractor.FOOD_MIN_OCCURRENCES, TagExtractor.minOccurrences(TagKind.MOOD))
        assertEquals(
            TagExtractor.DESCRIPTION_MIN_OCCURRENCES,
            TagExtractor.minOccurrences(TagKind.DESCRIPTION),
        )
        assertTrue(TagExtractor.meetsOccurrenceThreshold(TagKind.FOOD, 3))
        assertFalse(TagExtractor.meetsOccurrenceThreshold(TagKind.FOOD, 2))
        assertTrue(TagExtractor.meetsOccurrenceThreshold(TagKind.DESCRIPTION, 5))
        assertFalse(TagExtractor.meetsOccurrenceThreshold(TagKind.DESCRIPTION, 4))
    }

    @Test
    fun `punctuation is stripped from description tokens`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = "Chicken, rice!",
            foodId = null,
            foodName = null,
        )

        assertEquals(
            listOf(
                ExtractedTag("chicken", TagKind.DESCRIPTION),
                ExtractedTag("rice", TagKind.DESCRIPTION),
            ),
            tags,
        )
    }

    @Test
    fun `tokens shorter than three characters are dropped`() {
        val tags = TagExtractor.extract(
            note = null,
            mealDescription = "a PB & j wrap",
            foodId = null,
            foodName = null,
        )

        assertEquals(
            listOf(ExtractedTag("wrap", TagKind.DESCRIPTION)),
            tags,
        )
    }
}
