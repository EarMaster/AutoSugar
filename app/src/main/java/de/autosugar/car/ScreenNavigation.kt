package de.autosugar.car

import androidx.car.app.Screen

/**
 * Replaces everything above the root screen with [next].
 *
 * The Loading → NoProfiles → Glucose transitions can fire repeatedly during one session
 * (every time the last profile is deleted and a new one added), so a plain `push` would
 * grow the stack without bound and blow past the five-screen limit the car app quality
 * guidelines impose (AC-1).
 *
 * `popToRoot()` destroys the calling screen and cancels its `lifecycleScope`, but both
 * calls here are non-suspending: Kotlin cancellation is cooperative and only observed at
 * suspension points, so the `push` is guaranteed to run even when this is invoked from a
 * coroutine owned by the screen being popped. Do not introduce a suspension point between
 * the two calls.
 */
internal fun Screen.replaceStackWith(next: Screen) {
    screenManager.popToRoot()
    screenManager.push(next)
}
