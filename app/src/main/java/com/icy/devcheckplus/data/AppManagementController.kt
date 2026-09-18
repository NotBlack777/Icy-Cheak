package com.icy.devcheckplus.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import com.icy.devcheckplus.privilege.ShellExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Privileged app-management actions shared by the Installed Apps screen and the
 * Settings quick actions: force-stop and uninstall, each with the same watchdog
 * discipline as every other root/Shizuku call in the app.
 *
 * Failure is a value, never a crash: permission revocations mid-action, protected
 * system packages and hung shells all come back as [AppActionResult.Failure]
 * with a human-readable message. The actions never touch the UI thread, and each
 * privileged call is bounded by [ACTION_TIMEOUT_MS].
 */
object AppManagementController {

    /** One action privileges the shell for at most this long. */
    private const val ACTION_TIMEOUT_MS = 15_000L

    sealed interface AppActionResult {
        data class Success(val message: String) : AppActionResult
        data class Failure(val message: String) : AppActionResult
    }

    /** True when a privileged (root / Shizuku) shell is currently active. */
    private fun hasElevation(): Boolean {
        val mode = PrivilegeManager.status.value.activeMode
        return mode == PrivilegeMode.ROOT || mode == PrivilegeMode.SHIZUKU
    }

    /** Shell `exec` result lines (stdout then stderr) collapsed into one string. */
    private fun ShellExecutionResult.combined(): String =
        (stdout + stderr).map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")

    /**
     * Force-stops [packageName]. Uses the privileged `am force-stop` when
     * elevated; in Standard mode it launches the platform's force-stop screen,
     * space-aware with "system settings".
     */
    suspend fun forceStop(context: Context, packageName: String): AppActionResult = withContext(Dispatchers.IO) {
        if (hasElevation()) {
            val result = PrivilegeManager.executeCommand("am force-stop $packageName", timeoutMs = ACTION_TIMEOUT_MS)
            if (result.timedOut) {
                AppActionResult.Failure("Force-stop timed out — the shell did not answer in time.")
            } else if (result.isSuccess) {
                AppActionResult.Success("$packageName stopped.")
            } else {
                val detail = result.combined().ifBlank { "the shell reported a failure." }
                AppActionResult.Failure("Force-stop failed: $detail")
            }
        } else {
            openAppSettings(context, packageName)
            AppActionResult.Success("Opened Android's app-info screen — choose \"Force stop\" there.")
        }
    }

    /**
     * Uninstalls [packageName]. With elevation it runs `pm uninstall --user 0`,
     * which removes the package for the current user in one step and — unlike the
     * interactive `pm uninstall` — never needs a second `input keyevent ENTER`.
     * Standard mode falls back to the ACTION_DELETE intent, which opens Android's
     * own confirmation flow.
     */
    suspend fun uninstall(context: Context, packageName: String): AppActionResult = withContext(Dispatchers.IO) {
        if (hasElevation()) {
            val result = PrivilegeManager.executeCommand("pm uninstall --user 0 $packageName", timeoutMs = ACTION_TIMEOUT_MS)
            when {
                result.timedOut ->
                    AppActionResult.Failure("Uninstall timed out — the shell did not answer in time.")
                result.isSuccess ->
                    removedSuccess(packageName, result)
                result.combined().contains("not installed", ignoreCase = true) ->
                    AppActionResult.Failure("$packageName is no longer installed.")
                else -> {
                    val detail = result.combined().ifBlank { "the package is protected, or the shell is no longer available." }
                    AppActionResult.Failure("Uninstall failed: $detail")
                }
            }
        } else {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val started = runCatching {
                context.startActivity(intent)
            }.isSuccess
            if (started) {
                AppActionResult.Success("Opened Android's uninstall confirmation.")
            } else {
                AppActionResult.Failure("No activity is available to uninstall $packageName.")
            }
        }
    }

    /**
     * Privileged uninstall succeeded on the "Success" line — unless the output
     * already carries Android's "the shell does not have permission" error, which
     * `pm` still prefixes with `Success` in some releases.
     */
    private fun removedSuccess(packageName: String, result: ShellExecutionResult): AppActionResult {
        val combined = result.combined()
        return if (combined.contains("not permitted", ignoreCase = true)) {
            AppActionResult.Failure("Uninstall failed: the shell does not have permission for $packageName.")
        } else {
            AppActionResult.Success("$packageName uninstalled.")
        }
    }

    /** Launches the system app-details screen for [packageName]. */
    private fun openAppSettings(context: Context, packageName: String) {
        runCatching {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
