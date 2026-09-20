package com.icy.icycheak.data.providers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.icy.icycheak.model.BatteryInfo
import com.icy.icycheak.model.InfoRow
import com.icy.icycheak.privilege.PrivilegeEngine

object BatteryProvider {

    suspend fun getBatteryInfo(context: Context): BatteryInfo {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val pluggedInt = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val charging = pluggedInt != 0 && pluggedInt != -1
        val plugged = when (pluggedInt) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }
        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val health = healthName(healthInt)
        val tempRaw = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val temperatureC = if (tempRaw == Int.MIN_VALUE) null else tempRaw / 10f
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1

        // Privileged-ish fields: try BatteryManager first, then dumpsys battery.
        val cycleCount = readIntProperty(bm, BatteryManager.BATTERY_PROPERTY_CYCLE_COUNT)
        val fullCapacity = readIntProperty(bm, BatteryManager.BATTERY_PROPERTY_CHARGE_FULL)
        val currentNow = readIntProperty(bm, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargeCounter = readIntProperty(bm, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        val dumpsys = PrivilegeEngine.execute("dumpsys battery 2>/dev/null", 4000)
        val ds = if (dumpsys.isSuccess) dumpsys.stdout.joinToString("\n") else ""

        val chemistry = ds.lineSequence().firstOrNull { it.contains("technology", true) }
            ?.substringAfter(":").trim().ifBlank { null }
            ?: if (level >= 0) "Li-ion" else null

        val cycleFromDump = ds.lineSequence().firstOrNull { it.contains("cycle count", true) }
            ?.substringAfter(":").trim().toIntOrNull()
        val cycleStr = (cycleCount.takeIf { it > 0 } ?: cycleFromDump)?.toString()
        val fullFromDump = ds.lineSequence().firstOrNull { it.contains("charge full", true) }
            ?.substringAfter(":").trim().toIntOrNull()
        val fullStr = (fullCapacity.takeIf { it > 0 } ?: fullFromDump)?.let { "$it mAh" }

        val rows = listOfNotNull(
            row("Level", "$level%", emphasized = true),
            row("Status", if (charging) "Charging ($plugged)" else "Discharging"),
            row("Health", health),
            row("Temperature", temperatureC?.let { "%.1f °C / %.1f °F".format(it, it * 9 / 5 + 32) } ?: "—"),
            row("Voltage", if (voltageMv > 0) "$voltageMv mV" else "—"),
            cycleStr?.let { row("Charge cycles", it) },
            fullStr?.let { row("Full capacity", it) },
            currentNow.takeIf { it != 0 }?.let { row("Current", "%d µA".format(it)) },
            chargeCounter.takeIf { it != 0 }?.let { row("Charge counter", "%d µAh".format(it)) },
            chemistry?.let { row("Chemistry", it) }
        )
        return BatteryInfo(
            level = level, charging = charging, plugged = plugged, health = health,
            temperatureC = temperatureC ?: 0f, voltageMv = voltageMv,
            chemistry = chemistry ?: "—", cycleCount = cycleStr, fullCapacityMah = fullStr,
            currentNowUa = currentNow.takeIf { it != 0 }?.toString(),
            chargeCounter = chargeCounter.takeIf { it != 0 }?.toString(), rows = rows
        )
    }

    private fun readIntProperty(bm: BatteryManager, prop: Int): Int {
        return runCatching { bm.getIntProperty(prop) }.getOrDefault(0)
    }

    private fun healthName(h: Int): String = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Unknown"
    }
}
