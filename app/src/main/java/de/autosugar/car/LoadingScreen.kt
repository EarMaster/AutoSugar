package de.autosugar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import de.autosugar.R
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import kotlinx.coroutines.launch

class LoadingScreen(
    carContext: CarContext,
    private val repository: NightscoutRepository,
    private val appPrefs: AppPreferencesDataStore,
) : Screen(carContext) {

    init {
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

    override fun onGetTemplate(): Template = MessageTemplate.Builder(
        carContext.getString(R.string.label_loading)
    ).setTitle(carContext.getString(R.string.app_name)).build()
}
