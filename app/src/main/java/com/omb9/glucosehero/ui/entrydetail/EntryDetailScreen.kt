package com.omb9.glucosehero.ui.entrydetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.EntryType
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.util.Formatters
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private val editableCategoryTypes = listOf(
    EntryType.GLUCOSE,
    EntryType.INSULIN,
    EntryType.MEAL,
    EntryType.ACTIVITY,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    onDone: () -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel(),
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val suggestedBolus by viewModel.suggestedBolus.collectAsStateWithLifecycle()
    val isHealthConnect = entry?.source == EntrySource.HEALTH_CONNECT
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.saveErrors.collect { error ->
            snackbarHostState.showSnackbar(error.message ?: "Save failed")
        }
    }

    val entryTitle = editableCategoryTypes
        .firstOrNull { it in form.activeCategories }
        ?.detailTitle()
        ?: EntryType.NOTE.detailTitle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var addCategoryExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entryTitle) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::onEditToggle, enabled = !isHealthConnect) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = if (form.isEditing) "Done editing" else "Edit entry",
                            tint = if (form.isEditing) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (form.loadFailed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "This entry no longer exists",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "It may have been deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (entry == null || !form.isSeeded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    entryTitle,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Formatters.dayHeader(Formatters.localDate(form.timestamp)) +
                            " · " +
                            Formatters.time(form.timestamp, settings.use24HourTime),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showDatePicker = true }, enabled = !isHealthConnect) {
                        Text("Change date & time")
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (form.isEditing) {
                    val available = editableCategoryTypes.filter { it !in form.activeCategories }
                    if (available.isNotEmpty()) {
                        Box {
                            OutlinedButton(onClick = { addCategoryExpanded = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add category")
                            }
                            DropdownMenu(
                                expanded = addCategoryExpanded,
                                onDismissRequest = { addCategoryExpanded = false },
                            ) {
                                available.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.detailTitle()) },
                                        leadingIcon = {
                                            Icon(type.icon(), contentDescription = null)
                                        },
                                        onClick = {
                                            addCategoryExpanded = false
                                            viewModel.onAddCategory(type)
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (EntryType.GLUCOSE in form.activeCategories) {
                    CategoryHeader(
                        type = EntryType.GLUCOSE,
                        isEditing = form.isEditing,
                        onRemove = { viewModel.onRemoveCategory(EntryType.GLUCOSE) },
                    )
                    OutlinedTextField(
                        value = form.glucose,
                        onValueChange = viewModel::onGlucoseChange,
                        enabled = !isHealthConnect,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        suffix = { Text(settings.unit.label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            MealContext.FASTING to "Fasting",
                            MealContext.BEFORE_MEAL to "Before",
                            MealContext.AFTER_MEAL to "After",
                            MealContext.BEDTIME to "Bedtime",
                        ).forEach { (context, label) ->
                            FilterChip(
                                selected = form.mealContext == context,
                                enabled = !isHealthConnect,
                                onClick = {
                                    val next = if (form.mealContext == context) {
                                        MealContext.NONE
                                    } else {
                                        context
                                    }
                                    viewModel.onMealContextChange(next)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (EntryType.INSULIN in form.activeCategories) {
                    CategoryHeader(
                        type = EntryType.INSULIN,
                        isEditing = form.isEditing,
                        onRemove = { viewModel.onRemoveCategory(EntryType.INSULIN) },
                    )
                    OutlinedTextField(
                        value = form.insulinBasal,
                        onValueChange = viewModel::onInsulinBasalChange,
                        enabled = !isHealthConnect,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Basal (Long)") },
                        suffix = { Text("u") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = form.insulinBolus,
                        onValueChange = viewModel::onInsulinBolusChange,
                        enabled = !isHealthConnect,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Bolus (Rapid)") },
                        suffix = { Text("u") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    suggestedBolus?.let { smartBolus ->
                        SuggestedBolusRow(
                            suggestedBolus = smartBolus,
                            onUseSuggestion = viewModel::useSuggestedBolus,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (EntryType.MEAL in form.activeCategories) {
                    CategoryHeader(
                        type = EntryType.MEAL,
                        isEditing = form.isEditing,
                        onRemove = { viewModel.onRemoveCategory(EntryType.MEAL) },
                    )
                    OutlinedTextField(
                        value = form.carbs,
                        onValueChange = viewModel::onCarbsChange,
                        enabled = !isHealthConnect,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Carbs") },
                        suffix = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (settings.showAdvancedMacros || form.protein.isNotBlank() || form.fat.isNotBlank()) {
                        OutlinedTextField(
                            value = form.protein,
                            onValueChange = viewModel::onProteinChange,
                        enabled = !isHealthConnect,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Protein") },
                            suffix = { Text("g") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = form.fat,
                            onValueChange = viewModel::onFatChange,
                        enabled = !isHealthConnect,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Fat") },
                            suffix = { Text("g") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = form.mealDescription,
                        onValueChange = viewModel::onMealDescriptionChange,
                        enabled = !isHealthConnect,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Meal") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (EntryType.ACTIVITY in form.activeCategories) {
                    CategoryHeader(
                        type = EntryType.ACTIVITY,
                        isEditing = form.isEditing,
                        onRemove = { viewModel.onRemoveCategory(EntryType.ACTIVITY) },
                    )
                    OutlinedTextField(
                        value = form.exerciseMinutes,
                        onValueChange = viewModel::onExerciseMinutesChange,
                        enabled = !isHealthConnect,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Minutes") },
                        suffix = { Text("min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ActivityIntensity.entries.forEachIndexed { index, level ->
                            SegmentedButton(
                                selected = form.exerciseIntensity == level,
                                onClick = { viewModel.onExerciseIntensityChange(level) },
                                enabled = !isHealthConnect,
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ActivityIntensity.entries.size,
                                ),
                            ) {
                                Text(
                                    level.name.lowercase().replaceFirstChar { it.uppercase() }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = form.note,
                    onValueChange = viewModel::onNoteChange,
                    enabled = !isHealthConnect,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                    minLines = 3,
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.save(onDone) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave,
                ) {
                    Text("Save changes")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete entry?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(onDone)
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

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = form.timestamp.toUtcDateMillis(),
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
                            val currentTime = Instant.ofEpochMilli(form.timestamp)
                                .atZone(ZoneId.systemDefault())
                                .toLocalTime()
                            viewModel.onTimestampChange(
                                selectedDate
                                    .atTime(currentTime)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                            )
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
        val zoned = Instant.ofEpochMilli(form.timestamp).atZone(ZoneId.systemDefault())
        val timePickerState = rememberTimePickerState(
            initialHour = zoned.hour,
            initialMinute = zoned.minute,
            is24Hour = settings.use24HourTime,
        )
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = {
                val date = Instant.ofEpochMilli(form.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                viewModel.onTimestampChange(
                    date
                        .atTime(timePickerState.hour, timePickerState.minute)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                )
                showTimePicker = false
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@Composable
private fun CategoryHeader(
    type: EntryType,
    isEditing: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            type.icon(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            type.detailTitle(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (isEditing) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove ${type.detailTitle()}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
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

private fun EntryType.icon(): ImageVector = when (this) {
    EntryType.GLUCOSE -> Icons.Filled.Bloodtype
    EntryType.INSULIN -> Icons.Filled.Vaccines
    EntryType.MEAL -> Icons.Filled.Restaurant
    EntryType.ACTIVITY -> Icons.AutoMirrored.Filled.DirectionsRun
    EntryType.NOTE -> Icons.AutoMirrored.Filled.Notes
}

private fun EntryType.detailTitle(): String = when (this) {
    EntryType.GLUCOSE -> "Blood Glucose"
    EntryType.INSULIN -> "Insulin"
    EntryType.MEAL -> "Meal"
    EntryType.ACTIVITY -> "Exercise"
    EntryType.NOTE -> "Note"
}

@Composable
private fun SuggestedBolusRow(
    suggestedBolus: Double,
    onUseSuggestion: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Suggested bolus: ${formatInsulinUnits(suggestedBolus)} u",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUseSuggestion) {
            Text("Use suggestion")
        }
    }
}

private fun formatInsulinUnits(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
