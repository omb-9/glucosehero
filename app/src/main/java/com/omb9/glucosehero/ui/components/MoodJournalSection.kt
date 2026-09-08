package com.omb9.glucosehero.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.domain.model.MoodLabel

private val moodFaces: List<ImageVector> = listOf(
    Icons.Filled.SentimentVeryDissatisfied,
    Icons.Filled.SentimentDissatisfied,
    Icons.Filled.SentimentNeutral,
    Icons.Filled.SentimentSatisfied,
    Icons.Filled.SentimentVerySatisfied,
)

/**
 * Mood + journal section shared by the new-entry sheet and the entry-detail
 * screen. The journal field reads and writes the existing `note` column; the
 * two mood fields are the only new storage.
 */
@Composable
fun MoodJournalSection(
    moodScore: Int?,
    moodLabel: String?,
    journalText: String,
    onMoodScoreChange: (Int?) -> Unit,
    onMoodLabelChange: (String?) -> Unit,
    onJournalChange: (String) -> Unit,
    enabled: Boolean = true,
    journalFocusRequester: FocusRequester? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Mood",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            moodFaces.forEachIndexed { index, icon ->
                val score = index + 1
                val selected = moodScore == score
                IconButton(
                    onClick = { onMoodScoreChange(if (selected) null else score) },
                    enabled = enabled,
                    modifier = Modifier.size(48.dp),
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
                    Icon(icon, contentDescription = "Mood $score")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            var dropdownExpanded by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { dropdownExpanded = true },
                enabled = enabled && moodScore != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = moodLabel ?: "How do you feel?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
            ) {
                MoodLabel.entries.forEach { label ->
                    DropdownMenuItem(
                        text = { Text(label.display) },
                        onClick = {
                            onMoodLabelChange(label.display)
                            dropdownExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = journalText,
            onValueChange = onJournalChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (journalFocusRequester != null) {
                        Modifier.focusRequester(journalFocusRequester)
                    } else {
                        Modifier
                    }
                ),
            label = { Text("Journal") },
            minLines = 3,
        )
    }
}
