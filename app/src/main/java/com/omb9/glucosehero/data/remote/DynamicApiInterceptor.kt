package com.omb9.glucosehero.data.remote

import android.content.Context
import com.omb9.glucosehero.R
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.domain.model.ProviderHttpException
import com.omb9.glucosehero.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the live AI configuration (base URL + decrypted auth key) straight out
 * of DataStore on EVERY request, then rewrites the placeholder Retrofit URL.
 *
 * Destination hosts must be HTTPS (or an acknowledged private/loopback HTTP
 * endpoint) and must sit on the built-in allowlist or a user-acknowledged
 * custom host. Authorization is never attached to an untrusted destination.
 *
 * Net effect: switching providers in Settings takes effect on the very next
 * request. Switching providers in Settings takes effect on the very next
 * request with no rebuilt Retrofit/OkHttp singletons and no app restart.
 *
 * runBlocking is safe here: the block is bounded to a small DataStore read +
 * decrypt, and interceptors always execute on OkHttp's background dispatcher
 * threads, never the main thread.
 */
@Singleton
class DynamicApiInterceptor @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val settingsDataStore: SettingsDataStore,
    @ApplicationContext private val context: Context,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val config = runBlocking { settingsRepository.resolveAiConfig() }
        val acknowledged = runBlocking { settingsDataStore.acknowledgedAiHostsSnapshot() }

        val base = config.baseUrl.toHttpUrlOrNull()
            ?: throw ProviderHttpException(
                context.getString(R.string.ai_endpoint_invalid, config.baseUrl),
            )

        val original = chain.request()
        val rewrittenUrl = original.url.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .encodedPath(base.encodedPath.trimEnd('/') + original.url.encodedPath)
            .build()

        when (val status = AiEndpointGuard.evaluate(rewrittenUrl, acknowledged)) {
            AiEndpointGuard.Status.InvalidUrl ->
                throw ProviderHttpException(context.getString(R.string.ai_endpoint_invalid, config.baseUrl))
            AiEndpointGuard.Status.HttpsRequired ->
                throw ProviderHttpException(context.getString(R.string.ai_endpoint_https_required, rewrittenUrl.host))
            is AiEndpointGuard.Status.NeedsAcknowledgment ->
                throw ProviderHttpException(
                    context.getString(R.string.ai_endpoint_untrusted, status.host),
                )
            AiEndpointGuard.Status.Allowed -> Unit
        }

        val builder = original.newBuilder()
            .url(rewrittenUrl)
            .tag(AcknowledgedHostsTag::class.java, AcknowledgedHostsTag(acknowledged))
            .header("Authorization", "Bearer ${config.apiKey}")

        if (base.host.contains("openrouter.ai")) {
            builder.header("HTTP-Referer", OPENROUTER_REFERER)
            builder.header("X-Title", OPENROUTER_TITLE)
        }

        return chain.proceed(builder.build())
    }

    private companion object {
        const val OPENROUTER_REFERER = "https://glucosehero.app"
        const val OPENROUTER_TITLE = "GlucoseHero"
    }
}
