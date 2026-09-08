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

    /**
     * Inserts a fetched Open Food Facts product. Duplicate barcodes are ignored
     * so a race with a cache hit cannot wipe [FoodEntity.useCount], uuid, or
     * user corrections. Returns `-1` when the barcode is already stored.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(food: FoodEntity): Long

    @Insert
    suspend fun insertAll(foods: List<FoodEntity>): List<Long>

    @Delete
    suspend fun delete(food: FoodEntity)

    @Update
    suspend fun update(food: FoodEntity)

    /**
     * Writes [food] into the local library if its barcode is not already cached.
     * Repeat scans must reuse the existing row instead of replacing it.
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
    suspend fun recent(limit: Int = 20): List<FoodEntity>

    @Query("UPDATE foods SET use_count = use_count + 1, last_used_at = :now WHERE id = :id")
    suspend fun recordUse(id: Long, now: Long)

    /**
     * Updates Open Food Facts product data strictly when the row has NOT been
     * corrected by the user (`user_corrected = 0`). Never overwrites a
     * user-corrected row. Returns the number of updated rows (0 if
     * user_corrected was 1 or the food was not found).
     */
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

    // ---------- Backup/export paged reads (additive) ----------

    @Query("SELECT * FROM foods ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun pageForExport(limit: Int, offset: Int): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun countAll(): Int

    @Query("SELECT * FROM foods ORDER BY id")
    suspend fun getAll(): List<FoodEntity>

    @Query("DELETE FROM foods")
    suspend fun clear()
}
