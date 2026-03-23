package com.autosugar.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import com.autosugar.data.repository.NightscoutRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AutoSugarSession(private val repository: NightscoutRepository) : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        val profiles = runBlocking { repository.profilesFlow.first() }
        return when {
            profiles.isEmpty() -> NoProfilesScreen(carContext, repository)
            else -> {
                // Default to the first profile; user can switch via ActionStrip
                val activeId = repository.activeProfileId.value ?: profiles.first().id
                repository.setActiveProfile(activeId)
                GlucoseScreen(carContext, repository, activeId)
            }
        }
    }
}
