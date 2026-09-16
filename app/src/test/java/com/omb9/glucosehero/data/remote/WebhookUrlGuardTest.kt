package com.omb9.glucosehero.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookUrlGuardTest {

    @Test
    fun emptyIsPersistableAndClearsTheDestination() {
        assertEquals(WebhookUrlGuard.Status.Empty, WebhookUrlGuard.evaluate(""))
        assertEquals(WebhookUrlGuard.Status.Empty, WebhookUrlGuard.evaluate("   "))
        assertTrue(WebhookUrlGuard.isPersistable(""))
    }

    @Test
    fun lanHttpIsAllowed() {
        assertEquals(
            WebhookUrlGuard.Status.Allowed,
            WebhookUrlGuard.evaluate("http://192.168.1.10:8123/webhook"),
        )
        assertTrue(WebhookUrlGuard.isPersistable("http://127.0.0.1:8123/hook"))
    }

    @Test
    fun publicHttpRequiresHttps() {
        assertEquals(
            WebhookUrlGuard.Status.HttpsRequired,
            WebhookUrlGuard.evaluate("http://example.com/webhook"),
        )
        assertFalse(WebhookUrlGuard.isPersistable("http://example.com/webhook"))
    }

    @Test
    fun publicHttpsIsAllowed() {
        assertEquals(
            WebhookUrlGuard.Status.Allowed,
            WebhookUrlGuard.evaluate("https://example.com/webhook"),
        )
        assertTrue(WebhookUrlGuard.isPersistable("https://example.com/webhook"))
    }

    @Test
    fun garbageIsInvalid() {
        assertEquals(WebhookUrlGuard.Status.InvalidUrl, WebhookUrlGuard.evaluate("not a url"))
        assertEquals(WebhookUrlGuard.Status.InvalidUrl, WebhookUrlGuard.evaluate("ftp://192.168.1.10/hook"))
        assertFalse(WebhookUrlGuard.isPersistable("not a url"))
    }
}
