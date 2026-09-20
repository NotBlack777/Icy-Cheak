package com.icy.icycheak.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetMathTest {

    @Test
    fun batteryLabel_clampsAndFormats() {
        assertEquals("50%", WidgetMath.batteryLabel(50))
        assertEquals("100%", WidgetMath.batteryLabel(100))
        assertEquals("—", WidgetMath.batteryLabel(-5))
        assertEquals("—", WidgetMath.batteryLabel(150))
    }

    @Test
    fun ramUsedPercent_computesAndClamps() {
        assertEquals(50, WidgetMath.ramUsedPercent(2_000_000, 1_000_000))
        assertEquals(0, WidgetMath.ramUsedPercent(0, 0))
        assertEquals(0, WidgetMath.ramUsedPercent(2_000_000, 2_000_000))
        assertEquals(100, WidgetMath.ramUsedPercent(2_000_000, 0))
    }

    @Test
    fun freqMhz_formatsOrDash() {
        assertEquals("2000 MHz", WidgetMath.freqMhz(2_000_000_000))
        assertEquals("—", WidgetMath.freqMhz(0))
        assertEquals("—", WidgetMath.freqMhz(-1))
    }

    @Test
    fun drainPerHour_estimatesCorrectly() {
        // 100% -> 90% over 2 hours = 5%/h
        val samples = listOf(0L to 100, 7_200_000L to 90)
        assertEquals(5f, WidgetMath.drainPerHour(samples), 0.001f)
        assertEquals(0f, WidgetMath.drainPerHour(emptyList()))
    }

    @Test
    fun clampPercent_bounds() {
        assertEquals(100, WidgetMath.clampPercent(150))
        assertEquals(0, WidgetMath.clampPercent(-10))
        assertEquals(42, WidgetMath.clampPercent(42))
    }
}
