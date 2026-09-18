package com.icy.devcheckplus.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.UNAVAILABLE_NEEDS_PRIVILEGE
import com.icy.devcheckplus.privilege.UNAVAILABLE_TIMED_OUT
import com.icy.devcheckplus.privilege.PrivilegeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object BatteryDataProvider {

    /** Watchdog for one sysfs cat — these are normally instant. */
    private const val SYSFS_TIMEOUT_MS = 4_000L


    suspend fun getBatterySections(context: Context): List<InfoSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val statusItems = mutableListOf<InfoItem>()

        // 1. Status & Level
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else -1
        statusItems.add(InfoItem("Battery Level", if (batteryPct >= 0) "$batteryPct%" else "Unknown"))

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusText = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
        statusItems.add(InfoItem("Charging Status", statusText))

        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val powerSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> "Battery (Unplugged)"
        }
        statusItems.add(InfoItem("Power Source", powerSource))

        val health = batteryIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        statusItems.add(InfoItem("Battery Health", healthText))

        // Temperature (reported in tenths of degree Celsius)
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempC = if (tempRaw > 0) tempRaw / 10.0 else null
        val tempF = if (tempC != null) (tempC * 9 / 5) + 32 else null
        statusItems.add(
            InfoItem(
                "Temperature",
                if (tempC != null) String.format("%.1f °C / %.1f °F", tempC, tempF) else "Unknown"
            )
        )

        // Voltage (reported in mV)
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        statusItems.add(
            InfoItem(
                "Voltage",
                if (voltageMv > 0) "${voltageMv} mV (${String.format("%.2f", voltageMv / 1000.0)} V)" else "Unknown"
            )
        )

        val technology = batteryIntent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
        statusItems.add(InfoItem("Technology", technology))

        sections.add(InfoSection("Current State", statusItems))

        // 2. Deep Metrics (Root / Shizuku / Sysfs)
        val capacityItems = mutableListOf<InfoItem>()
        val designCapacityProfile = getDesignCapacityFromProfile(context)
        if (designCapacityProfile != null) {
            capacityItems.add(InfoItem("Design Capacity (Profile)", "$designCapacityProfile mAh"))
        }

        val privilegeState = PrivilegeManager.status.value
        val hasPrivilege = privilegeState.activeMode != PrivilegeMode.NONE

        val (chargeCycles, cyclesTimedOut) = readChargeCycles()
        val cyclesText = when {
            chargeCycles != null -> "$chargeCycles cycles"
            cyclesTimedOut -> UNAVAILABLE_TIMED_OUT
            hasPrivilege -> "Kernel driver does not report cycles"
            else -> UNAVAILABLE_NEEDS_PRIVILEGE
        }
        capacityItems.add(
            InfoItem(
                title = "Charge Cycle Count",
                value = cyclesText,
                requiresPrivilege = true
            )
        )

        val liveCurrentNow = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (liveCurrentNow != Int.MIN_VALUE && liveCurrentNow != 0) {
            capacityItems.add(InfoItem("Instant Current (uA)", "$liveCurrentNow µA"))
        }

        val chargeCounter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (chargeCounter != Int.MIN_VALUE && chargeCounter > 0) {
            capacityItems.add(InfoItem("Remaining Charge Counter", "${chargeCounter / 1000} mAh"))
        }

        val sysfsCapacity = readSysfsCapacity()
        if (sysfsCapacity != null) {
            capacityItems.add(
                InfoItem(
                    title = "Charge Full (Hardware Sysfs)",
                    value = "$sysfsCapacity mAh",
                    requiresPrivilege = true
                )
            )
        }

        sections.add(InfoSection("Capacity & Health Telemetry", capacityItems))

        sections
    }

    private fun getDesignCapacityFromProfile(context: Context): Int? {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val constructor = powerProfileClass.getConstructor(Context::class.java)
            val powerProfileInstance = constructor.newInstance(context)
            val method = powerProfileClass.getMethod("getBatteryCapacity")
            val capacity = method.invoke(powerProfileInstance) as Double
            capacity.toInt()
        } catch (_: Exception) {
            null
        }
    }

    /** Returns the cycle count plus whether a privileged read hit the watchdog. */
    private suspend fun readChargeCycles(): Pair<Long?, Boolean> {
        val paths = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/cycle_count"
        )
        var timedOut = false
        for (path in paths) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val count = f.readText().trim().toLongOrNull()
                    if (count != null && count >= 0) return Pair(count, false)
                }
            } catch (_: Exception) {
            }

            try {
                val res = PrivilegeManager.executeCommand("cat $path", timeoutMs = SYSFS_TIMEOUT_MS)
                if (res.timedOut) timedOut = true
                if (res.isSuccess && res.stdout.isNotEmpty()) {
                    val count = res.stdout.first().trim().toLongOrNull()
                    if (count != null && count >= 0) return Pair(count, false)
                }
            } catch (_: Exception) {
            }
        }
        return Pair(null, timedOut)
    }

    private suspend fun readSysfsCapacity(): Long? {
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/battery/charge_full_design"
        )
        for (p in paths) {
            try {
                val res = PrivilegeManager.executeCommand("cat $p", timeoutMs = SYSFS_TIMEOUT_MS)
                if (res.isSuccess && res.stdout.isNotEmpty()) {
                    val raw = res.stdout.first().trim().toLongOrNull()
                    if (raw != null && raw > 0) {
                        return if (raw > 50000) raw / 1000 else raw
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }
}
