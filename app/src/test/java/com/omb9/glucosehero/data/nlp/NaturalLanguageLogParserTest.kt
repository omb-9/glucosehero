package com.omb9.glucosehero.data.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalLanguageLogParserTest {

    @Test
    fun `oatmeal eggs humalog example fills meal insulin and minutes ago`() {
        val parsed = NaturalLanguageLogParser.parse(
            "Logged 40 grams of oatmeal, 2 eggs, and 3.5 units Humalog 15 minutes ago",
        )

        assertEquals(40.0, parsed.carbsGrams!!, 0.5)
        assertEquals(3.5, parsed.insulinBolusUnits!!, 1e-6)
        assertEquals(15, parsed.minutesAgo)
        assertTrue(parsed.mealDescription!!.contains("oatmeal", ignoreCase = true))
        assertTrue(parsed.foods.any { it.name.equals("oatmeal", ignoreCase = true) })
        assertTrue(parsed.foods.any { it.name.equals("eggs", ignoreCase = true) })
        assertEquals(2, parsed.foods.size)
        assertNotNull(parsed.proteinGrams)
        assertTrue(parsed.proteinGrams!! > 10.0)
    }

    @Test
    fun `lantus maps to basal and hours ago become minutes`() {
        val parsed = NaturalLanguageLogParser.parse("Took 12 units Lantus 2 hours ago")
        assertEquals(12.0, parsed.insulinBasalUnits!!, 1e-6)
        assertEquals(120, parsed.minutesAgo)
    }

    @Test
    fun `glucose with mmol unit is captured`() {
        val parsed = NaturalLanguageLogParser.parse("Glucose 6.4 mmol/L")
        assertEquals(6.4, parsed.glucoseValue!!, 1e-6)
        assertEquals("mmol/L", parsed.glucoseUnit)
    }

    @Test
    fun `blank utterance is empty`() {
        assertTrue(NaturalLanguageLogParser.parse("   ").isEmpty)
    }
}
