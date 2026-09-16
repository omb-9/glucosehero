package com.omb9.glucosehero.ui.insights

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.util.TagImpactCopy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodImpactScreen(
    onBack: () -> Unit,
    viewModel: FoodImpactViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var shareTarget by remember { mutableStateOf<TagImpactUi?>(null) }
    var showDismissed by rememberSaveable { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Food impact") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            // A lazy list rather than a scrolling Column so dismissing or
            // restoring a tag animates the remaining cards into place.
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 24.dp,
                ),
            ) {
                item(key = "intro") {
                    Column {
                        Text(
                            text = "Median glucose change two hours after tagged meals.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Bars read left (lower) and right (higher) from zero.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        FoodSwapsSection(swaps = state.swaps)
                        Spacer(Modifier.height(16.dp))
                    }
                }

                if (state.tags.isEmpty()) {
                    item(key = "tags-empty") {
                        EmptyBuildingState(closest = state.closestBuilding)
                    }
                } else {
                    items(state.tags, key = { it.tag }) { tag ->
                        TagImpactCard(
                            item = tag,
                            maxAbsDelta = state.maxAbsDeltaMgdl,
                            onShare = { shareTarget = tag },
                            onDismiss = { viewModel.dismiss(tag.tag) },
                            modifier = Modifier
                                .animateItem()
                                .padding(bottom = 12.dp),
                        )
                    }
                }

                if (state.building.isNotEmpty() && state.tags.isNotEmpty()) {
                    item(key = "building") {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            StillBuildingSection(building = state.building)
                        }
                    }
                }

                if (state.moods.isNotEmpty()) {
                    item(key = "moods") {
                        Column {
                            Spacer(Modifier.height(24.dp))
                            MoodImpactSection(moods = state.moods)
                        }
                    }
                }

                if (state.lifestyle.isNotEmpty()) {
                    item(key = "lifestyle") {
                        Column {
                            Spacer(Modifier.height(24.dp))
                            LifestyleImpactSection(items = state.lifestyle)
                        }
                    }
                }

                if (state.dismissed.isNotEmpty()) {
                    item(key = "dismissed") {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            DismissedSection(
                                dismissed = state.dismissed,
                                show = showDismissed,
                                onToggle = { showDismissed = !showDismissed },
                                onRestore = viewModel::restore,
                            )
                        }
                    }
                }
            }
        }

        TagImpactShareCapture(
            item = shareTarget,
            maxAbsDelta = state.maxAbsDeltaMgdl,
            onFinished = { shareTarget = null },
            modifier = Modifier.align(Alignment.TopStart),
        )
    }
}

@Composable
private fun EmptyBuildingState(closest: BuildingTagUi?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = TagImpactCopy.emptyFoodImpact(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (closest != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = TagImpactCopy.buildingProgress(
                        tag = closest.tag,
                        occurrences = closest.occurrences,
                        needed = closest.neededOccurrences,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StillBuildingSection(building: List<BuildingTagUi>) {
    Column {
        Text(
            text = "Still building",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                building.forEach { tag ->
                    Text(
                        text = TagImpactCopy.buildingProgress(
                            tag = tag.tag,
                            occurrences = tag.occurrences,
                            needed = tag.neededOccurrences,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DismissedSection(
    dismissed: List<TagImpactUi>,
    show: Boolean,
    onToggle: () -> Unit,
    onRestore: (String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Dismissed",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onToggle) {
                Text(if (show) "Hide" else "Show")
            }
        }
        if (show) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dismissed.forEach { tag ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = tag.tag,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onRestore(tag.tag) }) {
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }
    }
}
