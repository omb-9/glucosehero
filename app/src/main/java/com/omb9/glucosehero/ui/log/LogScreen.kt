package com.omb9.glucosehero.ui.log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.util.Formatters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.omb9.glucosehero.domain.model.EntrySource
import com.omb9.glucosehero.domain.model.EntryType
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.crisis.HypoSosPending
import com.omb9.glucosehero.data.cgm.GlucoseFreshness
import com.omb9.glucosehero.forecast.GlucoseForecastCard
import com.omb9.glucosehero.forecast.GlucoseForecastSnapshot
import com.omb9.glucosehero.ui.components.AddEntrySheet
import com.omb9.glucosehero.ui.components.GlucoseHeroRefreshIndicator
import com.omb9.glucosehero.ui.theme.GlucoseHigh
import com.omb9.glucosehero.ui.theme.GlucoseInRange
import com.omb9.glucosehero.ui.theme.GlucoseLow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onEntryClick: (Long) -> Unit,
    onManageFoods: () -> Unit = {},
    onOpenDosingProfile: () -> Unit = {},
    addGlucoseTick: Int = 0,
    addMealTick: Int = 0,
    addBolusTick: Int = 0,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val activeInsulin by viewModel.activeInsulin.collectAsStateWithLifecycle()
    val glucoseForecast by viewModel.glucoseForecast.collectAsStateWithLifecycle()
    val glucoseFreshness by viewModel.glucoseFreshness.collectAsStateWithLifecycle()
    val pendingHypoSos by viewModel.pendingHypoSos.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val streakReward by viewModel.streakReward.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isHealthConnectRevoked by viewModel.isHealthConnectRevoked.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()
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

    LaunchedEffect(addMealTick) {
        if (addMealTick > 0) {
            viewModel.openNewDraft(settings.postMealRemindersEnabled)
            viewModel.onCategorySelected(EntryType.MEAL)
            showSheet = true
        }
    }

    LaunchedEffect(addBolusTick) {
        if (addBolusTick > 0) {
            viewModel.openNewDraft(settings.postMealRemindersEnabled)
            viewModel.onCategorySelected(EntryType.INSULIN)
            showSheet = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log", style = MaterialTheme.typography.headlineMedium) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                actions = {
                    IconButton(
                        onClick = {
                            searchExpanded = !searchExpanded
                            if (!searchExpanded) viewModel.clearSearch()
                        },
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = if (searchExpanded) "Close search" else "Search entries",
                        )
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "Filter by date")
                    }
                },
            )
        },
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            indicator = {
                GlucoseHeroRefreshIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isHealthConnectRevoked) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Health Connect sync disabled — permissions revoked in settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            IconButton(
                                onClick = viewModel::dismissHealthConnectRevokedBanner,
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss banner",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                if (searchExpanded) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search entries") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = viewModel::clearSearch) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                    )
                }

                selectedDate?.let { date ->
                    DateFilterChip(
                        date = date,
                        onClear = viewModel::clearDateFilter,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                ActiveInsulinBar(activeInsulinUnits = activeInsulin)

                pendingHypoSos?.let { sos ->
                    HypoSosBanner(
                        pending = sos,
                        onDismiss = viewModel::dismissHypoSos,
                    )
                }

                val forecastUnit by remember { derivedStateOf { settings.unit } }
                LogForecastSlot(
                    snapshot = glucoseForecast,
                    unit = forecastUnit,
                    freshness = glucoseFreshness,
                    use24HourTime = settings.use24HourTime,
                    onOpenDosingProfile = onOpenDosingProfile,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                when {
                    state.isLoading -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    state.days.isEmpty() -> {
                        val hasSearch = searchQuery.isNotBlank()
                        val hasDate = selectedDate != null
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (hasSearch || hasDate) {
                                LogFilteredEmptyState(
                                    searchQuery = searchQuery,
                                    selectedDate = selectedDate,
                                    onClear = {
                                        if (hasSearch) viewModel.clearSearch()
                                        if (hasDate) viewModel.clearDateFilter()
                                    },
                                )
                            } else {
                                LogEmptyState()
                            }
                        }
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
    }

    if (showSheet) {
        AddEntrySheet(
            draft = draft,
            unit = settings.unit,
            showAdvancedMacros = settings.showAdvancedMacros,
            sendMealPhotosToHeroAi = settings.sendMealPhotosToHeroAi,
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
            onMoodScoreChange = viewModel::onMoodScoreChange,
            onMoodLabelChange = viewModel::onMoodLabelChange,
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
            onManageFoods = onManageFoods,
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            viewModel.onDateSelected(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("OK")
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
}

private fun EntryType.icon(): ImageVector = when (this) {
    EntryType.GLUCOSE -> Icons.Filled.Bloodtype
    EntryType.INSULIN -> Icons.Filled.Vaccines
    EntryType.MEAL -> Icons.Filled.Restaurant
    EntryType.ACTIVITY -> Icons.Filled.DirectionsRun
    EntryType.NOTE -> Icons.Filled.Notes
}

@Composable
private fun LogForecastSlot(
    snapshot: GlucoseForecastSnapshot?,
    unit: GlucoseUnit,
    freshness: GlucoseFreshness?,
    use24HourTime: Boolean,
    onOpenDosingProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlucoseForecastCard(
        snapshot = snapshot,
        unit = unit,
        freshness = freshness,
        use24HourTime = use24HourTime,
        onOpenDosingProfile = onOpenDosingProfile,
        modifier = modifier,
    )
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
private fun HypoSosBanner(
    pending: HypoSosPending,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.hypo_sos_banner_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.hypo_sos_prompt_body,
                    pending.glucoseMgdl.toInt(),
                    pending.trendLabel,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.hypo_sos_banner_action))
            }
        }
    }
}

/**
 * Compact filter affordance showing the active single-day filter with a
 * one-tap clear action.
 */
@Composable
private fun DateFilterChip(
    date: LocalDate,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                Formatters.dayHeader(date),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Clear date filter",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun HealthConnectBadge() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            "Health Connect",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    if (item.source == EntrySource.HEALTH_CONNECT) {
                        Spacer(Modifier.width(6.dp))
                        HealthConnectBadge()
                    }
                }
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
