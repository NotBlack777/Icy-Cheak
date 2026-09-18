package com.icy.devcheckplus.privilege

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger

const val UNAVAILABLE_NEEDS_PRIVILEGE = "Unavailable — requires root or Shizuku"
const val UNAVAILABLE_TIMED_OUT = "Unavailable — request timed out"

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
    val executionSource: String,
    val timedOut: Boolean = false
) {
    val unavailableText: String
        get() = if (timedOut) UNAVAILABLE_TIMED_OUT else UNAVAILABLE_NEEDS_PRIVILEGE
}

object PrivilegeManager {
    private const val PREFS_NAME = "devcheck_privilege_prefs"
    private const val KEY_PREFERRED_MODE = "pref_privilege_mode"
    private const val KEY_ONBOARDING_DONE = "pref_onboarding_completed"
    private const val SHIZUKU_REQUEST_CODE = 4001

    const val DEFAULT_COMMAND_TIMEOUT_MS = 10_000L
    const val ROOT_REQUEST_TIMEOUT_MS = 25_000L
    const val STATUS_PROBE_TIMEOUT_MS = 15_000L

    private const val CIRCUIT_TRIP_THRESHOLD = 2
    private const val CIRCUIT_COOLDOWN_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val consecutiveTimeouts = AtomicInteger(0)

    @Volatile
    private var circuitOpenUntil = 0L

    private val _status = MutableStateFlow(PrivilegeStatus())
    val status: StateFlow<PrivilegeStatus> = _status

    private var shizukuPermissionListenerRegistered = false

