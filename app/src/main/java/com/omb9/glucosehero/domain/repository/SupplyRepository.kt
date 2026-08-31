package com.omb9.glucosehero.domain.repository

import com.omb9.glucosehero.domain.model.Supply
import com.omb9.glucosehero.domain.model.SupplyType
import kotlinx.coroutines.flow.Flow

interface SupplyRepository {
    /** Supplies currently in rotation (not yet replaced by a newer item). */
    fun observeActiveSupplies(): Flow<List<Supply>>

    /**
     * Starts a new [type] and automatically retires any previous, still-active
     * supply of the same type (its lifecycle ends at [startedAt]).
     */
    suspend fun addSupply(
        type: SupplyType,
        startedAt: Long,
        expectedLifespanDays: Int,
    ): Long
}
