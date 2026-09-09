package com.omb9.glucosehero.crisis

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CaregiverContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
)

@Serializable
data class HypoSosPending(
    val startedAtMillis: Long,
    val timeoutAtMillis: Long,
    val glucoseMgdl: Double,
    val velocityMgdlPerMin: Double,
    val trendLabel: String,
)

data class GlucoseTrendSnapshot(
    val glucoseMgdl: Double,
    val timestampMillis: Long,
    val velocityMgdlPerMin: Double,
)
