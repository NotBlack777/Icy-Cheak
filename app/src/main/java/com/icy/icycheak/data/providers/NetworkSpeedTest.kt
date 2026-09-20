package com.icy.icycheak.data.providers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class SpeedTestResult(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val durationMs: Long,
    val networkType: String,
    val error: String? = null
)

/**
 * On-demand, best-effort throughput test against small public endpoints.
 * Optional/manual only — never runs automatically.
 */
object NetworkSpeedTest {
    private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=1000000"
    private const val UPLOAD_URL = "https://speed.cloudflare.com/__up"
    private const val UPLOAD_BYTES = 1_000_000

    suspend fun run(context: Context): SpeedTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val netType = networkType(context)
        try {
            val down = measureDownload()
            val up = measureUpload()
            SpeedTestResult(down, up, System.currentTimeMillis() - start, netType)
        } catch (e: Throwable) {
            SpeedTestResult(0.0, 0.0, System.currentTimeMillis() - start, netType, e.message ?: "failed")
        }
    }

    private fun measureDownload(): Double {
        val conn = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        val t0 = System.nanoTime()
        val bytes = conn.inputStream.use { it.readBytes().size }
        val t1 = System.nanoTime()
        conn.disconnect()
        val secs = (t1 - t0) / 1_000_000_000.0
        return if (secs > 0) bytes / 1_000_000.0 / secs else 0.0
    }

    private fun measureUpload(): Double {
        val data = ByteArray(UPLOAD_BYTES) { it.toByte() }
        val conn = URL(UPLOAD_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setFixedLengthStreamingMode(data.size)
        val t0 = System.nanoTime()
        conn.outputStream.use { it.write(data) }
        conn.inputStream.use { it.readBytes() }
        val t1 = System.nanoTime()
        conn.disconnect()
        val secs = (t1 - t0) / 1_000_000_000.0
        return if (secs > 0) data.size / 1_000_000.0 / secs else 0.0
    }

    private fun networkType(context: Context): String {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            when {
                caps == null -> "Offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Other"
            }
        }.getOrDefault("Unknown")
    }
}
