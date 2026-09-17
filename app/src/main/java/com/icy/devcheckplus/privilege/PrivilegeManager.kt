package com.icy.devcheckplus.privilege

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

data class PrivilegeStatus(
    val rootAvailable: Boolean = false,
    val rootGranted: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val activeMode: PrivilegeMode = PrivilegeMode.NONE,
    val preferredMode: PrivilegeMode = PrivilegeMode.AUTO
)

data class ShellExecutionResult(
    val isSuccess: Boolean,
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>,
    val executionSource: String
)

object PrivilegeManager {
    private const val PREFS_NAME = "devcheck_privilege_prefs"
    private const val KEY_PREFERRED_MODE = "pref_privilege_mode"
    private const val KEY_ONBOARDING_DONE = "pref_onboarding_completed"
    private const val SHIZUKU_REQUEST_CODE = 4001

    private val _status = MutableStateFlow(PrivilegeStatus())
    val status: StateFlow<PrivilegeStatus> = _status

    private var shizukuPermissionListenerRegistered = false

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = prefs.getString(KEY_PREFERRED_MODE, PrivilegeMode.AUTO.name) ?: PrivilegeMode.AUTO.name
        val preferredMode = try {
            PrivilegeMode.valueOf(modeStr)
        } catch (_: Exception) {
            PrivilegeMode.AUTO
        }

