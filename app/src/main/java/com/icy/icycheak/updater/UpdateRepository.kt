package com.icy.icycheak.updater

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

    suspend fun checkForUpdates(currentVersionName: String): UpdateCheckResult {
        return runCatching {
            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            val code = conn.responseCode
            if (code != 200) return UpdateCheckResult.Error("HTTP $code")
            val json = conn.inputStream.bufferedReader().readText()
            val release = parseRelease(json) ?: return UpdateCheckResult.Error("bad payload")
            // Compare semver-ish: if tag differs from current, it's an update.
            val isNewer = release.tagName != currentVersionName &&
                !release.tagName.equals(currentVersionName, true)
            if (isNewer) UpdateCheckResult.Available(release) else UpdateCheckResult.UpToDate
        }.getOrElse { UpdateCheckResult.Error(it.message ?: "network error") }
    }

    fun changelogList(jsonArray: String): List<ReleaseInfo> = runCatching {
        val arr = JSONArray(jsonArray)
        (0 until arr.length()).mapNotNull { parseRelease(arr.getJSONObject(it).toString()) }
    }.getOrDefault(emptyList())

    /** Fetch the recent release history (reused by the in-app Changelog). */
    suspend fun listReleases(perPage: Int = 10): List<ReleaseInfo> = runCatching {
        val url = URL("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=$perPage")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (conn.responseCode != 200) return emptyList()
        val arr = JSONArray(conn.inputStream.bufferedReader().readText())
        (0 until arr.length()).mapNotNull { parseRelease(arr.getJSONObject(it).toString()) }
    }.getOrDefault(emptyList())
}
