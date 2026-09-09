package com.omb9.glucosehero.data.remote.off

import com.omb9.glucosehero.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that sets the mandatory User-Agent header for Open Food Facts API requests.
 *
 * Open Food Facts requires applications to identify themselves with their app name, version,
 * and contact email/URL to prevent API bans and ensure compliance:
 * `User-Agent: GlucoseHero/${BuildConfig.VERSION_NAME} (outreach@chromagrid.com)`
 *
 * Contact email: outreach@chromagrid.com is the project outreach mailbox used
 * because the repo does not publish a dedicated Open Food Facts contact address.
 */
@Singleton
class OpenFoodFactsUserAgentInterceptor(
    private val contactEmail: String = CONTACT_EMAIL,
    private val versionName: String = BuildConfig.VERSION_NAME,
) : Interceptor {

    @Inject
    constructor() : this(CONTACT_EMAIL, BuildConfig.VERSION_NAME)

    val userAgent: String = "GlucoseHero/$versionName ($contactEmail)"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(HEADER_USER_AGENT, userAgent)
            .build()
        return chain.proceed(request)
    }

    companion object {
        const val HEADER_USER_AGENT = "User-Agent"
        const val CONTACT_EMAIL = "outreach@chromagrid.com"
        val DEFAULT_USER_AGENT = "GlucoseHero/${BuildConfig.VERSION_NAME} ($CONTACT_EMAIL)"
    }
}

typealias OffUserAgentInterceptor = OpenFoodFactsUserAgentInterceptor
typealias UserAgentInterceptor = OpenFoodFactsUserAgentInterceptor
