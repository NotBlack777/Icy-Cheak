package com.icy.devcheckplus.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Where an update check currently stands. */
sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class Available(val info: UpdateInfo) : UpdateCheckState
    data class UpToDate(val latestTag: String?) : UpdateCheckState
    data class Failed(val reason: String) : UpdateCheckState
}

/** Where the APK download currently stands. */
sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    /** [progress] is 0..1, or negative while the length is unknown. */
    data class Downloading(val progress: Float) : UpdateDownloadState
    data class Ready(val file: File) : UpdateDownloadState
    data class Failed(val reason: String) : UpdateDownloadState
}

/**
 * Process-wide update state.
 *
 * A single object (rather than per-screen state) so that a check started at
 * launch, a manual check from Settings and a download in progress all survive
 * whatever composable happens to be on screen — including rotation, where a
 * download must not restart from zero.
 */
object UpdateRepository {

    private const val AUTO_CHECK_THROTTLE_MS = 6 * 60 * 60 * 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val checkMutex = Mutex()

    private val _checkState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val checkState: StateFlow<UpdateCheckState> = _checkState.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    /** Version the user already said "later" to, so launch checks stay quiet. */
    private val _dismissedVersion = MutableStateFlow<String?>(null)
    val dismissedVersion: StateFlow<String?> = _dismissedVersion.asStateFlow()

    private var lastCheckAtMs: Long = 0L

    /**
     * Runs a check unless one is in flight. [automatic] applies the launch
     * throttle so opening the app twice in a row does not hit the API twice.
     */
    fun check(context: Context, automatic: Boolean = false) {
        val now = System.currentTimeMillis()
        if (automatic && now - lastCheckAtMs < AUTO_CHECK_THROTTLE_MS) return
        if (_checkState.value is UpdateCheckState.Checking) return

        lastCheckAtMs = now
        _checkState.value = UpdateCheckState.Checking
        scope.launch {
            checkMutex.withLock {
                val result = UpdateChecker.checkForUpdate()
                _checkState.value = when (result) {
                    is UpdateCheckResult.Available -> UpdateCheckState.Available(result.info)
                    is UpdateCheckResult.UpToDate -> UpdateCheckState.UpToDate(result.latestTag)
                    is UpdateCheckResult.Failed -> UpdateCheckState.Failed(result.reason)
                }
            }
        }
    }

    fun dismiss(result: UpdateCheckState) {
        val tag = (result as? UpdateCheckState.Available)?.info?.tagName
        _dismissedVersion.value = tag
        _checkState.value = UpdateCheckState.Idle
    }

    fun reset() {
        _checkState.value = UpdateCheckState.Idle
        _downloadState.value = UpdateDownloadState.Idle
    }

    /** Clears a terminal check state (used after the user reads "up to date"). */
    fun clearTransient() {
        if (_checkState.value !is UpdateCheckState.Available) {
            _checkState.value = UpdateCheckState.Idle
        }
    }

    fun download(context: Context, asset: UpdateAsset) {
        if (_downloadState.value is UpdateDownloadState.Downloading) return
        val appContext = context.applicationContext
        _downloadState.value = UpdateDownloadState.Downloading(-1f)
        scope.launch {
            val result = ApkUpdateInstaller.downloadApk(appContext, asset) { progress ->
                _downloadState.value = UpdateDownloadState.Downloading(progress)
            }
            _downloadState.value = result.fold(
                onSuccess = { UpdateDownloadState.Ready(it) },
                onFailure = { UpdateDownloadState.Failed(it.message ?: "Download failed.") }
            )
        }
    }

    fun clearDownload() {
        _downloadState.value = UpdateDownloadState.Idle
    }
}
