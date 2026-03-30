package com.autosugar.data.model

import androidx.annotation.DrawableRes
import com.autosugar.R
import java.util.UUID

enum class GlucoseUnit { MG_DL, MMOL_L }

enum class ProfileIcon(@DrawableRes val resId: Int) {
    PERSON(R.drawable.ic_profile_person),
    HOME(R.drawable.ic_profile_home),
    HEART(R.drawable.ic_profile_heart),
    STAR(R.drawable.ic_profile_star),
    CAR(R.drawable.ic_profile_car),
    MEDICAL(R.drawable.ic_profile_medical),
}

data class NightscoutProfile(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val baseUrl: String,
    val apiToken: String,
    val unit: GlucoseUnit = GlucoseUnit.MG_DL,
    val icon: ProfileIcon = ProfileIcon.PERSON,
)
