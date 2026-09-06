package com.omb9.glucosehero.domain.model

/**
 * Qualitative mood label stored alongside the 1–5 mood face scale.
 *
 * The five faces carry magnitude; this list carries quality. The label is
 * persisted as [display] text (not an enum ordinal) so adding a
 * feeling later requires no migration.
 */
enum class MoodLabel(val display: String) {
    EXCITED("Excited"),
    HAPPY("Happy"),
    CALM("Calm"),
    TIRED("Tired"),
    FOGGY("Foggy"),
    SHAKY("Shaky"),
    ANXIOUS("Anxious"),
    IRRITABLE("Irritable"),
    LOW("Low"),
    MOTIVATED("Motivated"),
    FRUSTRATED("Frustrated"),
    OVERWHELMED("Overwhelmed"),
}
