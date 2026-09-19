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

    /**
     * FIXED — shell timeout no longer kills unrelated processes.
     *
     * There used to be one global list of "active processes" that the hard
     * timeout destroyed wholesale: if one command hung, every other in-flight
     * shell command (e.g. the Dev Environment's parallel Node/Python/Git/Java
     * probes) had its process destroyed too.
     *
     * Each execution now owns an [ExecutionJob] with a unique id. The job holds
     * the *one* Process that execution spawned; a timeout (or a caller
     * cancellation) destroys only that job's process. [activeJobs] is retained
     * purely as a job-id→job diagnostic registry — nothing ever iterates it to
     * kill things.
     */
    private class ExecutionJob(val id: String) {
        @Volatile
        var process: Process? = null

        fun attach(process: Process) {
            this.process = process
        }

        /** Only clears the pointer if it still points at this process (never at another job's). */
        fun detach(process: Process) {
            if (this.process === process) this.process = null
        }

        /** Destroys only the process this job owns. */
        fun destroyProcess() {
            val process = process ?: return
            try {
                if (process.isAlive) {
                    process.destroy()
                    if (process.isAlive) {
                        Thread.sleep(50)
                        if (process.isAlive) process.destroyForcibly()
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    /** Diagnostics registry: execution id → the job that owns its process. */
    private val activeJobs = java.util.concurrent.ConcurrentHashMap<String, ExecutionJob>()

    /** Number of shell executions currently in flight (diagnostics/testing aid). */
    fun activeExecutionCount(): Int = activeJobs.size

    private fun newJobId(): String =
        "exec-" + System.currentTimeMillis().toString(36) + "-" + (100..999).random()

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

    /**
     * Visible for testing — the mode-selection contract:
     *  - AUTO tries Root first, then Shizuku, then none (never Standard here —
     *    Standard is a per-command fallback inside [executeCommandInternal]);
     *  - an explicit ROOT / SHIZUKU preference NEVER silently becomes something
     *    else: without its grant the mode is NONE, not a downgrade;
     *  - NONE stays NONE.
     */
    internal fun resolveActiveMode(rootGranted: Boolean, shizukuGranted: Boolean, pref: PrivilegeMode): PrivilegeMode {
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
        // FIXED — each command owns its job (and therefore its process). On a
        // timeout only this job's process is destroyed; unrelated concurrent
        // executions are untouched.
        val job = ExecutionJob(newJobId())
        activeJobs[job.id] = job
        try {
            val outcome = withHardTimeout(timeoutMs, job) { executeCommandInternal(command, job) }
            if (outcome == null) {
                if (consecutiveTimeouts.incrementAndGet() >= CIRCUIT_TRIP_THRESHOLD) {
                    circuitOpenUntil = SystemClock.elapsedRealtime() + CIRCUIT_COOLDOWN_MS
                    consecutiveTimeouts.set(0)
                }
                return timeoutResult(timeoutMs, "no response from shell")
            }
            consecutiveTimeouts.set(0)
            return outcome
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            // Caller gave up (e.g. Console "cancel"): destroy only this command's
            // process so a cancelled command cannot linger, then propagate.
            job.destroyProcess()
            throw cancellation
        } finally {
            activeJobs.remove(job.id)
        }
    }

    fun resetWatchdog() {
        consecutiveTimeouts.set(0)
        circuitOpenUntil = 0L
    }

    /**
     * Hard timeout wrapper.
     *
     * FIXED: on timeout, cleanup is now scoped to [job] — the single process the
     * timed-out execution owns. The old version destroyed every entry of a global
     * process list, taking down unrelated concurrent commands.
     *
     * Note on the Root path: libsu multiplexes commands over one shared root
     * shell, so no per-command Process exists there to destroy — the await is
     * simply abandoned (libsu's own shell timeout still bounds it).
     */
    private suspend fun <T> withHardTimeout(
        timeoutMs: Long,
        job: ExecutionJob? = null,
        block: suspend () -> T
    ): T? {
        val deferred = scope.async(Dispatchers.IO) { block() }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (timeout: TimeoutCancellationException) {
            deferred.cancel()
            job?.destroyProcess()
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

    /**
     * FIXED — execution-mode correctness.
     *
     * The old implementation executed the selected engine and, when it failed,
     * fell through to [executeStandard] no matter what the user had chosen: a
     * failed Root or Shizuku command silently ran unprivileged instead, and the
     * result could be presented as if it were complete device data.
     *
     * Required behaviour, now enforced:
     *  - AUTO  : Root first (if granted) → Shizuku fallback (if granted) →
     *            Standard fallback;
     *  - ROOT  : Root only — failure is returned as a clear failure;
     *  - SHIZUKU: Shizuku only — failure is returned as a clear failure;
     *  - NONE  : Standard only.
     *
     * [executionSource] always names the engine that actually ran, and the Root
     * path verifies the shell really is root before crediting it, so privileged
     * results can never be faked by a non-root shell.
     */
    private suspend fun executeCommandInternal(
        command: String,
        job: ExecutionJob
    ): ShellExecutionResult = withContext(Dispatchers.IO) {
        val current = _status.value
        when (current.preferredMode) {
            PrivilegeMode.ROOT -> {
                val rootRes = executeViaRoot(command)
                if (rootRes.isSuccess) {
                    rootRes
                } else {
                    // Explicit Root: a failed root command is a failure, full stop.
                    rootRes.copy(
                        stderr = rootRes.stderr +
                            "Explicit Root mode: root execution failed — NOT falling back to the standard shell."
                    )
                }
            }

            PrivilegeMode.SHIZUKU -> {
                val shizukuRes = executeViaShizuku(command, job)
                if (shizukuRes.isSuccess) {
                    shizukuRes
                } else {
                    // Explicit Shizuku: same contract — no silent standard fallback.
                    shizukuRes.copy(
                        stderr = shizukuRes.stderr +
                            "Explicit Shizuku mode: Shizuku execution failed — NOT falling back to the standard shell."
                    )
                }
            }

            PrivilegeMode.NONE -> executeStandard(command, job)

            PrivilegeMode.AUTO -> {
                // Root first, then Shizuku, then standard — only with engines the
                // user has actually granted.
                if (current.rootGranted) {
                    val rootRes = executeViaRoot(command)
                    if (rootRes.isSuccess) return@withContext rootRes
                }
                if (current.shizukuGranted) {
                    val shizukuRes = executeViaShizuku(command, job)
                    if (shizukuRes.isSuccess) return@withContext shizukuRes
                }
                executeStandard(command, job)
            }
        }
    }

    private fun executeViaRoot(command: String): ShellExecutionResult {
        return try {
            // Correctness guard: make sure the libsu shell actually runs as root
            // before attributing anything to "Root (libsu)". If the user picked
            // Root mode but never granted it, libsu would otherwise hand back a
            // plain non-root shell whose output could masquerade as privileged.
            val shell = Shell.getShell()
            if (!shell.isRoot) {
                return ShellExecutionResult(
                    isSuccess = false,
                    exitCode = -1,
                    stdout = emptyList(),
                    stderr = listOf("Root shell unavailable — root was not granted on this device."),
                    executionSource = "Root (libsu)"
                )
            }
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
     *
     * The spawned process is attached to [job] immediately, so a hard timeout or
     * a caller cancellation destroys exactly this process — never another
     * command's.
     */
    private fun executeViaShizuku(command: String, job: ExecutionJob): ShellExecutionResult {
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
            job.attach(process)

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
                    // Own-process cleanup on the normal/exception path. The job
                    // may already have destroyed it (timeout) — destroy is
                    // idempotent, and detach() only clears our own pointer.
                    process.destroy()
                    if (process.isAlive) {
                        Thread.sleep(50)
                        if (process.isAlive) process.destroyForcibly()
                    }
                    job.detach(process)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun executeStandard(command: String, job: ExecutionJob): ShellExecutionResult {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            job.attach(process)

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
                    process.destroy()
                    if (process.isAlive) {
                        Thread.sleep(50)
                        if (process.isAlive) process.destroyForcibly()
                    }
                    job.detach(process)
                }
            } catch (_: Throwable) {}
        }
    }
}
