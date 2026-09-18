package com.omb9.glucosehero.ui.chat

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omb9.glucosehero.R
import com.omb9.glucosehero.domain.model.ChatContextSummary
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurnKind
import com.omb9.glucosehero.ui.chat.components.AssistantErrorMessage
import com.omb9.glucosehero.ui.chat.components.AssistantMessage
import com.omb9.glucosehero.ui.chat.components.AssistantStreamingMessage
import com.omb9.glucosehero.ui.chat.components.ChatBanner
import com.omb9.glucosehero.ui.chat.components.ChatComposer
import com.omb9.glucosehero.ui.chat.components.ChatDataContextSheet
import com.omb9.glucosehero.ui.chat.components.ChatEmptyState
import com.omb9.glucosehero.ui.chat.components.ChatTimestampSeparator
import com.omb9.glucosehero.ui.chat.components.UserMessageBubble
import com.omb9.glucosehero.ui.components.CrisisSupportCard
import com.omb9.glucosehero.ui.settings.HeroAiSettingsCopy
import com.omb9.glucosehero.util.ChatCrisisGate
import com.omb9.glucosehero.util.Formatters
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenLog: () -> Unit,
    onHeroAiSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val streaming by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingReasoning by viewModel.streamingReasoning.collectAsStateWithLifecycle()
    val pipeline by viewModel.pipeline.collectAsStateWithLifecycle()
    val lastReasoning by viewModel.lastReasoning.collectAsStateWithLifecycle()
    val reasoningExpanded by viewModel.reasoningExpanded.collectAsStateWithLifecycle()
    val reasoningDuration by viewModel.reasoningDurationSeconds.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val composerEnabled = !state.needsProviderSetup
    val starterPrompts = listOf(
        stringResource(R.string.chat_prompt_spike_yesterday),
        stringResource(R.string.chat_prompt_summarize_week),
        stringResource(R.string.chat_prompt_high_morning),
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.chat_copied)
    val shareChooser = stringResource(R.string.chat_share)
    val shareSubject = stringResource(R.string.chat_share_subject)
    var revealedTimestampId by rememberSaveable { mutableStateOf<Long?>(null) }
    var contextSheetSummary by remember { mutableStateOf<ChatContextSummary?>(null) }
    val rows = remember(state.messages, state.use24HourTime) {
        buildChatListRows(state.messages, state.use24HourTime)
    }
    val streamingActive = streaming != null || pipeline.isInFlight || !streamingReasoning.isNullOrBlank()
    val newestId = state.messages.lastOrNull()?.id ?: 0L
    var lastScrolledNewestId by remember { mutableLongStateOf(0L) }
    var nearBottom by remember { mutableStateOf(true) }
    var unseenWhileAway by remember { mutableStateOf(false) }
    var forceFollow by remember { mutableStateOf(false) }
    val lastMessage = state.messages.lastOrNull()
    val lastAssistantId = lastMessage?.takeIf {
        it.role == ChatRole.ASSISTANT && it.kind == ChatTurnKind.NORMAL
    }?.id
    val lastUserNeedsRetry = lastMessage != null &&
        lastMessage.role == ChatRole.USER &&
        lastMessage.kind == ChatTurnKind.NORMAL &&
        !ChatCrisisGate.matches(lastMessage.content) &&
        !streamingActive &&
        lastMessage.id !in state.pendingUserMessageIds

    LaunchedEffect(Unit) {
        viewModel.openLogRequests.collect { onOpenLog() }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                ChatUiEvent.StreamCompleted ->
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }

    LaunchedEffect(listState, state.canLoadOlder) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index <= 1 && state.canLoadOlder && !state.loadingOlder) {
                    viewModel.loadOlder()
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            isNearBottom(
                lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index,
                totalItems = info.totalItemsCount,
            )
        }
            .distinctUntilChanged()
            .collect { atBottom ->
                nearBottom = atBottom
                if (atBottom) unseenWhileAway = false
            }
    }

    LaunchedEffect(newestId, streaming, streamingActive, nearBottom, forceFollow) {
        snapshotFlow {
            val extra = if (streamingActive) 1 else 0
            val itemCount = rows.size + extra
            Triple(itemCount, streaming?.length ?: 0, nearBottom)
        }
            .conflate()
            .collectLatest { (itemCount, _, atBottom) ->
                val newestChanged = newestId != lastScrolledNewestId && newestId != 0L
                val follow = atBottom && (newestChanged || shouldFollowStream(true, streaming != null))
                if (itemCount > 0 && (follow || forceFollow)) {
                    lastScrolledNewestId = newestId
                    forceFollow = false
                    listState.scrollToItem(itemCount - 1)
                } else if (!atBottom && (newestChanged || streaming != null)) {
                    unseenWhileAway = true
                }
            }
    }

    fun copyText(text: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
    }

    fun shareText(text: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, shareSubject)
        }
        context.startActivity(Intent.createChooser(sendIntent, shareChooser))
    }

    fun sendPrompt(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty()) return
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        forceFollow = true
        viewModel.send(prompt)
        input = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.headlineMedium) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                actions = {
                    state.remainingCalls?.let { remaining ->
                        Text(
                            text = HeroAiSettingsCopy.remainingCallsTopBar(remaining),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (remaining == 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = stringResource(R.string.chat_clear_history),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (state.needsProviderSetup) {
                ChatBanner(
                    text = HeroAiSettingsCopy.CHAT_SETUP_BANNER,
                    onClick = onHeroAiSettings,
                )
            }

            if (state.messages.isEmpty() && !streamingActive) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ChatEmptyState(
                        intro = stringResource(R.string.chat_empty_intro),
                        disclaimer = stringResource(R.string.settings_disclaimer),
                        starterPrompts = starterPrompts,
                        hasEnoughData = state.hasEnoughChatData,
                        startersEnabled = composerEnabled && !streamingActive,
                        onStarter = { sendPrompt(it) },
                        onOpenLog = onOpenLog,
                    )
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    SelectionContainer(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = 16.dp,
                                vertical = 12.dp,
                            ),
                        ) {
                            items(rows, key = { it.key }) { row ->
                                when (row) {
                                    is ChatListRow.TimeGap -> ChatTimestampSeparator(row.label)
                                    is ChatListRow.Message -> {
                                        val turn = row.turn
                                        val bottomPad = if (row.grouping.isLastInGroup) 10.dp else 2.dp
                                        val timestampLabel = if (revealedTimestampId == turn.id) {
                                            Formatters.time(turn.timestamp, state.use24HourTime)
                                        } else {
                                            null
                                        }
                                        Box(Modifier.padding(bottom = bottomPad)) {
                                            if (turn.kind == ChatTurnKind.CRISIS_SUPPORT) {
                                                CrisisSupportCard(
                                                    onDismiss = { viewModel.dismissCrisisSupport(turn.id) },
                                                )
                                            } else if (turn.role == ChatRole.USER) {
                                                UserMessageBubble(
                                                    text = turn.content,
                                                    grouping = row.grouping,
                                                    timestampLabel = timestampLabel,
                                                    onToggleTimestamp = {
                                                        revealedTimestampId =
                                                            if (revealedTimestampId == turn.id) null else turn.id
                                                    },
                                                    pending = turn.id in state.pendingUserMessageIds,
                                                    contextChipLabel = turn.contextSummary?.chipLabel(),
                                                    onContextClick = turn.contextSummary?.let { summary ->
                                                        { contextSheetSummary = summary }
                                                    },
                                                    onRetry = if (lastUserNeedsRetry && turn.id == lastMessage.id) {
                                                        { viewModel.retry(turn.id) }
                                                    } else {
                                                        null
                                                    },
                                                )
                                            } else if (turn.kind == ChatTurnKind.ERROR) {
                                                AssistantErrorMessage(
                                                    text = turn.content,
                                                    onRetry = if (!streamingActive) {
                                                        { viewModel.retry(turn.id) }
                                                    } else {
                                                        null
                                                    },
                                                )
                                            } else {
                                                val persisted = lastReasoning?.takeIf { it.messageId == turn.id }
                                                AssistantMessage(
                                                    text = turn.content,
                                                    grouping = row.grouping,
                                                    timestampLabel = timestampLabel,
                                                    onToggleTimestamp = {
                                                        revealedTimestampId =
                                                            if (revealedTimestampId == turn.id) null else turn.id
                                                    },
                                                    onCopy = { copyText(turn.content) },
                                                    showCopy = true,
                                                    persistedReasoning = persisted,
                                                    reasoningExpanded = reasoningExpanded && persisted != null,
                                                    onToggleReasoning = viewModel::toggleReasoningExpanded,
                                                    onShare = { shareText(turn.content) },
                                                    onRegenerate = if (
                                                        turn.id == lastAssistantId && !streamingActive && composerEnabled
                                                    ) {
                                                        { viewModel.regenerateLast() }
                                                    } else {
                                                        null
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (streamingActive) {
                                item(key = "streaming") {
                                    AssistantStreamingMessage(
                                        streamingText = streaming,
                                        streamingReasoning = streamingReasoning,
                                        reasoningExpanded = reasoningExpanded,
                                        reasoningDurationSeconds = reasoningDuration,
                                        pipeline = pipeline,
                                        onToggleReasoning = viewModel::toggleReasoningExpanded,
                                    )
                                }
                            }
                        }
                    }
                    if (!nearBottom && (state.messages.isNotEmpty() || streamingActive)) {
                        SmallFloatingActionButton(
                            onClick = {
                                forceFollow = true
                                unseenWhileAway = false
                                val extra = if (streamingActive) 1 else 0
                                val itemCount = rows.size + extra
                                if (itemCount > 0) {
                                    scope.launch { listState.scrollToItem(itemCount - 1) }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 12.dp),
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unseenWhileAway) Badge()
                                },
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.chat_jump_to_latest),
                                )
                            }
                        }
                    }
                }
            }

            ChatComposer(
                value = input,
                onValueChange = { input = it },
                onSend = { sendPrompt(input) },
                onStop = { viewModel.stopGeneration() },
                enabled = !streamingActive,
                sendEnabled = chatComposerSendEnabled(
                    input = input,
                    streamingActive = streamingActive,
                    composerEnabled = composerEnabled,
                    remainingCalls = state.remainingCalls,
                ),
                streaming = streamingActive,
            )
        }
    }

    contextSheetSummary?.let { summary ->
        ChatDataContextSheet(
            summary = summary,
            onDismiss = { contextSheetSummary = null },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.chat_clear_history_title)) },
            text = { Text(stringResource(R.string.chat_clear_history_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearHistory()
                    },
                ) {
                    Text(stringResource(R.string.chat_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.chat_clear_cancel))
                }
            },
        )
    }
}
