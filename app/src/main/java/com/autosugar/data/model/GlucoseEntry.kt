package com.autosugar.data.model

data class GlucoseEntry(
    /** Raw value always in mg/dL as returned by Nightscout. */
    val sgv: Int,
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
        GlucoseUnit.MG_DL  -> sgv.toString()
        GlucoseUnit.MMOL_L -> "%.1f".format(sgv / 18.0)
    }

    /** Returns the delta converted to the requested unit with sign prefix. */
    fun displayDelta(unit: GlucoseUnit): String? {
        if (delta == null) return null
        val converted = when (unit) {
            GlucoseUnit.MG_DL  -> delta
            GlucoseUnit.MMOL_L -> delta / 18.0
        }
        val sign = if (converted >= 0) "+" else ""
        return when (unit) {
            GlucoseUnit.MG_DL  -> "$sign${converted.toInt()}"
            GlucoseUnit.MMOL_L -> "$sign${"%.1f".format(converted)}"
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
