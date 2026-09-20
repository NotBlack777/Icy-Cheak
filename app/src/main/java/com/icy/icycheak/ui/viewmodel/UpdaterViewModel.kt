package com.icy.icycheak.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.icy.icycheak.BuildConfig
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.data.settings.InstallMethod
import com.icy.icycheak.updater.ReleaseInfo
import com.icy.icycheak.updater.UpdateInstaller
import com.icy.icycheak.updater.UpdateRepository
import com.icy.icycheak.updater.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdaterState {
    data object Idle : UpdaterState
    data object Checking : UpdaterState
    data object NoUpdate : UpdaterState
    data class Available(val release: ReleaseInfo, val canUpdate: Boolean) : UpdaterState
    data class Downloading(val progress: Int) : UpdaterState
    data class Ready(val file: File, val release: ReleaseInfo) : UpdaterState
    data class Installing(val method: String) : UpdaterState
    data class Error(val message: String) : UpdaterState
    data object Success : UpdaterState
}

class UpdaterViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<UpdaterState>(UpdaterState.Idle)
    val state: StateFlow<UpdaterState> = _state

    /** Seen/dismissed vs available is tracked separately in AppSettings. */
    val isUpdatePending = AppSettings.isUpdatePending

    fun check(force: Boolean = false) {
        viewModelScope.launch {
            if (_state.value == UpdaterState.Checking) return@launch
            _state.value = UpdaterState.Checking
            when (val result = UpdateRepository.checkForUpdates(BuildConfig.VERSION_NAME)) {
                is UpdateCheckResult.UpToDate -> {
                    AppSettings.setUpdateAvailable(null, null)
                    _state.value = UpdaterState.NoUpdate
                }
                is UpdateCheckResult.Error -> _state.value = UpdaterState.Error(result.message)
                is UpdateCheckResult.Available -> {
                    // Persist availability independently of "seen".
                    AppSettings.setUpdateAvailable(result.release.toJson(), result.release.tagName)
                    _state.value = UpdaterState.Available(result.release, canInstall(result.release))
                }
            }
        }
    }

    /** Dismiss the dialog WITHOUT clearing availability. */
    fun dismissDialog() {
        // Only mark seen; availability remains so the indicator persists.
        viewModelScope.launch { AppSettings.markUpdateSeen() }
        if (_state.value is UpdaterState.Available) {
            // keep availability flag; just hide the dialog by resetting to Idle
            _state.value = UpdaterState.Idle
        }
    }

    fun download(release: ReleaseInfo) {
        viewModelScope.launch {
            _state.value = UpdaterState.Downloading(0)
            val file = withContext(Dispatchers.IO) {
                UpdateInstaller.downloadApk(getApplication(), release.apkUrl ?: return@withContext null) {
                    _state.value = UpdaterState.Downloading(it)
                }
            }
            if (file != null) _state.value = UpdaterState.Ready(file, release)
            else _state.value = UpdaterState.Error("Download failed")
        }
    }

    /**
     * Install the downloaded APK using the chosen method. Falls back to the
     * Package Installer whenever root/Shizuku fails or is unavailable.
     */
    fun install(method: InstallMethod, file: File, release: ReleaseInfo, context: Context) {
        viewModelScope.launch {
            AppSettings.setLastInstallMethod(method)
            when (method) {
                InstallMethod.ROOT -> {
                    _state.value = UpdaterState.Installing("Root")
                    val r = UpdateInstaller.installViaRoot(file)
                    if (r.shouldFallback) fallbackToPackageInstaller(file, context)
                    else if (r.success) _state.value = UpdaterState.Success else _state.value = UpdaterState.Error(r.message)
                }
                InstallMethod.SHIZUKU -> {
                    _state.value = UpdaterState.Installing("Shizuku")
                    val r = UpdateInstaller.installViaShizuku(getApplication(), file)
                    if (r.shouldFallback) fallbackToPackageInstaller(file, context)
                    else if (r.success) _state.value = UpdaterState.Success else _state.value = UpdaterState.Error(r.message)
                }
                InstallMethod.PACKAGE -> fallbackToPackageInstaller(file, context)
            }
        }
    }

    private fun fallbackToPackageInstaller(file: File, context: Context) {
        val intent = UpdateInstaller.packageInstallerIntent(getApplication(), file)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        _state.value = UpdaterState.Success
    }

    private fun canInstall(release: ReleaseInfo): Boolean = release.apkUrl != null

    init {
        // On startup, re-hydrate the dialog state from persisted availability.
        viewModelScope.launch {
            val json = AppSettings.updateAvailableJson.first()
            if (!json.isNullOrBlank()) {
                UpdateRepository.parseRelease(json)?.let {
                    _state.value = UpdaterState.Available(it, canInstall(it))
                }
            }
        }
    }
}

private fun ReleaseInfo.toJson(): String = org.json.JSONObject().apply {
    put("tag_name", tagName); put("name", name); put("body", body)
    put("html_url", htmlUrl); put("apkUrl", apkUrl ?: ""); put("published_at", publishedAt ?: "")
}.toString()
