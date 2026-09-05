package com.omb9.glucosehero.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.omb9.glucosehero.domain.model.ActivityIntensity
import com.omb9.glucosehero.domain.model.EntryType
import com.omb9.glucosehero.domain.model.GlucoseUnit
import com.omb9.glucosehero.domain.model.MealContext
import com.omb9.glucosehero.domain.model.Metric
import com.omb9.glucosehero.ui.log.DraftEventState
import com.omb9.glucosehero.ui.log.FoodLookupState
import com.omb9.glucosehero.ui.log.LogViewModel
import com.omb9.glucosehero.ui.log.StreakReward
import com.omb9.glucosehero.ui.log.filledMetrics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.domain.model.FoodSource

private data class Category(
    val type: EntryType,
    val metric: Metric,
    val icon: ImageVector,
    val label: String,
)

private val categories = listOf(
    Category(EntryType.GLUCOSE, Metric.GLUCOSE, Icons.Filled.Bloodtype, "Glucose"),
    Category(EntryType.INSULIN, Metric.INSULIN, Icons.Filled.Vaccines, "Insulin"),
    Category(EntryType.MEAL, Metric.CARBS, Icons.Filled.Restaurant, "Meal"),
    Category(EntryType.ACTIVITY, Metric.EXERCISE, Icons.Filled.DirectionsRun, "Activity"),
    Category(EntryType.NOTE, Metric.NOTE, Icons.Filled.Notes, "Note"),
)

