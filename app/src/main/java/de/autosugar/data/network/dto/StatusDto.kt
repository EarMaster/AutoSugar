package de.autosugar.data.network.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class StatusDto(
    val settings: SettingsDto?,
    val authorized: AuthorizedDto? = null,
)

/** Subset of the Nightscout `/api/v1/status.json` `authorized` object. */
@JsonClass(generateAdapter = true)
data class AuthorizedDto(
    /** Permission bitmask per resource pattern (e.g. `{"*": 1}`). READ=1, CREATE=2, UPDATE=4, DELETE=8. */
    val permissions: Map<String, Int> = emptyMap(),
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
