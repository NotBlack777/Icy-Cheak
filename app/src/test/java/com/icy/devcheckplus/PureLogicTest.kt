package com.icy.devcheckplus

import com.icy.devcheckplus.data.CustomGradient
import com.icy.devcheckplus.widget.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM checks for the widget snapshot math and the custom-gradient
 * (de)serialization used by the settings store — no Android framework types,
 * so these run as plain unit tests in CI.
 */
class PureLogicTest {

    private fun snapshot(
        ramUsedMb: Long = 4096,
        ramTotalMb: Long = 8192,
        storageUsedGb: Float = 32f,
        storageTotalGb: Float = 128f
    ) = WidgetSnapshot(
        batteryPercent = 80,
        batteryCharging = false,
        batteryTempC = 28.5f,
        batteryVoltageMv = 3900,
        batteryHealth = "Good",
        ramUsedMb = ramUsedMb,
        ramTotalMb = ramTotalMb,
        cpuFreqMhz = 1800f,
        cpuReadable = true,
        cpuCoreCount = 8,
        deviceModel = "Test Device",
        androidVersion = "Android 14 (API 34)",
        storageUsedGb = storageUsedGb,
        storageTotalGb = storageTotalGb,
        networkType = "Wi-Fi",
        networkExtra = null
    )

    @Test
    fun `ram percent is computed from used and total`() {
        assertEquals(50, snapshot().ramPercent)
    }

    @Test
    fun `ram percent is unavailable when total is zero`() {
        assertEquals(-1, snapshot(ramTotalMb = 0).ramPercent)
    }

    @Test
    fun `storage percent is computed from used and total`() {
        assertEquals(25, snapshot().storagePercent)
    }

    @Test
    fun `custom gradient round-trips through serialization`() {
        val gradient = CustomGradient(
            id = "g1",
            name = "My ~weird; name,",
            colors = listOf(0xFF00D2FF, 0xFF9D7BFF),
            angleDegrees = 200,
            radial = false
        )
        val parsed = CustomGradient.parse(gradient.serialize())
        assertNotNull(parsed)
        assertEquals(gradient.id, parsed?.id)
        assertEquals(2, parsed?.colors?.size)
        assertEquals(gradient.colors, parsed?.colors)
        assertEquals(gradient.angleDegrees, parsed?.angleDegrees)
    }

    @Test
    fun `custom gradient list survives serialize and parse`() {
        val list = listOf(
            CustomGradient("a", "One", listOf(0xFF112233, 0xFF445566)),
            CustomGradient("b", "Two", listOf(0xFFAABBCC, 0xFFDDEEFF, 0xFF001122), angleDegrees = 90)
        )
        val parsed = CustomGradient.parseList(CustomGradient.serializeList(list))
        assertEquals(2, parsed.size)
        assertEquals("a", parsed[0].id)
        assertEquals("b", parsed[1].id)
        assertTrue(parsed[1].colors.size == 3)
    }

    @Test
    fun `corrupt gradient records degrade to null instead of crashing`() {
        assertNull(CustomGradient.parse(null))
        assertNull(CustomGradient.parse(""))
        assertNull(CustomGradient.parse("id~name~90~0~zzzz"))
        assertNull(CustomGradient.parse("id~name~90~0~AABBCC")) // only one colour
    }
}
