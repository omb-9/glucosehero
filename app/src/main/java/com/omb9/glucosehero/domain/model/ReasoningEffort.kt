package com.omb9.glucosehero.domain.model

/**
 * User-selected reasoning budget for Hero chat.
 *
 * Maps to OpenRouter's `reasoning.effort` values
 * (`none` / `low` / `medium` / `high`). Default is [OFF] so managed-tier
 * calls (daily cap, billed reasoning tokens) stay cheap unless the user
 * opts in. BYOK models that emit no reasoning tokens must degrade
 * silently: the UI never shows an empty reasoning container.
 */
enum class ReasoningEffort(val label: String, val apiEffort: String?) {
    OFF("Off", null),
    LOW("Low", "low"),
    MEDIUM("Medium", "medium"),
    HIGH("High", "high");
}
