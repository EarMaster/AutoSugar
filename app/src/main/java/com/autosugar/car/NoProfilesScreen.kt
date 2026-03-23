package com.autosugar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import com.autosugar.R
import com.autosugar.data.repository.NightscoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NoProfilesScreen(
    carContext: CarContext,
    private val repository: NightscoutRepository,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Watch for profiles being added on the phone side
        scope.launch {
            repository.profilesFlow.collect { profiles ->
                if (profiles.isNotEmpty()) {
                    val id = profiles.first().id
                    repository.setActiveProfile(id)
                    screenManager.push(GlucoseScreen(carContext, repository, id))
                }
            }
        }
    }

    override fun onGetTemplate(): Template = MessageTemplate.Builder(
        carContext.getString(R.string.label_no_profiles)
    ).setTitle(carContext.getString(R.string.app_name)).build()

    override fun onDestroy() {
        scope.cancel()
    }
}
