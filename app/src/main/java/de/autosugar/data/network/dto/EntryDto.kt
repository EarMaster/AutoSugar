package de.autosugar.data.network.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EntryDto(
    // Nullable: the entries feed can also contain non-sgv records (mbg meter-BG,
    // cal calibration) that carry no sgv field. Such records are filtered out by the
    // repository rather than failing the whole list during deserialization.
    val sgv: Double?,
    val direction: String?,
    val date: Long,
    @Json(name = "dateString") val dateString: String?,
    val delta: Double?,
)
