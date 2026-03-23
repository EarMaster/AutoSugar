package com.autosugar.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EntryDto(
    val sgv: Int,
    val direction: String?,
    val date: Long,
    @Json(name = "dateString") val dateString: String?,
    val delta: Double?,
)
