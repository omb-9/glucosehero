package com.omb9.glucosehero.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.ChatPipelineCopy
import com.omb9.glucosehero.domain.model.ChatPipelineStatus
import com.omb9.glucosehero.ui.chat.THINKING_ELAPSED_REVEAL_MILLIS
import com.omb9.glucosehero.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Truthful in-flight UI: pipeline phase from real work, and/or real reasoning
 * tokens. If neither has anything true to show, this is an animation with no
 * words. TalkBack hears phase changes only, never every reasoning token.
 */
@Composable
fun HeroThinking(
    pipeline: ChatPipelineStatus,
    reasoningText: String?,
    reasoningExpanded: Boolean,
    reasoningDurationSeconds: Int,
    onToggleReasoning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phaseLabel = ChatPipelineCopy.phaseAnnouncement(pipeline)
    val hasReasoning = !reasoningText.isNullOrBlank()
    val inFlight = pipeline.isInFlight
    if (!inFlight && !hasReasoning) return

    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(inFlight) {
        if (inFlight) {
            startedAt = System.currentTimeMillis()
            elapsedSeconds = 0
            while (true) {
                delay(1_000)
                elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).toInt()
            }
        }
    }
    val showElapsed = inFlight && elapsedSeconds >= (THINKING_ELAPSED_REVEAL_MILLIS / 1000)

    Column(modifier = modifier.fillMaxWidth()) {
        if (inFlight) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (phaseLabel != null) {
                    Crossfade(targetState = phaseLabel, label = "hero-phase") { label ->
                        Text(
                            text = if (showElapsed) {
                                stringResource(R.string.chat_thinking_elapsed, label, elapsedSeconds)
                            } else {
                                label
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.sm))
        }

        if (hasReasoning) {
            ReasoningBlock(
                text = reasoningText,
                expanded = reasoningExpanded,
                durationSeconds = reasoningDurationSeconds.coerceAtLeast(elapsedSeconds),
                onToggle = onToggleReasoning,
            )
        }
    }
}

@Composable
fun ReasoningBlock(
    text: String,
    expanded: Boolean,
    durationSeconds: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.chat_thought_for, durationSeconds.coerceAtLeast(1)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(vertical = Spacing.xs),
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(R.string.chat_reasoning_framing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
