package com.omb9.glucosehero.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.domain.model.SupplyType
import com.omb9.glucosehero.util.Formatters
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.ceil
import kotlinx.collections.immutable.ImmutableList

/**
 * The "Active Supplies" section on Stats: a card per active supply plus the
 * sheets used to add, edit, and delete them. Extracted from [StatsScreen] so
 * that screen can stay focused on the chart/TIR/food-impact content.
 */
@Composable
internal fun ActiveSuppliesSection(
    supplies: ImmutableList<ActiveSupplyUi>,
    use24HourTime: Boolean,
    onAdd: () -> Unit,
    onReplace: (SupplyType) -> Unit,
    onEdit: (ActiveSupplyUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Active Supplies",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAdd) {
                Text("Add")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (supplies.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "No active supplies",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAdd) {
                        Text("Log supply")
                    }
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(supplies, key = { it.id }) { supply ->
                    SupplyCard(
                        supply = supply,
                        use24HourTime = use24HourTime,
                        onEdit = { onEdit(supply) },
                        onReplace = { onReplace(supply.type) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SupplyCard(
    supply: ActiveSupplyUi,
    use24HourTime: Boolean,
    onEdit: () -> Unit,
    onReplace: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .width(168.dp)
            .clickable(onClick = onEdit),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = supply.type.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Started ${Formatters.shortDate(Formatters.localDate(supply.startedAt))}" +
                    " · ${Formatters.time(supply.startedAt, use24HourTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { supply.progressPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.14f),
            )
            Spacer(Modifier.height(8.dp))
            if (supply.isExpired) {
                AssistChip(
                    onClick = onReplace,
                    label = { Text("Replace") },
                )
            } else {
                Text(
                    text = remainingLabel(supply),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Shared type + lifespan field layout, reused by both the add and edit sheets. */
@Composable
private fun SupplyFields(
    selectedType: SupplyType,
    onTypeSelected: (SupplyType) -> Unit,
    selectedDays: Int,
    lifespanOptions: List<Int>,
    onDaysSelected: (Int) -> Unit,
) {
    Text(
        text = "Type",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SupplyType.entries.forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { Text(type.label) },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = "Expected lifespan",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        lifespanOptions.forEach { days ->
            FilterChip(
                selected = selectedDays == days,
                onClick = { onDaysSelected(days) },
                label = { Text("$days days") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LogSupplySheet(
    preselectedType: SupplyType?,
    onDismiss: () -> Unit,
    onSave: (SupplyType, Int) -> Unit,
) {
    var selectedType by remember(preselectedType) {
        mutableStateOf(preselectedType ?: SupplyType.SENSOR)
    }
    val lifespanOptions = remember(selectedType) { lifespanOptionsFor(selectedType) }
    var selectedDays by remember(selectedType) { mutableStateOf(lifespanOptions.first()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Log New Supply",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            SupplyFields(
                selectedType = selectedType,
                onTypeSelected = { selectedType = it },
                selectedDays = selectedDays,
                lifespanOptions = lifespanOptions,
                onDaysSelected = { selectedDays = it },
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSave(selectedType, selectedDays) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

/**
 * Edit sheet for an existing supply. Reuses [SupplyFields] for the type and
 * lifespan chips, and adds a real date + time picker for the start time plus a
 * delete action (with confirmation). Distinct from the Replace flow: saving an
 * edit mutates the same row in place, never retiring it or starting another.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditSupplySheet(
    supply: ActiveSupplyUi,
    use24HourTime: Boolean,
    onDismiss: () -> Unit,
    onSave: (SupplyType, Long, Int) -> Unit,
    onDelete: () -> Unit,
) {
    var selectedType by remember(supply.id) { mutableStateOf(supply.type) }
    val lifespanOptions = remember(selectedType) { lifespanOptionsFor(selectedType) }
    var selectedDays by remember(supply.id, selectedType) {
        mutableStateOf(
            if (supply.expectedLifespanDays in lifespanOptions) {
                supply.expectedLifespanDays
            } else {
                lifespanOptions.first()
            },
        )
    }
    var startedAt by remember(supply.id) { mutableLongStateOf(supply.startedAt) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Edit Supply",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(16.dp))
            SupplyFields(
                selectedType = selectedType,
                onTypeSelected = { selectedType = it },
                selectedDays = selectedDays,
                lifespanOptions = lifespanOptions,
                onDaysSelected = { selectedDays = it },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Started",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Formatters.shortDate(Formatters.localDate(startedAt)) +
                        " · ${Formatters.time(startedAt, use24HourTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showDatePicker = true }) {
                    Text("Change")
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSave(selectedType, startedAt, selectedDays) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startedAt.toUtcDateMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis ->
                            val selectedDate = Instant.ofEpochMilli(selectedDateMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            val currentTime = Instant.ofEpochMilli(startedAt)
                                .atZone(ZoneId.systemDefault())
                                .toLocalTime()
                            startedAt = selectedDate
                                .atTime(currentTime)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            showTimePicker = true
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val zoned = Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault())
        val timePickerState = rememberTimePickerState(
            initialHour = zoned.hour,
            initialMinute = zoned.minute,
            is24Hour = use24HourTime,
        )
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                val date = Instant.ofEpochMilli(startedAt)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                startedAt = date
                    .atTime(timePickerState.hour, timePickerState.minute)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                showTimePicker = false
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete supply?") },
            text = {
                Text(
                    "This removes the ${supply.type.label} started " +
                        Formatters.shortDate(Formatters.localDate(supply.startedAt)) +
                        ". This is for a supply logged by mistake, and can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select time") },
        text = { content() },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
    )
}

private fun Long.toUtcDateMillis(): Long {
    val localDate = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return localDate
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}

private fun lifespanOptionsFor(type: SupplyType): List<Int> = when (type) {
    SupplyType.SENSOR -> listOf(10, 14)
    SupplyType.INSULIN_VIAL -> listOf(28)
    SupplyType.PUMP_SITE -> listOf(3)
}

private fun remainingLabel(supply: ActiveSupplyUi): String = when {
    supply.daysRemaining >= 1.0 -> "${ceil(supply.daysRemaining).toInt()} days left"
    supply.hoursRemaining > 0.0 -> "${ceil(supply.hoursRemaining).toInt()} hours left"
    else -> "Expired"
}
