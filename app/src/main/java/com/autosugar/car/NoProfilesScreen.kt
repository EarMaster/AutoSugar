package com.autosugar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.autosugar.R
import com.autosugar.data.repository.NightscoutRepository
import com.autosugar.data.storage.AppPreferencesDataStore
import kotlinx.coroutines.launch

class NoProfilesScreen(
    carContext: CarContext,
    private val repository: NightscoutRepository,
    private val appPrefs: AppPreferencesDataStore,
) : Screen(carContext) {

    init {
        lifecycleScope.launch {
            repository.profilesFlow.collect { profiles ->
                if (profiles.isNotEmpty()) {
                    val id = profiles.first().id
                    repository.setActiveProfile(id)
                    screenManager.push(GlucoseScreen(carContext, repository, appPrefs, id))
                }
            }
        }
    }

    override fun onGetTemplate(): Template = MessageTemplate.Builder(
        carContext.getString(R.string.label_no_profiles)
    ).setTitle(carContext.getString(R.string.app_name)).build()
}
