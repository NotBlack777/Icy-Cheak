package com.icy.devcheckplus.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Reads the project's GitHub **Releases** feed.
 *
 * Releases rather than Actions artifacts on purpose: artifact endpoints need an
 * authenticated token and expire after two weeks, while `releases/latest` is a
 * public, permanent URL that works from an installed APK with no credentials.
 */
internal object UpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/NotBlack777/Icy-Cheak/releases/latest"

    /** GitHub rejects API calls without an identifying User-Agent. */
    internal const val USER_AGENT = "DevCheckPlus-Updater/1.0"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000

    /**
     * Newest published release that actually carries an APK.
     *
     * Returns null for anything that is not a usable answer - offline, DNS
     * failure, rate limited, 404 because no release exists yet, or a release
     * with no `.apk` asset. Callers decide whether null is worth surfacing.
     */
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val connection = try {
            open(LATEST_RELEASE_URL)
        } catch (e: IOException) {
            return@withContext null
        }

        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseRelease(body)
        } catch (e: IOException) {
            null
        } catch (e: RuntimeException) {
            // Malformed JSON from a proxy or captive portal.
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }

    /** Pulls the tag, notes and first APK asset out of the releases payload. */
    internal fun parseRelease(json: String): ReleaseInfo? {
        val root = JSONObject(json)
        val tag = root.optString("tag_name").trim()
        if (tag.isEmpty()) return null

        val assets = root.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        var apkName = ""
        var apkBytes = 0L
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url")
            if (url.isEmpty()) continue
            apkUrl = url
            apkName = name
            apkBytes = asset.optLong("size", 0L)
            break
        }
        val downloadUrl = apkUrl ?: return null

        return ReleaseInfo(
            tag = tag,
            name = root.optString("name").ifBlank { tag },
            notes = root.optString("body").trim(),
            apkUrl = downloadUrl,
            apkName = apkName,
            apkBytes = apkBytes,
            publishedAt = root.optString("published_at").trim()
        )
    }

    /**
     * Strictly-newer comparison on dotted numbers: `v1.0.42` beats `1.0.9`
     * because segments are compared numerically, not as text. Missing segments
     * count as zero, a leading `v` is ignored and a `-beta1` style suffix is
     * dropped, so a tag and a `versionName` can be compared directly.
     */
    internal fun isNewer(candidate: String, current: String): Boolean {
        val left = parseVersion(candidate)
        val right = parseVersion(current)
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        // Equal versions are never an update.
        return false
    }

    private fun parseVersion(raw: String): List<Int> = raw.trim()
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore('-')
        .substringBefore('+')
        .split('.')
        .map { segment -> segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}

/** One installable release from the feed. */
internal data class ReleaseInfo(
    val tag: String,
    val name: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkBytes: Long,
    val publishedAt: String
)
