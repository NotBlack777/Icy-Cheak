package com.icy.icycheak.data.providers

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.icy.icycheak.model.InfoRow
import com.icy.icycheak.model.NetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkProvider {

    suspend fun getNetworkInfo(
        context: Context,
        publicIpOptIn: Boolean
    ): NetworkInfo = withContext(Dispatchers.IO) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = runCatching { cm.activeNetwork }.getOrNull()
        val caps = runCatching { active?.let { cm.getNetworkCapabilities(it) } }.getOrNull()

        val type = when {
            caps == null -> "Disconnected"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other"
        }
        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        // Wi-Fi details
        var ssid: String? = null
        var bssid: String? = null
        var linkSpeed: String? = null
        var rssi: Int? = null
        var band: String? = null
        var generation: String? = null
        runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            if (info != null && info.networkId != -1) {
                ssid = info.ssid?.trim('"')?.ifBlank { null }
                bssid = info.bssid?.ifBlank { null }
                linkSpeed = if (info.linkSpeed >= 0) "${info.linkSpeed} Mbps" else null
                rssi = if (info.rssi != 0) info.rssi else null
                val freq = info.frequency
                band = when {
                    freq in 2400..2500 -> "2.4 GHz"
                    freq in 4900..5900 -> "5 GHz"
                    freq >= 5900 -> "6 GHz"
                    else -> null
                }
                generation = when {
                    freq >= 5900 -> "Wi-Fi 6E"
                    freq >= 4900 -> "Wi-Fi 5/6"
                    freq in 2400..2500 -> "Wi-Fi 4"
                    else -> null
                }
            }
        }

        // Cellular
        var carrier: String? = null
        var simCountry: String? = null
        var simState = "Unknown"
        runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            carrier = tm.networkOperatorName?.ifBlank { null }
            simState = when (tm.simState) {
                TelephonyManager.SIM_STATE_READY -> "Ready"
                TelephonyManager.SIM_STATE_ABSENT -> "Absent"
                TelephonyManager.SIM_STATE_UNKNOWN -> "Unknown"
                else -> "State ${tm.simState}"
            }
            if (android.os.Build.VERSION.SDK_INT >= 22) {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
                val sub = sm.activeSubscriptionInfoList?.firstOrNull()
                simCountry = sub?.countryIso?.uppercase()
            }
        }

        // Local IPv4 + DNS
        val localIp = findLocalIpv4()
        val dns = runCatching {
            val lp: LinkProperties? = active?.let { cm.getLinkProperties(it) }
            lp?.dnsServers?.map { it.hostAddress ?: "" }?.filter { it.isNotEmpty() } ?: emptyList()
        }.getOrDefault(emptyList())

        // Public IP — only when the user opts in (no requests otherwise).
        val publicIp = if (publicIpOptIn) fetchPublicIp() else null

        val rows = listOfNotNull(
            row("Connection", type + if (isVpn) " (VPN)" else ""),
            ssid?.let { row("Wi-Fi SSID", it) },
            bssid?.let { row("BSSID", it) },
            linkSpeed?.let { row("Link speed", it) },
            rssi?.let { row("Signal (RSSI)", "$it dBm") },
            band?.let { row("Band", it) },
            generation?.let { row("Standard", it) },
            carrier?.let { row("Carrier", it) },
            row("SIM state", simState),
            simCountry?.let { row("SIM country", it) },
            localIp?.let { row("Local IPv4", it) },
            if (dns.isNotEmpty()) row("DNS", dns.joinToString(", ")) else null,
            publicIp?.let { row("Public IP", it) }
        )
        NetworkInfo(
            connectionType = type, isVpn = isVpn, wifiSsid = ssid, wifiBssid = bssid,
            wifiLinkSpeed = linkSpeed, wifiRssi = rssi, wifiBand = band, wifiGeneration = generation,
            carrier = carrier, simCountry = simCountry, simState = simState,
            localIpv4 = localIp, dns = dns, publicIp = publicIp, rows = rows
        )
    }

    private fun findLocalIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence().flatMap { ni ->
            ni.inetAddresses.asSequence()
        }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }.getOrNull()

    private fun fetchPublicIp(): String? = runCatching {
        val url = java.net.URL("https://api.ipify.org")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.inputStream.bufferedReader().readText().trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}
