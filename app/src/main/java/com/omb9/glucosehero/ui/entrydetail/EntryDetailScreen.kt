package com.omb9.glucosehero.ui.entrydetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.ui.log.toLogEvent
import com.omb9.glucosehero.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    onDone: () -> Unit,
    viewModel: EntryDetailViewModel = hiltViewModel(),
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var glucoseInput by rememberSaveable { mutableStateOf("") }
    var noteInput by rememberSaveable { mutableStateOf("") }
    var insulinBasalInput by rememberSaveable { mutableStateOf("") }
    var insulinBolusInput by rememberSaveable { mutableStateOf("") }
    var carbsInput by rememberSaveable { mutableStateOf("") }
    var mealDescriptionInput by rememberSaveable { mutableStateOf("") }
    var exerciseInput by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(entry, settings.unit) {
        val e = entry
        if (e != null && !seeded) {
            glucoseInput = e.glucoseMgdl?.let { Formatters.glucose(it, settings.unit) } ?: ""
            noteInput = e.note.orEmpty()
            insulinBasalInput = e.insulinBasalUnits?.let { trimDouble(it) } ?: ""
            insulinBolusInput = e.insulinBolusUnits?.let { trimDouble(it) } ?: ""
            carbsInput = e.carbsGrams?.toString() ?: ""
            mealDescriptionInput = e.mealDescription.orEmpty()
            exerciseInput = e.exerciseMinutes?.toString() ?: ""
            seeded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Entry") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete entry")
                    }
                },
            )
        },
    ) { padding ->
        val e = entry
        if (e == null) {
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
                    "Entry",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    Formatters.dayHeader(Formatters.localDate(e.timestamp)) +
                        " · " +
                        Formatters.time(e.timestamp, settings.use24HourTime),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))

                // A row only renders when its metric was originally present, so
                // legacy single-metric events look unchanged while full multi-
                // metric events show every editable slot at once.
                if (e.glucoseMgdl != null) {
                    OutlinedTextField(
                        value = glucoseInput,
                        onValueChange = { glucoseInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        suffix = { Text(settings.unit.label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (e.insulinBasalUnits != null) {
                    OutlinedTextField(
                        value = insulinBasalInput,
                        onValueChange = { insulinBasalInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Basal (Long)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        suffix = { Text("u") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (e.insulinBolusUnits != null) {
                    OutlinedTextField(
                        value = insulinBolusInput,
                        onValueChange = { insulinBolusInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Bolus (Rapid)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        suffix = { Text("u") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (e.carbsGrams != null || e.mealDescription != null) {
                    if (e.carbsGrams != null) {
                        OutlinedTextField(
                            value = carbsInput,
                            onValueChange = { carbsInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            suffix = { Text("g") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (e.mealDescription != null) {
                        OutlinedTextField(
                            value = mealDescriptionInput,
                            onValueChange = { mealDescriptionInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Meal", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (e.exerciseMinutes != null) {
                    OutlinedTextField(
                        value = exerciseInput,
                        onValueChange = { exerciseInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        suffix = { Text("min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                    minLines = 3,
                )

                Spacer(Modifier.height(20.dp))

                // Seed a DraftEventState from the loaded event so `toLogEvent`
                // is the single validator here too — no second validator copy.
                val canSave = run {
                    val draft = viewModel.toDetailDraft(
                        e = e,
                        glucoseInput = glucoseInput,
                        insulinBasalInput = insulinBasalInput,
                        insulinBolusInput = insulinBolusInput,
                        carbsInput = carbsInput,
                        mealDescriptionInput = mealDescriptionInput,
                        exerciseInput = exerciseInput,
                        noteInput = noteInput,
                    )
                    draft.toLogEvent(settings, e.timestamp) != null
                }

                Button(
                    onClick = {
                        viewModel.save(
                            glucoseInput = glucoseInput,
                            insulinBasalInput = insulinBasalInput,
                            insulinBolusInput = insulinBolusInput,
                            carbsInput = carbsInput,
                            mealDescriptionInput = mealDescriptionInput,
                            exerciseInput = exerciseInput,
                            noteInput = noteInput,
                            onDone = onDone,
                        )
                    },
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
}

private fun trimDouble(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
