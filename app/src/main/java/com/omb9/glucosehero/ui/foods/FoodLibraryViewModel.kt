package com.omb9.glucosehero.ui.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.db.GlucoseHeroDatabase
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.remote.off.OpenFoodFactsApi
import com.omb9.glucosehero.data.remote.off.resolvedServingGrams
import com.omb9.glucosehero.data.remote.off.scaledCarbs
import com.omb9.glucosehero.data.remote.off.scaledFat
import com.omb9.glucosehero.data.remote.off.scaledKcal
import com.omb9.glucosehero.data.remote.off.scaledProtein
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Outcome of a user-triggered Open Food Facts refresh on a library food. */
sealed interface OffRefreshState {
    data object Idle : OffRefreshState
    data object Loading : OffRefreshState
    data class Success(val food: FoodEntity) : OffRefreshState
    data object KeptLocalEdits : OffRefreshState
    data class Failed(val message: String) : OffRefreshState
}

@HiltViewModel
class FoodLibraryViewModel @Inject constructor(
    database: GlucoseHeroDatabase,
    private val openFoodFactsApi: OpenFoodFactsApi,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val foodDao = database.foodDao()

    /**
     * Full observed library, ordered by [FoodEntity.useCount] descending via
     * [com.omb9.glucosehero.data.local.db.FoodDao.observeAll]. The sort is
     * deliberate: the foods people actually reach for most surface first.
     */
    val foods: StateFlow<List<FoodEntity>> = foodDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** The observed list filtered by the current search query, keeping the DAO order. */
    val visibleFoods: StateFlow<List<FoodEntity>> = combine(foods, _searchQuery) { all, query ->
        val trimmed = query.trim()
        if (trimmed.isBlank()) all else all.filter { it.matches(trimmed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Food awaiting delete confirmation; null when no dialog should be shown. */
    private val _pendingDelete = MutableStateFlow<FoodEntity?>(null)
    val pendingDelete: StateFlow<FoodEntity?> = _pendingDelete.asStateFlow()

    private val _offRefresh = MutableStateFlow<OffRefreshState>(OffRefreshState.Idle)
    val offRefresh: StateFlow<OffRefreshState> = _offRefresh.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(food: FoodEntity) {
        viewModelScope.launch {
            foodDao.update(food.copy(isFavorite = !food.isFavorite))
        }
    }

    fun updateFood(food: FoodEntity) {
        viewModelScope.launch {
            foodDao.update(food.copy(userCorrected = true))
        }
    }

    /**
     * Manual Open Food Facts refresh for [food]. Never runs on its own — the
     * food detail screen must call this. Does not overwrite a row the user
     * has already corrected, and does not fetch when barcode lookup is off.
     */
    fun refreshFood(food: FoodEntity) {
        if (_offRefresh.value is OffRefreshState.Loading) return
        val barcode = food.barcode?.trim()?.takeIf { it.isNotBlank() } ?: return
        if (food.userCorrected) {
            _offRefresh.value = OffRefreshState.KeptLocalEdits
            return
        }
        viewModelScope.launch {
            if (!settingsDataStore.barcodeLookupEnabled.first()) {
                _offRefresh.value = OffRefreshState.Failed(
                    "Barcode lookup is turned off in Settings.",
                )
                return@launch
            }
            _offRefresh.value = OffRefreshState.Loading
            try {
                val response = openFoodFactsApi.product(barcode)
                val body = response.body()
                val product = body?.product
                if (!response.isSuccessful || body == null || body.status != 1 || product == null) {
                    _offRefresh.value = OffRefreshState.Failed(
                        "Couldn't refresh this product from Open Food Facts.",
                    )
                    return@launch
                }
                val carbs = product.scaledCarbs()
                if (carbs == null) {
                    _offRefresh.value = OffRefreshState.Failed(
                        "Open Food Facts has no carbohydrate data for this product.",
                    )
                    return@launch
                }
                val servingGrams = product.resolvedServingGrams()
                val updatedRows = foodDao.refreshFromOff(
                    id = food.id,
                    name = product.productName?.trim().orEmpty().ifBlank { food.name },
                    brand = product.brands?.trim()?.ifBlank { null } ?: food.brand,
                    carbsGrams = carbs,
                    proteinGrams = product.scaledProtein() ?: food.proteinGrams,
                    fatGrams = product.scaledFat() ?: food.fatGrams,
                    kcal = product.scaledKcal() ?: food.kcal,
                    servingGrams = servingGrams ?: food.servingGrams,
                    servingLabel = product.servingSize?.trim()?.ifBlank { null } ?: food.servingLabel,
                    offFetchedAt = System.currentTimeMillis(),
                )
                if (updatedRows == 0) {
                    _offRefresh.value = OffRefreshState.KeptLocalEdits
                    return@launch
                }
                val refreshed = foodDao.getById(food.id)
                _offRefresh.value = if (refreshed != null) {
                    OffRefreshState.Success(refreshed)
                } else {
                    OffRefreshState.Failed("Couldn't refresh this product from Open Food Facts.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _offRefresh.value = OffRefreshState.Failed("Couldn't reach Open Food Facts.")
            }
        }
    }

    fun dismissOffRefresh() {
        _offRefresh.value = OffRefreshState.Idle
    }

    fun requestDelete(food: FoodEntity) {
        _pendingDelete.value = food
    }

    fun dismissDelete() {
        _pendingDelete.value = null
    }

    fun confirmDelete() {
        val food = _pendingDelete.value ?: return
        viewModelScope.launch {
            foodDao.delete(food)
            _pendingDelete.value = null
        }
    }
}

private fun FoodEntity.matches(query: String): Boolean =
    name.contains(query, ignoreCase = true) ||
        brand?.contains(query, ignoreCase = true) == true ||
        barcode?.contains(query, ignoreCase = true) == true
