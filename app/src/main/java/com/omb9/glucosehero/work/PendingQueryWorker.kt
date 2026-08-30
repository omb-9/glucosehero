package com.omb9.glucosehero.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.omb9.glucosehero.data.local.db.ChatMessageDao
import com.omb9.glucosehero.data.local.db.PendingAiQueryDao
import com.omb9.glucosehero.data.local.entity.toDomain
import com.omb9.glucosehero.domain.model.ApiKeyMissingException
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
        val pending = pendingAiQueryDao.getAll()
        if (pending.isEmpty()) return Result.success()

        var lastReply: String? = null

        for (query in pending) {
            try {
                // History up to (and including) the queued user message, so the
                // model sees the conversation exactly as it stood when asked.
                val history = chatMessageDao.getAll()
                    .map { it.toDomain() }
                    .filter { it.id <= query.userMessageId }

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

        lastReply?.let { insightNotifier.notifyInsightReady(it) }
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "pending_ai_query_worker"
    }
}
