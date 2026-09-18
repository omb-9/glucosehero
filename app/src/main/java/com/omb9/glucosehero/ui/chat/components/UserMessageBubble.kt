package com.omb9.glucosehero.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.R
import com.omb9.glucosehero.ui.chat.MessageGrouping
import com.omb9.glucosehero.ui.chat.USER_BUBBLE_WIDTH_FRACTION
import com.omb9.glucosehero.ui.theme.Spacing

@Composable
internal fun UserMessageBubble(
    text: String,
    grouping: MessageGrouping,
    timestampLabel: String?,
    onToggleTimestamp: () -> Unit,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
    contextChipLabel: String? = null,
    onContextClick: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * USER_BUBBLE_WIDTH_FRACTION
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    onClick = onToggleTimestamp,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd = if (grouping.isLastInGroup) 4.dp else 18.dp,
                    ),
                    color = if (pending) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.widthIn(max = maxBubbleWidth),
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (pending) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                if (pending) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = Spacing.xs, end = Spacing.xs),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.chat_waiting_connection),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                }
                if (contextChipLabel != null && onContextClick != null) {
                    AssistChip(
                        onClick = onContextClick,
                        label = { Text(contextChipLabel) },
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                if (onRetry != null && !pending) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.chat_retry))
                    }
                }
                if (timestampLabel != null) {
                    Text(
                        timestampLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs, end = Spacing.xs),
                    )
                }
            }
        }
    }
}
