package com.omb9.glucosehero.clinical

import kotlinx.serialization.Serializable

@Serializable
enum class ClinicalTestKind {
    OVERNIGHT_BASAL,
    MEAL_CARB_RATIO,
}

@Serializable
enum class ClinicalTestStatus {
    IDLE,
    RUNNING,
    INVALID,
    COMPLETE,
}

@Serializable
enum class ClinicalCalibrationHint {
    STABLE_NO_CHANGE,
    BASAL_MAY_BE_LOW,
    BASAL_MAY_BE_HIGH,
    ICR_MAY_BE_HIGH,
    ICR_MAY_BE_LOW,
    ISF_MAY_BE_LOW,
    ISF_MAY_BE_HIGH,
    INCONCLUSIVE,
}

@Serializable
data class ClinicalTestSession(
    val kind: ClinicalTestKind,
    val status: ClinicalTestStatus = ClinicalTestStatus.RUNNING,
    val startedAtMillis: Long,
    val plannedEndMillis: Long,
    val preMealGlucoseMgdl: Double? = null,
    val carbsGrams: Int? = null,
    val mealBolusUnits: Double? = null,
    val invalidReason: String? = null,
    val result: ClinicalTestResult? = null,
)

@Serializable
data class ClinicalTestResult(
    val kind: ClinicalTestKind,
    val startGlucoseMgdl: Double,
    val endGlucoseMgdl: Double,
    val minMgdl: Double,
    val maxMgdl: Double,
    val rangeMgdl: Double,
    val slopeMgdlPerHour: Double,
    val readingCount: Int,
    val hint: ClinicalCalibrationHint,
    val summary: String,
    val coveredSegmentStarts: List<String> = emptyList(),
    val attributionInconclusive: Boolean = false,
)

data class GlucoseObservation(
    val timestampMillis: Long,
    val glucoseMgdl: Double,
)

data class InterferingEvent(
    val timestampMillis: Long,
    val hasCarbs: Boolean,
    val hasBolus: Boolean,
)
