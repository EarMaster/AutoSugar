package de.autosugar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import de.autosugar.R

class LoadingScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template = MessageTemplate.Builder(
        carContext.getString(R.string.label_loading)
    ).setTitle(carContext.getString(R.string.app_name)).build()
}
