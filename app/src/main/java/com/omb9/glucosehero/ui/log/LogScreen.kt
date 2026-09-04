package com.omb9.glucosehero.ui.log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.EntryType
import com.omb9.glucosehero.ui.components.AddEntrySheet
import com.omb9.glucosehero.ui.theme.GlucoseHigh
import com.omb9.glucosehero.ui.theme.GlucoseInRange
import com.omb9.glucosehero.ui.theme.GlucoseLow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onEntryClick: (Long) -> Unit,
    addGlucoseTick: Int = 0,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val activeInsulin by viewModel.activeInsulin.collectAsStateWithLifecycle()
    val suggestedBolus by viewModel.suggestedBolus.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val streakReward by viewModel.streakReward.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.saveErrors.collect { error ->
            snackbarHostState.showSnackbar(error.message ?: "Save failed")
        }
    }

    val pendingPrefill by viewModel.pendingHeroAiPrefill.collectAsStateWithLifecycle()
    LaunchedEffect(pendingPrefill) {
        val prefill = pendingPrefill ?: return@LaunchedEffect
        viewModel.applyHeroAiPrefill(prefill)
        viewModel.consumeHeroAiPrefill()
        showSheet = true
    }

    LaunchedEffect(addGlucoseTick) {
        if (addGlucoseTick > 0) {
            viewModel.openNewDraft(settings.postMealRemindersEnabled)
            showSheet = true
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Log") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.openNewDraft(settings.postMealRemindersEnabled)
                    showSheet = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add entry")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ActiveInsulinBar(activeInsulinUnits = activeInsulin)

            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                state.days.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_logo_display),
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        alpha = 0.15f,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                state.days.forEach { day ->
                    item(key = "day-${day.epochDay}") {
                        Text(
                            day.header,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                        )
                    }
                    items(day.entries, key = { it.id }) { entry ->
                        EntryRow(
                            item = entry,
                            unitLabel = state.unit.label,
                            onClick = { onEntryClick(entry.id) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    if (showSheet) {
        AddEntrySheet(
            draft = draft,
            unit = settings.unit,
            showAdvancedMacros = settings.showAdvancedMacros,
            canSave = canSave,
            postMealReminderEnabled = draft.postMealReminderEnabled,
            onPostMealReminderChange = viewModel::onPostMealReminderChange,
            onCategorySelected = viewModel::onCategorySelected,
            onGlucoseChange = viewModel::onGlucoseChange,
            onMealContextChange = viewModel::onMealContextChange,
            onInsulinBasalChange = viewModel::onInsulinBasalChange,
            onInsulinBolusChange = viewModel::onInsulinBolusChange,
            onCarbsChange = viewModel::onCarbsChange,
            onProteinChange = viewModel::onProteinChange,
            onFatChange = viewModel::onFatChange,
            onMealDescriptionChange = viewModel::onMealDescriptionChange,
            onExerciseMinutesChange = viewModel::onExerciseMinutesChange,
            onExerciseIntensityChange = viewModel::onExerciseIntensityChange,
            onNoteChange = viewModel::onNoteChange,
            suggestedBolus = suggestedBolus,
            onUseSuggestion = viewModel::useSuggestedBolus,
            onSave = { viewModel.saveDraft { showSheet = false } },
            onDismiss = {
                viewModel.discardDraft()
                viewModel.consumeStreakReward()
                showSheet = false
            },
            streakReward = streakReward,
            onRewardConsumed = {
                viewModel.consumeStreakReward()
                showSheet = false
            },
        )
    }
}

private fun EntryType.icon(): ImageVector = when (this) {
    EntryType.GLUCOSE -> Icons.Filled.Bloodtype
    EntryType.INSULIN -> Icons.Filled.Vaccines
    EntryType.MEAL -> Icons.Filled.Restaurant
    EntryType.ACTIVITY -> Icons.Filled.DirectionsRun
    EntryType.NOTE -> Icons.Filled.Notes
}

/**
 * Prominent, glanceable insulin-on-board readout pinned above the log list.
 * Uses the clinical true-black/true-white palette with the accent for the
 * value, so it stays legible without competing with the entries below.
 */
@Composable
private fun ActiveInsulinBar(activeInsulinUnits: Double) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Active Insulin",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatInsulinUnits(activeInsulinUnits),
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "U on board",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatInsulinUnits(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

@Composable
private fun EntryRow(
    item: LogEntryItem,
    unitLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        item.type.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                val subtitle = item.subtitle
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val glucose = item.glucoseDisplay
                if (glucose != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            glucose,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = when (item.glucoseStatus) {
                                GlucoseStatus.LOW -> GlucoseLow
                                GlucoseStatus.HIGH -> GlucoseHigh
                                GlucoseStatus.IN_RANGE -> GlucoseInRange
                                null -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            unitLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
                Text(
                    item.timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
