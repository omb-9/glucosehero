package com.omb9.glucosehero.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity

@Dao
interface GlucoseSampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun upsertAll(samples: List<GlucoseSampleEntity>): List<Long>

    @Query("DELETE FROM glucose_samples WHERE hc_record_id = :hcRecordId")
    suspend fun deleteByHcRecordId(hcRecordId: String)

    @Query("SELECT COUNT(*) FROM glucose_samples")
    suspend fun count(): Int

    @Query("DELETE FROM glucose_samples")
    suspend fun clear()

    // ---------- Backup/export paged reads (additive) ----------

    @Query("SELECT * FROM glucose_samples ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun pageForExport(limit: Int, offset: Int): List<GlucoseSampleEntity>

    @Query("SELECT * FROM glucose_samples ORDER BY id")
    suspend fun getAll(): List<GlucoseSampleEntity>

    @Insert
    suspend fun insertAll(samples: List<GlucoseSampleEntity>): List<Long>
}
