package com.omb9.glucosehero.data.remote

import com.omb9.glucosehero.domain.repository.SettingsRepository
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the live AI configuration (base URL + decrypted auth key) straight out
 * of DataStore on EVERY request, then rewrites the placeholder Retrofit URL.
 *
 * Net effect: switching providers in Settings takes effect on the very next
 * request — no rebuilt Retrofit/OkHttp singletons, no app restart.
 *
 * runBlocking is safe here: interceptors always execute on OkHttp's background
 * dispatcher threads, never the main thread.
 */
@Singleton
class DynamicApiInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val config = runBlocking { settingsRepository.resolveAiConfig() }

        val base = config.baseUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid AI base URL: ${config.baseUrl}")

        val original = chain.request()
        val rewrittenUrl = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(base.encodedPath.trimEnd('/') + original.url.encodedPath)
            .build()

        val builder = original.newBuilder()
            .url(rewrittenUrl)
            .header("Authorization", "Bearer ${config.apiKey}")

        if (base.host.contains("openrouter.ai")) {
            builder.header("X-Title", "GlucoseHero")
        }

        return chain.proceed(builder.build())
    }
}
