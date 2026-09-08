package com.omb9.glucosehero.data.local.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.domain.model.FoodSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Instrumented Room test for the conditional Open Food Facts refresh queries.
 * The `user_corrected = 0` guard must prevent any refresh from overwriting a
 * row the user has explicitly corrected.
 */
class FoodDaoTest {

    private lateinit var db: GlucoseHeroDatabase
    private lateinit var dao: FoodDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, GlucoseHeroDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.foodDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun refreshFromOff_skipsUserCorrectedRow() = runBlocking {
        val id = dao.insert(food(userCorrected = true))

        val updated = dao.refreshFromOff(
            id = id,
            name = "New name",
            brand = "New brand",
            carbsGrams = 20.0,
            proteinGrams = 2.0,
            fatGrams = 2.0,
            kcal = 60.0,
            servingGrams = 50.0,
            servingLabel = "50 g",
            offFetchedAt = 2_000L,
        )

        assertEquals(0, updated)
        val row = dao.getById(id)!!
        assertEquals("Test", row.name)
        assertEquals(10.0, row.carbsGrams, 1e-9)
    }

    @Test
    fun refreshFromOff_updatesNonCorrectedRow() = runBlocking {
        val id = dao.insert(food(userCorrected = false))

        val updated = dao.refreshFromOff(
            id = id,
            name = "New name",
            brand = "New brand",
            carbsGrams = 20.0,
            proteinGrams = 2.0,
            fatGrams = 2.0,
            kcal = 60.0,
            servingGrams = 50.0,
            servingLabel = "50 g",
            offFetchedAt = 2_000L,
        )

        assertEquals(1, updated)
        val row = dao.getById(id)!!
        assertEquals("New name", row.name)
        assertEquals(20.0, row.carbsGrams, 1e-9)
    }

    @Test
    fun refreshByBarcodeFromOff_skipsUserCorrectedRow() = runBlocking {
        dao.insert(food(barcode = "123", userCorrected = true))

        val updated = dao.refreshByBarcodeFromOff(
            barcode = "123",
            name = "New name",
            brand = "New brand",
            carbsGrams = 20.0,
            proteinGrams = 2.0,
            fatGrams = 2.0,
            kcal = 60.0,
            servingGrams = 50.0,
            servingLabel = "50 g",
            offFetchedAt = 2_000L,
        )

        assertEquals(0, updated)
        val row = dao.getByBarcode("123")!!
        assertEquals("Test", row.name)
        assertEquals(10.0, row.carbsGrams, 1e-9)
    }

    @Test
    fun refreshByBarcodeFromOff_updatesNonCorrectedRow() = runBlocking {
        dao.insert(food(barcode = "123", userCorrected = false))

        val updated = dao.refreshByBarcodeFromOff(
            barcode = "123",
            name = "New name",
            brand = "New brand",
            carbsGrams = 20.0,
            proteinGrams = 2.0,
            fatGrams = 2.0,
            kcal = 60.0,
            servingGrams = 50.0,
            servingLabel = "50 g",
            offFetchedAt = 2_000L,
        )

        assertEquals(1, updated)
        val row = dao.getByBarcode("123")!!
        assertEquals("New name", row.name)
        assertEquals(20.0, row.carbsGrams, 1e-9)
    }

    @Test
    fun cacheOffProduct_insertsNewBarcode() = runBlocking {
        val cached = dao.cacheOffProduct(food(barcode = "999"))

        assertTrue(cached.id > 0L)
        val row = dao.getByBarcode("999")!!
        assertEquals(cached.id, row.id)
        assertEquals("Test", row.name)
        assertEquals(false, row.userCorrected)
    }

    @Test
    fun cacheOffProduct_ignoresDuplicateBarcode() = runBlocking {
        val first = dao.cacheOffProduct(food(barcode = "123", userCorrected = false))
        dao.recordUse(first.id, now = 5_000L)

        val second = dao.cacheOffProduct(
            food(barcode = "123").copy(name = "Should not replace", carbsGrams = 99.0),
        )

        assertEquals(first.id, second.id)
        val row = dao.getByBarcode("123")!!
        assertEquals("Test", row.name)
        assertEquals(10.0, row.carbsGrams, 1e-9)
        assertEquals(1, row.useCount)
        assertEquals(5_000L, row.lastUsedAt)
        assertEquals(1, dao.countAll())
    }

    @Test
    fun search_matchesNameBrandAndBarcode() = runBlocking {
        dao.insert(food(barcode = "0123456789012").copy(name = "Yogurt", brand = "Acme"))
        dao.insert(food().copy(name = "Apple", brand = null, barcode = null))

        assertEquals(1, dao.search("yog").size)
        assertEquals(1, dao.search("acme").size)
        assertEquals(1, dao.search("0123456789012").size)
        assertEquals(0, dao.search("missing").size)
    }

    private fun food(
        barcode: String? = null,
        userCorrected: Boolean = false,
    ) = FoodEntity(
        name = "Test",
        brand = "Brand",
        barcode = barcode,
        carbsGrams = 10.0,
        proteinGrams = 1.0,
        fatGrams = 1.0,
        kcal = 50.0,
        servingGrams = 100.0,
        servingLabel = "100 g",
        source = FoodSource.OPEN_FOOD_FACTS,
        offFetchedAt = 1_000L,
        userCorrected = userCorrected,
        createdAt = 1_000L,
    )
}
