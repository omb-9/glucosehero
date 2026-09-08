package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.omb9.glucosehero.data.local.entity.FoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Insert
    suspend fun insert(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(food: FoodEntity): Long

    @Insert
    suspend fun insertAll(foods: List<FoodEntity>): List<Long>

    @Delete
    suspend fun delete(food: FoodEntity)

    @Update
    suspend fun update(food: FoodEntity)

    /**
     * Persists an Open Food Facts product without clobbering a row the user
     * already has. Duplicate barcodes are ignored so uuid, use count, and
     * manual corrections stay intact.
     */
    @Transaction
    suspend fun cacheOffProduct(food: FoodEntity): FoodEntity {
        val id = insertIgnore(food)
        if (id != -1L) return food.copy(id = id)
        return food.barcode?.let { getByBarcode(it) } ?: food
    }

    @Query("SELECT * FROM foods ORDER BY use_count DESC")
    fun observeAll(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun getById(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): FoodEntity?

    @Query(
        """
        SELECT * FROM foods
        WHERE name LIKE '%' || :query || '%'
           OR IFNULL(brand, '') LIKE '%' || :query || '%'
           OR IFNULL(barcode, '') LIKE '%' || :query || '%'
        ORDER BY use_count DESC
        LIMIT 50
        """,
    )
    suspend fun search(query: String): List<FoodEntity>

    @Query("SELECT * FROM foods ORDER BY use_count DESC LIMIT :limit")
    suspend fun recent(limit: Int = 12): List<FoodEntity>

    @Query("SELECT * FROM foods ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun pageForExport(limit: Int, offset: Int): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun countAll(): Int

    @Query("SELECT * FROM foods ORDER BY id")
    suspend fun getAll(): List<FoodEntity>

    @Query("UPDATE foods SET use_count = use_count + 1, last_used_at = :now WHERE id = :id")
    suspend fun recordUse(id: Long, now: Long)

    @Query(
        """
        UPDATE foods SET name = :name, brand = :brand, carbs_grams = :carbsGrams,
            protein_grams = :proteinGrams, fat_grams = :fatGrams, kcal = :kcal,
            serving_grams = :servingGrams, serving_label = :servingLabel,
            off_fetched_at = :offFetchedAt
        WHERE id = :id AND user_corrected = 0
        """,
    )
    suspend fun refreshFromOff(
        id: Long,
        name: String,
        brand: String?,
        carbsGrams: Double,
        proteinGrams: Double?,
        fatGrams: Double?,
        kcal: Double?,
        servingGrams: Double?,
        servingLabel: String?,
        offFetchedAt: Long,
    ): Int

    @Query(
        """
        UPDATE foods SET name = :name, brand = :brand, carbs_grams = :carbsGrams,
            protein_grams = :proteinGrams, fat_grams = :fatGrams, kcal = :kcal,
            serving_grams = :servingGrams, serving_label = :servingLabel,
            off_fetched_at = :offFetchedAt
        WHERE barcode = :barcode AND user_corrected = 0
        """,
    )
    suspend fun refreshByBarcodeFromOff(
        barcode: String,
        name: String,
        brand: String?,
        carbsGrams: Double,
        proteinGrams: Double?,
        fatGrams: Double?,
        kcal: Double?,
        servingGrams: Double?,
        servingLabel: String?,
        offFetchedAt: Long,
    ): Int

    @Query("DELETE FROM foods")
    suspend fun clear()
}
