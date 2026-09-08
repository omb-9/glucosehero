package com.omb9.glucosehero.data.remote.off

import com.omb9.glucosehero.util.AppJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OffDtoTest {

    private val json = AppJson

    @Test
    fun `complete product decodes both 100g and serving values`() {
        val response = json.decodeFromString<OffProductResponse>(
            """
            {
              "status": 1,
              "code": "3017620422003",
              "product": {
                "product_name": "Nutella",
                "brands": "Ferrero",
                "quantity": "400 g",
                "serving_size": "15 g",
                "nutriments": {
                  "carbohydrates_100g": 57.5,
                  "proteins_100g": 6.3,
                  "fat_100g": 30.9,
                  "energy-kcal_100g": 539,
                  "carbohydrates_serving": 8.6,
                  "proteins_serving": 0.9,
                  "fat_serving": 4.6,
                  "energy-kcal_serving": 81
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(1, response.status)
        assertEquals("3017620422003", response.code)

        val product = response.product
        assertNotNull(product)
        assertEquals("Nutella", product!!.productName)
        assertEquals("Ferrero", product.brands)
        assertEquals("400 g", product.quantity)
        assertEquals("15 g", product.servingSize)

        val nutriments = product.nutriments
        assertNotNull(nutriments)
        assertEquals(57.5, nutriments!!.carbs100g!!, 1e-9)
        assertEquals(6.3, nutriments.protein100g!!, 1e-9)
        assertEquals(30.9, nutriments.fat100g!!, 1e-9)
        assertEquals(539.0, nutriments.kcal100g!!, 1e-9)
        assertEquals(8.6, nutriments.carbsServing!!, 1e-9)
        assertEquals(0.9, nutriments.proteinServing!!, 1e-9)
        assertEquals(4.6, nutriments.fatServing!!, 1e-9)
        assertEquals(81.0, nutriments.kcalServing!!, 1e-9)
    }

    @Test
    fun `missing serving fields land as null`() {
        val response = json.decodeFromString<OffProductResponse>(
            """
            {
              "status": 1,
              "code": "3017620422003",
              "product": {
                "product_name": "Nutella",
                "brands": "Ferrero",
                "quantity": "400 g",
                "serving_size": "15 g",
                "nutriments": {
                  "carbohydrates_100g": 57.5,
                  "proteins_100g": 6.3,
                  "fat_100g": 30.9,
                  "energy-kcal_100g": 539
                }
              }
            }
            """.trimIndent()
        )

        val product = response.product
        assertNotNull(product)

        val nutriments = product!!.nutriments
        assertNotNull(nutriments)

        assertEquals(57.5, nutriments!!.carbs100g!!, 1e-9)
        assertNull(nutriments.carbsServing)
        assertNull(nutriments.proteinServing)
        assertNull(nutriments.fatServing)
        assertNull(nutriments.kcalServing)
    }

    @Test
    fun `missing carbohydrates 100g lands as null without throwing`() {
        val response = json.decodeFromString<OffProductResponse>(
            """
            {
              "status": 1,
              "code": "3017620422003",
              "product": {
                "product_name": "Some product",
                "nutriments": {
                  "proteins_100g": 1.0,
                  "fat_100g": 0.5,
                  "energy-kcal_100g": 40
                }
              }
            }
            """.trimIndent()
        )

        val product = response.product
        assertNotNull(product)

        val nutriments = product!!.nutriments
        assertNotNull(nutriments)

        assertNull(nutriments!!.carbs100g)
        assertEquals(1.0, nutriments.protein100g!!, 1e-9)
        assertEquals(0.5, nutriments.fat100g!!, 1e-9)
        assertEquals(40.0, nutriments.kcal100g!!, 1e-9)
    }

    @Test
    fun `not found response has status 0 and no product`() {
        val response = json.decodeFromString<OffProductResponse>(
            """{"status":0,"status_verbose":"product not found","code":"0000000000000"}"""
        )

        assertEquals(0, response.status)
        assertEquals("product not found", response.statusVerbose)
        assertEquals("0000000000000", response.code)
        assertNull(response.product)
        org.junit.Assert.assertTrue(response.isNotFound)
        org.junit.Assert.assertFalse(response.isFound)
    }

    @Test
    fun `unknown keys in product and nutriments are safely ignored by AppJson`() {
        val response = json.decodeFromString<OffProductResponse>(
            """
            {
              "status": 1,
              "code": "3017620422003",
              "ecoscore_grade": "e",
              "unknown_field_1": "some_value",
              "product": {
                "product_name": "Nutella",
                "nova_group": 4,
                "unknown_product_flag": true,
                "nutriments": {
                  "carbohydrates_100g": 57.5,
                  "proteins_100g": 6.3,
                  "fat_100g": 30.9,
                  "energy-kcal_100g": 539,
                  "carbohydrates_serving": 8.6,
                  "proteins_serving": 0.9,
                  "fat_serving": 4.6,
                  "energy-kcal_serving": 81,
                  "sugars_100g": 56.3,
                  "salt_100g": 0.107,
                  "saturated-fat_100g": 10.6,
                  "unknown_nested_object": { "nested_key": 123 }
                }
              }
            }
            """.trimIndent()
        )

        assertEquals(1, response.status)
        val product = response.product
        assertNotNull(product)
        assertEquals("Nutella", product!!.productName)

        val nutriments = product.nutriments
        assertNotNull(nutriments)
        assertEquals(57.5, nutriments!!.carbs100g!!, 1e-9)
        assertEquals(6.3, nutriments.protein100g!!, 1e-9)
        assertEquals(30.9, nutriments.fat100g!!, 1e-9)
        assertEquals(539.0, nutriments.kcal100g!!, 1e-9)
        assertEquals(8.6, nutriments.carbsServing!!, 1e-9)
        assertEquals(0.9, nutriments.proteinServing!!, 1e-9)
        assertEquals(4.6, nutriments.fatServing!!, 1e-9)
        assertEquals(81.0, nutriments.kcalServing!!, 1e-9)
    }
}
