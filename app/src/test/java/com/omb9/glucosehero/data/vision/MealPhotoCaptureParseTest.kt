package com.omb9.glucosehero.data.vision

import com.omb9.glucosehero.domain.model.InvalidAiJsonException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MealPhotoCaptureParseTest {

    @Test
    fun `strict schema JSON fills portions fiber protein fat and late spike note`() {
        val raw = """
            {
              "description": "Oatmeal with eggs",
              "portions": [
                {"name": "oatmeal", "portion_label": "1 cup", "portion_grams": 80, "carbs_grams": 40, "fiber_grams": 4, "protein_grams": 5, "fat_grams": 3},
                {"name": "eggs", "portion_label": "2 large", "portion_grams": 100, "carbs_grams": 1, "fiber_grams": 0, "protein_grams": 12, "fat_grams": 10}
              ],
              "total_carbs_grams": 41,
              "dietary_fiber_grams": 4,
              "protein_grams": 17,
              "dietary_fat_grams": 13,
              "fat_delays_carb_absorption": false,
              "late_spike_note": null
            }
        """.trimIndent()

        val parsed = MealPhotoCapture.parseAnalysis(raw)
        assertEquals("Oatmeal with eggs", parsed.description)
        assertEquals(2, parsed.portions.size)
        assertEquals(41.0, parsed.carbsGrams!!, 1e-6)
        assertEquals(4.0, parsed.dietaryFiberGrams!!, 1e-6)
        assertEquals(17.0, parsed.proteinGrams!!, 1e-6)
        assertEquals(13.0, parsed.fatGrams!!, 1e-6)
        assertFalse(parsed.fatDelaysCarbAbsorption == true)
        val description = MealPhotoCapture.draftMealDescription(parsed)!!
        assertTrue(description.contains("oatmeal"))
        assertTrue(description.contains("Fiber 4 g"))
    }

    @Test
    fun `legacy carbs_grams and fat_grams keys still decode`() {
        val parsed = MealPhotoCapture.parseAnalysis(
            """{"description":"Toast","carbs_grams":30,"protein_grams":4,"fat_grams":8}""",
        )
        assertEquals(30.0, parsed.carbsGrams!!, 1e-6)
        assertEquals(8.0, parsed.fatGrams!!, 1e-6)
    }

    @Test
    fun `markdown fence and preface still parse`() {
        val raw = """
            Sure.
            ```json
            {"description":"Rice","total_carbs_grams":45,"dietary_fiber_grams":1,"protein_grams":4,"dietary_fat_grams":1,"portions":[],"fat_delays_carb_absorption":false,"late_spike_note":null}
            ```
        """.trimIndent()
        val parsed = MealPhotoCapture.parseAnalysis(raw)
        assertEquals("Rice", parsed.description)
        assertEquals(45, parsed.carbsGramsInt)
    }

    @Test
    fun `high fat sets default late spike note in the draft description`() {
        val parsed = MealPhotoCapture.parseAnalysis(
            """{"description":"Pizza","total_carbs_grams":60,"dietary_fiber_grams":3,"protein_grams":18,"dietary_fat_grams":22,"fat_delays_carb_absorption":true,"late_spike_note":null,"portions":[]}""",
        )
        val description = MealPhotoCapture.draftMealDescription(parsed)!!
        assertTrue(description.contains("delay"))
    }

    @Test(expected = InvalidAiJsonException::class)
    fun `prose without JSON throws`() {
        MealPhotoCapture.parseAnalysis("Looks like oatmeal and eggs to me.")
    }

    @Test(expected = InvalidAiJsonException::class)
    fun `empty JSON object throws`() {
        MealPhotoCapture.parseAnalysis("{}")
    }
}