    // Track active processes for cleanup on timeout
    private val activeProcesses = java.util.Collections.synchronizedList(mutableListOf<Process>())

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
            } catch (_: Throwable) {}
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
        resetWatchdog()
        refreshStatus(context, mode)
    }

    fun refreshStatus(context: Context, preferred: PrivilegeMode? = null) {
        val pref = preferred ?: _status.value.preferredMode
        val cached = _status.value
        _status.value = cached.copy(
            preferredMode = pref,
            activeMode = resolveActiveMode(cached.rootGranted, cached.shizukuGranted, pref)
        )
        scope.launch {
            val probed = try {
                withHardTimeout(STATUS_PROBE_TIMEOUT_MS) { probePrivileges() }
            } catch (t: Throwable) {
                null
            } ?: return@launch
            val current = _status.value
            _status.value = probed.copy(
                preferredMode = current.preferredMode,
                activeMode = resolveActiveMode(probed.rootGranted, probed.shizukuGranted, current.preferredMode)
            )
        }
    }

    private fun probePrivileges(): PrivilegeStatus {
        val pref = _status.value.preferredMode
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
        return PrivilegeStatus(
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
            val granted = withHardTimeout(ROOT_REQUEST_TIMEOUT_MS) { Shell.getShell().isRoot }
            if (granted == null) return@withContext _status.value.rootGranted
            val current = _status.value
            _status.value = current.copy(
                rootAvailable = true,
                rootGranted = granted,
                activeMode = resolveActiveMode(granted, current.shizukuGranted, current.preferredMode)
            )
            granted
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

    suspend fun executeCommand(
        command: String,
        timeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS
    ): ShellExecutionResult {
        if (SystemClock.elapsedRealtime() < circuitOpenUntil) {
            return timeoutResult(timeoutMs, "shell circuit open")
        }
        val outcome = withHardTimeout(timeoutMs) { executeCommandInternal(command) }
        if (outcome == null) {
            if (consecutiveTimeouts.incrementAndGet() >= CIRCUIT_TRIP_THRESHOLD) {
                circuitOpenUntil = SystemClock.elapsedRealtime() + CIRCUIT_COOLDOWN_MS
                consecutiveTimeouts.set(0)
            }
            return timeoutResult(timeoutMs, "no response from shell")
        }
        consecutiveTimeouts.set(0)
        return outcome
    }

    fun resetWatchdog() {
        consecutiveTimeouts.set(0)
        circuitOpenUntil = 0L
    }

    /**
     * FIXED: Hard timeout now cleans up abandoned processes to prevent accumulation.
     * Previously timed-out tasks left processes running in background.
     */
    private suspend fun <T> withHardTimeout(timeoutMs: Long, block: suspend () -> T): T? {
        val deferred = scope.async(Dispatchers.IO) { block() }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (timeout: TimeoutCancellationException) {
            deferred.cancel()
            try {
                synchronized(activeProcesses) {
                    activeProcesses.forEach { proc ->
                        try {
                            if (proc.isAlive) {
                                proc.destroy()
                                Thread.sleep(50)
                                if (proc.isAlive) proc.destroyForcibly()
                            }
                        } catch (_: Throwable) {}
                    }
                    activeProcesses.clear()
                }
            } catch (_: Throwable) {}
            null
        }
    }

    private fun timeoutResult(timeoutMs: Long, reason: String) = ShellExecutionResult(
        isSuccess = false,
        exitCode = -1,
        stdout = emptyList(),
        stderr = listOf("Timed out after ${timeoutMs / 1000} s ($reason)"),
        executionSource = "Watchdog",
        timedOut = true
    )

    private suspend fun executeCommandInternal(command: String): ShellExecutionResult = withContext(Dispatchers.IO) {
        val current = _status.value
        val mode = current.activeMode
        if (mode == PrivilegeMode.ROOT) {
            val rootRes = executeViaRoot(command)
            if (rootRes.isSuccess) return@withContext rootRes
        } else if (mode == PrivilegeMode.SHIZUKU) {
            val shizukuRes = executeViaShizuku(command)
            if (shizukuRes.isSuccess) return@withContext shizukuRes
        }
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

    /**
     * FIXED: Concurrent stdout/stderr reading to avoid deadlock.
     * Previously sequential reading could deadlock when stderr buffer filled while reading stdout.
     */
    private fun executeViaShizuku(command: String): ShellExecutionResult {
        var process: Process? = null
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
            process = newProcessMethod.invoke(null, cmdArray, null, null) as Process
            synchronized(activeProcesses) { activeProcesses.add(process) }

            val stdoutLines = mutableListOf<String>()
            val stderrLines = mutableListOf<String>()

            val stdoutThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                        lines.forEach { stdoutLines.add(it) }
                    }
                } catch (_: Throwable) {}
            }
            val stderrThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).useLines { lines ->
                        lines.forEach { stderrLines.add(it) }
                    }
                } catch (_: Throwable) {}
            }
            stdoutThread.start()
            stderrThread.start()

            val exitCode = process.waitFor()
            stdoutThread.join(2000)
            stderrThread.join(2000)

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
        } finally {
            try {
                if (process != null) {
                    synchronized(activeProcesses) { activeProcesses.remove(process) }
                    process.destroy()
                    Thread.sleep(50)
                    if (process.isAlive) process.destroyForcibly()
                }
            } catch (_: Throwable) {}
        }
    }

    private fun executeStandard(command: String): ShellExecutionResult {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            synchronized(activeProcesses) { activeProcesses.add(process) }

            val stdoutLines = mutableListOf<String>()
            val stderrLines = mutableListOf<String>()

            val stdoutThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                        lines.forEach { stdoutLines.add(it) }
                    }
                } catch (_: Throwable) {}
            }
            val stderrThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream)).useLines { lines ->
                        lines.forEach { stderrLines.add(it) }
                    }
                } catch (_: Throwable) {}
            }
            stdoutThread.start()
            stderrThread.start()

            val exitVal = process.waitFor()
            stdoutThread.join(2000)
            stderrThread.join(2000)

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
        } finally {
            try {
                if (process != null) {
                    synchronized(activeProcesses) { activeProcesses.remove(process) }
                    process.destroy()
                    Thread.sleep(50)
                    if (process.isAlive) process.destroyForcibly()
                }
            } catch (_: Throwable) {}
        }
    }
}
