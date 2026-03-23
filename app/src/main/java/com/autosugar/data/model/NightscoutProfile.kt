package com.autosugar.data.model

import java.util.UUID

enum class GlucoseUnit { MG_DL, MMOL_L }

data class NightscoutProfile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val baseUrl: String,
    val apiToken: String,
    val unit: GlucoseUnit = GlucoseUnit.MG_DL,
)
