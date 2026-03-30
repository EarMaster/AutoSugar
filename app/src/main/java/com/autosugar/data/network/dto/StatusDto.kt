package com.autosugar.data.network.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StatusDto(
    val settings: SettingsDto?,
)

@JsonClass(generateAdapter = true)
data class SettingsDto(
    val thresholds: ThresholdsDto?,
)

@JsonClass(generateAdapter = true)
data class ThresholdsDto(
    val bgHigh: Int?,
    val bgTargetTop: Int?,
    val bgTargetBottom: Int?,
    val bgLow: Int?,
)
