package com.omb9.glucosehero.crisis

import android.app.Activity
import android.telephony.SmsManager

/**
 * Per-recipient SMS outcome. Success is only [SENT] ([Activity.RESULT_OK]
 * from the sentIntent). "At least one send() call did not throw" is not
 * success: the radio or carrier can still drop the message.
 */
enum class SmsSendStatus {
    SENT,
    NO_SERVICE,
    RADIO_OFF,
    FAILED,
    HANDED_TO_MESSAGING_APP,
}

data class RecipientSmsResult(
    val label: String,
    val status: SmsSendStatus,
)

object HypoSosSmsResult {

    fun fromSentCode(code: Int?): SmsSendStatus = when (code) {
        Activity.RESULT_OK -> SmsSendStatus.SENT
        SmsManager.RESULT_ERROR_NO_SERVICE -> SmsSendStatus.NO_SERVICE
        SmsManager.RESULT_ERROR_RADIO_OFF -> SmsSendStatus.RADIO_OFF
        else -> SmsSendStatus.FAILED
    }

    /**
     * Multipart SMS: every part must be [Activity.RESULT_OK]. Actionable
     * radio failures win over a generic part failure so the user sees
     * "no signal" / "radio off" instead of a vague error.
     */
    fun combinePartCodes(codes: List<Int>): SmsSendStatus {
        if (codes.isEmpty()) return SmsSendStatus.FAILED
        val statuses = codes.map { fromSentCode(it) }
        return when {
            statuses.any { it == SmsSendStatus.NO_SERVICE } -> SmsSendStatus.NO_SERVICE
            statuses.any { it == SmsSendStatus.RADIO_OFF } -> SmsSendStatus.RADIO_OFF
            statuses.any { it == SmsSendStatus.FAILED } -> SmsSendStatus.FAILED
            statuses.all { it == SmsSendStatus.SENT } -> SmsSendStatus.SENT
            else -> SmsSendStatus.FAILED
        }
    }
}
