package com.omb9.glucosehero.domain.repository

import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.MealPhotoAnalysis
import com.omb9.glucosehero.domain.model.QuickLogParseResult
import com.omb9.glucosehero.domain.model.StreamEvent
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    /** Persisted chat history, oldest first. */
    fun observeHistory(): Flow<List<ChatTurn>>

    /** Count of queries waiting for connectivity. */
    fun observePendingCount(): Flow<Int>

    suspend fun appendUserMessage(text: String): Long
    suspend fun appendAssistantMessage(text: String): Long
    suspend fun clearHistory()

    /**
     * Streams an LLM reply token-by-token over SSE. The system prompt with the
     * SQL-assembled glucose context is prepended inside the repository.
     */
    fun streamReply(history: List<ChatTurn>): Flow<StreamEvent>

    /** Non-streaming completion used by the offline-queue background worker. */
    suspend fun completeReply(history: List<ChatTurn>): String

    /**
     * Sends a meal photo (as an in-memory base64 `data:` URI) to the AI for a
     * one-shot nutrition estimate. The image bytes are never persisted locally
     * and no chat history is touched.
     */
    suspend fun analyzeMealPhoto(imageDataUri: String): MealPhotoAnalysis

    /**
     * One-shot natural-language parse of a typed or spoken log line into
     * [QuickLogParseResult]. Does not persist chat history. Throws on missing
     * API key, HTTP errors, quota exhaustion, or invalid JSON so the caller
     * can fall back to the on-device parser.
     */
    suspend fun parseQuickLog(utterance: String): QuickLogParseResult

    /**
     * Queue a query for later dispatch and schedule the connectivity worker.
     *
     * @param ttlSeconds validity window. Defaults to 30 minutes (acute queries).
     * Values above 30 minutes are capped. The worker never sends expired rows.
     */
    suspend fun queueOffline(userMessageId: Long, prompt: String, ttlSeconds: Int = 30 * 60)

    /** Compiles the rolling-average + 14-day history payload at the SQLite level. */
    suspend fun buildSystemPrompt(): String
}
