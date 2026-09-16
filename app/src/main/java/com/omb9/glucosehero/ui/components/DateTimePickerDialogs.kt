package com.omb9.glucosehero.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Converts a local timestamp to the UTC midnight millis the date picker uses
 * for that same calendar date.
 */
fun Long.toUtcDateMillis(): Long {
    val localDate = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return localDate
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
}

/** True when [utcTimeMillis] is a DatePicker UTC date on or before [today]. */
fun isUtcDateOnOrBeforeToday(utcTimeMillis: Long, today: LocalDate): Boolean {
    val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return !date.isAfter(today)
}

fun isCalendarYearSelectable(year: Int, today: LocalDate): Boolean = year <= today.year

@OptIn(ExperimentalMaterial3Api::class)
object PastOrTodaySelectableDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        isUtcDateOnOrBeforeToday(utcTimeMillis, LocalDate.now())

    override fun isSelectableYear(year: Int): Boolean =
        isCalendarYearSelectable(year, LocalDate.now())
}

/**
 * Date picker followed by a time picker. Confirming a date keeps the current
 * wall-clock time, then opens the time picker. Future calendar days are not
 * selectable, and a combined timestamp in the future is clamped to now.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialogs(
    timestampMillis: Long,
    use24HourTime: Boolean,
    showDatePicker: Boolean,
    showTimePicker: Boolean,
    onShowDatePickerChange: (Boolean) -> Unit,
    onShowTimePickerChange: (Boolean) -> Unit,
    onTimestampChange: (Long) -> Unit,
) {
    if (showDatePicker) {
        val today = LocalDate.now()
        val todayUtcMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = minOf(timestampMillis.toUtcDateMillis(), todayUtcMillis),
            yearRange = IntRange(DatePickerDefaults.YearRange.first, today.year),
            selectableDates = PastOrTodaySelectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { onShowDatePickerChange(false) },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis ->
                            if (!isUtcDateOnOrBeforeToday(selectedDateMillis, LocalDate.now())) {
                                onShowDatePickerChange(false)
                                return@TextButton
                            }
                            val selectedDate = Instant.ofEpochMilli(selectedDateMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            val currentTime = Instant.ofEpochMilli(timestampMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalTime()
                            onTimestampChange(
                                clampToNow(
                                    selectedDate
                                        .atTime(currentTime)
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli(),
                                ),
                            )
                            onShowTimePickerChange(true)
                        }
                        onShowDatePickerChange(false)
                    },
                ) {
                    Text("Next")
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowDatePickerChange(false) }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val zoned = Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault())
        val timePickerState = rememberTimePickerState(
            initialHour = zoned.hour,
            initialMinute = zoned.minute,
            is24Hour = use24HourTime,
        )
        TimePickerDialog(
            onDismiss = { onShowTimePickerChange(false) },
            onConfirm = {
                val date = Instant.ofEpochMilli(timestampMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                onTimestampChange(
                    clampToNow(
                        date
                            .atTime(timePickerState.hour, timePickerState.minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                    ),
                )
                onShowTimePickerChange(false)
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select time") },
        text = { content() },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("OK") }
        },
    )
}

private fun clampToNow(millis: Long): Long = millis.coerceAtMost(System.currentTimeMillis())
