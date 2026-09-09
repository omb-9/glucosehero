package com.omb9.glucosehero.ui.glance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefresherTest {

    @Test
    fun `detects TransactionTooLargeException by message when the type is wrapped`() {
        val wrapped = RuntimeException(
            "Failed to update widget",
            Exception("android.os.TransactionTooLargeException: data parcel size 1200000 bytes"),
        )
        assertTrue(isTransactionTooLarge(wrapped))
        assertFalse(isTransactionTooLarge(IllegalStateException("widget missing")))
    }
}
