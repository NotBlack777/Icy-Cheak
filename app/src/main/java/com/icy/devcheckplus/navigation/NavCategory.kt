package com.icy.devcheckplus.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PermDeviceInformation
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavCategory(
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    DASHBOARD("Dashboard", Icons.Default.Home, "Your pinned readings at a glance"),
    HARDWARE("Hardware", Icons.Default.Memory, "CPU, GPU, RAM, Display"),
    SOFTWARE("Software", Icons.Default.PermDeviceInformation, "Android OS, Kernel, Fingerprint, Bootloader"),
    BATTERY("Battery", Icons.Default.BatteryChargingFull, "Level, Health, Capacity, Temperature, Cycles"),
    STORAGE("Storage", Icons.Default.Storage, "Internal, Partitions, Directory Breakdown"),
    NETWORK("Network", Icons.Default.Wifi, "Wi-Fi, Cellular, DNS, IP Addresses"),
    PROCESSES("Processes", Icons.Default.DeveloperBoard, "Running System Processes & Resource Usage"),
    APPS("Installed Apps", Icons.Default.Apps, "System & User Packages, Permissions"),
    LOGS("System Logs", Icons.Default.ReceiptLong, "Live Logcat Viewer & Filters"),
    SENSORS("Sensors", Icons.Default.Sensors, "Live Hardware Sensors & Real-time Graphing"),
    CONSOLE("Console", Icons.Default.Terminal, "Run commands with the active privilege"),
    SETTINGS("Settings", Icons.Default.Settings, "Privilege Mode, Lookups, About")
}
