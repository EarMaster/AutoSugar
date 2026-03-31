package de.autosugar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import de.autosugar.R
import de.autosugar.data.model.NightscoutProfile
import de.autosugar.data.repository.NightscoutRepository
import kotlinx.coroutines.launch

class SourceSelectScreen(
    carContext: CarContext,
    private val repository: NightscoutRepository,
    private val onProfileSelected: (String) -> Unit,
) : Screen(carContext) {

    private var profiles: List<NightscoutProfile> = emptyList()

    init {
        lifecycleScope.launch {
            repository.profilesFlow.collect { updated ->
                profiles = updated
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val itemList = ItemList.Builder().apply {
            profiles.forEach { profile ->
                addItem(Row.Builder()
                    .setTitle(profile.displayName)
                    .addText(profile.baseUrl)
                    .setOnClickListener {
                        onProfileSelected(profile.id)
                        screenManager.pop()
                    }
                    .build())
            }
        }.build()

        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.action_switch_source))
            .setSingleList(itemList)
            .setHeaderAction(androidx.car.app.model.Action.BACK)
            .build()
    }
}
