package com.omb9.glucosehero.data.nlp

import com.omb9.glucosehero.domain.model.InvalidAiJsonException
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickLogNlpParseTest {

    @Test
    fun `example JSON decodes into entry fields`() {
        val raw = """
            {"glucose_value":null,"glucose_unit":null,"insulin_basal_units":null,
             "insulin_bolus_units":3.5,"carbs_grams":40,"protein_grams":12.6,"fat_grams":10,
             "meal_description":"oatmeal, 2 eggs",
             "foods":[{"name":"oatmeal","portion_label":"40 g oatmeal","portion_grams":40,"carbs_grams":40,"protein_grams":null,"fat_grams":null}],
             "exercise_minutes":null,"note":null,"minutes_ago":15}
        """.trimIndent()
        val parsed = QuickLogNlp.parseResponse(raw)
        assertEquals(3.5, parsed.insulinBolusUnits!!, 1e-6)
        assertEquals(40, parsed.carbsGramsInt)
        assertEquals(15, parsed.minutesAgo)
        assertEquals("oatmeal, 2 eggs", parsed.mealDescription)
        assertEquals(1, parsed.foods.size)
    }

    @Test(expected = InvalidAiJsonException::class)
    fun `empty object is rejected`() {
        QuickLogNlp.parseResponse("{}")
    }
}
