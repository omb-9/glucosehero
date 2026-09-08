package com.omb9.glucosehero.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.domain.model.Macros
import java.util.UUID

/**
 * The unified foods table and portion model entity.
 *
 * One table, not two: represents either a scanned product (e.g. from Open Food Facts)
 * or a whole meal captured from a past entry ([FoodSource.FROM_ENTRY]).
 * Component breakdown is deliberately deferred to a future `components` JSON column.
 *
 * SQLite permits multiple NULL values in the UNIQUE [barcode] index.
 */
@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index("last_used_at"),
        Index("name"),
    ],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "uuid") val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "brand") val brand: String? = null,
    @ColumnInfo(name = "barcode") val barcode: String? = null,
    @ColumnInfo(name = "carbs_grams") val carbsGrams: Double,
    @ColumnInfo(name = "protein_grams") val proteinGrams: Double? = null,
    @ColumnInfo(name = "fat_grams") val fatGrams: Double? = null,
    @ColumnInfo(name = "kcal") val kcal: Double? = null,
    @ColumnInfo(name = "serving_grams") val servingGrams: Double? = null,
    @ColumnInfo(name = "serving_label") val servingLabel: String? = null,
    @ColumnInfo(name = "source") val source: FoodSource,
    @ColumnInfo(name = "off_fetched_at") val offFetchedAt: Long? = null,
    @ColumnInfo(name = "user_corrected") val userCorrected: Boolean = false,
    @ColumnInfo(name = "use_count") val useCount: Int = 0,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long,
) {
    companion object {
        /** Sentinel value indicating carbohydrate data was missing from the source (e.g. Open Food Facts). */
        const val CARBS_MISSING = -1.0
    }

    /** True if carbohydrate data was absent rather than zero. */
    val hasMissingCarbs: Boolean
        get() = carbsGrams < 0.0
}

/** Extract per-serving macros for portion scaling or bolus calculation. */
fun FoodEntity.toMacros(): Macros = Macros(
    carbsGrams = carbsGrams,
    proteinGrams = proteinGrams,
    fatGrams = fatGrams,
    kcal = kcal,
)

/** Scale this food's macros by a number of servings. */
fun FoodEntity.scaleByServings(servings: Double): Macros =
    com.omb9.glucosehero.util.PortionCalculator.scaleByServings(toMacros(), servings)

/**
 * Scale this food's macros by grams.
 *
 * If [servingGrams] is available, scales by `grams / servingGrams`.
 * Otherwise, falls back to per-100g (`grams / 100.0`).
 */
fun FoodEntity.scaleByGrams(grams: Double): Macros =
    com.omb9.glucosehero.util.PortionCalculator.scaleByGrams(toMacros(), grams, servingGrams = servingGrams ?: 100.0)

