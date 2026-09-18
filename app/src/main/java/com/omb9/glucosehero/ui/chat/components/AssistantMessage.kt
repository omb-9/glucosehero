package com.omb9.glucosehero.ui.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.ChatPipelineStatus
import com.omb9.glucosehero.domain.model.InMemoryReasoning
import com.omb9.glucosehero.ui.chat.ASSISTANT_USES_BUBBLE
import com.omb9.glucosehero.ui.chat.MessageGrouping
import com.omb9.glucosehero.ui.chat.shouldParseMarkdown
import com.omb9.glucosehero.ui.theme.Spacing

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AssistantMessage(
    text: String,
    grouping: MessageGrouping,
    timestampLabel: String?,
    onToggleTimestamp: () -> Unit,
    onCopy: () -> Unit,
    showCopy: Boolean,
    persistedReasoning: InMemoryReasoning?,
    reasoningExpanded: Boolean,
    onToggleReasoning: () -> Unit,
    modifier: Modifier = Modifier,
    useMarkdown: Boolean = true,
    onShare: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val bubble = ASSISTANT_USES_BUBBLE
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (grouping.isFirstInGroup) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(22.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "H",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        } else {
            Spacer(Modifier.size(22.dp))
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            if (persistedReasoning != null) {
                ReasoningBlock(
                    text = persistedReasoning.text,
                    expanded = reasoningExpanded,
                    durationSeconds = persistedReasoning.durationSeconds,
                    onToggle = onToggleReasoning,
                )
            }
            val parseMarkdown = shouldParseMarkdown(useMarkdown)
            val body: @Composable () -> Unit = {
                if (text.isNotBlank()) {
                    if (parseMarkdown) {
                        ChatMarkdown(
                            content = text,
                            modifier = Modifier.padding(
                                horizontal = if (bubble) 14.dp else 0.dp,
                                vertical = if (bubble) 10.dp else 0.dp,
                            ),
                        )
                    } else {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                horizontal = if (bubble) 14.dp else 0.dp,
                                vertical = if (bubble) 10.dp else 0.dp,
                            ),
                        )
                    }
                }
            }
            if (bubble) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (grouping.isLastInGroup) 4.dp else 18.dp,
                        bottomEnd = 18.dp,
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.combinedClickable(
                        onClick = onToggleTimestamp,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCopy()
                        },
                    ),
                ) {
                    body()
                }
            } else {
                Column(
                    modifier = Modifier.combinedClickable(
                        onClick = onToggleTimestamp,
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCopy()
                        },
                    ),
                ) {
                    body()
                }
            }
            if (showCopy && text.isNotBlank()) {
                Row {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCopy()
                        },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = stringResource(R.string.chat_copy_answer),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (onShare != null) {
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = stringResource(R.string.chat_share_answer),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    if (onRegenerate != null) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.chat_regenerate),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            if (timestampLabel != null) {
                Text(
                    timestampLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

@Composable
internal fun AssistantErrorMessage(
    text: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.chat_retry))
                }
            }
        }
    }
}

@Composable
internal fun AssistantStreamingMessage(
    streamingText: String?,
    streamingReasoning: String?,
    reasoningExpanded: Boolean,
    reasoningDurationSeconds: Int,
    pipeline: ChatPipelineStatus,
    onToggleReasoning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HeroThinking(
            pipeline = pipeline,
            reasoningText = streamingReasoning,
            reasoningExpanded = reasoningExpanded,
            reasoningDurationSeconds = reasoningDurationSeconds,
            onToggleReasoning = onToggleReasoning,
        )
        val content = streamingText.orEmpty()
        if (content.isNotBlank()) {
            AssistantMessage(
                text = content,
                grouping = MessageGrouping(isFirstInGroup = true, isLastInGroup = true),
                timestampLabel = null,
                onToggleTimestamp = {},
                onCopy = {},
                showCopy = false,
                persistedReasoning = null,
                reasoningExpanded = false,
                onToggleReasoning = {},
                useMarkdown = false,
            )
        }
    }
}
