package com.omb9.glucosehero.crisis

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends caregiver SOS texts and waits for the radio sentIntent instead of
 * treating a non-throwing [SmsManager.sendTextMessage] as success.
 *
 * Without [android.Manifest.permission.SEND_SMS], the honest path is
 * [Intent.ACTION_SENDTO]: the message is handed to the user's messaging app,
 * not confirmed on the radio.
 */
@Singleton
class HypoSosSmsSender @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val requestCodes = AtomicInteger(REQUEST_SMS_SENT_BASE)

    suspend fun dispatch(
        phone: String,
        body: String,
        hasSmsPermission: Boolean,
    ): SmsSendStatus {
        val normalized = phone.filter { it.isDigit() || it == '+' }
        if (normalized.isBlank()) return SmsSendStatus.FAILED
        return if (hasSmsPermission) {
            sendViaRadio(normalized, body)
        } else {
            if (handToMessagingApp(normalized, body)) {
                SmsSendStatus.HANDED_TO_MESSAGING_APP
            } else {
                SmsSendStatus.FAILED
            }
        }
    }

    private suspend fun sendViaRadio(phone: String, body: String): SmsSendStatus {
        val smsManager = smsManager() ?: return SmsSendStatus.FAILED
        return try {
            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                sendMultipart(smsManager, phone, parts)
            } else {
                awaitSent(phone) { sentIntent ->
                    smsManager.sendTextMessage(phone, null, body, sentIntent, null)
                }
            }
        } catch (_: Exception) {
            SmsSendStatus.FAILED
        }
    }

    private suspend fun sendMultipart(
        smsManager: SmsManager,
        phone: String,
        parts: ArrayList<String>,
    ): SmsSendStatus {
        val tokens = parts.map { UUID.randomUUID().toString() }
        val deferreds = tokens.map { HypoSosSmsSentBroker.register(it) }
        val sentIntents = ArrayList(
            tokens.map { token -> sentPendingIntent(token, phone) },
        )
        return try {
            smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
            val codes = withTimeoutOrNull(SENT_TIMEOUT_MS) {
                deferreds.map { it.await() }
            }
            if (codes == null) SmsSendStatus.FAILED else HypoSosSmsResult.combinePartCodes(codes)
        } catch (_: Exception) {
            SmsSendStatus.FAILED
        } finally {
            tokens.forEach { HypoSosSmsSentBroker.forget(it) }
        }
    }

    private suspend fun awaitSent(
        phone: String,
        send: (PendingIntent) -> Unit,
    ): SmsSendStatus {
        val token = UUID.randomUUID().toString()
        val deferred = HypoSosSmsSentBroker.register(token)
        return try {
            send(sentPendingIntent(token, phone))
            val code = withTimeoutOrNull(SENT_TIMEOUT_MS) { deferred.await() }
            HypoSosSmsResult.fromSentCode(code)
        } catch (_: Exception) {
            SmsSendStatus.FAILED
        } finally {
            HypoSosSmsSentBroker.forget(token)
        }
    }

    private fun handToMessagingApp(phone: String, body: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")).apply {
            putExtra("sms_body", body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun sentPendingIntent(token: String, phone: String): PendingIntent {
        val intent = Intent(context, HypoSosSmsSentReceiver::class.java).apply {
            action = ACTION_SMS_SENT
            data = Uri.parse("glucosehero-sms://sent/$token")
            putExtra(EXTRA_TOKEN, token)
            putExtra(EXTRA_PHONE, phone)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodes.getAndIncrement(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag(),
        )
    }

    private fun smsManager(): SmsManager? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    } catch (_: Exception) {
        null
    }

    private fun mutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }

    companion object {
        const val ACTION_SMS_SENT = "com.omb9.glucosehero.action.HYPO_SOS_SMS_SENT"
        const val EXTRA_TOKEN = "sms_sent_token"
        const val EXTRA_PHONE = "sms_sent_phone"
        const val SENT_TIMEOUT_MS: Long = 20_000L
        private const val REQUEST_SMS_SENT_BASE = 4400
    }
}
