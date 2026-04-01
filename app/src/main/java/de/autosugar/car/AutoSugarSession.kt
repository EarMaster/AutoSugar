package de.autosugar.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.lifecycleScope
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import kotlinx.coroutines.launch

class AutoSugarSession(
    private val repository: NightscoutRepository,
    private val appPrefs: AppPreferencesDataStore,
) : Session() {

    override fun onCreateScreen(intent: Intent): Screen = LoadingScreen(carContext).also {
        lifecycleScope.launch {
            repository.profilesFlow.collect { profiles ->
                val nextScreen = when {
                    profiles.isEmpty() -> NoProfilesScreen(carContext, repository, appPrefs)
                    else -> {
                        val activeId = repository.activeProfileId.value ?: profiles.first().id
                        repository.setActiveProfile(activeId)
                        GlucoseScreen(carContext, repository, appPrefs, activeId)
                    }
                }
                screenManager.push(nextScreen)
            }
        }
    }
}
