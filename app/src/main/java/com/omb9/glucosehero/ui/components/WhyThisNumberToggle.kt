package com.omb9.glucosehero.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.omb9.glucosehero.R

/**
 * Expand/collapse control for a "Why this number" explanation. Used by the
 * glucose forecast card; it must not be wired to a dose suggestion.
 */
@Composable
internal fun WhyThisNumberToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val action = stringResource(
        if (expanded) R.string.why_this_number_hide else R.string.why_this_number_show,
    )
    TextButton(
        onClick = onToggle,
        modifier = modifier.semantics { contentDescription = action },
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
        )
        Text(stringResource(R.string.why_this_number))
    }
}
