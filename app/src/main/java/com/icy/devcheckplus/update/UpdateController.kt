package com.icy.devcheckplus.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.icy.devcheckplus.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** What the updater is doing, mirrored straight into the UI. */
internal sealed interface UpdateState {
    /** Nothing happening; also where a silent background check returns to. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class Available(val release: ReleaseInfo) : UpdateState

    data class Downloading(
        val release: ReleaseInfo,
        val receivedBytes: Long,
        val totalBytes: Long
    ) : UpdateState

    /** APK is on disk; the system installer has been handed its content URI. */
    data class ReadyToInstall(val release: ReleaseInfo, val file: File) : UpdateState

    /**
     * Android 8+ makes the user allow "install unknown apps" for DevCheck+
     * first. No API can flip that toggle for us.
     */
    data class NeedsInstallPermission(val release: ReleaseInfo, val file: File) : UpdateState

    /** Only surfaced for a manual check; background checks fail silently. */
    data class Failed(val message: String, val release: ReleaseInfo?) : UpdateState

    data class UpToDate(val installedVersion: String, val latestTag: String) : UpdateState
}

/**
 * Owns the whole update lifecycle: feed lookup, download with progress, and the
 * hand-off to the system package installer.
 *
 * A process-wide singleton with a [StateFlow], so the prompt can appear over
 * whichever screen the user is on and a check started at launch is not repeated
 * by the Settings screen.
 */
internal object UpdateController {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val UPDATE_DIR = "updates"
    private const val APK_MIME = "application/vnd.android.package-archive"

    /** Upper bound on the lookup; the socket itself has 8 s timeouts. */
    private const val CHECK_TIMEOUT_MS = 15_000L
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000

    /** Progress posts are throttled so the dialog does not recompose per chunk. */
    private const val PROGRESS_INTERVAL_MS = 120L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    @Volatile
    private var busy = false

    @Volatile
    private var downloadJob: Job? = null

