package com.omb9.glucosehero.data.repository

import com.omb9.glucosehero.data.local.db.SupplyDao
import com.omb9.glucosehero.data.local.entity.SupplyEntity
import com.omb9.glucosehero.data.local.entity.toDomain
import com.omb9.glucosehero.domain.model.Supply
import com.omb9.glucosehero.domain.model.SupplyType
import com.omb9.glucosehero.domain.repository.SupplyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplyRepositoryImpl @Inject constructor(
    private val supplyDao: SupplyDao,
) : SupplyRepository {

    override fun observeActiveSupplies(): Flow<List<Supply>> =
        supplyDao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun addSupply(
        type: SupplyType,
        startedAt: Long,
        expectedLifespanDays: Int,
    ): Long {
        // Retire the previous item of the same type at the moment its
        // replacement starts, keeping `observeActive` a clean lifecycle query.
        supplyDao.deactivateSuppliesOfType(type.name, startedAt)
        return supplyDao.insert(
            SupplyEntity(
                type = type,
                startedAt = startedAt,
                expectedLifespanDays = expectedLifespanDays,
                replacedAt = null,
            )
        )
    }
}