        setupShizukuListener()
        refreshStatus(context, preferredMode)
    }

    private fun setupShizukuListener() {
        if (!shizukuPermissionListenerRegistered) {
            try {
                Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                    if (requestCode == SHIZUKU_REQUEST_CODE) {
                        val isGranted = grantResult == PackageManager.PERMISSION_GRANTED
                        val current = _status.value
                        _status.value = current.copy(
                            shizukuGranted = isGranted,
                            activeMode = resolveActiveMode(current.rootGranted, isGranted, current.preferredMode)
                        )
                    }
                }
                shizukuPermissionListenerRegistered = true
            } catch (_: Throwable) {
            }
        }
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
    }

    fun setPreferredMode(context: Context, mode: PrivilegeMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PREFERRED_MODE, mode.name).apply()
        refreshStatus(context, mode)
    }

    fun refreshStatus(context: Context, preferred: PrivilegeMode? = null) {
        val pref = preferred ?: _status.value.preferredMode

        // 1. Shizuku check
        var shizukuRunning = false
        var shizukuGranted = false
        try {
            if (Shizuku.pingBinder()) {
                shizukuRunning = true
                shizukuGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (_: Throwable) {
            shizukuRunning = false
            shizukuGranted = false
        }

        // 2. Root check via libsu
        var rootAvailable = false
        var rootGranted = false
        try {
            val rootState = Shell.isAppGrantedRoot()
            if (rootState == true) {
                rootAvailable = true
                rootGranted = true
            } else {
                rootAvailable = isSuBinaryAvailable()
                rootGranted = false
            }
        } catch (_: Throwable) {
            rootAvailable = false
            rootGranted = false
        }

        val active = resolveActiveMode(rootGranted, shizukuGranted, pref)

        _status.value = PrivilegeStatus(
            rootAvailable = rootAvailable,
            rootGranted = rootGranted,
            shizukuRunning = shizukuRunning,
            shizukuGranted = shizukuGranted,
            activeMode = active,
            preferredMode = pref
        )
    }

    suspend fun requestRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            val isRoot = shell.isRoot
            val current = _status.value
            _status.value = current.copy(
                rootAvailable = true,
                rootGranted = isRoot,
                activeMode = resolveActiveMode(isRoot, current.shizukuGranted, current.preferredMode)
            )
            isRoot
        } catch (_: Throwable) {
            false
        }
    }

    fun requestShizukuPermission(): Boolean {
        return try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    true
                } else {
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                    false
                }
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveActiveMode(rootGranted: Boolean, shizukuGranted: Boolean, pref: PrivilegeMode): PrivilegeMode {
        return when (pref) {
            PrivilegeMode.ROOT -> if (rootGranted) PrivilegeMode.ROOT else PrivilegeMode.NONE
            PrivilegeMode.SHIZUKU -> if (shizukuGranted) PrivilegeMode.SHIZUKU else PrivilegeMode.NONE
            PrivilegeMode.NONE -> PrivilegeMode.NONE
            PrivilegeMode.AUTO -> {
                if (rootGranted) PrivilegeMode.ROOT
                else if (shizukuGranted) PrivilegeMode.SHIZUKU
                else PrivilegeMode.NONE
            }
        }
    }

    private fun isSuBinaryAvailable(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/su",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su-backup",
            "/system/xbin/mu"
        )
        return paths.any { java.io.File(it).exists() }
    }

    suspend fun executeCommand(command: String): ShellExecutionResult = withContext(Dispatchers.IO) {
        val current = _status.value
        val mode = current.activeMode

        // Try primary selected mode first
        if (mode == PrivilegeMode.ROOT) {
            val rootRes = executeViaRoot(command)
            if (rootRes.isSuccess) return@withContext rootRes
        } else if (mode == PrivilegeMode.SHIZUKU) {
            val shizukuRes = executeViaShizuku(command)
            if (shizukuRes.isSuccess) return@withContext shizukuRes
        }

        // If Auto or primary failed, attempt alternate privilege if available
        if (current.preferredMode == PrivilegeMode.AUTO) {
            if (current.shizukuGranted && mode != PrivilegeMode.SHIZUKU) {
                val shizukuRes = executeViaShizuku(command)
                if (shizukuRes.isSuccess) return@withContext shizukuRes
            }
            if (current.rootGranted && mode != PrivilegeMode.ROOT) {
                val rootRes = executeViaRoot(command)
                if (rootRes.isSuccess) return@withContext rootRes
            }
        }

        // Fallback to standard app unprivileged process
        executeStandard(command)
    }

    private fun executeViaRoot(command: String): ShellExecutionResult {
        return try {
            val result = Shell.cmd(command).exec()
            ShellExecutionResult(
                isSuccess = result.isSuccess,
                exitCode = result.code,
                stdout = result.out ?: emptyList(),
                stderr = result.err ?: emptyList(),
                executionSource = "Root (libsu)"
            )
        } catch (e: Throwable) {
            ShellExecutionResult(
                isSuccess = false,
                exitCode = -1,
                stdout = emptyList(),
                stderr = listOf("Root execution failed: ${e.message}"),
                executionSource = "Root (libsu)"
            )
        }
    }

    private fun executeViaShizuku(command: String): ShellExecutionResult {
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod: Method = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val cmdArray = arrayOf("sh", "-c", command)
            val process = newProcessMethod.invoke(null, cmdArray, null, null) as Process

            val stdoutLines = mutableListOf<String>()
            val stderrLines = mutableListOf<String>()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            stdoutReader.useLines { lines -> lines.forEach { stdoutLines.add(it) } }
            stderrReader.useLines { lines -> lines.forEach { stderrLines.add(it) } }

            val exitCode = process.waitFor()

            ShellExecutionResult(
                isSuccess = exitCode == 0,
                exitCode = exitCode,
                stdout = stdoutLines,
                stderr = stderrLines,
                executionSource = "Shizuku"
            )
        } catch (e: Throwable) {
            ShellExecutionResult(
                isSuccess = false,
                exitCode = -1,
                stdout = emptyList(),
                stderr = listOf("Shizuku execution failed: ${e.message}"),
                executionSource = "Shizuku"
            )
        }
    }

    private fun executeStandard(command: String): ShellExecutionResult {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val stdoutLines = mutableListOf<String>()
            val stderrLines = mutableListOf<String>()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            stdoutReader.useLines { lines -> lines.forEach { stdoutLines.add(it) } }
            stderrReader.useLines { lines -> lines.forEach { stderrLines.add(it) } }

            val exitVal = process.waitFor()

            ShellExecutionResult(
                isSuccess = exitVal == 0,
                exitCode = exitVal,
                stdout = stdoutLines,
                stderr = stderrLines,
                executionSource = "Standard (Non-privileged)"
            )
        } catch (e: Throwable) {
            ShellExecutionResult(
                isSuccess = false,
                exitCode = -1,
                stdout = emptyList(),
                stderr = listOf("Execution failed: ${e.message}"),
                executionSource = "Standard (Non-privileged)"
            )
        }
    }
}
