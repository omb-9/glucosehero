package com.omb9.glucosehero.ui.log

import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.remote.off.OffNutriments
import com.omb9.glucosehero.data.remote.off.OffProduct
import com.omb9.glucosehero.data.remote.off.hasCarbs
import com.omb9.glucosehero.data.remote.off.resolvedServingGrams
import com.omb9.glucosehero.data.remote.off.scaledCarbs
import com.omb9.glucosehero.data.remote.off.scaledFat
import com.omb9.glucosehero.data.remote.off.scaledKcal
import com.omb9.glucosehero.data.remote.off.scaledProtein
import com.omb9.glucosehero.domain.model.FoodSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Confirm-before-commit lookup flow: carbohydrates from Open Food Facts must
 * never be silently auto-filled, and missing carb data must never collapse to
 * zero. These tests cover the pure mapping/sentinel logic behind those
 * guarantees without requiring an Android/Room/Retrofit harness.
 */
class FoodLookupFlowTest {

    @Test
    fun `missing carbs use a negative sentinel never zero`() {
        val row = FoodEntity(
            name = "Test",
            carbsGrams = FoodEntity.CARBS_MISSING,
            source = FoodSource.OPEN_FOOD_FACTS,
            createdAt = 0L,
        )
        assertEquals(-1.0, FoodEntity.CARBS_MISSING, 1e-9)
        assertTrue(row.hasMissingCarbs)
        assertFalse(row.copy(carbsGrams = 0.0).hasMissingCarbs)
    }

    @Test
    fun `valid carbs route to a confirm draft not a missing state`() {
        val product = product(carbs100g = 12.5)
        assertTrue(product.hasCarbs)
        assertEquals(12.5, product.scaledCarbs()!!, 1e-9)
    }

    @Test
    fun `missing carbs route to manual entry without a zero value`() {
        val product = product(carbs100g = null, carbsServing = null)
        assertFalse(product.hasCarbs)
        assertNull(product.scaledCarbs())

        val state = FoodLookupState.MissingCarbohydrates(
            barcode = "123",
            draft = OffFoodDraft(
                barcode = "123",
                name = "Test Food",
                brand = "Brand",
                servingLabel = "100 g",
                servingGrams = 100.0,
                carbs = "",
                protein = "",
                fat = "",
                kcal = null,
            ),
        )
        assertEquals("123", state.barcode)
        assertTrue(state.draft.carbs.isBlank())
    }

    @Test
    fun `search result with present carbs reports hasCarbs and prefers per-serving`() {
        val product = product(carbs100g = 10.0, carbsServing = 15.0)
        assertTrue(product.hasCarbs)
        assertEquals(15.0, product.scaledCarbs()!!, 1e-9)
    }

    @Test
    fun `serving parsing scales per-100g values`() {
        val product = OffProduct(
            code = "123",
            productName = "Test",
            brands = null,
            servingSize = "50 g",
            nutriments = OffNutriments(
                carbs100g = 20.0,
                protein100g = 10.0,
                fat100g = 5.0,
                kcal100g = 200.0,
            ),
        )
        assertEquals(50.0, product.resolvedServingGrams()!!, 1e-9)
        assertEquals(10.0, product.scaledCarbs()!!, 1e-9)
        assertEquals(5.0, product.scaledProtein()!!, 1e-9)
        assertEquals(2.5, product.scaledFat()!!, 1e-9)
        assertEquals(100.0, product.scaledKcal()!!, 1e-9)
    }

    private fun product(carbs100g: Double? = null, carbsServing: Double? = null) = OffProduct(
        code = "123",
        productName = "Test Food",
        brands = "Brand",
        servingSize = "100 g",
        nutriments = OffNutriments(
            carbs100g = carbs100g,
            carbsServing = carbsServing,
            protein100g = 1.0,
            fat100g = 2.0,
            kcal100g = 50.0,
        ),
    )
}
