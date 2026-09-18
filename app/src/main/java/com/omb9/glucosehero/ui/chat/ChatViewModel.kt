package com.omb9.glucosehero.ui.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.data.billing.BillingRepository
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.domain.model.AiConfig
import com.omb9.glucosehero.domain.model.AiProvider
import com.omb9.glucosehero.domain.model.ApiKeyMissingException
import com.omb9.glucosehero.domain.model.ChatHistoryPaging
import com.omb9.glucosehero.domain.model.ChatPipelineStatus
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.ChatTurnKind
import com.omb9.glucosehero.domain.model.HeroAiPrefill
import com.omb9.glucosehero.domain.model.InMemoryReasoning
import com.omb9.glucosehero.domain.model.ProviderHttpException
import com.omb9.glucosehero.domain.model.QuotaExhaustedException
import com.omb9.glucosehero.domain.model.StreamEvent
import com.omb9.glucosehero.domain.model.UserProfile
import com.omb9.glucosehero.domain.repository.ChatRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.log.HeroAiPrefillCoordinator
import com.omb9.glucosehero.ui.settings.HeroAiSettingsCopy
import com.omb9.glucosehero.util.AiQuota
import com.omb9.glucosehero.util.AiTier
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.util.ChatCrisisGate
import com.omb9.glucosehero.work.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class ChatUiState(
    val messages: ImmutableList<ChatTurn> = persistentListOf(),
    val pendingUserMessageIds: ImmutableSet<Long> = persistentSetOf(),
    val hasApiKey: Boolean = false,
    val needsProviderSetup: Boolean = false,
    val providerLabel: String = "",
    val remainingCalls: Int? = null,
    val use24HourTime: Boolean = false,
    val canLoadOlder: Boolean = false,
    val loadingOlder: Boolean = false,
    val glucosePointCount: Int = 0,
) {
    val hasEnoughChatData: Boolean
        get() = hasEnoughDataForGroundedChat(glucosePointCount)
}

sealed interface ChatUiEvent {
    data object StreamCompleted : ChatUiEvent
}

private data class ChatQuotaFlags(
    val used: Int,
    val use24HourTime: Boolean,
    val loadingOlder: Boolean,
    val reachedOldest: Boolean,
)

private data class ChatAux(
    val quota: ChatQuotaFlags,
    val glucosePointCount: Int,
    val pendingUserMessageIds: List<Long>,
)

