package com.autosugar.data.model

/**
 * All four Nightscout threshold values, in mg/dL.
 *
 * bgLow / bgHigh are the alert boundaries (outside these = alert).
 * bgTargetBottom / bgTargetTop are the target-range boundaries (used for the green graph band).
 */
data class GlucoseThresholds(
    val bgLow: Int,
    val bgTargetBottom: Int,
    val bgTargetTop: Int,
    val bgHigh: Int,
)
