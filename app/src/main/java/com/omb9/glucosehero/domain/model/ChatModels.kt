package com.omb9.glucosehero.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

enum class ChatRole { USER, ASSISTANT, SYSTEM }

enum class ChatTurnKind {
    NORMAL,
    ERROR,

    /**
     * Local-only crisis support card. Never sent to a model and never written
     * to backups. The matching user text is not stored; only this row is
     * persisted so CrisisSupportCard still shows after process death.
     */
    CRISIS_SUPPORT,
}

/**
 * Last-gate signal when [com.omb9.glucosehero.util.ChatCrisisGate] blocks
 * [com.omb9.glucosehero.domain.repository.ChatRepository.completeReply].
 * Not an IOException: callers must not treat this as offline and must not
 * queue the prompt.
 */
class CrisisInterceptedException : Exception("Crisis content is never sent to the model")

@Immutable
@Serializable
data class ChatContextSummary(
    val historyDays: Int,
    val cgmReadingCount: Int,
    val manualReadingCount: Int,
    val recentEntryCount: Int,
    val includedIob: Boolean,
    val includedCarbs: Boolean,
    val includedInsulin: Boolean,
    val includedMeals: Boolean,
    val foodPatternCount: Int,
    val conversationTurns: Int = 0,
    val includedProfile: Boolean = false,
    val medicationNamesWithheld: Boolean = true,
    val feelingSickWithheld: Boolean = true,
) {
    val readingCount: Int get() = cgmReadingCount + manualReadingCount
}

@Immutable
data class ChatTurn(
    val id: Long = 0L,
    val role: ChatRole,
    val content: String,
    val timestamp: Long,
    val kind: ChatTurnKind = ChatTurnKind.NORMAL,
    val contextSummary: ChatContextSummary? = null,
)

/**
 * Real pipeline phases for Hero chat. Labels on [GatheringContext] must
 * describe work the app is actually doing (reading N days of readings,
 * computing insulin on board, and so on). Invented "thinking" copy is
 * forbidden.
 */
sealed interface ChatPipelineStatus {
    data object Idle : ChatPipelineStatus
    data object Preparing : ChatPipelineStatus
    data class GatheringContext(val label: String) : ChatPipelineStatus
    data object Connecting : ChatPipelineStatus
    data object Waiting : ChatPipelineStatus
    data object Streaming : ChatPipelineStatus
    data object Done : ChatPipelineStatus
    data class Failed(val message: String? = null) : ChatPipelineStatus

    val isInFlight: Boolean
        get() = when (this) {
            Idle, Done -> false
            is Failed -> false
            Preparing, Connecting, Waiting, Streaming -> true
            is GatheringContext -> true
        }
}

/**
 * Last-turn reasoning kept only in memory so the user can collapse and
 * re-expand. Never persisted to Room, backups, or Markdown/CSV exports.
 */
@Immutable
data class InMemoryReasoning(
    val messageId: Long,
    val text: String,
    val durationSeconds: Int,
)

/** Events emitted while streaming an LLM reply over SSE. */
sealed interface StreamEvent {
    data class Token(val text: String) : StreamEvent

    /** Real reasoning tokens from the model. Never concatenated with [Token]. */
    data class ReasoningToken(val text: String) : StreamEvent

    /** A truthful pipeline phase from the repository. */
    data class Phase(val status: ChatPipelineStatus) : StreamEvent

    /**
     * A function/tool call requested by the model. [arguments] is the raw JSON
     * object payload (possibly empty while the model streams the schema).
     */
    data class FunctionCall(
        val name: String,
        val arguments: String,
    ) : StreamEvent

    data class Done(val fullText: String) : StreamEvent
    data class Failure(val throwable: Throwable) : StreamEvent
}
