package com.omb9.glucosehero.ui.log

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.R
import com.omb9.glucosehero.util.Formatters
import java.time.LocalDate

/**
 * Shown when the Log has no entries at all. This is a first-run / fresh-state
 * message, deliberately distinct from the "nothing matches your filters" state
 * so a search that returns zero results never tells someone to add their
 * first entry.
 */
@Composable
fun LogEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_display),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            alpha = 0.15f,
        )
        Text("No entries yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap + to log a glucose reading, a meal, or a dose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shown when entries exist but none match the active search and/or date
 * filter. Names the active filter and offers a single tap to clear it.
 */
@Composable
fun LogFilteredEmptyState(
    searchQuery: String,
    selectedDate: LocalDate?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (searchQuery.isNotBlank()) {
                Icons.Filled.Search
            } else {
                Icons.Filled.DateRange
            },
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Text("No matching entries", style = MaterialTheme.typography.titleMedium)
        Text(
            buildFilterDescription(searchQuery, selectedDate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClear) {
            Text("Clear filters")
        }
    }
}

private fun buildFilterDescription(searchQuery: String, selectedDate: LocalDate?): String {
    val query = searchQuery.trim()
    val date = selectedDate
    return when {
        query.isNotEmpty() && date != null ->
            "No entries match \"$query\" on ${Formatters.dayHeader(date)}."

        query.isNotEmpty() ->
            "No entries match \"$query\"."

        date != null ->
            "No entries on ${Formatters.dayHeader(date)}."

        else ->
            "No entries match the current filter."
    }
}
