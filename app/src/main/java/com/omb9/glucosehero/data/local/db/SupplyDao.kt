package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.omb9.glucosehero.data.local.entity.SupplyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplyDao {

    /** Active (not-yet-replaced) supplies, oldest start date first. */
    @Query(
        """
        SELECT * FROM supplies
        WHERE replaced_at IS NULL
        ORDER BY started_at ASC
        """
    )
    fun observeActive(): Flow<List<SupplyEntity>>

    @Insert
    suspend fun insert(entity: SupplyEntity): Long

    /** Retires any still-active supply of [type] when a replacement is logged. */
    @Query(
        """
        UPDATE supplies
        SET replaced_at = :replacedAt
        WHERE type = :type AND replaced_at IS NULL
        """
    )
    suspend fun deactivateSuppliesOfType(type: String, replacedAt: Long)

    /**
     * Atomically retires the previous active supply of [type] and inserts its
     * replacement. Room runs the body in a transaction, so an insert failure
     * rolls back the retirement instead of leaving the slot empty.
     */
    @Transaction
    suspend fun deactivateAndInsert(
        type: String,
        replacedAt: Long,
        entity: SupplyEntity,
    ): Long {
        deactivateSuppliesOfType(type, replacedAt)
        return insert(entity)
    }
}
