package com.icy.devcheckplus.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections

object NetworkDataProvider {

    /**
     * The permission Android requires to report Wi-Fi identity (SSID/BSSID):
     * fine location on API 29+, coarse location on 26–28.
     */
    val requiredLocationPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.Manifest.permission.ACCESS_FINE_LOCATION
        } else {
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        }

    fun hasLocationPermission(context: Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, requiredLocationPermission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Location *services* master switch — Android also blanks Wi-Fi identity
     * when it is off, even with the permission granted.
     */
    fun isLocationEnabled(context: Context): Boolean = try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        when {
            lm == null -> false
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> lm.isLocationEnabled
            else -> lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        }
    } catch (_: Exception) {
        false
    }

    /** True when Android will actually report Wi-Fi identity to this app. */
    fun canReadWifiIdentity(context: Context): Boolean =
        hasLocationPermission(context) && isLocationEnabled(context)

    suspend fun getNetworkSections(context: Context, fetchPublicIp: Boolean): List<InfoSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()

        // Permission-aware Wi-Fi identity: Android blanks SSID/BSSID unless the
        // location permission is granted AND location services are on. The state
        // is resolved once, up front, and both items explain their own
        // unavailability instead of showing a bare "<unknown ssid>".
        val wifiIdentityAllowed = canReadWifiIdentity(context)
        val locationPermissionGranted = hasLocationPermission(context)
        val locationServicesOn = isLocationEnabled(context)

        // 1. Connection Overview
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null

        val connItems = mutableListOf<InfoItem>()
        val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val hasCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val hasEthernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val hasVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val activeType = when {
            hasWifi -> "Wi-Fi"
            hasCellular -> "Cellular / Mobile Data"
            hasEthernet -> "Ethernet"
            hasVpn -> "VPN Protected"
            else -> "Disconnected / Airplane Mode"
        }
        connItems.add(InfoItem("Active Network Type", activeType))
        connItems.add(InfoItem("Internet Capable", if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true) "Yes" else "No"))
        connItems.add(InfoItem("Validated Connectivity", if (caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) "Yes" else "No"))
        connItems.add(InfoItem("VPN Active", if (hasVpn) "Yes" else "No"))

        if (caps != null) {
            val downSpeed = caps.linkDownstreamBandwidthKbps
            val upSpeed = caps.linkUpstreamBandwidthKbps
            if (downSpeed > 0) connItems.add(InfoItem("Downlink Bandwidth", "${downSpeed / 1000} Mbps"))
            if (upSpeed > 0) connItems.add(InfoItem("Uplink Bandwidth", "${upSpeed / 1000} Mbps"))
        }
        sections.add(InfoSection("Connection State", connItems))

        // 2. Wi-Fi Details
        val wifiItems = mutableListOf<InfoItem>()
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo: WifiInfo? = wm.connectionInfo

        if (wifiInfo != null && hasWifi) {
            val identityExplanation = when {
                locationPermissionGranted && !locationServicesOn ->
                    "Location services are off — Android reports Wi-Fi identity only while they are on"
                !locationPermissionGranted ->
                    "Grant the Location permission (button at the top of this page) to reveal it"
                else -> null
            }
            val rawSsid = wifiInfo.ssid?.replace("\"", "") ?: "Unknown"
            wifiItems.add(
                InfoItem(
                    "SSID",
                    when {
                        rawSsid != "<unknown ssid>" -> rawSsid
                        !locationPermissionGranted -> "Hidden by Android — Location permission not granted"
                        !locationServicesOn -> "Hidden by Android — Location services are off"
                        else -> "Hidden by Android"
                    },
                    subtitle = if (wifiIdentityAllowed) null else identityExplanation
                )
            )
            val rawBssid = wifiInfo.bssid
            wifiItems.add(
                InfoItem(
                    "BSSID (MAC)",
                    when {
                        wifiIdentityAllowed && !rawBssid.isNullOrBlank() -> rawBssid
                        !locationPermissionGranted -> "Hidden by Android — Location permission not granted"
                        !locationServicesOn -> "Hidden by Android — Location services are off"
                        else -> rawBssid?.takeIf { it.isNotBlank() } ?: "Unavailable"
                    },
                    subtitle = if (wifiIdentityAllowed) null else identityExplanation
                )
            )
            wifiItems.add(InfoItem("Link Speed", "${wifiInfo.linkSpeed} ${WifiInfo.LINK_SPEED_UNITS}"))
            wifiItems.add(InfoItem("Signal Strength (RSSI)", "${wifiInfo.rssi} dBm"))
            wifiItems.add(InfoItem("Frequency Band", "${wifiInfo.frequency} MHz (${getWifiBand(wifiInfo.frequency)})"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiItems.add(InfoItem("Wi-Fi Standard", getWifiStandardName(wifiInfo.wifiStandard)))
            }
        } else {
            wifiItems.add(InfoItem("Wi-Fi Status", if (wm.isWifiEnabled) "Enabled (Not Connected)" else "Disabled"))
        }
        sections.add(InfoSection("Wi-Fi Telemetry", wifiItems))

        // 3. Cellular & SIM
        val cellItems = mutableListOf<InfoItem>()
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        cellItems.add(InfoItem("Network Operator Name", tm.networkOperatorName.ifEmpty { "Unavailable" }))
        cellItems.add(InfoItem("SIM Operator Name", tm.simOperatorName.ifEmpty { "Unavailable" }))
        cellItems.add(InfoItem("SIM Country ISO", tm.simCountryIso.uppercase().ifEmpty { "Unavailable" }))
        cellItems.add(InfoItem("Network Country ISO", tm.networkCountryIso.uppercase().ifEmpty { "Unavailable" }))
        val simStateText = when (tm.simState) {
            TelephonyManager.SIM_STATE_READY -> "Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "No SIM"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
            else -> "Not Ready"
        }
        cellItems.add(InfoItem("SIM State", simStateText))
        // Least-permission note: every value in this section is readable without
        // READ_PHONE_STATE — the app deliberately never reads IMEI/serial numbers.
        cellItems.add(
            InfoItem(
                "Phone Permission",
                "Not needed for this data",
                subtitle = "These carrier/SIM fields are public per SIM. Icy Cheak does not read device identifiers (IMEI/serial), so no phone-state permission is requested."
            )
        )
        sections.add(InfoSection("Cellular Network", cellItems))

        // 4. IP Addresses & DNS
        val ipItems = mutableListOf<InfoItem>()
        val localIpv4 = getLocalIpv4()
        ipItems.add(InfoItem("Local IPv4", localIpv4.ifEmpty { "Not Assigned" }))

        val dnsServers = getDnsServers(cm)
        ipItems.add(InfoItem("DNS Servers", dnsServers.ifEmpty { "System Default" }))

        // Opt-in Public IP
        if (fetchPublicIp) {
            val pubIp = fetchPublicIpAddress()
            ipItems.add(InfoItem("Public IPv4", pubIp))
        } else {
            ipItems.add(InfoItem("Public IPv4", "Lookup disabled (Enable in Settings)"))
        }

        sections.add(InfoSection("IP & DNS Configuration", ipItems))

        sections
    }

    private fun getWifiBand(freq: Int): String {
        return when {
            freq in 2400..2500 -> "2.4 GHz"
            freq in 4900..5900 -> "5 GHz"
            freq in 5925..7125 -> "6 GHz (Wi-Fi 6E/7)"
            else -> "Unknown"
        }
    }

    private fun getWifiStandardName(standard: Int): String {
        return when (standard) {
            ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
            ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
            ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 / 6E (802.11ax)"
            ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7 (802.11be)"
            ScanResult.WIFI_STANDARD_LEGACY -> "Legacy (802.11a/b/g)"
            else -> "Standard $standard"
        }
    }

    private fun getLocalIpv4(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: ""
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private fun getDnsServers(cm: ConnectivityManager): String {
        val linkProps = cm.getLinkProperties(cm.activeNetwork)
        if (linkProps != null) {
            val servers = linkProps.dnsServers.mapNotNull { it.hostAddress }
            if (servers.isNotEmpty()) {
                return servers.joinToString(", ")
            }
        }
        return ""
    }

    private fun fetchPublicIpAddress(): String {
        return try {
            val url = URL("https://api.ipify.org?format=text")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readLine() ?: "Failed" }
            } else {
                "HTTP ${conn.responseCode}"
            }
        } catch (e: Exception) {
            "Unavailable (${e.message ?: "timeout"})"
        }
    }
}