    /**
     * Look for a newer release.
     *
     * @param manual true when the user tapped "Check now" - only then are "up to
     * date" and network failures reported. An automatic launch check leaves the
     * UI exactly as it was when there is nothing to install.
     */
    fun check(context: Context, manual: Boolean) {
        if (busy) return
        busy = true
        _state.value = UpdateState.Checking

        scope.launch {
            val release = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                async(Dispatchers.IO) { UpdateChecker.fetchLatestRelease() }.await()
            }

            busy = false
            _state.value = when {
                release == null -> if (manual) {
                    UpdateState.Failed(
                        message = "Couldn't reach GitHub Releases. Check the connection and try again.",
                        release = null
                    )
                } else {
                    UpdateState.Idle
                }

                UpdateChecker.isNewer(release.tag, BuildConfig.VERSION_NAME) ->
                    UpdateState.Available(release)

                else -> if (manual) {
                    UpdateState.UpToDate(
                        installedVersion = BuildConfig.VERSION_NAME,
                        latestTag = release.tag
                    )
                } else {
                    UpdateState.Idle
                }
            }
        }
    }

    /** Start (or restart) the download for whichever release is on screen. */
    fun download(context: Context) {
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release
            else -> null
        } ?: return
        if (busy) return
        busy = true

        val appContext = context.applicationContext
        val target = File(File(appContext.cacheDir, UPDATE_DIR), "DevCheckPlus-${release.tag}.apk")

        downloadJob = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val file = downloadTo(release = release, target = target) { received, total ->
                        _state.value = UpdateState.Downloading(release, received, total)
                    }
                    when {
                        file == null -> DownloadResult.Network
                        // Verified off the main thread: reading an APK's signer
                        // means parsing the whole archive.
                        !isSameSigner(appContext, file) -> {
                            file.delete()
                            DownloadResult.SignerMismatch
                        }
                        else -> DownloadResult.Ok(file)
                    }
                }
            } catch (e: IOException) {
                DownloadResult.Network
            }

            busy = false
            downloadJob = null
            when (result) {
                // install() publishes ReadyToInstall / NeedsInstallPermission itself.
                is DownloadResult.Ok -> install(appContext, release, result.file)
                DownloadResult.SignerMismatch -> _state.value = UpdateState.Failed(
                    message = "That APK is signed with a different key than this install, so Android would " +
                        "reject it. Grab it from the GitHub Releases page instead.",
                    release = release
                )
                DownloadResult.Network -> _state.value = UpdateState.Failed(
                    message = "Download didn't finish. Try again on a steadier connection.",
                    release = release
                )
            }
        }
    }

    /** Stops an in-flight download and throws away the partial file. */
    fun cancelDownload(context: Context) {
        val current = _state.value
        val job = downloadJob
        downloadJob = null
        busy = false
        job?.cancel()

        if (current is UpdateState.Downloading) {
            val appContext = context.applicationContext
            val partial = File(
                File(appContext.cacheDir, UPDATE_DIR),
                "DevCheckPlus-${current.release.tag}.apk.part"
            )
            scope.launch(Dispatchers.IO) { if (partial.exists()) partial.delete() }
        }
        _state.value = UpdateState.Idle
    }

    /** Re-runs the install hand-off, e.g. after returning from Settings. */
    fun retryInstall(context: Context) {
        val appContext = context.applicationContext
        when (val current = _state.value) {
            is UpdateState.ReadyToInstall -> launchInstaller(appContext, current.release, current.file)
            is UpdateState.NeedsInstallPermission -> install(appContext, current.release, current.file)
            else -> Unit
        }
    }

    /**
     * Hands the APK to the system package installer.
     *
     * Android offers no silent install to a normal app: the platform installer
     * always shows its own confirmation sheet, so this is a hand-off rather than
     * an update that completes by itself. What the code can do is pre-check the
     * "unknown apps" toggle and route the user to the exact screen that owns it.
     */
    fun install(context: Context, release: ReleaseInfo, file: File) {
        val appContext = context.applicationContext
        if (!appContext.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(release, file)
            openInstallPermissionSettings(appContext)
            return
        }
        launchInstaller(appContext, release, file)
    }

    /** Opens Android's per-app "install unknown apps" screen for this package. */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:" + context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Failed(
                message = "This device has no 'install unknown apps' screen. Sideload the APK manually.",
                release = (_state.value as? UpdateState.NeedsInstallPermission)?.release
            )
        }
    }

    /** Close the prompt. An active download must be cancelled explicitly. */
    fun dismiss() {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Idle
    }

    /* ------------------------------------------------------------------ */

    private sealed interface DownloadResult {
        data class Ok(val file: File) : DownloadResult
        data object Network : DownloadResult
        data object SignerMismatch : DownloadResult
    }

    /**
     * True when [apk] carries the same signing certificate as the running app.
     *
     * Android enforces this anyway (a mismatched signer fails with
     * INSTALL_FAILED_UPDATE_INCOMPATIBLE), but checking here turns an opaque
     * installer error into an honest message and refuses a tampered feed before
     * anything is handed to the system.
     */
    private fun isSameSigner(context: Context, apk: File): Boolean = try {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val installed = signerDigest(pm.getPackageInfo(context.packageName, flags))
        @Suppress("DEPRECATION")
        val candidate = signerDigest(pm.getPackageArchiveInfo(apk.absolutePath, flags))
        installed != null && candidate != null && installed.contentEquals(candidate)
    } catch (e: Exception) {
        // Unreadable archive, no PackageManager answer: treat as a mismatch.
        false
    }

    private fun signerDigest(info: PackageInfo?): ByteArray? {
        if (info == null) return null
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return null
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        val first = signatures?.firstOrNull() ?: return null
        return MessageDigest.getInstance("SHA-256").digest(first.toByteArray())
    }

    private fun launchInstaller(context: Context, release: ReleaseInfo, file: File) {
        val uri = try {
            FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        } catch (e: IllegalArgumentException) {
            // Path outside res/xml/file_paths.xml would be a bug, not a user error.
            _state.value = UpdateState.Failed(
                message = "The downloaded file can't be shared with the installer.",
                release = release
            )
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
            _state.value = UpdateState.ReadyToInstall(release, file)
        } catch (e: ActivityNotFoundException) {
            _state.value = UpdateState.Failed(
                message = "No package installer found on this device.",
                release = release
            )
        }
    }

    /** Writes `<name>.part` first so an interrupted download is never installed. */
    private fun downloadTo(
        release: ReleaseInfo,
        target: File,
        onProgress: (received: Long, total: Long) -> Unit
    ): File? {
        val connection = try {
            (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UpdateChecker.USER_AGENT)
            }
        } catch (e: IOException) {
            return null
        }

        val partial = File(target.parentFile, target.name + ".part")
        return try {
            if (connection.responseCode !in 200..299) return null

            val declared = connection.contentLengthLong
            val total = if (declared > 0L) declared else release.apkBytes
            target.parentFile?.mkdirs()
            if (partial.exists()) partial.delete()

            var received = 0L
            var lastPost = 0L
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        received += read
                        val now = System.currentTimeMillis()
                        if (now - lastPost >= PROGRESS_INTERVAL_MS || (total > 0L && received >= total)) {
                            lastPost = now
                            onProgress(received, total)
                        }
                    }
                }
            }

            // A short read means a dropped connection, not a smaller APK.
            if (received <= 0L || (total > 0L && received < total)) {
                partial.delete()
                return null
            }
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.delete()
                return null
            }
            target
        } catch (e: IOException) {
            partial.delete()
            null
        } finally {
            connection.disconnect()
        }
    }
}
