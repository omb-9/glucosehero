package com.omb9.glucosehero.ui.foods

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.util.Formatters

/**
 * Standalone library for browsing, searching, favoriting, editing, and
 * deleting saved foods. Independent of the in-sheet picker owned by the
 * Add Entry flow.
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

    LaunchedEffect(offRefresh) {
        val refreshed = (offRefresh as? OffRefreshState.Success)?.food ?: return@LaunchedEffect
        editingFood = refreshed
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food Library") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
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
                placeholder = { Text("Search foods") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            when {
                foods.isEmpty() && searchQuery.isBlank() -> EmptyLibraryState()

                foods.isEmpty() -> NoSearchResultsState()

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(foods, key = { it.id }) { food ->
                        FoodRow(
                            food = food,
                            onClick = { editingFood = food },
                            onToggleFavorite = { viewModel.toggleFavorite(food) },
                            onDelete = { viewModel.requestDelete(food) },
                        )
                    }
                }
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
            title = { Text("Delete food?") },
            text = { Text("This removes \"${food.name}\" from your library.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text("Cancel")
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
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
                    maxLines = 1,
                )
                val subtitle = foodSubtitle(food)
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
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
                    tint = if (food.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete food",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

    val carbsInvalid = carbs.isBlank() || Formatters.parseDecimal(carbs)?.takeIf { it > 0 } == null
    val canSave = !carbsInvalid && name.isNotBlank()
    val canRefresh = food.barcode != null &&
        !food.userCorrected &&
        refreshState !is OffRefreshState.Loading
    val showRefresh = food.barcode != null

    AlertDialog(
        onDismissRequest = onDismiss,
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
                )

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = servingLabel,
                    onValueChange = { servingLabel = it },
                    label = { Text("Serving") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                )

                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it },
                    label = { Text("Protein (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text("Fat (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
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
                            )
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            if (refreshState is OffRefreshState.Loading) {
                                "Refreshing…"
                            } else {
                                "Refresh from Open Food Facts"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    val refreshMessage = when (refreshState) {
                        is OffRefreshState.Success -> "Updated from Open Food Facts."
                        OffRefreshState.KeptLocalEdits ->
                            "Your edits are kept — Open Food Facts won't overwrite them."
                        is OffRefreshState.Failed -> refreshState.message
                        else -> if (food.userCorrected) {
                            "Your edits are kept — Open Food Facts won't overwrite them."
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
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
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
                            userCorrected = true,
                        ),
                    )
                },
                enabled = canSave && refreshState !is OffRefreshState.Loading,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = refreshState !is OffRefreshState.Loading,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun EmptyLibraryState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text("No saved foods yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Foods you save while logging will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoSearchResultsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text("No foods found", style = MaterialTheme.typography.titleMedium)
            Text(
                "Try a different search.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun foodSubtitle(food: FoodEntity): String = buildList {
    food.brand?.takeIf { it.isNotBlank() }?.let { add(it) }
    if (food.hasMissingCarbs) {
        add("No carb data")
    } else {
        add("${formatMacro(food.carbsGrams)}g carbs")
    }
    food.proteinGrams?.let { add("${formatMacro(it)}g protein") }
    food.fatGrams?.let { add("${formatMacro(it)}g fat") }
}.joinToString(" · ")

private fun formatMacro(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
