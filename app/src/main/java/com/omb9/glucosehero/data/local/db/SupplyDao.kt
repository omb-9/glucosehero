package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.omb9.glucosehero.data.local.entity.SupplyEntity
import com.omb9.glucosehero.domain.model.SupplyType
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplyDao {

    /** Active (not-yet-replaced) supplies, oldest start date first. */
    @Query(
        """
        SELECT * FROM supplies
        WHERE replaced_at IS NULL
        ORDER BY started_at ASC
        """,
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

    /**
     * Corrects an existing supply's type, start time, or expected lifespan in
     * place. Only the three editable columns are touched, so `replaced_at` and
     * `uuid` are preserved: an edit never retires the row (unlike
     * [deactivateAndInsert]) and never changes its backup/export identity.
     */
    @Query(
        """
        UPDATE supplies
        SET type = :type, started_at = :startedAt, expected_lifespan_days = :expectedLifespanDays
        WHERE id = :id
        """
    )
    suspend fun update(id: Long, type: SupplyType, startedAt: Long, expectedLifespanDays: Int)

    /**
     * Removes a supply that was logged by mistake. Distinct from replacement:
     * this deletes the row outright and starts nothing new.
     */
    @Query("DELETE FROM supplies WHERE id = :id")
    suspend fun delete(id: Long)

    // ---------- Backup/export paged reads (additive) ----------

    @Query("SELECT * FROM supplies ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun pageForExport(limit: Int, offset: Int): List<SupplyEntity>

    @Query("SELECT COUNT(*) FROM supplies")
    suspend fun countAll(): Int

    @Query("SELECT * FROM supplies ORDER BY id")
    suspend fun getAll(): List<SupplyEntity>

    @Query("DELETE FROM supplies")
    suspend fun clear()

    @Insert
    suspend fun insertAll(supplies: List<SupplyEntity>): List<Long>

    /**
     * Inserts supplies, skipping any whose unique `uuid` (migration 8→9)
     * already exists. Backup MERGE uses this so repeated imports stay
     * idempotent without loading every uuid into memory.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(supplies: List<SupplyEntity>): List<Long>
}
