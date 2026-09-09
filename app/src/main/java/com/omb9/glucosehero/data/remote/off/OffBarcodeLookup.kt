package com.omb9.glucosehero.data.remote.off

import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.FoodDao
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.entity.FoodEntity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Barcode lookup that always consults [FoodDao] before Open Food Facts.
 *
 * Cached products stay in Room indefinitely ([FoodDao.cacheOffProduct] never
 * expires a row). Repeat scans and keystroke-adjacent barcode retries therefore
 * stay offline after the first successful fetch.
 */
@Singleton
class OffBarcodeLookup @Inject constructor(
    database: GlucoseHeroDatabase,
    private val api: OpenFoodFactsApi,
    private val settingsDataStore: SettingsDataStore,
) {
    private val foodDao: FoodDao = database.foodDao()

    suspend fun lookup(barcode: String): OffBarcodeLookupResult {
        val code = barcode.trim()
        if (code.isEmpty()) return OffBarcodeLookupResult.Empty

        val cached = foodDao.getByBarcode(code)
        if (cached != null) {
            return OffBarcodeLookupResult.Cached(cached)
        }

        if (!settingsDataStore.barcodeLookupEnabled.first()) {
            return OffBarcodeLookupResult.Disabled(code)
        }

        return try {
            val response = api.product(code)
            if (response.code() == 404) {
                return OffBarcodeLookupResult.NotFound(code)
            }
            val body = response.body()
            val product = body?.product
            when {
                response.isSuccessful && body != null && body.status == 1 && product != null -> {
                    val entity = product.toFoodEntity(code)
                    val saved = foodDao.cacheOffProduct(entity)
                    OffBarcodeLookupResult.Fetched(saved, product)
                }
                response.isSuccessful && (body == null || body.status == 0 || product == null) ->
                    OffBarcodeLookupResult.NotFound(code)
                else -> OffBarcodeLookupResult.HttpError(code)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            OffBarcodeLookupResult.NetworkError(code)
        }
    }

    companion object {
        /**
         * Pure cache-vs-network decision used by tests and by [lookup].
         * [cached] wins even when network lookup is enabled.
         */
        fun resolveSource(
            cached: FoodEntity?,
            lookupEnabled: Boolean,
        ): OffLookupSource = when {
            cached != null -> OffLookupSource.CACHE
            !lookupEnabled -> OffLookupSource.DISABLED
            else -> OffLookupSource.NETWORK
        }
    }
}

enum class OffLookupSource { CACHE, DISABLED, NETWORK }

sealed interface OffBarcodeLookupResult {
    data object Empty : OffBarcodeLookupResult
    data class Cached(val food: FoodEntity) : OffBarcodeLookupResult
    data class Fetched(val food: FoodEntity, val product: OffProduct) : OffBarcodeLookupResult
    data class NotFound(val barcode: String) : OffBarcodeLookupResult
    data class Disabled(val barcode: String) : OffBarcodeLookupResult
    data class HttpError(val barcode: String) : OffBarcodeLookupResult
    data class NetworkError(val barcode: String) : OffBarcodeLookupResult
}
