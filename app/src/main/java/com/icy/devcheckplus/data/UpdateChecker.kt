package com.icy.devcheckplus.data

import com.icy.devcheckplus.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The APK attached to a GitHub release. */
data class UpdateAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long
)

/** Everything the UI needs about a newer release. */
data class UpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String?,
    val notes: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val apk: UpdateAsset?
) {
    val highlights: String
        get() = notes.trim().take(1_200)
}

sealed interface UpdateCheckResult {
    /** A release newer than the installed build exists. */
    data class Available(val info: UpdateInfo) : UpdateCheckResult

    /** Nothing newer on the releases page. */
    data class UpToDate(val latestTag: String?) : UpdateCheckResult

    /** Offline, rate-limited, repo not reachable — never fatal, never a crash. */
    data class Failed(val reason: String) : UpdateCheckResult
}

/**
 * Checks this repository's **GitHub Releases** for a newer build.
 *
 * Releases are used rather than Actions artifacts on purpose: artifact downloads
 * require authentication, are zipped without an APK-friendly MIME type and expire
 * after the retention window, so they cannot back a public in-app updater. A
 * release asset is a permanent, public, directly-downloadable URL.
 *
 * The endpoint is the public, read-only "latest release" API — no token, no
 * account. It is excluded from rate limiting for anonymous clients in practice
 * (60 requests/hour/IP, and the app makes at most one request per launch), and
 * every failure mode degrades to [UpdateCheckResult.Failed] instead of throwing,
 * so an offline device simply sees "couldn't check".
 */
object UpdateChecker {

    const val REPO_OWNER = "NotBlack777"
    const val REPO_NAME = "Icy-Cheak"

    const val RELEASES_LATEST_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    const val RELEASES_PAGE_URL =
        "https://github.com/$REPO_OWNER/$REPO_NAME/releases"

    /** Short timeouts: an update check must never hold anything up. */
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 7_000

    /** Current build, as shipped in BuildConfig. */
    fun currentVersionName(): String = BuildConfig.VERSION_NAME

    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE

    suspend fun checkForUpdate(currentVersionName: String = currentVersionName()): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(RELEASES_LATEST_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    setRequestProperty("User-Agent", "DevCheckPlus/${BuildConfig.VERSION_NAME}")
                }
                val code = connection.responseCode
                if (code == 404) {
                    return@withContext UpdateCheckResult.UpToDate(latestTag = null)
                }
                if (code == 403 || code == 429) {
                    return@withContext UpdateCheckResult.Failed("GitHub rate limit reached — try again later.")
                }
                if (code !in 200..299) {
                    return@withContext UpdateCheckResult.Failed("GitHub returned HTTP $code.")
                }

                val payload = connection.inputStream.bufferedReader().use { it.readText() }
                val info = parseRelease(payload)
                    ?: return@withContext UpdateCheckResult.Failed("Release response could not be parsed.")

                if (isNewer(info.versionName, currentVersionName)) {
                    UpdateCheckResult.Available(info)
                } else {
                    UpdateCheckResult.UpToDate(info.tagName)
                }
            } catch (t: Throwable) {
                // Offline, DNS failure, timeout, TLS problem: same graceful path.
                UpdateCheckResult.Failed(t.message?.take(120) ?: "Network unavailable.")
            } finally {
                connection?.disconnect()
            }
        }

    /** Parses the "latest release" payload; `null` when the body is not usable. */
    internal fun parseRelease(json: String): UpdateInfo? {
        return try {
            val root = JSONObject(json)
            val tag = root.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            val assets = root.optJSONArray("assets")
            var apk: UpdateAsset? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                        apk = UpdateAsset(name = name, url = url, sizeBytes = asset.optLong("size"))
                        break
                    }
                }
            }
            UpdateInfo(
                tagName = tag,
                versionName = normalizeVersion(tag),
                releaseName = root.optString("name").takeIf { it.isNotBlank() },
                notes = root.optString("body"),
                htmlUrl = root.optString("html_url", RELEASES_PAGE_URL),
                publishedAt = root.optString("published_at").takeIf { it.isNotBlank() },
                apk = apk
            )
        } catch (_: Throwable) {
            null
        }
    }

    /** `v1.0.42` → `1.0.42`. */
    fun normalizeVersion(tag: String): String = tag.trim().removePrefix("v").removePrefix("V").trim()

    /**
     * Compares two dotted version names the way a user reads them: numeric
     * component by component, missing components treated as 0, non-numeric
     * suffixes ignored (`1.2.3-beta` == `1.2.3`).
     *
     * Deliberately *not* comparing against `versionCode`: the release tag and the
     * code are generated by different pipelines, and a mismatch there would make
     * the app offer an update forever.
     */
    fun isNewer(candidate: String, current: String): Boolean =
        compareVersions(candidate, current) > 0

    internal fun compareVersions(a: String, b: String): Int {
        val left = numericParts(a)
        val right = numericParts(b)
        val size = maxOf(left.size, right.size)
        for (index in 0 until size) {
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) return if (l > r) 1 else -1
        }
        return 0
    }

    private fun numericParts(version: String): List<Int> =
        normalizeVersion(version)
            .split('.', '-', '+', '_')
            .map { part -> part.takeWhile { it.isDigit() } }
            .map { digits -> digits.toIntOrNull() ?: 0 }
}
