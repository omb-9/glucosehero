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

    /**
     * Corrects an existing supply's type, start time, or expected lifespan in
     * place. Unlike [addSupply], this never retires another row — the edited
     * supply keeps its identity and simply gets recalculated.
     */
    suspend fun updateSupply(
        id: Long,
        type: SupplyType,
        startedAt: Long,
        expectedLifespanDays: Int,
    )

    /** Removes a supply that was logged by mistake (starts nothing new). */
    suspend fun deleteSupply(id: Long)
}
