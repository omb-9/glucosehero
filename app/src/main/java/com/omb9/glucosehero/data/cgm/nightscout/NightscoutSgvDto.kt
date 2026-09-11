package com.omb9.glucosehero.data.cgm.nightscout

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Nightscout v1 SGV entry. Extra fields are ignored.
 *
 * FEATURE: cgm-direct-ingest
 */
@Serializable
data class NightscoutSgvDto(
    @SerialName("_id") val id: String? = null,
    val sgv: Double? = null,
    val date: Long? = null,
    val mills: Long? = null,
    val direction: String? = null,
    val device: String? = null,
    val type: String? = null,
)
