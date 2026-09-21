package com.icy.icycheak.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val publishedAt: String?
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val release: ReleaseInfo) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

/**
 * Fetches the latest GitHub Release for the project. Pure network + JSON, no
 * Android dependencies, so it is unit-testable.
 */
object UpdateRepository {
    const val OWNER = "NotBlack777"
    const val REPO = "Icy-Cheak"
    private const val ENDPOINT = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    fun parseRelease(json: String): ReleaseInfo? = runCatching {
        val o = JSONObject(json)
        val assets = o.optJSONArray("assets") ?: JSONArray()
        var apk: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name", "")
            if (name.endsWith(".apk", true)) { apk = a.optString("browser_download_url"); break }
        }
        ReleaseInfo(
            tagName = o.optString("tag_name", ""),
            name = o.optString("name", o.optString("tag_name", "")),
            body = o.optString("body", ""),
            htmlUrl = o.optString("html_url", ""),
            apkUrl = apk,
            publishedAt = o.optString("published_at", null)
        )
    }.getOrNull()

    /**
     * Compare two version strings. Returns true if [remoteTag] is strictly newer than [currentVersion].
     * Handles semantic versions like "v2.0.102" or "2.0.0".
     */
    fun isVersionNewer(remoteTag: String, currentVersion: String): Boolean {
        if (remoteTag.equals(currentVersion, ignoreCase = true) ||
            remoteTag.trimStart('v', 'V').equals(currentVersion.trimStart('v', 'V'), ignoreCase = true)) {
            return false
        }
        val remoteParts = remoteTag.trimStart('v', 'V').split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentVersion.trimStart('v', 'V').split(".").mapNotNull { it.toIntOrNull() }
        if (remoteParts.isNotEmpty() && currentParts.isNotEmpty()) {
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
        // Fallback for non-numeric versions
        return remoteTag != currentVersion
    }

    suspend fun checkForUpdates(currentVersionName: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "IcyCheak-App")
            val code = conn.responseCode
            if (code != 200) {
                return@withContext UpdateCheckResult.Error("GitHub API error (HTTP $code)")
            }
            val json = conn.inputStream.bufferedReader().readText()
            val release = parseRelease(json) ?: return@withContext UpdateCheckResult.Error("Invalid release response payload")
            if (isVersionNewer(release.tagName, currentVersionName)) {
                UpdateCheckResult.Available(release)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: UnknownHostException) {
            UpdateCheckResult.Error("No internet connection (unable to resolve host)")
        } catch (e: SocketTimeoutException) {
            UpdateCheckResult.Error("Connection timed out — please check your internet connection")
        } catch (e: JSONException) {
            UpdateCheckResult.Error("Failed to parse update info (${e.message ?: "invalid JSON"})")
        } catch (e: IOException) {
            UpdateCheckResult.Error("Network error: ${e.message ?: "unable to reach update server"}")
        } catch (e: Throwable) {
            UpdateCheckResult.Error(e.message ?: "Unexpected error during update check")
        }
    }

    fun changelogList(jsonArray: String): List<ReleaseInfo> = runCatching {
        val arr = JSONArray(jsonArray)
        (0 until arr.length()).mapNotNull { parseRelease(arr.getJSONObject(it).toString()) }
    }.getOrDefault(emptyList())

    /** Fetch the recent release history (reused by the in-app Changelog). */
    suspend fun listReleases(perPage: Int = 10): List<ReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=$perPage")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "IcyCheak-App")
            if (conn.responseCode != 200) return@withContext emptyList()
            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            (0 until arr.length()).mapNotNull { parseRelease(arr.getJSONObject(it).toString()) }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
