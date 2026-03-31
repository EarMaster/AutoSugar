package com.autosugar.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class GlucoseEntryTest {

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    private fun entry(
        sgv: Int = 120,
        direction: String = "Flat",
        delta: Double? = null,
    ) = GlucoseEntry(
        sgv = sgv,
        direction = direction,
        dateIso = "2024-01-01T12:00:00Z",
        delta = delta,
        dateMs = 1_000_000L,
    )

    // region displayValue

    @Test
    fun `displayValue returns raw sgv for mg_dL`() {
        assertEquals("120", entry(sgv = 120).displayValue(GlucoseUnit.MG_DL))
    }

    @Test
    fun `displayValue converts 180 mg_dL to 10_0 mmol_L`() {
        assertEquals("10.0", entry(sgv = 180).displayValue(GlucoseUnit.MMOL_L))
    }

    @Test
    fun `displayValue rounds mmol_L to one decimal place`() {
        // 100 / 18.0 = 5.555… → 5.6
        assertEquals("5.6", entry(sgv = 100).displayValue(GlucoseUnit.MMOL_L))
    }

    @Test
    fun `displayValue converts 90 mg_dL to 5_0 mmol_L`() {
        assertEquals("5.0", entry(sgv = 90).displayValue(GlucoseUnit.MMOL_L))
    }

    // endregion

    // region displayDelta

    @Test
    fun `displayDelta returns null when delta is null for mg_dL`() {
        assertNull(entry(delta = null).displayDelta(GlucoseUnit.MG_DL))
    }

    @Test
    fun `displayDelta returns null when delta is null for mmol_L`() {
        assertNull(entry(delta = null).displayDelta(GlucoseUnit.MMOL_L))
    }

    @Test
    fun `displayDelta prefixes positive delta with plus in mg_dL`() {
        assertEquals("+5", entry(delta = 5.0).displayDelta(GlucoseUnit.MG_DL))
    }

    @Test
    fun `displayDelta negative delta in mg_dL shows minus sign`() {
        assertEquals("-3", entry(delta = -3.0).displayDelta(GlucoseUnit.MG_DL))
    }

    @Test
    fun `displayDelta zero delta shows plus sign in mg_dL`() {
        assertEquals("+0", entry(delta = 0.0).displayDelta(GlucoseUnit.MG_DL))
    }

    @Test
    fun `displayDelta converts positive delta to mmol_L with one decimal`() {
        // 5.4 / 18.0 = 0.3
        assertEquals("+0.3", entry(delta = 5.4).displayDelta(GlucoseUnit.MMOL_L))
    }

    @Test
    fun `displayDelta converts negative delta to mmol_L with minus sign`() {
        // -3.6 / 18.0 = -0.2
        assertEquals("-0.2", entry(delta = -3.6).displayDelta(GlucoseUnit.MMOL_L))
    }

    // endregion

    // region trendArrow

    @Test
    fun `trendArrow maps all known Nightscout direction strings`() {
        mapOf(
            "DoubleUp"          to "⇈",
            "SingleUp"          to "↑",
            "FortyFiveUp"       to "↗",
            "Flat"              to "→",
            "FortyFiveDown"     to "↘",
            "SingleDown"        to "↓",
            "DoubleDown"        to "⇊",
            "NOT COMPUTABLE"    to "?",
            "RATE OUT OF RANGE" to "⚠",
        ).forEach { (direction, expected) ->
            assertEquals(
                "Expected '$expected' for direction '$direction'",
                expected,
                entry(direction = direction).trendArrow,
            )
        }
    }

    @Test
    fun `trendArrow returns dash for unknown direction`() {
        assertEquals("-", entry(direction = "Unknown").trendArrow)
    }

    @Test
    fun `trendArrow returns dash for empty direction`() {
        assertEquals("-", entry(direction = "").trendArrow)
    }

    // endregion
}
