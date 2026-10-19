package com.lycosp.health

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthDTO(
    val status: String,
    val db: String,
    @SerialName("uptime_s") val uptime: Long? = null,
    val error: String? = null
)
