package com.icy.devcheckplus.data

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DisplayDataProvider {
    suspend fun getDisplaySections(context: Context): List<InfoSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val metrics2 = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getMetrics(metrics2)

        val displayItems = mutableListOf<InfoItem>()
        displayItems.add(InfoItem("Screen Resolution (Real)", "${metrics.widthPixels} x ${metrics.heightPixels} px"))
        displayItems.add(InfoItem("Screen Resolution (App)", "${metrics2.widthPixels} x ${metrics2.heightPixels} px"))
        displayItems.add(InfoItem("Density", "${metrics.density}"))
        displayItems.add(InfoItem("Density DPI", "${metrics.densityDpi} dpi"))
        displayItems.add(InfoItem("Scaled Density", "${metrics.scaledDensity}"))
        displayItems.add(InfoItem("XDPI / YDPI", "${metrics.xdpi} / ${metrics.ydpi} dpi"))
        displayItems.add(InfoItem("Refresh Rate", "${display.refreshRate} Hz"))
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val mode = display.mode
                displayItems.add(InfoItem("Physical Width", "${mode.physicalWidth} px"))
                displayItems.add(InfoItem("Physical Height", "${mode.physicalHeight} px"))
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val supportedModes = display.supportedModes
                val refreshRates = supportedModes.map { it.refreshRate }.distinct().sorted()
                displayItems.add(InfoItem("Supported Refresh Rates", refreshRates.joinToString(", ") { "${it.toInt()} Hz" }))
            } catch (_: Exception) {}
        }

        val aspectRatio = metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
        displayItems.add(InfoItem("Aspect Ratio", String.format("%.2f:1 (%.2f)", aspectRatio, 1/aspectRatio)))

        // Calculate physical size if possible
        try {
            val widthInches = metrics.widthPixels / metrics.xdpi
            val heightInches = metrics.heightPixels / metrics.ydpi
            val diagonal = kotlin.math.sqrt((widthInches * widthInches + heightInches * heightInches).toDouble())
            displayItems.add(InfoItem("Physical Size", String.format("%.2f inches (%.1f x %.1f)", diagonal, widthInches, heightInches)))
        } catch (_: Exception) {}

        sections.add(InfoSection("Display Metrics", displayItems))

        // HDR and color
        val hdrItems = mutableListOf<InfoItem>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val hdrCaps = display.hdrCapabilities
                if (hdrCaps != null) {
                    val types = hdrCaps.supportedHdrTypes.map {
                        when (it) {
                            Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                            Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                            Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                            Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                            else -> "Type $it"
                        }
                    }
                    hdrItems.add(InfoItem("HDR Supported Types", if (types.isEmpty()) "None" else types.joinToString(", ")))
                    hdrItems.add(InfoItem("Max Luminance", "${hdrCaps.desiredMaxLuminance} nits"))
                    hdrItems.add(InfoItem("Min Luminance", "${hdrCaps.desiredMinLuminance} nits"))
                } else {
                    hdrItems.add(InfoItem("HDR Capabilities", "Not available"))
                }
            } catch (_: Exception) {
                hdrItems.add(InfoItem("HDR Capabilities", "Error reading"))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val wideColor = display.isWideColorGamut
                hdrItems.add(InfoItem("Wide Color Gamut", if (wideColor) "Yes" else "No"))
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display?.cutout
                } else null
                // Fallback: try window cutout via resources
                hdrItems.add(InfoItem("Display Cutout", if (cutout != null) "Present (${cutout.boundingRects.size} rects)" else "None or unknown"))
            } catch (_: Exception) {
                hdrItems.add(InfoItem("Display Cutout", "Unknown"))
            }
        }

        hdrItems.add(InfoItem("HDR Supported", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) "Check HDR types above" else "Requires Android N+"))

        sections.add(InfoSection("HDR & Color", hdrItems))

        // Logical display info
        val logicalItems = mutableListOf<InfoItem>()
        logicalItems.add(InfoItem("Display Name", display.name ?: "Unknown"))
        logicalItems.add(InfoItem("Display ID", "${display.displayId}"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            logicalItems.add(InfoItem("State", when (display.state) {
                Display.STATE_ON -> "On"
                Display.STATE_OFF -> "Off"
                Display.STATE_DOZE -> "Doze"
                Display.STATE_DOZE_SUSPEND -> "Doze Suspend"
                Display.STATE_VR -> "VR"
                Display.STATE_ON_SUSPEND -> "On Suspend"
                else -> "Unknown (${display.state})"
            }))
        }
        logicalItems.add(InfoItem("Rotation", when (display.rotation) {
            android.view.Surface.ROTATION_0 -> "0°"
            android.view.Surface.ROTATION_90 -> "90°"
            android.view.Surface.ROTATION_180 -> "180°"
            android.view.Surface.ROTATION_270 -> "270°"
            else -> "Unknown"
        }))

        sections.add(InfoSection("Logical Display", logicalItems))

        sections
    }
}
