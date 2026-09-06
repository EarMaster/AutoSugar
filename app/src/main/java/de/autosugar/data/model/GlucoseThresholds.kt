package de.autosugar.data.model

/**
 * Nightscout's threshold values, in mg/dL.
 *
 * [bgLow]/[bgHigh] and [bgTargetBottom]/[bgTargetTop] are always populated, because the graph
 * needs a scale and a target band to draw; where Nightscout reported nothing they hold
 * AutoSugar's display defaults.
 *
 * [alertLow]/[alertHigh] are the same low/high bounds *only* where the user's own Nightscout
 * actually reported them, and null otherwise. Notifications must be driven from these, never from
 * the display values: falling back there would mean AutoSugar deciding what counts as high or low
 * for someone, which is a judgement a display for Nightscout data must not make on their behalf.
 */
data class GlucoseThresholds(
    val bgLow: Int,
    val bgTargetBottom: Int,
    val bgTargetTop: Int,
    val bgHigh: Int,
    val alertLow: Int? = null,
    val alertHigh: Int? = null,
) {
    /** True when Nightscout reported at least one bound, so a notification is possible at all. */
    val canNotify: Boolean get() = alertLow != null || alertHigh != null
}
