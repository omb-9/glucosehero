package com.omb9.glucosehero.ui.chat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omb9.glucosehero.domain.model.ApiKeyMissingException
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.HeroAiPrefill
import com.omb9.glucosehero.domain.model.StreamEvent
import com.omb9.glucosehero.domain.repository.ChatRepository
import com.omb9.glucosehero.domain.repository.SettingsRepository
import com.omb9.glucosehero.ui.log.HeroAiPrefillCoordinator
import com.omb9.glucosehero.util.AppJson
import com.omb9.glucosehero.work.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ChatUiState(
    val messages: ImmutableList<ChatTurn> = persistentListOf(),
    /** Non-null while a reply is streaming; grows token-by-token (typewriter). */
    val streamingText: String? = null,
    val pendingCount: Int = 0,
    val hasApiKey: Boolean = false,
    val providerLabel: String = "",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val heroAiPrefillCoordinator: HeroAiPrefillCoordinator,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Emitted to [com.omb9.glucosehero.ui.chat.ChatScreen] when a prefill tool call arrives. */
    val openLogRequests = heroAiPrefillCoordinator.openLogRequests

    /** SSE tokens fold into this StateFlow; Compose renders it live. */
    private val streamingText = MutableStateFlow<String?>(null)

    /**
     * In-flight send guard. The old `streamingText.value != null` check only
     * became true after two suspension points (the user-message insert and
     * the history read), so two rapid taps — or suggestion-chip spam — both
     * passed it and interleaved two concurrent SSE streams into the same
     * StateFlow. send() is main-thread-confined (Compose click handlers), so
     * checking and assigning this Job synchronously closes the race.
     */
    private var sendJob: Job? = null

    val uiState: StateFlow<ChatUiState> =
        combine(
            chatRepository.observeHistory(),
            streamingText,
            chatRepository.observePendingCount(),
            settingsRepository.aiConfig,
        ) { history, streaming, pending, aiConfig ->
            ChatUiState(
                messages = history.toImmutableList(),
                streamingText = streaming,
                pendingCount = pending,
                hasApiKey = aiConfig.hasApiKey,
                providerLabel = aiConfig.provider.label,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || sendJob?.isActive == true) return

        sendJob = viewModelScope.launch {
            var userMessageId: Long? = null
            try {
                val messageId = chatRepository.appendUserMessage(prompt)
                userMessageId = messageId

                // Offline pre-check: queue instantly instead of waiting on a timeout.
                if (!connectivityObserver.isOnline()) {
                    queueAndAcknowledge(messageId, prompt)
                    return@launch
                }

                val history = chatRepository.observeHistory().first()
                streamingText.value = ""
                var prefillHandled = false

                chatRepository.streamReply(history)
                    .catch { e -> handleFailure(e, messageId, prompt) }
                    .collect { event ->
                        when (event) {
                            is StreamEvent.Token ->
                                streamingText.value = (streamingText.value ?: "") + event.text

                            is StreamEvent.FunctionCall -> {
                                prefillHandled = handleFunctionCall(event) || prefillHandled
                            }

                            is StreamEvent.Done -> {
                                // Providers that close without content would
                                // otherwise persist an empty bubble forever.
                                if (event.fullText.isNotBlank()) {
                                    chatRepository.appendAssistantMessage(event.fullText)
                                } else if (!prefillHandled) {
                                    chatRepository.appendAssistantMessage(
                                        "Your provider returned an empty reply — please try again."
                                    )
                                }
                                streamingText.value = null
                            }

                            is StreamEvent.Failure ->
                                handleFailure(event.throwable, messageId, prompt)
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Failures *before* the stream (Room insert, history read,
                // WorkManager enqueue) previously escaped the launch and
                // crashed the process. Best-effort surface, never rethrow.
                val id = userMessageId
                if (id != null) runCatching { handleFailure(e, id, prompt) }
            } finally {
                // No exit path may leave the UI stuck in "streaming" state.
                streamingText.value = null
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { chatRepository.clearHistory() }
    }

    private suspend fun handleFailure(error: Throwable, userMessageId: Long, prompt: String) {
        streamingText.value = null
        when (error) {
            is ApiKeyMissingException -> chatRepository.appendAssistantMessage(
                "I can't reach your AI provider yet — add an API key under " +
                    "Settings → Hero AI and ask me again."
            )

            is IOException -> queueAndAcknowledge(userMessageId, prompt)

            else -> chatRepository.appendAssistantMessage(
                "Something went wrong talking to your AI provider: " +
                    "${error.message ?: error::class.simpleName}. " +
                    "Check your provider settings and try again."
            )
        }
    }

    private suspend fun queueAndAcknowledge(userMessageId: Long, prompt: String) {
        chatRepository.queueOffline(userMessageId, prompt)
        chatRepository.appendAssistantMessage(
            "You're offline right now. I saved your question and will answer it " +
                "automatically once you're back online — you'll get a notification."
        )
    }

    private fun handleFunctionCall(event: StreamEvent.FunctionCall): Boolean {
        if (event.name != PREFILL_TOOL_NAME) return false

        val prefill = runCatching {
            AppJson.decodeFromString<HeroAiPrefill>(event.arguments)
        }.getOrNull() ?: return false
        if (prefill.isEmpty) return false

        heroAiPrefillCoordinator.requestPrefill(prefill)
        streamingText.value = null
        return true
    }

    private companion object {
        const val PREFILL_TOOL_NAME = "prefill_log_draft"
    }
}
