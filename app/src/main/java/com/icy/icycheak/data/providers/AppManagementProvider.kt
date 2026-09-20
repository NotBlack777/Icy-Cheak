package com.icy.icycheak.data.providers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.icy.icycheak.BuildConfig
import com.icy.icycheak.privilege.PrivilegeEngine
import com.icy.icycheak.privilege.ShellResult

object AppManagementProvider {

    /** Force-stop an app. Requires elevation — there is no public standard API. */
    suspend fun forceStop(packageName: String): ShellResult {
        if (!PrivilegeEngine.status.value.hasElevated) {
            return ShellResult(
                isSuccess = false, exitCode = -1, stdout = emptyList(),
                stderr = listOf("Force-stop requires root or Shizuku"),
                executionSource = "—", timedOut = false
            )
        }
        return PrivilegeEngine.execute("am force-stop $packageName", 8000)
    }

    /**
     * Plan an uninstall. If elevated, perform it directly via `pm uninstall`.
     * Otherwise return a real system intent the caller should launch (standard
     * fallback). Targeting Icy Cheak itself is refused upstream in the UI.
     */
    fun planUninstall(context: Context, packageName: String): UninstallAction {
        return if (PrivilegeEngine.status.value.hasElevated) {
            UninstallAction.DeferredShell("pm uninstall --user 0 $packageName")
        } else {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            UninstallAction.LaunchIntent(intent)
        }
    }

    /** Execute a previously-planned privileged uninstall (with hard timeout). */
    suspend fun executePlannedUninstall(command: String): ShellResult =
        PrivilegeEngine.execute(command, 20_000)

    /** Open the system App-info screen for a package (used as a safe fallback). */
    fun appDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun isIcyCheak(packageName: String): Boolean = packageName == BuildConfig.APPLICATION_ID
}

sealed interface UninstallAction {
    /** Standard-mode: caller must start this intent. */
    data class LaunchIntent(val intent: Intent) : UninstallAction

    /** Privileged: caller must run this command through the engine. */
    data class DeferredShell(val command: String) : UninstallAction
}
