package de.autosugar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import de.autosugar.R
import de.autosugar.data.repository.NightscoutRepository
import de.autosugar.data.storage.AppPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Shown while no Nightscout source is configured.
 *
 * The body is a plain status statement: it must not tell the driver to pick up their
 * phone, because setup is a phone-side task and the car app quality guidelines forbid
 * surfacing it while driving (IT-1, VI-1). The actual instructions live behind
 * [ParkedOnlyOnClickListener], so the host runs the action only when the car is parked
 * and otherwise tells the user it is unavailable while driving.
 */
class NoProfilesScreen(
    carContext: CarContext,
    private val repository: NightscoutRepository,
    private val appPrefs: AppPreferencesDataStore,
) : Screen(carContext) {

    init {
        lifecycleScope.launch {
            val profiles = repository.profilesFlow.first { it.isNotEmpty() }
            val id = profiles.first().id
            repository.setActiveProfile(id)
            replaceStackWith(GlucoseScreen(carContext, repository, appPrefs, id))
        }
    }

    override fun onGetTemplate(): Template = MessageTemplate.Builder(
        carContext.getString(R.string.label_not_set_up)
    )
        .setTitle(carContext.getString(R.string.app_name))
        .addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.action_how_to_set_up))
                .setOnClickListener(
                    ParkedOnlyOnClickListener.create {
                        screenManager.push(SetupInstructionsScreen(carContext))
                    }
                )
                .build()
        )
        .build()
}
