package com.omb9.glucosehero.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.local.db.ChatMessageDao
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao
import com.omb9.glucosehero.data.local.entity.PendingAiQueryEntity
import com.omb9.glucosehero.data.local.entity.toDomain
import com.omb9.glucosehero.domain.model.ApiKeyMissingException
import com.omb9.glucosehero.domain.model.ChatRole
import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.ProviderHttpException
import com.omb9.glucosehero.domain.repository.ChatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Drains the offline queue once WorkManager's CONNECTED constraint is met:
 * every pending query is replayed against the AI endpoint (with a *fresh*
 * SQL-assembled context), the reply is persisted to chat history, and a
 * system notification announces that the insight is ready.
 *
 * Expired rows (FEATURE: pending-query-ttl) are never sent to the backend.
 */
@HiltWorker
class PendingQueryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingAiQueryDao: PendingAiQueryDao,
    private val chatMessageDao: ChatMessageDao,
    private val chatRepository: ChatRepository,
    private val insightNotifier: InsightNotifier,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        var lastReply: String? = null

        while (true) {
            val pending = pendingAiQueryDao.getAll()
            if (pending.isEmpty()) {
                lastReply?.let { insightNotifier.notifyInsightReady(it) }
                return Result.success()
            }

            for (query in pending) {
                try {
                    if (query.isExpired(System.currentTimeMillis())) {
                        chatRepository.appendAssistantMessage(
                            applicationContext.getString(R.string.pending_ai_query_expired),
                        )
                        pendingAiQueryDao.deleteById(query.id)
                        continue
                    }

                    val history = resolveHistoryForDispatch(query)

                    val reply = chatRepository.completeReply(history)
                    chatRepository.appendAssistantMessage(reply)
                    pendingAiQueryDao.deleteById(query.id)
                    lastReply = reply
                } catch (e: CancellationException) {
                    // WorkManager stopped the worker (constraints changed, app
                    // update, etc.). Rethrow so the queue survives untouched and
                    // the run is rescheduled — never treat this as a query error.
                    throw e
                } catch (e: ApiKeyMissingException) {
                    // Must be caught BEFORE IOException (it now IS an IOException,
                    // so the interceptor can raise it without crashing OkHttp).
                    // Nothing to dispatch with — drop the queue entry and surface
                    // the problem in the chat history instead of retrying forever.
                    chatRepository.appendAssistantMessage(e.message ?: "API key missing.")
                    pendingAiQueryDao.deleteById(query.id)
                } catch (e: ProviderHttpException) {
                    // Non-2xx means the provider settings are wrong, not that
                    // connectivity dropped. Drop the row so it does not retry
                    // forever and surface a settings hint in chat history.
                    chatRepository.appendAssistantMessage(
                        e.message ?: "Your AI provider returned an error."
                    )
                    pendingAiQueryDao.deleteById(query.id)
                } catch (e: IOException) {
                    // Connectivity dropped again mid-drain: keep the remainder
                    // queued and let WorkManager retry under the same constraint.
                    lastReply?.let { insightNotifier.notifyInsightReady(it) }
                    return Result.retry()
                } catch (e: Exception) {
                    chatRepository.appendAssistantMessage(
                        "Error answering queued question: ${e.message ?: "unknown error"}"
                    )
                    pendingAiQueryDao.deleteById(query.id)
                }
            }
        }
    }

    /**
     * Rebuilds the conversation context for a queued prompt. If the referenced
     * user message is no longer present in the local repository (for example,
     * the user cleared their chat history while offline, so Room returns an
     * empty history), we synthesise a single "user" turn so the payload can
     * still be dispatched. The warning prefix tells the model that the original
     * conversation is gone and this is a standalone question.
     */
    private suspend fun resolveHistoryForDispatch(query: PendingAiQueryEntity): List<ChatTurn> {
        val history = chatMessageDao.getUpTo(query.userMessageId)
            .map { it.toDomain() }
            .toMutableList()

        val referencedTurnStillPresent = history.any {
            it.id == query.userMessageId && it.role == ChatRole.USER
        }

        return if (referencedTurnStillPresent) {
            history
        } else {
            history + ChatTurn(
                id = query.userMessageId,
                role = ChatRole.USER,
                content = CLEARED_CONTEXT_PREFIX + query.prompt,
                timestamp = query.createdAt,
            )
        }
    }

    companion object {
        const val UNIQUE_NAME = "pending_ai_query_worker"
        private const val CLEARED_CONTEXT_PREFIX = "[Original Context Cleared] "
    }
}
