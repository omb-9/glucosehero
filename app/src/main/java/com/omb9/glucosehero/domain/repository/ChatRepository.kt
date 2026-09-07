package com.omb9.glucosehero.domain.repository

import com.omb9.glucosehero.domain.model.ChatTurn
import com.omb9.glucosehero.domain.model.MealPhotoAnalysis
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

    /** Queue a query for later dispatch and schedule the connectivity worker. */
    suspend fun queueOffline(userMessageId: Long, prompt: String)

    /** Compiles the rolling-average + 14-day history payload at the SQLite level. */
    suspend fun buildSystemPrompt(): String
}
