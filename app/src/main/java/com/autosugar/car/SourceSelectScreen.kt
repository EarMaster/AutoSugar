package com.autosugar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.autosugar.R
import com.autosugar.data.model.NightscoutProfile
import com.autosugar.data.repository.NightscoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SourceSelectScreen(
    carContext: CarContext,
    private val repository: NightscoutRepository,
    private val onProfileSelected: (String) -> Unit,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var profiles: List<NightscoutProfile> = emptyList()

    init {
        scope.launch {
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

    override fun onDestroy() {
        scope.cancel()
    }
}
