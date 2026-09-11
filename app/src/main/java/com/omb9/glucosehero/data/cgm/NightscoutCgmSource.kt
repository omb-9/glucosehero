package com.omb9.glucosehero.data.cgm

import com.omb9.glucosehero.data.cgm.nightscout.NightscoutAuth
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutAuthMode
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutCleartextException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutAuthException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutEndpointGuard
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutHostUnacknowledgedException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutHttpException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutInvalidUrlException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutLimits
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutMapper
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutMissingCredentialException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutMissingUrlException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutNetworkException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutParseException
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutUrls
import com.omb9.glucosehero.data.cgm.nightscout.NightscoutV1UnsupportedException
import com.omb9.glucosehero.data.local.datastore.SettingsDataStore
import com.omb9.glucosehero.data.local.entity.GlucoseSampleEntity
import com.omb9.glucosehero.data.local.entity.GlucoseSampleSource
import com.omb9.glucosehero.data.security.KeystoreManager
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Pulls SGV entries from a user-configured Nightscout instance.
 *
 * Why this exists: Nightscout is a read-only REST source. Mapping lives here;
 * persistence and fan-out stay in [CgmIngestService]. The HTTP client is the
 * `@Named("nightscout")` OkHttpClient, which must never carry
 * [com.omb9.glucosehero.data.remote.DynamicApiInterceptor] (that interceptor
 * attaches the AI bearer token).
 *
 * Assumptions:
 * - API v1 only: `GET /api/v1/entries/sgv.json`. A 404 is treated as a
 *   v3-only (or missing v1) instance rather than an empty reading list.
 * - Incremental cursor is [since] from Room. A zero cursor uses the bounded
 *   backfill window instead of pulling years.
 * - Outbound data is the auth secret and a millisecond time cursor. No
 *   glucose, notes, or AI keys are uploaded.
 *
 * FEATURE: cgm-direct-ingest
 */
@Singleton
class NightscoutCgmSource(
    private val client: OkHttpClient,
    private val loadSnapshot: suspend () -> NightscoutFetchSnapshot,
    private val decryptCredential: (String) -> String,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) : CgmSource {

    @Inject
    constructor(
        @Named("nightscout") client: OkHttpClient,
        settingsDataStore: SettingsDataStore,
        keystoreManager: KeystoreManager,
    ) : this(
        client = client,
        loadSnapshot = {
            NightscoutFetchSnapshot(
                url = settingsDataStore.nightscoutUrlSnapshot(),
                authMode = settingsDataStore.nightscoutAuthModeSnapshot(),
                encryptedCredential = settingsDataStore.encryptedNightscoutCredential(),
                acknowledgedHosts = settingsDataStore.acknowledgedNightscoutHostsSnapshot(),
                backfillHours = settingsDataStore.nightscoutBackfillHoursSnapshot(),
            )
        },
        decryptCredential = { blob -> keystoreManager.decrypt(blob) },
    )

    override val key: GlucoseSampleSource = GlucoseSampleSource.NIGHTSCOUT

    override suspend fun fetch(since: Long): List<GlucoseSampleEntity> =
        withContext(Dispatchers.IO) {
            val snapshot = loadSnapshot()
            val rawUrl = snapshot.url.trim()
            if (rawUrl.isBlank()) throw NightscoutMissingUrlException()

            val base = NightscoutUrls.parseBase(rawUrl) ?: throw NightscoutInvalidUrlException()
            when (val status = NightscoutEndpointGuard.evaluate(base, snapshot.acknowledgedHosts)) {
                NightscoutEndpointGuard.Status.InvalidUrl -> throw NightscoutInvalidUrlException()
                NightscoutEndpointGuard.Status.HttpsRequired -> throw NightscoutCleartextException()
                is NightscoutEndpointGuard.Status.NeedsAcknowledgment ->
                    throw NightscoutHostUnacknowledgedException()
                NightscoutEndpointGuard.Status.Allowed -> Unit
            }

            val encrypted = snapshot.encryptedCredential?.trim().orEmpty()
            if (encrypted.isEmpty()) throw NightscoutMissingCredentialException()
            val credential = try {
                decryptCredential(encrypted)
            } catch (_: GeneralSecurityException) {
                throw NightscoutMissingCredentialException()
            } catch (_: IllegalArgumentException) {
                throw NightscoutMissingCredentialException()
            }
            if (credential.isBlank()) throw NightscoutMissingCredentialException()

            val now = nowMillis()
            val floor = now - NightscoutLimits.backfillMillis(snapshot.backfillHours)
            val cursor = if (since <= 0L) floor else since
            val windowMillis = (now - cursor).coerceAtLeast(0L)
            val estimated = ((windowMillis / FIVE_MINUTES_MS) + 12L).toInt()
            val count = estimated.coerceIn(1, NightscoutLimits.MAX_ENTRY_COUNT)

            val urlBuilder = NightscoutUrls.requestUrl(base, cursor, count)
            val requestBuilder = Request.Builder()
            NightscoutAuth.apply(urlBuilder, requestBuilder, snapshot.authMode, credential)
            val request = requestBuilder.url(urlBuilder.build()).get().build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: NightscoutException) {
                throw e
            } catch (e: IOException) {
                if (isCleartextRefusal(e)) throw NightscoutCleartextException()
                throw NightscoutNetworkException()
            }

            response.use { http ->
                when (http.code) {
                    in 200..299 -> {
                        val body = http.body.string()
                        try {
                            NightscoutMapper.parseEntries(body)
                        } catch (e: NightscoutParseException) {
                            throw e
                        }
                    }
                    401, 403 -> throw NightscoutAuthException()
                    404 -> throw NightscoutV1UnsupportedException()
                    else -> throw NightscoutHttpException()
                }
            }
        }

    private fun isCleartextRefusal(error: IOException): Boolean {
        val message = error.message ?: return false
        return message.contains("unencrypted connection", ignoreCase = true) ||
            message.contains("Plain HTTP is only permitted", ignoreCase = true)
    }

    private companion object {
        const val FIVE_MINUTES_MS = 5L * 60L * 1000L
    }
}

/**
 * Nightscout settings needed for one fetch. The credential field is the
 * Keystore ciphertext, never plaintext.
 *
 * FEATURE: cgm-direct-ingest
 */
data class NightscoutFetchSnapshot(
    val url: String,
    val authMode: NightscoutAuthMode,
    val encryptedCredential: String?,
    val acknowledgedHosts: Set<String>,
    val backfillHours: Int,
)
