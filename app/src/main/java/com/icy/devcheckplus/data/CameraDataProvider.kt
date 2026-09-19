package com.icy.devcheckplus.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Camera inspection — FIXED permission/availability handling.
 *
 * Technically correct Android behaviour this now relies on:
 *  - Reading camera *metadata* via `CameraManager.getCameraCharacteristics`
 *    requires NO runtime permission and never opens or powers the camera.
 *    (`openCamera` is the permission-gated call — this provider never calls it.)
 *  - Since API 29 a small set of metadata keys (mostly depth/lens-correction
 *    values, listed by `CameraCharacteristics.getKeysNeedingPermission()`) are
 *    redacted for apps without CAMERA permission: `get` returns null for them.
 *
 * So the least-permission flow is: read everything without permission, and if
 * Android redacted keys, tell the user exactly that and offer an explicit
 * [android.Manifest.permission.CAMERA] request to unlock the few hidden fields.
 * A device that nevertheless throws SecurityException (OEM quirk) is surfaced
 * as a clear "Camera permission required" state instead of a fake camera error.
 */
object CameraDataProvider {

    sealed interface CameraInfoResult {
        /** Metadata read successfully. [redactedKeyCount] > 0 means Android hid
         *  that many fields because CAMERA is not granted (API 29+). */
        data class Success(
            val sections: List<InfoSection>,
            val redactedKeyCount: Int
        ) : CameraInfoResult

        /** A SecurityException came back — the device gates metadata behind CAMERA. */
        data class PermissionRequired(val reason: String) : CameraInfoResult

        /** A real failure (no camera service, CameraAccessException, …). */
        data class Failure(val message: String) : CameraInfoResult
    }

    fun hasCameraPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun getCameraInfo(context: Context): CameraInfoResult = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

        if (cameraManager == null) {
            return@withContext CameraInfoResult.Failure("No camera service is available on this device.")
        }

        var redactedKeys = 0

        try {
            val cameraIds = cameraManager.cameraIdList
            val overviewItems = mutableListOf<InfoItem>()
            overviewItems.add(InfoItem("Camera Count", "${cameraIds.size} cameras"))

            // Count front/back/external properly instead of repeating the total.
            var front = 0
            var back = 0
            var external = 0
            cameraIds.forEach { id ->
                runCatching {
                    cameraManager.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                }.getOrNull()?.let { facing ->
                    when (facing) {
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT -> front++
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK -> back++
                        else -> external++
                    }
                }
            }
            overviewItems.add(InfoItem("Front / Back / External", "$front / $back / $external"))
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

                    // API 29+: keys Android redacts without the CAMERA permission.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        redactedKeys += characteristics.keysNeedingPermission.size
                    }

                    sections.add(InfoSection("Camera $id ($facingStr)", items))
                } catch (e: SecurityException) {
                    // This specific camera's metadata is permission-gated.
                    sections.add(
                        InfoSection(
                            "Camera $id",
                            listOf(InfoItem("Details", "Requires camera permission", subtitle = e.message))
                        )
                    )
                    redactedKeys += 1
                } catch (e: Exception) {
                    sections.add(InfoSection("Camera $id", listOf(InfoItem("Error", e.message ?: "Failed to read"))))
                }
            }

            CameraInfoResult.Success(sections, redactedKeys)
        } catch (e: SecurityException) {
            // The whole enumeration is gated on this device — request permission.
            CameraInfoResult.PermissionRequired(e.message ?: "The device requires the camera permission to list cameras.")
        } catch (e: Exception) {
            CameraInfoResult.Failure(e.message ?: "Failed to list cameras")
        }
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
