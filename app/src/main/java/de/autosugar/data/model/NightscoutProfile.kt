package de.autosugar.data.model

import androidx.annotation.DrawableRes
import de.autosugar.R
import java.util.UUID

enum class GlucoseUnit { MG_DL, MMOL_L }

enum class ProfileIcon(@DrawableRes val resId: Int) {
    // People — generic
    PERSON(R.drawable.ic_profile_person),
    // People — adults
    MAN(R.drawable.ic_profile_man),
    WOMAN(R.drawable.ic_profile_woman),
    // People — children
    BOY(R.drawable.ic_profile_boy),
    GIRL(R.drawable.ic_profile_girl),
    BABY(R.drawable.ic_profile_baby),
    // People — elderly
    ELDERLY_MAN(R.drawable.ic_profile_elderly_man),
    ELDERLY_WOMAN(R.drawable.ic_profile_elderly_woman),
    // Other
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
    val alertsEnabled: Boolean = false,
)
