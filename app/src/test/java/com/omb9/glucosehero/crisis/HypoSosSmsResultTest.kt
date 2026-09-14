package com.omb9.glucosehero.crisis

import android.app.Activity
import android.telephony.SmsManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HypoSosSmsResultTest {

    @Test
    fun resultOkIsSent() {
        assertEquals(SmsSendStatus.SENT, HypoSosSmsResult.fromSentCode(Activity.RESULT_OK))
    }

    @Test
    fun noServiceIsDistinctFromGenericFailure() {
        assertEquals(
            SmsSendStatus.NO_SERVICE,
            HypoSosSmsResult.fromSentCode(SmsManager.RESULT_ERROR_NO_SERVICE),
        )
    }

    @Test
    fun radioOffIsDistinctFromGenericFailure() {
        assertEquals(
            SmsSendStatus.RADIO_OFF,
            HypoSosSmsResult.fromSentCode(SmsManager.RESULT_ERROR_RADIO_OFF),
        )
    }

    @Test
    fun genericFailureAndTimeoutAreFailed() {
        assertEquals(
            SmsSendStatus.FAILED,
            HypoSosSmsResult.fromSentCode(SmsManager.RESULT_ERROR_GENERIC_FAILURE),
        )
        assertEquals(SmsSendStatus.FAILED, HypoSosSmsResult.fromSentCode(null))
    }

    @Test
    fun oneFailedMultipartPartIsNotSent() {
        assertEquals(
            SmsSendStatus.FAILED,
            HypoSosSmsResult.combinePartCodes(
                listOf(Activity.RESULT_OK, SmsManager.RESULT_ERROR_GENERIC_FAILURE),
            ),
        )
    }

    @Test
    fun noServicePartWinsOverOkParts() {
        assertEquals(
            SmsSendStatus.NO_SERVICE,
            HypoSosSmsResult.combinePartCodes(
                listOf(Activity.RESULT_OK, SmsManager.RESULT_ERROR_NO_SERVICE),
            ),
        )
    }

    @Test
    fun mixedRecipientsAreNotAggregatedAsSuccess() {
        val results = listOf(
            RecipientSmsResult("Alice", SmsSendStatus.SENT),
            RecipientSmsResult("Bob", SmsSendStatus.FAILED),
        )
        assertFalse(results.all { it.status == SmsSendStatus.SENT })
        assertTrue(results.any { it.status == SmsSendStatus.SENT })
        assertTrue(results.any { it.status == SmsSendStatus.FAILED })
    }

    @Test
    fun brokerDeliversSentResultCode() = runTest {
        val token = "sms-token-1"
        val deferred = HypoSosSmsSentBroker.register(token)
        HypoSosSmsSentBroker.complete(token, Activity.RESULT_OK)
        assertEquals(Activity.RESULT_OK, deferred.await())
    }

    @Test
    fun forgottenTokenDoesNotComplete() = runTest {
        val token = "sms-token-2"
        val deferred = HypoSosSmsSentBroker.register(token)
        HypoSosSmsSentBroker.forget(token)
        HypoSosSmsSentBroker.complete(token, Activity.RESULT_OK)
        assertFalse(deferred.isCompleted)
    }
}
