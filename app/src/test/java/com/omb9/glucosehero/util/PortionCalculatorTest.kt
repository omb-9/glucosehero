package com.omb9.glucosehero.util

import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.local.entity.scaleByGrams
import com.omb9.glucosehero.data.local.entity.scaleByServings
import com.omb9.glucosehero.data.local.entity.toMacros
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.domain.model.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortionCalculatorTest {

    @Test
    fun `parses grams with a space`() {
        assertEquals(30.0, PortionCalculator.parseServingGrams("30 g")!!, 1e-9)
    }

    @Test
    fun `parses grams without a space`() {
        assertEquals(30.0, PortionCalculator.parseServingGrams("30g")!!, 1e-9)
    }

    @Test
    fun `parses grams with full word grams`() {
        assertEquals(45.0, PortionCalculator.parseServingGrams("45 grams")!!, 1e-9)
        assertEquals(1.0, PortionCalculator.parseServingGrams("1 gram")!!, 1e-9)
    }

    @Test
    fun `millilitres are not grams`() {
        assertNull(PortionCalculator.parseServingGrams("1 cup (240 ml)"))
        assertNull(PortionCalculator.parseServingGrams("250ml"))
    }

    @Test
    fun `milligrams and kilograms are not grams`() {
        assertNull(PortionCalculator.parseServingGrams("500 mg"))
        assertNull(PortionCalculator.parseServingGrams("500mg"))
        assertNull(PortionCalculator.parseServingGrams("2 kg"))
    }

    @Test
    fun `extracts gram weight from a parenthetical`() {
        assertEquals(25.0, PortionCalculator.parseServingGrams("2 biscuits (25 g)")!!, 1e-9)
    }

    @Test
    fun `normalises comma decimals before parsing`() {
        assertEquals(55.0, PortionCalculator.parseServingGrams("0,5 portion (55 g)")!!, 1e-9)
    }

    @Test
    fun `normalises a bare comma decimal`() {
        assertEquals(12.5, PortionCalculator.parseServingGrams("12,5 g")!!, 1e-9)
    }

    @Test
    fun `parses leading decimal dot grams`() {
        assertEquals(0.5, PortionCalculator.parseServingGrams(".5 g")!!, 1e-9)
    }

    @Test
    fun `empty string returns null`() {
        assertNull(PortionCalculator.parseServingGrams(""))
    }

    @Test
    fun `null serving size returns null`() {
        assertNull(PortionCalculator.parseServingGrams(null))
    }

    @Test
    fun `parseServingGramsOrDefault returns parsed value or fallback to 100g`() {
        assertEquals(30.0, PortionCalculator.parseServingGramsOrDefault("30 g"), 1e-9)
        // Defensive: "1 cup (240 ml)" has no gram weight, falls back to 100g
        assertEquals(100.0, PortionCalculator.parseServingGramsOrDefault("1 cup (240 ml)"), 1e-9)
        assertEquals(100.0, PortionCalculator.parseServingGramsOrDefault(null), 1e-9)
        assertEquals(50.0, PortionCalculator.parseServingGramsOrDefault("", fallbackGrams = 50.0), 1e-9)
    }

    @Test
    fun `scales per-serving macros by fractional servings`() {
        val base = Macros(carbsGrams = 20.0, proteinGrams = 10.0, fatGrams = 5.0, kcal = 180.0)

        val scaled = PortionCalculator.scaleByServings(base, servings = 1.5)

        assertEquals(30.0, scaled.carbsGrams, 1e-9)
        assertEquals(15.0, scaled.proteinGrams!!, 1e-9)
        assertEquals(7.5, scaled.fatGrams!!, 1e-9)
        assertEquals(270.0, scaled.kcal!!, 1e-9)
    }

    @Test
    fun `scaling preserves null protein and fat`() {
        val base = Macros(carbsGrams = 20.0, proteinGrams = null, fatGrams = null, kcal = null)

        val scaled = PortionCalculator.scaleByServings(base, servings = 2.0)

        assertEquals(40.0, scaled.carbsGrams, 1e-9)
        assertNull(scaled.proteinGrams)
        assertNull(scaled.fatGrams)
        assertNull(scaled.kcal)
    }

    @Test
    fun `scales per-100g macros to an arbitrary gram weight defaulting to 100g serving`() {
        val per100g = Macros(carbsGrams = 40.0, proteinGrams = 8.0, fatGrams = 10.0, kcal = 280.0)

        val scaled = PortionCalculator.scaleByGrams(per100g, grams = 250.0)

        assertEquals(100.0, scaled.carbsGrams, 1e-9)
        assertEquals(20.0, scaled.proteinGrams!!, 1e-9)
        assertEquals(25.0, scaled.fatGrams!!, 1e-9)
        assertEquals(700.0, scaled.kcal!!, 1e-9)
    }

    @Test
    fun `scales macros by grams using grams divided by serving_grams`() {
        val base = Macros(carbsGrams = 15.0, proteinGrams = 3.0, fatGrams = 2.0, kcal = 90.0)
        // 60g eaten where serving_grams is 30g -> factor = 60 / 30 = 2.0
        val scaled = PortionCalculator.scaleByGrams(base, grams = 60.0, servingGrams = 30.0)

        assertEquals(30.0, scaled.carbsGrams, 1e-9)
        assertEquals(6.0, scaled.proteinGrams!!, 1e-9)
        assertEquals(4.0, scaled.fatGrams!!, 1e-9)
        assertEquals(180.0, scaled.kcal!!, 1e-9)
    }

    @Test
    fun `zero servings yield zero macros`() {
        val base = Macros(carbsGrams = 20.0, proteinGrams = 10.0, fatGrams = 5.0, kcal = 180.0)

        val scaled = PortionCalculator.scaleByServings(base, servings = 0.0)

        assertEquals(0.0, scaled.carbsGrams, 1e-9)
        assertEquals(0.0, scaled.proteinGrams!!, 1e-9)
        assertEquals(0.0, scaled.fatGrams!!, 1e-9)
        assertEquals(0.0, scaled.kcal!!, 1e-9)
    }

    @Test
    fun `negative servings or invalid grams produce zero macros safely`() {
        val base = Macros(carbsGrams = 20.0, proteinGrams = 10.0, fatGrams = 5.0, kcal = 180.0)

        val negServings = PortionCalculator.scaleByServings(base, servings = -1.0)
        assertEquals(0.0, negServings.carbsGrams, 1e-9)

        val zeroServingGrams = PortionCalculator.scaleByGrams(base, grams = 50.0, servingGrams = 0.0)
        assertEquals(0.0, zeroServingGrams.carbsGrams, 1e-9)

        val negGrams = PortionCalculator.scaleByGrams(base, grams = -50.0, servingGrams = 30.0)
        assertEquals(0.0, negGrams.carbsGrams, 1e-9)
    }

    @Test
    fun `foodEntity toMacros converts accurately and scales with portion calculator`() {
        val food = FoodEntity(
            id = 1L,
            name = "Rolled Oats",
            brand = "Quaker",
            barcode = "012345678901",
            carbsGrams = 27.0,
            proteinGrams = 5.0,
            fatGrams = 3.0,
            kcal = 150.0,
            servingGrams = 40.0,
            servingLabel = "1/2 cup (40g)",
            source = FoodSource.OPEN_FOOD_FACTS,
            createdAt = 1000L,
        )

        val macros = food.toMacros()
        assertEquals(27.0, macros.carbsGrams, 1e-9)
        assertEquals(5.0, macros.proteinGrams!!, 1e-9)
        assertEquals(3.0, macros.fatGrams!!, 1e-9)
        assertEquals(150.0, macros.kcal!!, 1e-9)

        val scaled = PortionCalculator.scaleByServings(macros, servings = 2.0)
        assertEquals(54.0, scaled.carbsGrams, 1e-9)
        assertEquals(10.0, scaled.proteinGrams!!, 1e-9)
    }

    @Test
    fun `foodEntity extension functions scale by servings and by grams with servingGrams or 100g fallback`() {
        val foodWithServingGrams = FoodEntity(
            id = 1L,
            name = "Cereal",
            carbsGrams = 25.0,
            servingGrams = 50.0,
            servingLabel = "50g",
            source = FoodSource.OPEN_FOOD_FACTS,
            createdAt = 1000L,
        )

        val scaledByServings = foodWithServingGrams.scaleByServings(2.5)
        assertEquals(62.5, scaledByServings.carbsGrams, 1e-9)

        // 75g eaten with 50g serving -> factor 1.5
        val scaledByGrams = foodWithServingGrams.scaleByGrams(75.0)
        assertEquals(37.5, scaledByGrams.carbsGrams, 1e-9)

        // Food without servingGrams (e.g. from Open Food Facts with "1 cup (240 ml)" where servingGrams is null)
        val foodWithoutServingGrams = FoodEntity(
            id = 2L,
            name = "Milk",
            carbsGrams = 5.0, // per 100g
            servingGrams = null,
            servingLabel = "1 cup (240 ml)",
            source = FoodSource.OPEN_FOOD_FACTS,
            createdAt = 1000L,
        )

        // Falls back to per-100g: 200g eaten -> 200 / 100 = 2.0 factor -> 10.0g carbs
        val scaledFallback = foodWithoutServingGrams.scaleByGrams(200.0)
        assertEquals(10.0, scaledFallback.carbsGrams, 1e-9)
    }

    @Test
    fun `unrounded carbs from portion calculator pass directly into bolus calculator`() {
        val base = Macros(carbsGrams = 23.45, proteinGrams = 4.0, fatGrams = 2.0, kcal = 120.0)
        // 1.5 servings -> 35.175g unrounded carbs
        val scaled = PortionCalculator.scaleByServings(base, 1.5)
        assertEquals(35.175, scaled.carbsGrams, 1e-9)

        // Bolus recommendation with unrounded carbs:
        // meal dose = 35.175 / 10.0 = 3.5175
        // correction = (160.0 - 100.0) / 40.0 = 1.5
        // IOB = 0.5
        // total = 3.5175 + 1.5 - 0.5 = 4.5175
        val dose = BolusCalculator.recommend(
            currentGlucoseMgdl = 160.0,
            targetGlucoseMgdl = 100.0,
            carbsGrams = scaled.carbsGrams,
            carbRatio = 10.0,
            insulinSensitivityMgdl = 40.0,
            insulinOnBoard = 0.5,
        )
        assertEquals(4.5175, dose.units, 1e-9)

        // Round once at display:
        val display = PortionCalculator.roundForDisplay(scaled.carbsGrams)
        assertEquals("35.2", display)
    }

    @Test
    fun `roundForDisplay formats integer values cleanly and non-integers to one decimal`() {
        assertEquals("30", PortionCalculator.roundForDisplay(30.0))
        assertEquals("30.5", PortionCalculator.roundForDisplay(30.5))
        assertEquals("30.6", PortionCalculator.roundForDisplay(30.56))
        assertEquals("0", PortionCalculator.roundForDisplay(0.0))
    }
}
