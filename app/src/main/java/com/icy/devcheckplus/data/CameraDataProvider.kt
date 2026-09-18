package com.icy.devcheckplus.data

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CameraDataProvider {
    suspend fun getCameraSections(context: Context): List<InfoSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

        if (cameraManager == null) {
            sections.add(InfoSection("Camera", listOf(InfoItem("Camera Service", "Unavailable"))))
            return@withContext sections
        }

        try {
            val cameraIds = cameraManager.cameraIdList
            val overviewItems = mutableListOf<InfoItem>()
            overviewItems.add(InfoItem("Camera Count", "${cameraIds.size} cameras"))
            overviewItems.add(InfoItem("Front/Back", "${cameraIds.size} total"))
            sections.add(InfoSection("Overview", overviewItems))

            cameraIds.forEach { id ->
                try {
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val items = mutableListOf<InfoItem>()

                    val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                    val facingStr = when (facing) {
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK -> "Back"
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                        else -> "Unknown"
                    }
                    items.add(InfoItem("Facing", facingStr))

                    val hardwareLevel = characteristics.get(android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
                    val levelStr = when (hardwareLevel) {
                        android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                        android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                        android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                        android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                        android.hardware.camera2.CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
                        else -> "Unknown ($hardwareLevel)"
                    }
                    items.add(InfoItem("Hardware Level", levelStr))

                    val flashAvailable = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                    items.add(InfoItem("Flash", if (flashAvailable) "Available" else "Not available"))

                    val afModes = characteristics.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                    items.add(InfoItem("AF Modes", afModes?.joinToString(", ") { afModeToString(it) } ?: "Unknown"))

                    val aeModes = characteristics.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
                    items.add(InfoItem("AE Modes", aeModes?.size?.let { "$it modes" } ?: "Unknown"))

                    val fpsRanges = characteristics.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    items.add(InfoItem("FPS Ranges", fpsRanges?.joinToString(", ") { "${it.lower}-${it.upper}" } ?: "Unknown"))

                    val capabilities = characteristics.get(android.hardware.camera2.CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    items.add(InfoItem("Capabilities", capabilities?.joinToString(", ") { capToString(it) } ?: "Unknown"))

                    val maxRes = characteristics.get(android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    maxRes?.let { map ->
                        val jpegSizes = map.getOutputSizes(android.graphics.ImageFormat.JPEG)
                        if (!jpegSizes.isNullOrEmpty()) {
                            val largest = jpegSizes.maxByOrNull { it.width * it.height }
                            largest?.let {
                                items.add(InfoItem("Max JPEG Resolution", "${it.width} x ${it.height} (${(it.width * it.height / 1000000f)} MP)"))
                            }
                        }
                        val yuvSizes = map.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
                        if (!yuvSizes.isNullOrEmpty()) {
                            items.add(InfoItem("YUV Sizes", "${yuvSizes.size} formats"))
                        }
                    }

                    val focalLengths = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    items.add(InfoItem("Focal Lengths", focalLengths?.joinToString(", ") { "${it}mm" } ?: "Unknown"))

                    val sensorSize = characteristics.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    sensorSize?.let {
                        items.add(InfoItem("Sensor Size", "${it.width} x ${it.height} mm"))
                    }

                    val stabilization = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                    items.add(InfoItem("OIS", stabilization?.joinToString(", ") { oisToString(it) } ?: "Unknown"))

                    sections.add(InfoSection("Camera $id ($facingStr)", items))
                } catch (e: Exception) {
                    sections.add(InfoSection("Camera $id", listOf(InfoItem("Error", e.message ?: "Failed to read"))))
                }
            }

        } catch (e: Exception) {
            sections.add(InfoSection("Camera", listOf(InfoItem("Error", e.message ?: "Failed to list cameras"))))
        }

        sections
    }

    private fun afModeToString(mode: Int): String = when (mode) {
        0 -> "Off"
        1 -> "Auto"
        2 -> "Macro"
        3 -> "Continuous Video"
        4 -> "Continuous Picture"
        5 -> "EDOF"
        else -> "Mode $mode"
    }

    private fun capToString(cap: Int): String = when (cap) {
        0 -> "Backwards Compatible"
        1 -> "Manual Sensor"
        2 -> "Manual Post-Processing"
        3 -> "RAW"
        4 -> "Private Reprocessing"
        5 -> "Read Sensor Settings"
        6 -> "Burst Capture"
        7 -> "YUV Reprocessing"
        8 -> "Depth Output"
        9 -> "Constrained High Speed"
        10 -> "Motion Tracking"
        11 -> "Logical Multi-Camera"
        12 -> "Monochrome"
        13 -> "Secure Image Data"
        else -> "Cap $cap"
    }

    private fun oisToString(mode: Int): String = when (mode) {
        0 -> "Off"
        1 -> "On"
        else -> "Mode $mode"
    }
}
