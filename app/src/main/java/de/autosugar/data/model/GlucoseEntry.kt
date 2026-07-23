package de.autosugar.data.model

import java.util.Locale
import kotlin.math.roundToInt

/** Exact mg/dL → mmol/L divisor (glucose molar mass 180.156 g/mol). */
const val MG_DL_PER_MMOL_L = 18.0156

data class GlucoseEntry(
    /** Raw value always in mg/dL as returned by Nightscout. Some sources (e.g. Juggluco) send fractional values. */
    val sgv: Double,
    /** Nightscout direction string, e.g. "Flat", "SingleUp", "DoubleUp", "FortyFiveUp", etc. */
    val direction: String,
    /** ISO-8601 date string from the entry. */
    val dateIso: String,
    /** Delta to the previous reading, in the same unit as sgv (mg/dL). Null if unavailable. */
    val delta: Double?,
    /** Unix timestamp in milliseconds — used for reliable chronological sorting. */
    val dateMs: Long = 0L,
) {
    /** Returns the display value converted to the requested unit. */
    fun displayValue(unit: GlucoseUnit): String = when (unit) {
        GlucoseUnit.MG_DL  -> sgv.roundToInt().toString()
        GlucoseUnit.MMOL_L -> "%.1f".format(Locale.US, sgv / MG_DL_PER_MMOL_L)
    }

    /** Returns the delta converted to the requested unit with sign prefix. */
    fun displayDelta(unit: GlucoseUnit): String? {
        if (delta == null) return null
        // Choose the sign from the rounded/displayed magnitude, not the raw value, so a
        // tiny delta that rounds to zero shows "+0"/"0.0" rather than "-0"/"-0.0".
        return when (unit) {
            GlucoseUnit.MG_DL  -> {
                val rounded = delta.roundToInt()
                "${if (rounded >= 0) "+" else ""}$rounded"
            }
            GlucoseUnit.MMOL_L -> {
                val rounded = (delta / MG_DL_PER_MMOL_L * 10).roundToInt() / 10.0
                "${if (rounded >= 0.0) "+" else ""}${"%.1f".format(Locale.US, rounded)}"
            }
        }
    }

    /** Maps Nightscout direction string to a unicode arrow character. */
    val trendArrow: String get() = when (direction) {
        "DoubleUp"       -> "⇈"
        "SingleUp"       -> "↑"
        "FortyFiveUp"    -> "↗"
        "Flat"           -> "→"
        "FortyFiveDown"  -> "↘"
        "SingleDown"     -> "↓"
        "DoubleDown"     -> "⇊"
        "NOT COMPUTABLE" -> "?"
        "RATE OUT OF RANGE" -> "⚠"
        else             -> "-"
    }
}
