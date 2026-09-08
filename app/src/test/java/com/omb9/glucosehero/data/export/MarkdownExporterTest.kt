package com.omb9.glucosehero.data.export

import com.omb9.glucosehero.data.local.entity.EntryEntity
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.MealContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownExporterTest {

    @Test
    fun markdownVaultFileName_isStrictKebabCase() {
        val name = markdownVaultFileName("2026-09")
        assertEquals("glucose-log-2026-09.md", name)
        assertTrue(name.matches(Regex("[a-z0-9.-]+")))
        assertFalse(name.any { it.isUpperCase() || it == '_' })
    }

    @Test
    fun renderMarkdownVaultMonth_writesFrontMatterAggregatesAndTable() {
        val entries = listOf(
            EntryEntity(
                timestamp = 1_725_768_000_000L,
                glucoseMgdl = 110.0,
                mealContext = MealContext.AFTER_MEAL,
                insulinBolusUnits = 4.0,
                carbsGrams = 40,
                mealDescription = "Lunch",
                source = EntrySource.MANUAL,
                uuid = "entry-1",
            ),
            EntryEntity(
                timestamp = 1_725_780_000_000L,
                glucoseMgdl = 130.0,
                insulinBolusUnits = 6.0,
                carbsGrams = 50,
                note = "walk",
                source = EntrySource.MANUAL,
                uuid = "entry-2",
            ),
        )

        val markdown = renderMarkdownVaultMonth("2026-09", entries)

        assertTrue(markdown.startsWith("---\n"))
        assertTrue(markdown.contains("month: 2026-09"))
        assertTrue(markdown.contains("entries: 2"))
        assertTrue(markdown.contains("earliestEntry:"))
        assertTrue(markdown.contains("latestEntry:"))
        assertTrue(markdown.contains("averageGlucoseMgdl: 120"))
        assertTrue(markdown.contains("averageCarbsGrams: 45"))
        assertTrue(markdown.contains("averageBolusUnits: 5"))
        assertTrue(markdown.contains("| Timestamp | Glucose (mg/dL) |"))
        assertTrue(markdown.contains("Lunch"))
        assertFalse(markdown.contains("apiKey"))
        assertFalse(markdown.contains("ai_api_key"))
        assertFalse(markdown.contains("ai_api_key_enc"))
    }
}
