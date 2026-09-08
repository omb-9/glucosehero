package com.omb9.glucosehero.util

/**
 * On-device, offline crisis matcher for journal text.
 *
 * This is deliberately a pure Kotlin object: no Android imports and no network
 * access. It is a keyword/phrase match against a small fixed list, run entirely
 * on the device.
 *
 * When journal text is later wired into `buildSystemPrompt`, this check MUST run
 * first and short-circuit: a matching journal must never be sent to the AI.
 */
object CrisisDetector {

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
}
