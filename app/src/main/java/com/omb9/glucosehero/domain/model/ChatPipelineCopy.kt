package com.omb9.glucosehero.domain.model

/**
 * Truthful, present-tense labels for Hero's in-flight pipeline. Every string
 * is emitted only at the matching code path in the chat repository. Do not
 * add canned "analyzing your glucose" copy here.
 */
object ChatPipelineCopy {
    fun preparing(): String = "Preparing your request"

    fun readingGlucose(days: Int): String =
        "Reading last $days days of glucose readings"

    fun readingGlucoseCounted(days: Int, readings: Int): String =
        "Reading $days days, $readings readings"

    fun computingAverages(): String = "Computing 7, 14, and 30-day averages"

    fun computingTimeInRange(days: Int): String =
        "Computing time in range for the last $days days"

    fun loadingRecentEntries(count: Int): String =
        "Loading the $count most recent log entries"

    fun computingIob(): String = "Computing insulin on board from logged boluses"

    fun loadingFoodPatterns(): String = "Loading tagged meal patterns"

    fun connecting(): String = "Connecting to your AI provider"

    fun waiting(): String = "Waiting for the first token"

    fun streaming(): String = "Receiving Hero's answer"

    fun phaseAnnouncement(status: ChatPipelineStatus): String? = when (status) {
        ChatPipelineStatus.Idle, ChatPipelineStatus.Done -> null
        is ChatPipelineStatus.Failed -> status.message
        ChatPipelineStatus.Preparing -> preparing()
        is ChatPipelineStatus.GatheringContext -> status.label
        ChatPipelineStatus.Connecting -> connecting()
        ChatPipelineStatus.Waiting -> waiting()
        ChatPipelineStatus.Streaming -> streaming()
    }
}

object ChatHistoryPaging {
    const val PAGE_SIZE = 40
    const val MAX_IN_MEMORY = 200
}
