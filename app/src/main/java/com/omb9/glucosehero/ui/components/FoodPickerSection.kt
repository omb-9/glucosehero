package com.omb9.glucosehero.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.data.local.entity.FoodEntity
import com.omb9.glucosehero.data.vision.MealBarcodeScanResult
import com.omb9.glucosehero.data.vision.MealBarcodeScanner
import com.omb9.glucosehero.domain.model.FoodSource
import kotlinx.coroutines.launch

/**
 * Meal-path food affordances for the Add Entry sheet: one-tap recent foods,
 * local library search, and the permission-free barcode scanner. Selecting a
 * saved food auto-fills the draft macros and links `entries.food_id`.
 *
 * Palette: true-black / true-white surfaces, gray for secondary text and
 * chrome, orange (theme primary) for the accent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodPickerSection(
    recentFoods: List<FoodEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<FoodEntity>,
    onFoodSelected: (FoodEntity) -> Unit,
    onBarcodeScanned: (String) -> Unit,
    isLookingUp: Boolean,
    lookupMessage: String?,
    lookupIsError: Boolean,
    modifier: Modifier = Modifier,
    selectedFood: FoodEntity? = null,
    barcodeLookupEnabled: Boolean = true,
    onManageFoods: (() -> Unit)? = null,
    scanner: MealBarcodeScanner = remember { MealBarcodeScanner() },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val chrome = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline = MaterialTheme.colorScheme.outline
    val canScan = barcodeLookupEnabled && !isScanning && !isLookingUp

    val scan: () -> Unit = {
        if (canScan) {
            isScanning = true
            scanError = null
            scope.launch {
                when (val result = scanner.scanBarcodes(context)) {
                    is MealBarcodeScanResult.Success ->
                        onBarcodeScanned(result.values.firstOrNull().orEmpty())

                    MealBarcodeScanResult.Canceled -> Unit

                    MealBarcodeScanResult.Unavailable ->
                        scanError = "Barcode scanner isn't available on this device."
                }
                isScanning = false
            }
        }
    }

    val selectFood: (FoodEntity) -> Unit = { food ->
        onFoodSelected(food)
        onSearchQueryChange("")
    }

    val showsOffData = selectedFood?.source == FoodSource.OPEN_FOOD_FACTS ||
        recentFoods.any { it.source == FoodSource.OPEN_FOOD_FACTS } ||
        searchResults.any { it.source == FoodSource.OPEN_FOOD_FACTS }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Food",
            style = MaterialTheme.typography.labelLarge,
            color = muted,
        )
        Spacer(Modifier.height(8.dp))

        if (recentFoods.isNotEmpty() && searchQuery.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recentFoods.forEach { food ->
                    val selected = selectedFood?.id != null &&
                        selectedFood.id > 0L &&
                        selectedFood.id == food.id
                    FilterChip(
                        selected = selected,
                        onClick = { selectFood(food) },
                        label = { Text(food.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = chrome,
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedLabelColor = accent,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = outline,
                            selectedBorderColor = accent,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search library", color = muted) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = muted,
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = scan,
                    enabled = canScan,
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = "Scan barcode",
                        tint = if (barcodeLookupEnabled) accent else muted,
                    )
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                cursorColor = accent,
            ),
        )

        if (searchQuery.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            if (searchResults.isEmpty()) {
                Text(
                    "No foods found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    searchResults.forEach { food ->
                        FoodResultRow(
                            food = food,
                            selected = selectedFood?.id != null &&
                                selectedFood.id > 0L &&
                                selectedFood.id == food.id,
                            onClick = { selectFood(food) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            onClick = scan,
            enabled = canScan,
            shape = RoundedCornerShape(16.dp),
            color = chrome,
            border = BorderStroke(1.dp, outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = if (barcodeLookupEnabled) accent else muted,
                )
                Text(
                    "Scan barcode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        val linkedName = selectedFood?.name?.takeIf {
            selectedFood.id > 0L && it.isNotBlank()
        }
        if (linkedName != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Linked · $linkedName",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
        }

        val error = scanError
        when {
            error != null -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            isScanning -> {
                Spacer(Modifier.height(4.dp))
                StatusRow(text = "Scanning barcode…")
            }

            isLookingUp -> {
                Spacer(Modifier.height(4.dp))
                StatusRow(text = "Looking up barcode…")
            }

            lookupMessage != null -> {
                Spacer(Modifier.height(4.dp))
                Text(
                    lookupMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lookupIsError) MaterialTheme.colorScheme.error else muted,
                )
            }
        }

        if (showsOffData) {
            Spacer(Modifier.height(8.dp))
            OffAttribution()
        }

        if (onManageFoods != null) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onManageFoods) {
                Text("Manage foods", color = accent)
            }
        }
    }
}

/**
 * Captures the current meal draft as a library food with
 * [FoodSource.FROM_ENTRY]. Hidden while a library food is already linked.
 */
@Composable
fun SaveDraftAsFoodButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            "Save this as a food",
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun StatusRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodResultRow(
    food: FoodEntity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (selected) accent else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                food.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
            )
            val subtitle = buildList {
                food.brand?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (food.hasMissingCarbs) {
                    add("No carb data")
                } else {
                    add("${formatMacro(food.carbsGrams)}g carbs")
                }
                food.proteinGrams?.let { add("${formatMacro(it)}g protein") }
                food.fatGrams?.let { add("${formatMacro(it)}g fat") }
            }.joinToString(" · ")
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (food.source == FoodSource.OPEN_FOOD_FACTS) {
                OffAttribution(modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun formatMacro(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
