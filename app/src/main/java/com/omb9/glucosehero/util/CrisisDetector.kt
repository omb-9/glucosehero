package com.omb9.glucosehero.util

/**
 * On-device crisis helpers: journal-text matching plus severe hypoglycemia.
 *
 * Journal matching is a pure Kotlin keyword/phrase check (no Android imports,
 * no network). When journal text is later wired into `buildSystemPrompt`, this
 * check MUST run first and short-circuit: a matching journal must never be
 * sent to the AI.
 *
 * Severe hypo uses the ADA Level 2 threshold of 54 mg/dL. SOS dispatch lives
 * in `com.omb9.glucosehero.crisis`; this object only classifies readings.
 */
object CrisisDetector {

    /** ADA Level 2 hypoglycemia, canonical mg/dL. */
    const val SEVERE_HYPO_MGDL: Double = 54.0

    /** Recovery threshold used to auto-cancel a pending SOS, canonical mg/dL. */
    const val HYPO_RECOVERY_MGDL: Double = 70.0

    /** Default caregiver-dispatch timeout if the user does not dismiss. */
    const val DEFAULT_SOS_TIMEOUT_MINUTES: Int = 5

    const val MIN_SOS_TIMEOUT_MINUTES: Int = 5
    const val MAX_SOS_TIMEOUT_MINUTES: Int = 10

    private val phrases = listOf(
        "kill myself",
        "want to die",
        "don't want to live",
        "end my life",
        "suicide",
        "suicidal",
        "hurt myself",
        "harm myself",
        "self harm",
        "self-harm",
    )

    fun isCrisis(journal: String?): Boolean {
        if (journal.isNullOrBlank()) return false
        val text = journal.lowercase()
        return phrases.any { text.contains(it) }
    }

    /** True when [glucoseMgdl] is a confirmed Level 2 low. Null readings are not crises. */
    fun isSevereHypoglycemia(glucoseMgdl: Double?): Boolean {
        if (glucoseMgdl == null) return false
        if (!glucoseMgdl.isFinite()) return false
        return glucoseMgdl < SEVERE_HYPO_MGDL
    }

    fun isHypoRecovered(glucoseMgdl: Double?): Boolean {
        if (glucoseMgdl == null) return false
        if (!glucoseMgdl.isFinite()) return false
        return glucoseMgdl >= HYPO_RECOVERY_MGDL
    }

    fun clampSosTimeoutMinutes(minutes: Int): Int =
        minutes.coerceIn(MIN_SOS_TIMEOUT_MINUTES, MAX_SOS_TIMEOUT_MINUTES)
}
