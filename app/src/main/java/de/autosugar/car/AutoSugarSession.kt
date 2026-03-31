package de.autosugar.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AutoSugarSession(
    private val repository: NightscoutRepository,
    private val appPrefs: AppPreferencesDataStore,
) : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        val profiles = runBlocking { repository.profilesFlow.first() }
        return when {
            profiles.isEmpty() -> NoProfilesScreen(carContext, repository, appPrefs)
            else -> {
                val activeId = repository.activeProfileId.value ?: profiles.first().id
                repository.setActiveProfile(activeId)
                GlucoseScreen(carContext, repository, appPrefs, activeId)
            }
        }
    }
}
