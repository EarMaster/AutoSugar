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
    /**
     * One entry per granted role, each a list of Apache-Shiro permission strings such as
     * `*:*:read` (read-only), `api:treatments:create` (write) or `*` (admin/full access).
     */
    val permissionGroups: List<List<String>> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class SettingsDto(
    val thresholds: ThresholdsDto?,
)

@JsonClass(generateAdapter = true)
data class ThresholdsDto(
    val bgHigh: Double?,
    val bgTargetTop: Double?,
    val bgTargetBottom: Double?,
    val bgLow: Double?,
)
