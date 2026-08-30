package com.omb9.glucosehero.domain.model

/** UI-only category selector for the Add Entry sheet's icon grid. Never persisted. */
enum class EntryType { GLUCOSE, INSULIN, MEAL, ACTIVITY, NOTE }

enum class InsulinType { BOLUS, BASAL }

enum class ActivityIntensity { LIGHT, MODERATE, INTENSE }

enum class MealContext { NONE, FASTING, BEFORE_MEAL, AFTER_MEAL, BEDTIME }
