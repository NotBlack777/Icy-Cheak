package com.icy.devcheckplus.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import com.icy.devcheckplus.privilege.ShellExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppManagementController {

    private const val ACTION_TIMEOUT_MS = 15_000L

    sealed interface AppActionResult {
        data class Success(val message: String) : AppActionResult
        data class Failure(val message: String) : AppActionResult

        /**
         * Standard (non-privileged) uninstall: the system confirmation dialog is
         * about to take over. The caller must launch [intent] with an
         * activity-result launcher and verify the outcome on resume — see
         * [isPackageInstalled]. The launch itself is NOT a success.
         */
        data class NeedsUserConfirmation(val intent: Intent, val message: String) : AppActionResult
    }

    private fun hasElevation(): Boolean {
        val mode = PrivilegeManager.status.value.activeMode
        return mode == PrivilegeMode.ROOT || mode == PrivilegeMode.SHIZUKU
    }

    private fun ShellExecutionResult.combined(): String =
        (stdout + stderr).map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")

    /**
     * Authoritative post-uninstall check — the package manager, not a timer.
     * FIXED: the standard uninstall flow used to decide on a fixed 30-second
     * window after resume; now the caller verifies against PackageManager.
     */
    fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    /** The system uninstall confirmation for [packageName] (standard mode). */
    fun buildStandardUninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Force-stops [packageName].
     * FIXED: Standard mode now properly checks if app settings launch succeeded,
     * instead of always returning Success even when launch fails.
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
            val opened = openAppSettings(context, packageName)
            if (opened) {
                AppActionResult.Success("Opened Android's app-info screen — choose \"Force stop\" there.")
            } else {
                AppActionResult.Failure("Could not open app settings for $packageName — no activity found.")
            }
        }
    }

    /**
     * Uninstalls [packageName].
     * FIXED: Correct wording for system app removal via pm uninstall --user 0
     * which removes for current user but app remains on device.
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
            // FIXED — standard uninstall lifecycle: launching the confirmation
            // dialog used to be reported as a *completed* uninstall and the app
            // then guessed at the outcome with a 30-second timer. The screen now
            // launches the intent through an activity-result launcher, waits for
            // the real result, and verifies the package against PackageManager.
            AppActionResult.NeedsUserConfirmation(
                intent = buildStandardUninstallIntent(packageName),
                message = "Waiting for Android's uninstall confirmation…"
            )
        }
    }

    private fun removedSuccess(packageName: String, result: ShellExecutionResult): AppActionResult {
        val combined = result.combined()
        return if (combined.contains("not permitted", ignoreCase = true)) {
            AppActionResult.Failure("Uninstall failed: the shell does not have permission for $packageName.")
        } else {
            // FIXED: pm uninstall --user 0 removes for current user, system app remains on device
            // Wording must reflect this distinction
            AppActionResult.Success("$packageName removed for current user (system apps remain on device, user data cleared).")
        }
    }

    /** Launches system app-details screen, returns true if succeeded */
    private fun openAppSettings(context: Context, packageName: String): Boolean {
        return runCatching {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse { false }
    }
}