@HiltViewModel
class ChatViewModel internal constructor(
    private val chatRepository: ChatRepository,
    private val isOnline: () -> Boolean,
    private val heroAiPrefillCoordinator: HeroAiPrefillCoordinator,
    premium: Flow<Boolean>,
    quotaUsedToday: Flow<Int>,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    @Inject
    constructor(
        chatRepository: ChatRepository,
        connectivityObserver: ConnectivityObserver,
        heroAiPrefillCoordinator: HeroAiPrefillCoordinator,
        billingRepository: BillingRepository,
        settingsDataStore: SettingsDataStore,
        settingsRepository: SettingsRepository,
    ) : this(
        chatRepository = chatRepository,
        isOnline = connectivityObserver::isOnline,
        heroAiPrefillCoordinator = heroAiPrefillCoordinator,
        premium = billingRepository.isPremium,
        quotaUsedToday = settingsDataStore.aiQuotaUsedToday,
        settingsRepository = settingsRepository,
    )

    /** Emitted to [com.omb9.glucosehero.ui.chat.ChatScreen] when a prefill tool call arrives. */
    val openLogRequests = heroAiPrefillCoordinator.openLogRequests

    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText

    private val _streamingReasoning = MutableStateFlow<String?>(null)
    val streamingReasoning: StateFlow<String?> = _streamingReasoning

    private val _pipeline = MutableStateFlow<ChatPipelineStatus>(ChatPipelineStatus.Idle)
    val pipeline: StateFlow<ChatPipelineStatus> = _pipeline

    private val _lastReasoning = MutableStateFlow<InMemoryReasoning?>(null)
    val lastReasoning: StateFlow<InMemoryReasoning?> = _lastReasoning

    private val _reasoningExpanded = MutableStateFlow(false)
    val reasoningExpanded: StateFlow<Boolean> = _reasoningExpanded

    private val _reasoningDurationSeconds = MutableStateFlow(0)
    val reasoningDurationSeconds: StateFlow<Int> = _reasoningDurationSeconds

    private val _uiEvents = MutableSharedFlow<ChatUiEvent>(extraBufferCapacity = 1)
    val uiEvents: SharedFlow<ChatUiEvent> = _uiEvents.asSharedFlow()

    private val displayedMessages = MutableStateFlow<List<ChatTurn>>(emptyList())
    private val loadingOlder = MutableStateFlow(false)
    private val reachedOldest = MutableStateFlow(false)

    /**
     * In-flight send guard. The old `streamingText.value != null` check only
     * became true after two suspension points (the user-message insert and
     * the history read), so two rapid taps, or suggestion-chip spam, both
     * passed it and interleaved two concurrent SSE streams into the same
     * StateFlow. send() is main-thread-confined (Compose click handlers), so
     * checking and assigning this Job synchronously closes the race.
     */
    private var sendJob: Job? = null

    private val quotaFlags = combine(
        quotaUsedToday,
        settingsRepository.settings,
        loadingOlder,
        reachedOldest,
    ) { used, settings, loading, oldest ->
        ChatQuotaFlags(used, settings.use24HourTime, loading, oldest)
    }

    private val aux = combine(
        quotaFlags,
        chatRepository.observeGlucosePointCount(),
        chatRepository.observePendingUserMessageIds(),
    ) { quota, glucosePoints, pendingIds ->
        ChatAux(quota, glucosePoints, pendingIds)
    }

    val uiState: StateFlow<ChatUiState> =
        combine(
            displayedMessages,
            settingsRepository.aiConfig,
            premium,
            aux,
        ) { history, aiConfig, premium, flags ->
            val tier = resolveTier(aiConfig, premium)
            val capped = history.takeLast(ChatHistoryPaging.MAX_IN_MEMORY)
            ChatUiState(
                messages = capped.toImmutableList(),
                pendingUserMessageIds = flags.pendingUserMessageIds.toImmutableSet(),
                hasApiKey = aiConfig.hasApiKey,
                needsProviderSetup = HeroAiSettingsCopy.needsProviderSetup(aiConfig),
                providerLabel = aiConfig.provider.label,
                remainingCalls = AiQuota.remaining(tier, flags.quota.used),
                use24HourTime = flags.quota.use24HourTime,
                canLoadOlder = capped.isNotEmpty() &&
                    !flags.quota.reachedOldest &&
                    capped.size < ChatHistoryPaging.MAX_IN_MEMORY,
                loadingOlder = flags.quota.loadingOlder,
                glucosePointCount = flags.glucosePointCount,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    val profile: StateFlow<UserProfile> = settingsRepository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())

    init {
        viewModelScope.launch {
            chatRepository.observeRecentHistory(ChatHistoryPaging.PAGE_SIZE).collect { recent ->
                if (recent.isEmpty()) {
                    displayedMessages.value = emptyList()
                    reachedOldest.value = false
                } else {
                    displayedMessages.update { current -> mergeChatPages(current, recent) }
                }
            }
        }
    }

    fun loadOlder() {
        if (loadingOlder.value || reachedOldest.value) return
        val oldest = displayedMessages.value.firstOrNull() ?: return
        if (displayedMessages.value.size >= ChatHistoryPaging.MAX_IN_MEMORY) {
            reachedOldest.value = true
            return
        }
        viewModelScope.launch {
            loadingOlder.value = true
            try {
                val page = chatRepository.loadOlderHistory(
                    oldest.timestamp,
                    oldest.id,
                    ChatHistoryPaging.PAGE_SIZE,
                )
                if (page.isEmpty()) {
                    reachedOldest.value = true
                } else {
                    displayedMessages.update { current -> mergeChatPages(current, page) }
                    if (page.size < ChatHistoryPaging.PAGE_SIZE) {
                        reachedOldest.value = true
                    }
                }
            } finally {
                loadingOlder.value = false
            }
        }
    }

    fun toggleReasoningExpanded() {
        _reasoningExpanded.update { !it }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || sendJob?.isActive == true) return
        if (ChatCrisisGate.matches(prompt)) {
            sendJob = viewModelScope.launch { persistCrisisSupportAndStop() }
            return
        }
        if (uiState.value.needsProviderSetup) return
        sendJob = viewModelScope.launch {
            runTurn(
                prompt = prompt,
                insertUserMessage = true,
                replaceMessageId = null,
                existingUserMessageId = null,
            )
        }
    }

    fun stopGeneration() {
        sendJob?.cancel()
    }

    fun regenerateLast() {
        if (sendJob?.isActive == true) return
        if (uiState.value.needsProviderSetup) return
        val messages = displayedMessages.value
        val last = messages.lastOrNull() ?: return
        if (last.role != ChatRole.ASSISTANT || last.kind != ChatTurnKind.NORMAL) return
        val user = messages.dropLast(1).lastOrNull {
            it.role == ChatRole.USER && it.kind == ChatTurnKind.NORMAL
        } ?: return
        if (ChatCrisisGate.matches(user.content)) {
            sendJob = viewModelScope.launch { persistCrisisSupportAndStop() }
            return
        }
        sendJob = viewModelScope.launch {
            runTurn(
                prompt = user.content,
                insertUserMessage = false,
                replaceMessageId = last.id,
                existingUserMessageId = user.id,
            )
        }
    }

    fun retry(messageId: Long) {
        if (sendJob?.isActive == true) return
        if (uiState.value.needsProviderSetup) return
        val messages = displayedMessages.value
        val target = messages.firstOrNull { it.id == messageId } ?: return
        if (target.kind == ChatTurnKind.CRISIS_SUPPORT) return
        val user = if (target.role == ChatRole.USER) {
            target
        } else {
            messages.takeWhile { it.id != messageId }.lastOrNull {
                it.role == ChatRole.USER && it.kind == ChatTurnKind.NORMAL
            }
        } ?: return
        if (ChatCrisisGate.matches(user.content)) {
            sendJob = viewModelScope.launch { persistCrisisSupportAndStop() }
            return
        }
        val replaceId = if (target.role == ChatRole.ASSISTANT) {
            target.id
        } else {
            null
        }
        sendJob = viewModelScope.launch {
            runTurn(
                prompt = user.content,
                insertUserMessage = false,
                replaceMessageId = replaceId,
                existingUserMessageId = user.id,
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            sendJob?.cancel()
            chatRepository.clearHistory()
            _lastReasoning.value = null
            _reasoningExpanded.value = false
        }
    }

    fun dismissCrisisSupport(messageId: Long) {
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
        }
    }

    private suspend fun runTurn(
        prompt: String,
        insertUserMessage: Boolean,
        replaceMessageId: Long?,
        existingUserMessageId: Long?,
    ) {
        var userMessageId: Long? = existingUserMessageId
        val response = StringBuilder()
        val reasoning = StringBuilder()
        var reasoningStartedAt = 0L
        var reasoningFrozenSeconds = 0
        try {
            if (ChatCrisisGate.matches(prompt)) {
                persistCrisisSupportAndStop()
                return
            }
            _pipeline.value = ChatPipelineStatus.Preparing
            if (replaceMessageId != null) {
                chatRepository.deleteMessage(replaceMessageId)
            }
            if (insertUserMessage) {
                userMessageId = chatRepository.appendUserMessage(prompt)
            }
            val messageId = userMessageId ?: return

            if (!isOnline()) {
                queueAndAcknowledge(messageId, prompt)
                return
            }

            val history = chatRepository.observeHistory().first()
            _streamingText.value = ""
            _streamingReasoning.value = null
            var prefillHandled = false

            chatRepository.streamReply(history)
                .catch { e -> handleFailure(e, messageId, prompt) }
                .collect { event ->
                    when (event) {
                        is StreamEvent.Phase -> _pipeline.value = event.status

                        is StreamEvent.ReasoningToken -> {
                            if (reasoningStartedAt == 0L) {
                                reasoningStartedAt = System.currentTimeMillis()
                            }
                            reasoning.append(event.text)
                            _streamingReasoning.value = reasoning.toString()
                            if (response.isEmpty()) {
                                _reasoningExpanded.value = true
                            }
                            val elapsed = ((System.currentTimeMillis() - reasoningStartedAt) / 1000L).toInt()
                            _reasoningDurationSeconds.value = elapsed.coerceAtLeast(1)
                        }

                        is StreamEvent.Token -> {
                            if (reasoning.isNotEmpty() && response.isEmpty()) {
                                _reasoningExpanded.value = false
                                reasoningFrozenSeconds = if (reasoningStartedAt == 0L) {
                                    1
                                } else {
                                    ((System.currentTimeMillis() - reasoningStartedAt) / 1000L)
                                        .toInt()
                                        .coerceAtLeast(1)
                                }
                                _reasoningDurationSeconds.value = reasoningFrozenSeconds
                            }
                            response.append(event.text)
                            _streamingText.value = response.toString()
                        }

                        is StreamEvent.FunctionCall -> {
                            prefillHandled = handleFunctionCall(event) || prefillHandled
                        }

                        is StreamEvent.Done -> {
                            if (event.fullText.isNotBlank()) {
                                val assistantId = chatRepository.appendAssistantMessage(event.fullText)
                                val reasoningText = reasoning.toString()
                                if (reasoningText.isNotBlank()) {
                                    val seconds = if (reasoningFrozenSeconds > 0) {
                                        reasoningFrozenSeconds
                                    } else if (reasoningStartedAt == 0L) {
                                        1
                                    } else {
                                        ((System.currentTimeMillis() - reasoningStartedAt) / 1000L)
                                            .toInt()
                                            .coerceAtLeast(1)
                                    }
                                    _lastReasoning.value = InMemoryReasoning(
                                        messageId = assistantId,
                                        text = reasoningText,
                                        durationSeconds = seconds,
                                    )
                                }
                                _uiEvents.tryEmit(ChatUiEvent.StreamCompleted)
                            } else if (!prefillHandled) {
                                chatRepository.appendAssistantMessage(
                                    "Your provider returned an empty reply. Please try again.",
                                    ChatTurnKind.ERROR,
                                )
                            }
                            _streamingText.value = null
                            _streamingReasoning.value = null
                        }

                        is StreamEvent.Failure ->
                            handleFailure(event.throwable, messageId, prompt)
                    }
                }
        } catch (e: CancellationException) {
            val partial = persistPartialOnStop(response.toString())
            if (partial != null) {
                withContext(NonCancellable) {
                    val assistantId = chatRepository.appendAssistantMessage(partial)
                    val reasoningText = reasoning.toString()
                    if (reasoningText.isNotBlank()) {
                        val seconds = if (reasoningFrozenSeconds > 0) {
                            reasoningFrozenSeconds
                        } else if (reasoningStartedAt == 0L) {
                            1
                        } else {
                            ((System.currentTimeMillis() - reasoningStartedAt) / 1000L)
                                .toInt()
                                .coerceAtLeast(1)
                        }
                        _lastReasoning.value = InMemoryReasoning(
                            messageId = assistantId,
                            text = reasoningText,
                            durationSeconds = seconds,
                        )
                    }
                }
            }
            throw e
        } catch (e: Exception) {
            val id = userMessageId
            if (id != null) runCatching { handleFailure(e, id, prompt) }
        } finally {
            _streamingText.value = null
            _streamingReasoning.value = null
            if (_pipeline.value.isInFlight) {
                _pipeline.value = ChatPipelineStatus.Idle
            }
        }
    }

    private suspend fun handleFailure(error: Throwable, userMessageId: Long, prompt: String) {
        _streamingText.value = null
        _streamingReasoning.value = null
        _pipeline.value = ChatPipelineStatus.Failed(error.message)
        when (error) {
            is ProviderHttpException -> chatRepository.appendAssistantMessage(
                error.message ?: "Your AI provider returned an error. Double-check the base URL, " +
                    "model, and API key under Settings → Hero AI → Use your own API key, then try again.",
                ChatTurnKind.ERROR,
            )

            is ApiKeyMissingException -> chatRepository.appendAssistantMessage(
                error.message ?: HeroAiSettingsCopy.CHAT_SETUP_BANNER,
                ChatTurnKind.ERROR,
            )

            is QuotaExhaustedException -> chatRepository.appendAssistantMessage(
                error.message ?: "You've reached your daily AI limit.",
                ChatTurnKind.ERROR,
            )

            is IOException -> queueAndAcknowledge(userMessageId, prompt)

            else -> chatRepository.appendAssistantMessage(
                "Something went wrong talking to your AI provider: " +
                    "${error.message ?: error::class.simpleName}. " +
                    "Check your provider settings and try again.",
                ChatTurnKind.ERROR,
            )
        }
        _pipeline.value = ChatPipelineStatus.Idle
    }

    private suspend fun queueAndAcknowledge(userMessageId: Long, prompt: String) {
        if (ChatCrisisGate.matches(prompt)) {
            persistCrisisSupportAndStop()
            return
        }
        chatRepository.queueOffline(userMessageId, prompt)
        _pipeline.value = ChatPipelineStatus.Idle
    }

    /**
     * Persist a CRISIS_SUPPORT row only. The matching composer text is never
     * written to the chat table, never queued offline, and never sent to a
     * provider.
     */
    private suspend fun persistCrisisSupportAndStop() {
        chatRepository.appendAssistantMessage(
            ChatCrisisGate.SUPPORT_PLACEHOLDER,
            ChatTurnKind.CRISIS_SUPPORT,
        )
        _pipeline.value = ChatPipelineStatus.Idle
    }

    private suspend fun handleFunctionCall(event: StreamEvent.FunctionCall): Boolean {
        if (event.name != PREFILL_TOOL_NAME) return false

        val prefill = runCatching {
            AppJson.decodeFromString<HeroAiPrefill>(event.arguments)
        }.getOrNull() ?: return false
        if (prefill.isEmpty) return false

        heroAiPrefillCoordinator.requestPrefill(prefill)
        _streamingText.value = null
        _streamingReasoning.value = null
        return true
    }

    private fun resolveTier(config: AiConfig, premium: Boolean): AiTier =
        if (config.provider == AiProvider.OPENROUTER && !config.hasApiKey) {
            if (premium) AiTier.PRO else AiTier.FREE
        } else {
            AiTier.BYOK
        }

    private companion object {
        const val PREFILL_TOOL_NAME = "prefill_log_draft"
    }
}
