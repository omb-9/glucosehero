package com.omb9.glucosehero.di

import com.omb9.glucosehero.BuildConfig
import com.omb9.glucosehero.data.remote.AiApi
import com.omb9.glucosehero.data.remote.AuthRedirectSanitizer
import com.omb9.glucosehero.data.remote.CleartextGuardInterceptor
import com.omb9.glucosehero.data.remote.DynamicApiInterceptor
import com.omb9.glucosehero.data.remote.off.OffHttpClient
import com.omb9.glucosehero.data.remote.off.OffRetrofit
import com.omb9.glucosehero.data.remote.off.OpenFoodFactsApi
import com.omb9.glucosehero.data.remote.off.OpenFoodFactsThrottleInterceptor
import com.omb9.glucosehero.data.remote.off.OpenFoodFactsUserAgentInterceptor
import com.omb9.glucosehero.util.AppJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Duration
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Never hit: [DynamicApiInterceptor] rewrites scheme/host/port/path on every
     * request from the live DataStore config. Retrofit just needs *a* base URL
     * at construction time.
     */
    private const val PLACEHOLDER_BASE_URL = "https://placeholder.invalid/"

    /** Open Food Facts requires a User-Agent identifying the app. */
    val OFF_USER_AGENT = OpenFoodFactsUserAgentInterceptor.DEFAULT_USER_AGENT

    /**
     * Shared dispatcher, connection pool, and default timeouts. Carries no
     * interceptors, especially not [DynamicApiInterceptor], so derived clients
     * cannot inherit the AI bearer token by accident.
     */
    @Provides
    @Singleton
    @Named("okhttp-base")
    fun provideBaseOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(60))
            .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @Named("okhttp-base") base: OkHttpClient,
        dynamicApiInterceptor: DynamicApiInterceptor,
    ): OkHttpClient =
        base.newBuilder()
            .addInterceptor(dynamicApiInterceptor)
            .addInterceptor(CleartextGuardInterceptor())
            .addNetworkInterceptor(AuthRedirectSanitizer())
            .apply {
                if (BuildConfig.DEBUG) {
                    // BASIC = method/URL/status only; auth headers are never logged.
                    addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
            }
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(60))
            .build()

    /**
     * SSE client: zero read timeout so long-lived token streams are never cut
     * off mid-reply. The SSE path builds its Request manually (fresh config per
     * call), so the dynamic interceptor is not attached here.
     */
    @Provides
    @Singleton
    @Named("sse")
    fun provideSseOkHttpClient(@Named("okhttp-base") base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .addInterceptor(CleartextGuardInterceptor())
            .addNetworkInterceptor(AuthRedirectSanitizer())
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ZERO)
            .build()

    /**
     * Fire-and-forget local webhook client. Deliberately does not attach
     * [DynamicApiInterceptor], so the user-supplied URL is used verbatim
     * (never rewritten to the live AI provider), and uses short timeouts so a
     * slow or unreachable automation endpoint never lingers on a save.
     */
    @Provides
    @Singleton
    @Named("webhook")
    fun provideWebhookOkHttpClient(@Named("okhttp-base") base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .addInterceptor(CleartextGuardInterceptor())
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(5))
            .build()

    /**
     * Isolated WebDAV / Drive backup client. Never attached to
     * [DynamicApiInterceptor], so encrypted blobs are not rewritten to the AI
     * provider or tagged with a bearer token. Logging is omitted even in debug
     * so ciphertext and credentials never hit logcat.
     */
    @Provides
    @Singleton
    @Named("webdav")
    fun provideWebDavOkHttpClient(@Named("okhttp-base") base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .addInterceptor(CleartextGuardInterceptor())
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(120))
            .writeTimeout(Duration.ofSeconds(120))
            .build()

    /**
     * Isolated Nightscout poller. Never attached to [DynamicApiInterceptor],
     * so the AI bearer token cannot leak to a user-supplied Nightscout host.
     * Logging is omitted even in debug: SGV JSON is glucose, and token auth
     * puts the access token in the query string.
     *
     * FEATURE: cgm-direct-ingest
     */
    @Provides
    @Singleton
    @Named("nightscout")
    fun provideNightscoutOkHttpClient(@Named("okhttp-base") base: OkHttpClient): OkHttpClient =
        base.newBuilder()
            .addInterceptor(CleartextGuardInterceptor())
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(Duration.ofSeconds(20))
            .readTimeout(Duration.ofSeconds(30))
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAiApi(retrofit: Retrofit): AiApi = retrofit.create(AiApi::class.java)

    @Provides
    @Singleton
    fun provideOpenFoodFactsUserAgentInterceptor(): OpenFoodFactsUserAgentInterceptor =
        OpenFoodFactsUserAgentInterceptor()

    @Provides
    @Singleton
    fun provideOpenFoodFactsThrottleInterceptor(): OpenFoodFactsThrottleInterceptor =
        OpenFoodFactsThrottleInterceptor()

    /**
     * Isolated Open Food Facts client. Derived from [provideBaseOkHttpClient]
     * so it shares the dispatcher and pool, but never sees
     * [DynamicApiInterceptor], so barcode lookups are never rewritten to the
     * user's live AI provider nor tagged with their AI bearer token.
     */
    @Provides
    @Singleton
    @Named("openfoodfacts")
    fun provideOffOkHttpClient(
        @Named("okhttp-base") base: OkHttpClient,
        userAgent: OpenFoodFactsUserAgentInterceptor,
        throttle: OpenFoodFactsThrottleInterceptor,
    ): OkHttpClient =
        base.newBuilder()
            .addInterceptor(userAgent)
            .addInterceptor(throttle)
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .build()

    fun provideOpenFoodFactsOkHttpClient(): OkHttpClient =
        provideOffOkHttpClient(
            provideBaseOkHttpClient(),
            OpenFoodFactsUserAgentInterceptor(),
            OpenFoodFactsThrottleInterceptor(),
        )

    @Provides
    @Singleton
    @Named("openfoodfacts")
    fun provideOffRetrofit(@Named("openfoodfacts") client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://world.openfoodfacts.org/")
            .client(client)
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()

    fun provideOpenFoodFactsRetrofit(client: OkHttpClient): Retrofit = provideOffRetrofit(client)

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(@Named("openfoodfacts") retrofit: Retrofit): OpenFoodFactsApi =
        retrofit.create(OpenFoodFactsApi::class.java)

    @Provides
    @Singleton
    @OffHttpClient
    fun provideOffHttpClientQualifier(@Named("openfoodfacts") client: OkHttpClient): OkHttpClient = client

    @Provides
    @Singleton
    @OffRetrofit
    fun provideOffRetrofitQualifier(@Named("openfoodfacts") retrofit: Retrofit): Retrofit = retrofit
}
