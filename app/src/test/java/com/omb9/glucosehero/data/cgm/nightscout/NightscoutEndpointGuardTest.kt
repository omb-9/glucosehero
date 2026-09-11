package com.omb9.glucosehero.data.cgm.nightscout

import com.omb9.glucosehero.data.remote.TrustedHosts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightscoutEndpointGuardTest {

    @Test
    fun publicHttpsNeedsAcknowledgment() {
        val status = NightscoutEndpointGuard.evaluate(
            "https://mysite.example.com",
            emptySet(),
        )
        assertTrue(status is NightscoutEndpointGuard.Status.NeedsAcknowledgment)
        assertEquals(
            "mysite.example.com",
            (status as NightscoutEndpointGuard.Status.NeedsAcknowledgment).host,
        )
    }

    @Test
    fun acknowledgedPublicHttpsIsAllowed() {
        assertEquals(
            NightscoutEndpointGuard.Status.Allowed,
            NightscoutEndpointGuard.evaluate(
                "https://mysite.example.com",
                setOf("mysite.example.com"),
            ),
        )
        assertTrue(
            TrustedHosts.isAllowed("mysite.example.com", setOf("mysite.example.com")),
        )
    }

    @Test
    fun publicHttpIsHttpsRequiredEvenIfAcknowledged() {
        assertEquals(
            NightscoutEndpointGuard.Status.HttpsRequired,
            NightscoutEndpointGuard.evaluate("http://mysite.example.com", emptySet()),
        )
        assertEquals(
            NightscoutEndpointGuard.Status.HttpsRequired,
            NightscoutEndpointGuard.evaluate(
                "http://mysite.example.com",
                setOf("mysite.example.com"),
            ),
        )
    }

    @Test
    fun lanHttpAllowedOnlyAfterAcknowledgment() {
        assertEquals(
            NightscoutEndpointGuard.Status.NeedsAcknowledgment::class,
            NightscoutEndpointGuard.evaluate(
                "http://192.168.1.50:1337",
                emptySet(),
            )::class,
        )
        assertEquals(
            NightscoutEndpointGuard.Status.Allowed,
            NightscoutEndpointGuard.evaluate(
                "http://192.168.1.50:1337",
                setOf("192.168.1.50"),
            ),
        )
    }

    @Test
    fun builtInTrustedSuffixesAreNotImplicitNightscoutHosts() {
        assertFalse(TrustedHosts.TRUSTED_SUFFIXES.contains("nightscout.org"))
        assertFalse(TrustedHosts.TRUSTED_SUFFIXES.contains("herokuapp.com"))
        val status = NightscoutEndpointGuard.evaluate(
            "https://glucosehero.app",
            emptySet(),
        )
        assertTrue(status is NightscoutEndpointGuard.Status.NeedsAcknowledgment)
    }
}
