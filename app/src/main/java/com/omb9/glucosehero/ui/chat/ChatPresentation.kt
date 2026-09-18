package com.omb9.glucosehero.ui.chat

import com.omb9.glucosehero.domain.model.ChatContextSummary
import com.omb9.glucosehero.util.CrisisDetector

/**
 * Flip to `true` to wrap assistant replies in a bubble again. Full-width
 * text on the chat canvas is the default.
 */
internal const val ASSISTANT_USES_BUBBLE = false

/** User-bubble max width as a fraction of the list's available width. */
internal const val USER_BUBBLE_WIDTH_FRACTION = 0.85f

internal const val TIMESTAMP_GAP_MILLIS = 5L * 60L * 1000L

internal const val THINKING_ELAPSED_REVEAL_MILLIS = 3_000L

/** Last visible item is within this many of the end of the list. */
internal const val AUTO_SCROLL_NEAR_BOTTOM_THRESHOLD = 3

internal const val MIN_GLUCOSE_POINTS_FOR_STARTERS = 3

internal fun isNearBottom(
    lastVisibleIndex: Int?,
    totalItems: Int,
    threshold: Int = AUTO_SCROLL_NEAR_BOTTOM_THRESHOLD,
): Boolean {
    if (totalItems <= 0 || lastVisibleIndex == null) return true
    return lastVisibleIndex >= totalItems - threshold
}

internal fun shouldFollowStream(nearBottom: Boolean, streaming: Boolean): Boolean =
    nearBottom && streaming

internal fun hasEnoughDataForGroundedChat(glucosePointCount: Int): Boolean =
    glucosePointCount >= MIN_GLUCOSE_POINTS_FOR_STARTERS

/** Streaming uses plain Text. Markdown parse runs once the reply is complete. */
internal fun shouldParseMarkdown(messageComplete: Boolean): Boolean = messageComplete

internal fun persistPartialOnStop(partialText: String): String? =
    partialText.takeIf { it.isNotBlank() }

internal fun chatComposerSendEnabled(
    input: String,
    streamingActive: Boolean,
    composerEnabled: Boolean,
    remainingCalls: Int?,
): Boolean {
    if (input.isBlank() || streamingActive) return false
    if (CrisisDetector.isCrisis(input)) return true
    return composerEnabled && remainingCalls != 0
}

internal fun ChatContextSummary.chipLabel(): String {
    val parts = buildList {
        add("$historyDays days")
        add("$readingCount readings")
        if (includedIob) add("IOB")
        if (includedCarbs) add("carbs")
    }
    return parts.joinToString(" · ")
}

internal fun ChatContextSummary.sheetLines(): List<String> = buildList {
    add("History window: $historyDays days")
    add("Glucose readings: $cgmReadingCount CGM, $manualReadingCount manual")
    add("Recent log entries: $recentEntryCount")
    add("Conversation turns sent: $conversationTurns")
    add("Insulin on board: ${if (includedIob) "included" else "not included"}")
    add("Carbs in recent entries: ${if (includedCarbs) "included" else "not included"}")
    add("Insulin doses in recent entries: ${if (includedInsulin) "included" else "not included"}")
    add("Meal descriptions: ${if (includedMeals) "included" else "not included"}")
    add(
        "Food patterns: " +
            if (foodPatternCount == 0) "none met the occurrence threshold" else "$foodPatternCount tags",
    )
    add("Profile details: ${if (includedProfile) "included" else "not included"}")
    if (medicationNamesWithheld) add("Medication names: withheld")
    if (feelingSickWithheld) add("Feeling-sick flags: not sent")
}
