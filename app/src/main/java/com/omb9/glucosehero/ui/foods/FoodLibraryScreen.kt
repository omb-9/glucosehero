package com.omb9.glucosehero.ui.foods

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.domain.model.FoodSource
import com.omb9.glucosehero.util.Formatters

/**
 * Saved-foods library: search, favorite, edit, and delete. Default order is
 * [FoodEntity.useCount] descending from [com.omb9.glucosehero.data.local.db.FoodDao.observeAll].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLibraryScreen(
    onBack: () -> Unit,
    viewModel: FoodLibraryViewModel = hiltViewModel(),
) {
    val foods by viewModel.visibleFoods.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val offRefresh by viewModel.offRefresh.collectAsStateWithLifecycle()
    var editingFood by remember { mutableStateOf<FoodEntity?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }

    val displayedFoods = remember(foods, favoritesOnly) {
        if (favoritesOnly) foods.filter { it.isFavorite } else foods
    }

    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LaunchedEffect(offRefresh) {
        val refreshed = (offRefresh as? OffRefreshState.Success)?.food ?: return@LaunchedEffect
        editingFood = refreshed
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Food Library") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search foods", color = muted) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = muted)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = muted,
                            )
                        }
                    }
                },
                singleLine = true,
                colors = libraryFieldColors(accent),
            )

            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                label = { Text("Favorites") },
                leadingIcon = {
                    Icon(
                        imageVector = if (favoritesOnly) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                    iconColor = muted,
                    selectedContainerColor = accent.copy(alpha = 0.18f),
                    selectedLabelColor = accent,
                    selectedLeadingIconColor = accent,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = favoritesOnly,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = accent,
                ),
            )

            when {
                displayedFoods.isNotEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayedFoods, key = { it.id }) { food ->
                        FoodRow(
                            food = food,
                            onClick = { editingFood = food },
                            onToggleFavorite = { viewModel.toggleFavorite(food) },
                            onDelete = { viewModel.requestDelete(food) },
                        )
                    }
                }

                searchQuery.isNotBlank() -> NoSearchResultsState()

                favoritesOnly -> NoFavoritesState()

                else -> EmptyLibraryState()
            }
        }
    }

    editingFood?.let { food ->
        FoodEditDialog(
            food = food,
            refreshState = offRefresh,
            onDismiss = {
                viewModel.dismissOffRefresh()
                editingFood = null
            },
            onSave = { updated ->
                viewModel.updateFood(updated)
                viewModel.dismissOffRefresh()
                editingFood = null
            },
            onRefresh = { viewModel.refreshFood(food) },
        )
    }

    pendingDelete?.let { food ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete food?") },
            text = { Text("This removes \"${food.name}\" from your library.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text("Cancel", color = muted)
                }
            },
        )
    }
}

@Composable
private fun FoodRow(
    food: FoodEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                Text(
                    food.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = foodSubtitle(food)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (food.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (food.isFavorite) {
                        "Remove from favorites"
                    } else {
                        "Add to favorites"
                    },
                    tint = if (food.isFavorite) accent else muted,
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete food",
                    tint = muted,
                )
            }
        }
    }
}

@Composable
private fun FoodEditDialog(
    food: FoodEntity,
    refreshState: OffRefreshState,
    onDismiss: () -> Unit,
    onSave: (FoodEntity) -> Unit,
    onRefresh: () -> Unit,
) {
    var name by remember(food) { mutableStateOf(food.name) }
    var brand by remember(food) { mutableStateOf(food.brand.orEmpty()) }
    var servingLabel by remember(food) { mutableStateOf(food.servingLabel.orEmpty()) }
    var carbs by remember(food) {
        mutableStateOf(if (food.hasMissingCarbs) "" else formatMacro(food.carbsGrams))
    }
    var protein by remember(food) { mutableStateOf(food.proteinGrams?.let(::formatMacro).orEmpty()) }
    var fat by remember(food) { mutableStateOf(food.fatGrams?.let(::formatMacro).orEmpty()) }
    var kcal by remember(food) { mutableStateOf(food.kcal?.let(::formatMacro).orEmpty()) }

    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fieldColors = libraryFieldColors(accent)

    val carbsInvalid = carbs.isBlank() || Formatters.parseDecimal(carbs)?.takeIf { it > 0 } == null
    val canSave = !carbsInvalid && name.isNotBlank()
    val canRefresh = food.barcode != null &&
        !food.userCorrected &&
        refreshState !is OffRefreshState.Loading
    val showRefresh = food.barcode != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Edit food") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )

                OutlinedTextField(
                    value = servingLabel,
                    onValueChange = { servingLabel = it },
                    label = { Text("Serving") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )

                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it },
                    label = { Text("Carbs (g)") },
                    isError = carbsInvalid,
                    supportingText = {
                        if (carbsInvalid) Text("Enter a positive number.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )

                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text("Protein (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )

                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text("Fat (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )

                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it },
                    label = { Text("Calories (kcal)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )

                if (showRefresh) {
                    TextButton(
                        onClick = onRefresh,
                        enabled = canRefresh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (refreshState is OffRefreshState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = accent,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (canRefresh) accent else muted,
                            )
                        }
                        Text(
                            if (refreshState is OffRefreshState.Loading) {
                                "Refreshing…"
                            } else {
                                "Refresh from Open Food Facts"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                            color = if (canRefresh) accent else muted,
                        )
                    }
                    val refreshMessage = when (refreshState) {
                        is OffRefreshState.Success -> "Updated from Open Food Facts."
                        OffRefreshState.KeptLocalEdits ->
                            "Your edits are kept. Open Food Facts won't overwrite them."
                        is OffRefreshState.Failed -> refreshState.message
                        else -> if (food.userCorrected) {
                            "Your edits are kept. Open Food Facts won't overwrite them."
                        } else {
                            null
                        }
                    }
                    if (refreshMessage != null) {
                        Text(
                            refreshMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (refreshState is OffRefreshState.Failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                muted
                            },
                        )
                    }
                }

                if (food.source == FoodSource.OPEN_FOOD_FACTS) {
                    Text(
                        "Nutrition data from Open Food Facts",
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedCarbs = Formatters.parseDecimal(carbs)?.takeIf { it > 0 }
                        ?: return@TextButton
                    onSave(
                        food.copy(
                            name = name.trim(),
                            brand = brand.trim().ifBlank { null },
                            servingLabel = servingLabel.trim().ifBlank { null },
                            carbsGrams = parsedCarbs,
                            proteinGrams = Formatters.parseDecimal(protein)?.takeIf { it > 0 },
                            fatGrams = Formatters.parseDecimal(fat)?.takeIf { it > 0 },
                            kcal = Formatters.parseDecimal(kcal)?.takeIf { it > 0 },
                            userCorrected = true,
                        ),
                    )
                },
                enabled = canSave && refreshState !is OffRefreshState.Loading,
            ) {
                Text("Save", color = if (canSave) accent else muted)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = refreshState !is OffRefreshState.Loading,
            ) {
                Text("Cancel", color = muted)
            }
        },
    )
}

@Composable
private fun EmptyLibraryState() {
    LibraryEmptyState(
        icon = Icons.Filled.Restaurant,
        title = "No saved foods yet",
        body = "Foods you save while logging will show up here.",
    )
}

@Composable
private fun NoSearchResultsState() {
    LibraryEmptyState(
        icon = Icons.Filled.Search,
        title = "No foods found",
        body = "Try a different search.",
    )
}

@Composable
private fun NoFavoritesState() {
    LibraryEmptyState(
        icon = Icons.Filled.StarBorder,
        title = "No favorites yet",
        body = "Star foods you reach for often.",
    )
}

@Composable
private fun LibraryEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = muted.copy(alpha = 0.4f),
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
    }
}

@Composable
private fun libraryFieldColors(accent: Color) =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        cursorColor = accent,
        focusedLabelColor = accent,
    )

private fun foodSubtitle(food: FoodEntity): String = buildList {
    food.brand?.takeIf { it.isNotBlank() }?.let { add(it) }
    food.servingLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
    if (food.hasMissingCarbs) {
        add("No carb data")
    } else {
        add("${formatMacro(food.carbsGrams)}g carbs")
    }
    food.proteinGrams?.let { add("${formatMacro(it)}g protein") }
    food.fatGrams?.let { add("${formatMacro(it)}g fat") }
    if (food.useCount > 0) add("Used ${food.useCount}×")
}.joinToString(" · ")

private fun formatMacro(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