/**
 * Sub-5-second entry path (spec §4): a modal sheet with a category icon grid
 * whose primary input auto-focuses — FocusRequester fired from a
 * LaunchedEffect the instant the sheet composes/the category changes — with
 * the soft keyboard forced open, so the user can type a value and hit save
 * without a single extra tap.
 *
 * Stateless: all draft state lives in [DraftEventState] owned by
 * [com.omb9.glucosehero.ui.log.LogViewModel]; this composable only renders it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntrySheet(
    draft: DraftEventState,
    unit: GlucoseUnit,
    showAdvancedMacros: Boolean,
    canSave: Boolean,
    postMealReminderEnabled: Boolean,
    onPostMealReminderChange: (Boolean) -> Unit,
    onCategorySelected: (EntryType) -> Unit,
    onGlucoseChange: (String) -> Unit,
    onMealContextChange: (MealContext) -> Unit,
    onInsulinBasalChange: (String) -> Unit,
    onInsulinBolusChange: (String) -> Unit,
    onCarbsChange: (String) -> Unit,
    onProteinChange: (String) -> Unit,
    onFatChange: (String) -> Unit,
    onMealDescriptionChange: (String) -> Unit,
    onExerciseMinutesChange: (String) -> Unit,
    onExerciseIntensityChange: (ActivityIntensity) -> Unit,
    onNoteChange: (String) -> Unit,
    suggestedBolus: Double? = null,
    onUseSuggestion: () -> Unit = {},
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    streakReward: StreakReward? = null,
    onRewardConsumed: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    var showStreakConfirmation by remember { mutableStateOf(false) }

    val viewModel = hiltViewModel<LogViewModel>()
    val recentFoods by viewModel.recentFoods.collectAsStateWithLifecycle()
    val foodSearchQuery by viewModel.foodSearchQuery.collectAsStateWithLifecycle()
    val foodSearchResults by viewModel.foodSearchResults.collectAsStateWithLifecycle()
    val selectedFood by viewModel.selectedFood.collectAsStateWithLifecycle()
    val foodLookupState by viewModel.foodLookupState.collectAsStateWithLifecycle()

    val isLookingUp = foodLookupState is FoodLookupState.Loading
    val lookupMessage = when (val state = foodLookupState) {
        is FoodLookupState.ManualEntry -> "Barcode not found. Enter the meal details below."
        is FoodLookupState.Error -> state.message
        else -> null
    }
    val lookupIsError = foodLookupState is FoodLookupState.Error

    LaunchedEffect(streakReward) {
        streakReward ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        showStreakConfirmation = true
        delay(1600)
        showStreakConfirmation = false
        delay(400)
        onRewardConsumed()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text("New entry", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            // --- Category icon grid ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                categories.forEach { category ->
                    val selected = category.type == draft.activeCategory
                    val hasData = category.metric in draft.filledMetrics
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            IconButton(
                                onClick = { onCategorySelected(category.type) },
                                modifier = Modifier.size(56.dp),
                                colors = if (selected) {
                                    IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                            ) {
                                Icon(category.icon, contentDescription = category.label)
                            }
                            if (hasData) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                        Text(
                            category.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // --- Primary input per category ---
            when (draft.activeCategory) {
                EntryType.GLUCOSE -> {
                    OutlinedTextField(
                        value = draft.glucose,
                        onValueChange = onGlucoseChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        suffix = { Text(unit.label) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            MealContext.FASTING to "Fasting",
                            MealContext.BEFORE_MEAL to "Before",
                            MealContext.AFTER_MEAL to "After",
                            MealContext.BEDTIME to "Bedtime",
                        ).forEach { (context, label) ->
                            FilterChip(
                                selected = draft.mealContext == context,
                                onClick = {
                                    val next = if (draft.mealContext == context) MealContext.NONE else context
                                    onMealContextChange(next)
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                EntryType.INSULIN -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = draft.insulinBasal,
                            onValueChange = onInsulinBasalChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = {
                                Text("Basal (Long)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            suffix = { Text("u") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        OutlinedTextField(
                            value = draft.insulinBolus,
                            onValueChange = onInsulinBolusChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("Bolus (Rapid)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            suffix = { Text("u") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        if (suggestedBolus != null) {
                            SuggestedBolusRow(
                                suggestedBolus = suggestedBolus,
                                onUseSuggestion = onUseSuggestion,
                            )
                        }
                    }
                }

                EntryType.MEAL -> {
                    FoodPickerSection(
                        recentFoods = recentFoods,
                        searchQuery = foodSearchQuery,
                        onSearchQueryChange = viewModel::onFoodSearchQueryChange,
                        searchResults = foodSearchResults,
                        onFoodSelected = viewModel::onFoodSelected,
                        onBarcodeScanned = viewModel::onBarcodeScanned,
                        isLookingUp = isLookingUp,
                        lookupMessage = lookupMessage,
                        lookupIsError = lookupIsError,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draft.carbsGrams,
                        onValueChange = onCarbsChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        suffix = { Text("g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    AnimatedVisibility(
                        visible = showAdvancedMacros,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column {
                            OutlinedTextField(
                                value = draft.proteinGrams,
                                onValueChange = onProteinChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Protein (g)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                suffix = { Text("g") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = draft.fatGrams,
                                onValueChange = onFatChange,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Fat (g)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                suffix = { Text("g") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    OutlinedTextField(
                        value = draft.mealDescription,
                        onValueChange = onMealDescriptionChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("What did you eat?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (selectedFood == null &&
                        draft.carbsGrams.isNotBlank() &&
                        draft.mealDescription.isNotBlank()
                    ) {
                        TextButton(onClick = viewModel::saveAsFood) {
                            Text("Save this as a food")
                        }
                    }
                    if (selectedFood?.source == FoodSource.OPEN_FOOD_FACTS) {
                        OffAttribution(Modifier.padding(top = 4.dp))
                    }
                }

                EntryType.ACTIVITY -> {
                    OutlinedTextField(
                        value = draft.exerciseMinutes,
                        onValueChange = onExerciseMinutesChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = { Text("0", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        suffix = { Text("min") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ActivityIntensity.entries.forEachIndexed { index, level ->
                            SegmentedButton(
                                selected = draft.exerciseIntensity == level,
                                onClick = { onExerciseIntensityChange(level) },
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
                }

                EntryType.NOTE -> Unit // The quick-note field below is the input.
            }

            val showReminderToggle = draft.activeCategory == EntryType.MEAL ||
                (draft.activeCategory == EntryType.INSULIN && draft.insulinBolus.isNotBlank())

            if (showReminderToggle) {
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = postMealReminderEnabled,
                    onClick = { onPostMealReminderChange(!postMealReminderEnabled) },
                    label = { Text("+2hr Reminder") },
                )
            }

            Spacer(Modifier.height(12.dp))

            // --- Quick note (always present per spec) ---
            OutlinedTextField(
                value = draft.note,
                onValueChange = onNoteChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (draft.activeCategory == EntryType.NOTE) {
                            Modifier.focusRequester(focusRequester)
                        } else {
                            Modifier
                        }
                    ),
                label = { Text("Quick note") },
                minLines = if (draft.activeCategory == EntryType.NOTE) 3 else 1,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }

            Spacer(Modifier.height(12.dp))

            AnimatedVisibility(
                visible = showStreakConfirmation,
                enter = fadeIn(tween(200)) +
                    slideInVertically(tween(240)) { fullHeight -> fullHeight / 3 },
                exit = fadeOut(tween(320)) +
                    slideOutVertically(tween(320)) { fullHeight -> fullHeight / 3 },
            ) {
                StreakExtendedConfirmation(
                    currentStreak = streakReward?.currentStreak ?: 0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    (foodLookupState as? FoodLookupState.ConfirmOff)?.let { confirm ->
        FoodConfirmationDialog(
            initial = confirm.draft,
            onConfirm = viewModel::confirmOffFood,
            onDismiss = viewModel::dismissFoodLookup,
        )
    }

    LaunchedEffect(draft.activeCategory, streakReward) {
        if (streakReward != null) return@LaunchedEffect
        focusRequester.requestFocus()
        keyboard?.show()
    }
}



@Composable
private fun StreakExtendedConfirmation(
    currentStreak: Int,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent, CircleShape),
            )
            Text(
                text = "Streak Extended",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (currentStreak == 1) "1 day" else "$currentStreak days",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
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
