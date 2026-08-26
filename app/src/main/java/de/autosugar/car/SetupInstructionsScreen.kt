package de.autosugar.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import de.autosugar.R

/**
 * Explains how to add a Nightscout source on the phone.
 *
 * Setting the app up is a phone-side task, and the car app quality guidelines forbid
 * surfacing setup tasks — or directing the driver to their phone — while the vehicle is
 * moving (IT-1, VI-1). [LongMessageTemplate] is the host-enforced parked-only template:
 * Android Auto refuses to render it while driving, and its builder only accepts actions
 * backed by a [ParkedOnlyOnClickListener]. The instruction text therefore also carries the
 * "only look at your phone when it is safe" wording VI-1 requires.
 */
class SetupInstructionsScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template = LongMessageTemplate.Builder(
        carContext.getString(R.string.msg_setup_instructions)
    )
        .setTitle(carContext.getString(R.string.title_setup_required))
        .setHeaderAction(Action.BACK)
        .addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.action_done))
                .setOnClickListener(ParkedOnlyOnClickListener.create { screenManager.pop() })
                .build()
        )
        .build()
}
