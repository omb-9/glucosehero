package com.omb9.glucosehero.ui.log

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sick
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
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
import com.omb9.glucosehero.ui.LocalOnLogFirstContentReady
import com.omb9.glucosehero.ui.cgm.compactText
import com.omb9.glucosehero.ui.components.AddEntrySheet
import com.omb9.glucosehero.ui.components.GlucoseHeroCard
import com.omb9.glucosehero.ui.components.GlucoseHeroRefreshIndicator
import com.omb9.glucosehero.ui.components.shimmer
import com.omb9.glucosehero.ui.theme.Spacing
import com.omb9.glucosehero.ui.theme.GlucoseHigh
import com.omb9.glucosehero.ui.theme.GlucoseInRange
import com.omb9.glucosehero.ui.theme.GlucoseLow
import com.omb9.glucosehero.util.FirstDrawProbe
import com.omb9.glucosehero.util.probeFirstDraw

/** Shared slide-and-fade for the banners and filters stacked above the log list. */
private val LogHeaderEnter = expandVertically() + fadeIn()
private val LogHeaderExit = shrinkVertically() + fadeOut()

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
    val pagingScope = rememberCoroutineScope()
    val pagedLogItems = remember(viewModel, pagingScope) {
        viewModel.pagedLogItems(pagingScope)
    }.collectAsLazyPagingItems()
    val searchItems by viewModel.searchItems.collectAsStateWithLifecycle()
    val dayItems by viewModel.dayItems.collectAsStateWithLifecycle()
    val settingsState by viewModel.settings.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val canSave by viewModel.canSave.collectAsStateWithLifecycle()
    val activeInsulin by viewModel.activeInsulin.collectAsStateWithLifecycle()
    val glucoseForecast by viewModel.glucoseForecast.collectAsStateWithLifecycle()
    val glucoseFreshness by viewModel.glucoseFreshness.collectAsStateWithLifecycle()
    val pendingHypoSos by viewModel.pendingHypoSos.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val streakReward by viewModel.streakReward.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val showRefreshCaption by viewModel.showRefreshCaption.collectAsStateWithLifecycle()
    val isHealthConnectRevoked by viewModel.isHealthConnectRevoked.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val pullToRefreshState = rememberPullToRefreshState()
    val logListState = rememberLazyListState()
    val searchFocusRequester = remember { FocusRequester() }
    val searchKeyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val noSyncSourceMessage = stringResource(R.string.refresh_no_sync_source)
    LaunchedEffect(viewModel) {
        viewModel.saveErrors.collect { error ->
            snackbarHostState.showSnackbar(error.message ?: "Save failed")
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.noSyncSourceMessages.collect {
            snackbarHostState.showSnackbar(noSyncSourceMessage)
        }
    }
    // New entries are prepended, so a save from further down the log would
    // otherwise land above the viewport with nothing to show for it.
    LaunchedEffect(viewModel) {
        viewModel.savedEntries.collect {
            if (logListState.layoutInfo.totalItemsCount > 0) {
                logListState.animateScrollToItem(0)
            }
        }
    }

    // AnimatedVisibility keeps composing its content while it shrinks away, so
    // the chip and banner below need the last non-null value to render with on
    // the way out.
    var lastFilterDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(selectedDate) { selectedDate?.let { lastFilterDate = it } }
    var lastHypoSos by remember { mutableStateOf<HypoSosPending?>(null) }
    LaunchedEffect(pendingHypoSos) { pendingHypoSos?.let { lastHypoSos = it } }

    val settings = settingsState

    // [settings] gates every surface below. It must be resolved BEFORE the prefill
    // effect: applyHeroAiPrefill bails out on a null settings.value (LogViewModel),
    // yet the caller still consumes the prefill and opens the sheet — so wiring it
    // during the loading window silently drops the prefill and opens an empty
    // sheet. Wiring it only once settings are non-null closes that gap.
    if (settings != null) {
        val pendingPrefill by viewModel.pendingHeroAiPrefill.collectAsStateWithLifecycle()
        LaunchedEffect(pendingPrefill) {
            val prefill = pendingPrefill ?: return@LaunchedEffect
            viewModel.applyHeroAiPrefill(prefill)
            viewModel.consumeHeroAiPrefill()
            showSheet = true
        }
    }

    LaunchedEffect(addGlucoseTick) {
        if (addGlucoseTick > 0) {
            viewModel.openNewDraft(settings?.postMealRemindersEnabled ?: true)
            showSheet = true
        }
    }

    LaunchedEffect(addMealTick) {
        if (addMealTick > 0) {
            viewModel.openNewDraft(settings?.postMealRemindersEnabled ?: true)
            viewModel.onCategorySelected(EntryType.MEAL)
            showSheet = true
        }
    }

    LaunchedEffect(addBolusTick) {
        if (addBolusTick > 0) {
            viewModel.openNewDraft(settings?.postMealRemindersEnabled ?: true)
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
                            if (!searchExpanded) {
                                searchKeyboard?.hide()
                                viewModel.clearSearch()
                            }
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
                    viewModel.openNewDraft(settings?.postMealRemindersEnabled ?: true)
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
                AnimatedVisibility(
                    visible = isHealthConnectRevoked,
                    enter = LogHeaderEnter,
                    exit = LogHeaderExit,
                ) {
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
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Health Connect sync disabled, permissions revoked in settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
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

                AnimatedVisibility(
                    visible = searchExpanded,
                    enter = LogHeaderEnter,
                    exit = LogHeaderExit,
                ) {
                    LaunchedEffect(Unit) {
                        searchFocusRequester.requestFocus()
                        searchKeyboard?.show()
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .focusRequester(searchFocusRequester),
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

                AnimatedVisibility(
                    visible = selectedDate != null,
                    enter = LogHeaderEnter,
                    exit = LogHeaderExit,
                ) {
                    lastFilterDate?.let { date ->
                        DateFilterChip(
                            date = date,
                            onClear = viewModel::clearDateFilter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = showRefreshCaption,
                    enter = LogHeaderEnter,
                    exit = LogHeaderExit,
                ) {
                    val age = glucoseFreshness?.compactText(context)
                        ?: stringResource(R.string.freshness_compact_no_data)
                    Text(
                        text = stringResource(R.string.refresh_last_updated, age),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                AnimatedVisibility(
                    visible = activeInsulin > 0.0,
                    enter = LogHeaderEnter,
                    exit = LogHeaderExit,
                ) {
                    ActiveInsulinBar(activeInsulinUnits = activeInsulin)
                }

                AnimatedVisibility(
                    visible = pendingHypoSos != null,
                    enter = LogHeaderEnter,
                    exit = LogHeaderExit,
                ) {
                    lastHypoSos?.let { sos ->
                        HypoSosBanner(
                            pending = sos,
                            unit = settings?.unit ?: GlucoseUnit.MGDL,
                            onDismiss = viewModel::dismissHypoSos,
                        )
                    }
                }

                // Forecast + list read the display unit, so they are gated on loaded
                // settings. The top bar, FAB and snackbar stay composed throughout.
                if (settings != null) {
                    LogForecastSlot(
                        snapshot = glucoseForecast,
                        unit = settings.unit,
                        freshness = glucoseFreshness,
                        use24HourTime = settings.use24HourTime,
                        onOpenDosingProfile = onOpenDosingProfile,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                val hasSearch = searchQuery.isNotBlank()
                val hasDate = selectedDate != null
                val pagedRefreshLoading =
                    !hasSearch && !hasDate && pagedLogItems.itemCount == 0 &&
                        (pagedLogItems.loadState.refresh is LoadState.Loading)
                val pagedEmpty =
                    !hasSearch && !hasDate && pagedLogItems.itemCount == 0 &&
                        (pagedLogItems.loadState.refresh !is LoadState.Loading)
                val staticItems = when {
                    hasSearch -> searchItems
                    hasDate -> dayItems
                    else -> emptyList()
                }
                val staticEmpty = (hasSearch || hasDate) && staticItems.isEmpty()
                val onLogFirstContentReady = LocalOnLogFirstContentReady.current
                val logFirstContentReady = settings != null && !pagedRefreshLoading
                SideEffect {
                    if (logFirstContentReady) onLogFirstContentReady()
                }

                if (settings == null || pagedRefreshLoading) {
                    // Settings and the first paging page both use the same skeleton
                    // so the two loads do not flash a spinner after the placeholders.
                    LogListPlaceholder()
                } else {
                    when {
                        pagedEmpty || staticEmpty -> {
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
                                    LogEmptyState(
                                        modifier = Modifier.probeFirstDraw(
                                            "empty_state",
                                            "FIRST_EMPTY_STATE_DRAW",
                                        ),
                                    )
                                }
                            }
                        }

                        else -> LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .probeFirstDraw(
                                    "real_log_list",
                                    "FIRST_REAL_LOG_LIST_DRAW",
                                ),
                            state = logListState,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 72.dp,
                            ),
                        ) {
                            when {
                                hasSearch -> items(
                                    items = searchItems,
                                    key = ::logListItemKey,
                                    contentType = ::logListItemContentType,
                                ) { item ->
                                    LogListItemRow(
                                        item = item,
                                        unitLabel = settings.unit.label,
                                        onEntryClick = onEntryClick,
                                        modifier = Modifier.animateItem(),
                                    )
                                }

                                hasDate -> items(
                                    items = dayItems,
                                    key = ::logListItemKey,
                                    contentType = ::logListItemContentType,
                                ) { item ->
                                    LogListItemRow(
                                        item = item,
                                        unitLabel = settings.unit.label,
                                        onEntryClick = onEntryClick,
                                        modifier = Modifier.animateItem(),
                                    )
                                }

                                else -> items(
                                    count = pagedLogItems.itemCount,
                                    key = pagedLogItems.itemKey(::logListItemKey),
                                    contentType = pagedLogItems.itemContentType(::logListItemContentType),
                                ) { index ->
                                    pagedLogItems[index]?.let {
                                        LogListItemRow(
                                            item = it,
                                            unitLabel = settings.unit.label,
                                            onEntryClick = onEntryClick,
                                            modifier = Modifier.animateItem(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSheet && settings != null) {
        AddEntrySheet(
            draft = draft,
            unit = settings.unit,
            use24HourTime = settings.use24HourTime,
            showAdvancedMacros = settings.showAdvancedMacros,
            sendMealPhotosToHeroAi = settings.sendMealPhotosToHeroAi,
            canSave = canSave,
            postMealReminderEnabled = draft.postMealReminderEnabled,
            onPostMealReminderChange = viewModel::onPostMealReminderChange,
            onOccurredAtChange = viewModel::setOccurredAt,
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
            onMedicationNameChange = viewModel::onMedicationNameChange,
            onMedicationDoseChange = viewModel::onMedicationDoseChange,
            onFeelingSickChange = viewModel::setFeelingSick,
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

/**
 * A skeleton log row sized to match [EntryRow] (40.dp leading avatar, 12.dp
 * row padding) so the loading → loaded swap does not jump.
 */
@Composable
private fun LogRowPlaceholder() {
        GlucoseHeroCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .shimmer(),
                )
                Spacer(Modifier.width(Spacing.md))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(16.dp)
                        .clip(MaterialTheme.shapes.small)
                        .shimmer(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(MaterialTheme.shapes.small)
                        .shimmer(),
                )
            }
        }
    }
}

/** Placeholder list shown while settings — and therefore the display unit — load. */
@Composable
private fun LogListPlaceholder(modifier: Modifier = Modifier) {
    DisposableEffect(Unit) {
        FirstDrawProbe.logOnce("placeholder_compose", "PLACEHOLDER_COMPOSE")
        onDispose {
            FirstDrawProbe.logOnce("placeholder_dispose", "PLACEHOLDER_DISPOSE")
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .probeFirstDraw("placeholder_draw", "PLACEHOLDER_FIRST_DRAW")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(6) { LogRowPlaceholder() }
    }
}

private fun EntryType.icon(): ImageVector = when (this) {
    EntryType.GLUCOSE -> Icons.Filled.Bloodtype
    EntryType.INSULIN -> Icons.Filled.Vaccines
    EntryType.MEAL -> Icons.Filled.Restaurant
    EntryType.MEDICATION -> Icons.Filled.Medication
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
    GlucoseHeroCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        contentPadding = PaddingValues(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Active Insulin",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatInsulinUnits(activeInsulinUnits),
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Text(
                "U on board",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatInsulinUnits(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

@Composable
private fun HypoSosBanner(
    pending: HypoSosPending,
    unit: GlucoseUnit,
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
                    Formatters.glucoseWithUnit(pending.glucoseMgdl, unit),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun logListItemKey(item: LogListItem): Any = when (item) {
    is LogListItem.Header -> item.date
    is LogListItem.Entry -> item.item.id
}

private fun logListItemContentType(item: LogListItem): Any = when (item) {
    is LogListItem.Header -> "header"
    is LogListItem.Entry -> when (item.item.type) {
        EntryType.GLUCOSE -> "entry-glucose"
        EntryType.INSULIN -> "entry-insulin"
        EntryType.MEAL -> "entry-meal"
        EntryType.MEDICATION -> "entry-medication"
        EntryType.ACTIVITY -> "entry-activity"
        EntryType.NOTE -> "entry-note"
    }
}

@Composable
private fun LogListItemRow(
    item: LogListItem,
    unitLabel: String,
    onEntryClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is LogListItem.Header -> Text(
            item.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Row gap as padding rather than a trailing Spacer, so each item is a
        // single node and animateItem() moves the gap along with the row.
        is LogListItem.Entry -> EntryRow(
            item = item.item,
            unitLabel = unitLabel,
            onClick = { onEntryClick(item.item.id) },
            modifier = modifier
                .padding(bottom = 8.dp)
                .probeFirstDraw(
                    "real_entry_row",
                    "FIRST_REAL_ENTRY_ROW_DRAW",
                ),
        )
    }
}

@Composable
private fun EntryRow(
    item: LogEntryItem,
    unitLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlucoseHeroCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        item.type.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.feelingSick) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.Sick,
                            contentDescription = "Feeling sick",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (item.source == EntrySource.HEALTH_CONNECT) {
                    Spacer(Modifier.height(4.dp))
                    HealthConnectBadge()
                }
                val subtitle = item.subtitle
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    item.timeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
