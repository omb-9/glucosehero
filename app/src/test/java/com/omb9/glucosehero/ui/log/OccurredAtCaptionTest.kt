package com.omb9.glucosehero.ui.log

import com.omb9.glucosehero.ui.components.isCalendarYearSelectable
import com.omb9.glucosehero.ui.components.isUtcDateOnOrBeforeToday
import com.omb9.glucosehero.ui.components.toUtcDateMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class OccurredAtCaptionTest {

    private val now = 1_700_000_000_000L

    @Test
    fun nullOccurredAtIsNow() {
        assertEquals(OccurredAtCaption.Now, occurredAtCaption(null, now))
    }

    @Test
    fun underOneHourUsesMinutes() {
        assertEquals(
            OccurredAtCaption.MinutesAgo(0),
            occurredAtCaption(now, now),
        )
        assertEquals(
            OccurredAtCaption.MinutesAgo(59),
            occurredAtCaption(now - 59 * 60_000L, now),
        )
    }

    @Test
    fun fromOneHourToUnderOneDayUsesHours() {
        assertEquals(
            OccurredAtCaption.HoursAgo(1),
            occurredAtCaption(now - 60 * 60_000L, now),
        )
        assertEquals(
            OccurredAtCaption.HoursAgo(23),
            occurredAtCaption(now - 23 * 60 * 60_000L, now),
        )
    }

    @Test
    fun pastTwentyFourHoursUsesAbsoluteDate() {
        val occurred = now - 24 * 60 * 60_000L
        assertEquals(OccurredAtCaption.Absolute(occurred), occurredAtCaption(occurred, now))
        val older = now - 25 * 60 * 60_000L
        assertEquals(OccurredAtCaption.Absolute(older), occurredAtCaption(older, now))
    }

    @Test
    fun futureTimestampIsTreatedAsZeroElapsed() {
        assertEquals(
            OccurredAtCaption.MinutesAgo(0),
            occurredAtCaption(now + 5 * 60_000L, now),
        )
    }

    @Test
    fun absoluteLabelIncludesDateAnd24HourTime() {
        val millis = LocalDate.of(2020, 1, 15)
            .atTime(14, 30)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val nowMillis = LocalDate.of(2026, 9, 15)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        assertEquals(OccurredAtCaption.Absolute(millis), occurredAtCaption(millis, nowMillis))
        assertEquals(
            com.omb9.glucosehero.util.Formatters.dayHeader(LocalDate.of(2020, 1, 15)) + " · 14:30",
            formatAbsoluteOccurredAt(millis, use24HourTime = true),
        )
    }

    @Test
    fun clampDropsFutureTimesAndKeepsNull() {
        assertNull(clampOccurredAtMillis(null, now))
        assertEquals(now - 1, clampOccurredAtMillis(now - 1, now))
        assertEquals(now, clampOccurredAtMillis(now + 60_000L, now))
    }
}

class SelectableUtcDatesTest {

    private val today = LocalDate.of(2026, 9, 15)

    @Test
    fun todayAndPastDatesAreSelectable() {
        val todayUtc = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val yesterdayUtc = today.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertTrue(isUtcDateOnOrBeforeToday(todayUtc, today))
        assertTrue(isUtcDateOnOrBeforeToday(yesterdayUtc, today))
    }

    @Test
    fun futureDatesAreRejected() {
        val tomorrowUtc = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertFalse(isUtcDateOnOrBeforeToday(tomorrowUtc, today))
    }

    @Test
    fun futureYearsAreRejected() {
        assertTrue(isCalendarYearSelectable(2026, today))
        assertTrue(isCalendarYearSelectable(2019, today))
        assertFalse(isCalendarYearSelectable(2027, today))
    }

    @Test
    fun toUtcDateMillisUsesLocalCalendarDate() {
        val localNoon = today.atTime(LocalTime.of(15, 30))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val utcMillis = localNoon.toUtcDateMillis()
        val utcDate = java.time.Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        assertEquals(today, utcDate)
    }
}
