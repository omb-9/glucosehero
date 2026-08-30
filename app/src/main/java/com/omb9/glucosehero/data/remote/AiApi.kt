package com.omb9.glucosehero.data.remote

import com.omb9.glucosehero.data.remote.dto.ChatCompletionRequest
import com.omb9.glucosehero.data.remote.dto.ChatCompletionResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Relative path only — [DynamicApiInterceptor] rewrites the host and prefixes
 * the configured provider base path on every call.
 */
interface AiApi {
    @POST("chat/completions")
    suspend fun complete(@Body request: ChatCompletionRequest): ChatCompletionResponse
}
