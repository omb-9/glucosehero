package com.omb9.glucosehero.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingAiQueryTtlTest {

    @Test
    fun defaultAndMaxValidityAreThirtyMinutes() {
        assertEquals(30 * 60, PendingAiQueryTtl.DEFAULT_TTL_SECONDS)
        assertEquals(30 * 60, PendingAiQueryTtl.MAX_VALIDITY_SECONDS)
    }

    @Test
    fun normalizeForInsertCapsAtThirtyMinutesAndRejectsNonPositive() {
        assertEquals(1_800, PendingAiQueryTtl.normalizeForInsert(0))
        assertEquals(1_800, PendingAiQueryTtl.normalizeForInsert(-5))
        assertEquals(1_800, PendingAiQueryTtl.normalizeForInsert(7_200))
        assertEquals(60, PendingAiQueryTtl.normalizeForInsert(60))
    }

    @Test
    fun storedZeroTtlIsUnknownAgeAndExpiresImmediately() {
        val now = 1_700_000_000_000L
        assertTrue(PendingAiQueryTtl.isExpired(createdAt = now, ttlSeconds = 0, nowMillis = now))
        assertTrue(
            PendingAiQueryEntity(
                userMessageId = 1L,
                prompt = "sugar is 65",
                createdAt = now - 1_000L,
                ttlSeconds = 0,
            ).isExpired(now),
        )
    }

    @Test
    fun freshDefaultTtlIsNotExpired() {
        val createdAt = 1_000_000L
        val now = createdAt + 10 * 60 * 1000L
        assertFalse(
            PendingAiQueryTtl.isExpired(
                createdAt = createdAt,
                ttlSeconds = PendingAiQueryTtl.DEFAULT_TTL_SECONDS,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun acuteQueryExpiresAfterThirtyMinutes() {
        val createdAt = 1_000_000L
        val justOver = createdAt + 30 * 60 * 1000L + 1L
        assertTrue(
            PendingAiQueryTtl.isExpired(
                createdAt = createdAt,
                ttlSeconds = PendingAiQueryTtl.DEFAULT_TTL_SECONDS,
                nowMillis = justOver,
            ),
        )
    }

    @Test
    fun perQueryTtlCanBeShorterThanDefault() {
        val createdAt = 5_000L
        assertFalse(
            PendingAiQueryTtl.isExpired(createdAt, ttlSeconds = 60, nowMillis = createdAt + 59_000L),
        )
        assertTrue(
            PendingAiQueryTtl.isExpired(createdAt, ttlSeconds = 60, nowMillis = createdAt + 61_000L),
        )
    }

    @Test
    fun longerRequestedTtlIsStillCappedAtMaxValidity() {
        val createdAt = 0L
        val afterMax = createdAt + PendingAiQueryTtl.MAX_VALIDITY_SECONDS * 1000L + 1L
        assertTrue(
            PendingAiQueryTtl.isExpired(createdAt, ttlSeconds = 86_400, nowMillis = afterMax),
        )
    }

    @Test
    fun futureCreatedAtIsExpiredForSafety() {
        assertTrue(
            PendingAiQueryTtl.isExpired(createdAt = 2_000L, ttlSeconds = 1_800, nowMillis = 1_000L),
        )
    }
}
