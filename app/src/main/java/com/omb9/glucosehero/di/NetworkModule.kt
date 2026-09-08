package com.omb9.glucosehero.di

import com.omb9.glucosehero.BuildConfig
import com.omb9.glucosehero.data.remote.AiApi
import com.omb9.glucosehero.data.remote.CleartextGuardInterceptor
import com.omb9.glucosehero.data.remote.DynamicApiInterceptor
import com.omb9.glucosehero.data.remote.off.OffHttpClient
import com.omb9.glucosehero.data.remote.off.OffRetrofit
import com.omb9.glucosehero.data.remote.off.OpenFoodFactsApi
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

    @Provides
    @Singleton
    fun provideOkHttpClient(dynamicApiInterceptor: DynamicApiInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(dynamicApiInterceptor)
            .addInterceptor(CleartextGuardInterceptor())
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
    fun provideSseOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(CleartextGuardInterceptor())
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
    fun provideWebhookOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(CleartextGuardInterceptor())
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(5))
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

    /**
     * Isolated Open Food Facts client. Deliberately built on a fresh
     * [OkHttpClient.Builder] that never sees [DynamicApiInterceptor], so
     * barcode lookups are never rewritten to the user's live AI provider
     * nor tagged with their AI bearer token.
     */
    @Provides
    @Singleton
    @Named("openfoodfacts")
    fun provideOffOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(OpenFoodFactsUserAgentInterceptor())
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(15))
            .build()

    fun provideOpenFoodFactsOkHttpClient(): OkHttpClient = provideOffOkHttpClient()

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
