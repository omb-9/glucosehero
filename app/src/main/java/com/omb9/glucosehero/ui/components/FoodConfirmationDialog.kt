package com.omb9.glucosehero.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.ui.log.OffFoodDraft
import com.omb9.glucosehero.util.Formatters

/**
 * Human review step after a successful Open Food Facts lookup. Every editable
 * field is prefilled but overridable; carbohydrates are required and are shown
 * as missing when Open Food Facts did not supply them, rather than silently
 * defaulting to zero.
 */
@Composable
fun FoodConfirmationDialog(
    initial: OffFoodDraft,
    onConfirm: (OffFoodDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var brand by remember(initial) { mutableStateOf(initial.brand) }
    var servingLabel by remember(initial) { mutableStateOf(initial.servingLabel) }
    var carbs by remember(initial) { mutableStateOf(initial.carbs) }
    var protein by remember(initial) { mutableStateOf(initial.protein) }
    var fat by remember(initial) { mutableStateOf(initial.fat) }
    var kcal by remember(initial) { mutableStateOf(initial.kcal?.let(::formatMacro).orEmpty()) }

    val carbsWasMissing = initial.carbs.isBlank()
    val carbsValue = Formatters.parseDecimal(carbs)?.takeIf { it > 0 }
    val carbsInvalid = carbs.isBlank() || carbsValue == null
    val canConfirm = !carbsInvalid && name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review food") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null)
                        Text(
                            "Open Food Facts data — review and confirm",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

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
                        if (carbsWasMissing && carbs.isBlank()) {
                            Text("No carbohydrate data from Open Food Facts — enter it to continue.")
                        } else if (carbsInvalid) {
                            Text("Enter a positive number.")
                        }
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

                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it },
                    label = { Text("Calories (kcal)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                OffAttribution()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        initial.copy(
                            name = name.trim(),
                            brand = brand.trim(),
                            servingLabel = servingLabel.trim(),
                            carbs = carbs.trim(),
                            protein = protein.trim(),
                            fat = fat.trim(),
                            kcal = Formatters.parseDecimal(kcal),
                        ),
                    )
                },
                enabled = canConfirm,
            ) {
                Text("Save food")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** ODbL attribution shown alongside any Open Food Facts-sourced data. */
@Composable
internal fun OffAttribution(modifier: Modifier = Modifier) {
    Text(
        text = "Nutrition data from Open Food Facts",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private fun formatMacro(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
