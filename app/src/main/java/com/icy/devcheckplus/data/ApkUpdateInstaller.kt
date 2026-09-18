package com.icy.devcheckplus.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads a release APK and hands it to the platform package installer.
 *
 * Android does not allow a normal app to install an update silently — that needs
 * root or device-owner privileges. The supported flow, used here, is:
 *
 *  1. download the APK into a dedicated cache subdirectory
 *     (`cacheDir/updates`, exposed through a FileProvider that shares nothing
 *     else);
 *  2. check the user has granted "install unknown apps" for this app, and send
 *     them to `ACTION_MANAGE_UNKNOWN_APP_SOURCES` if not;
 *  3. `ACTION_VIEW` with the APK's `content://` URI, which opens the system
 *     installer confirmation dialog.
 *
 * The confirmation sheet at the end is an Android platform requirement, not a
 * bug: the user always sees the version and the permissions before it installs.
 */
object ApkUpdateInstaller {

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /** Cache subdirectory shared through the FileProvider. */
    private const val UPDATE_DIR_NAME = "updates"

    /** Sanity floor for a downloaded APK (anything smaller is an error page). */
    private const val MIN_APK_BYTES = 64 * 1024L

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000

    val fileProviderAuthoritySuffix = ".fileprovider"

    fun fileProviderAuthority(context: Context): String =
        context.packageName + fileProviderAuthoritySuffix

    fun updateDirectory(context: Context): File =
        File(context.cacheDir, UPDATE_DIR_NAME).apply { if (!exists()) mkdirs() }

    /** Drops previously downloaded APKs (stale versions pile up in the cache). */
    fun clearDownloads(context: Context) {
        runCatching {
            updateDirectory(context).listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * `true` when this app is allowed to request a package install. On Android 8+
     * this is the per-app "install unknown apps" grant; the manifest declares
     * `REQUEST_INSTALL_PACKAGES`, which is what makes the app eligible for it.
     */
    fun canInstallPackages(context: Context): Boolean =
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /** Deep link into the per-app "install unknown apps" screen. */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /**
     * Streams [asset] into the update cache. Progress is reported as 0..1 (or -1
     * while the content length is unknown) on every ~64 KB.
     */
    suspend fun downloadApk(
        context: Context,
        asset: UpdateAsset,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val target = File(updateDirectory(context), asset.name.ifBlank { "DevCheckPlus-update.apk" })
            if (target.exists()) target.delete()
            // Only one APK is ever needed: drop leftovers from earlier versions so
            // the cache cannot grow with every release.
            updateDirectory(context).listFiles()?.forEach { stale ->
                if (stale.name != target.name) stale.delete()
            }

            connection = (URL(asset.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "DevCheckPlus")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("Download failed (HTTP $code)."))
            }

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.sizeBytes
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastReported = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied - lastReported >= 64 * 1024) {
                            lastReported = copied
                            onProgress(if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else -1f)
                        }
                    }
                    output.flush()
                }
            }

            // A truncated download or an HTML error page must never reach the
            // installer, so verify size and the ZIP magic bytes.
            val length = target.length()
            if (length < MIN_APK_BYTES) {
                target.delete()
                return@withContext Result.failure(IllegalStateException("Downloaded file is not a valid APK."))
            }
            if (!looksLikeZip(target)) {
                target.delete()
                return@withContext Result.failure(IllegalStateException("Downloaded file is not a valid APK."))
            }

            onProgress(1f)
            Result.success(target)
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            connection?.disconnect()
        }
    }

    /** Opens the system installer for an already-downloaded [file]. */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun looksLikeZip(file: File): Boolean = runCatching {
        file.inputStream().use { stream ->
            val header = ByteArray(2)
            stream.read(header) == 2 && header[0] == 'P'.code.toByte() && header[1] == 'K'.code.toByte()
        }
    }.getOrDefault(false)
}
