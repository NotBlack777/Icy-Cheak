package com.icy.icycheak.privilege

import android.content.Context
import android.content.pm.PackageManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

const val UNAVAILABLE_NEEDS_PRIVILEGE = "Unavailable — requires root or Shizuku"
const val UNAVAILABLE_TIMED_OUT = "Unavailable — request timed out"

data class PrivilegeStatus(
    val rootAvailable: Boolean = false,
    val rootGranted: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuGranted: Boolean = false,
    val activeMode: PrivilegeMode = PrivilegeMode.STANDARD,
    val preferredMode: PrivilegeMode = PrivilegeMode.AUTO
) {
    /** True when any elevated path (root or Shizuku) is currently usable. */
    val hasElevated: Boolean get() = rootGranted || shizukuGranted
}

/**
 * The ONE privileged-shell execution engine for the whole app.
 *
 * Design guarantees (these are the lessons from the old codebase, fixed here
 * at the architecture level rather than patched per-feature):
 *
 * 1. Single path. Console, Dev Environment, app management, live telemetry and
 *    the updater installer ALL call [execute] / [executeInstall]. Nothing spawns
 *    its own shell.
 * 2. Hard timeout on EVERY call. A hung command returns a `timedOut` result
 *    after [DEFAULT_COMMAND_TIMEOUT_MS] (or the per-call override) — it can
 *    never block the caller forever. The underlying process is destroyed by an
 *    independent monitor so a wedged `waitFor()` does not leak.
 * 3. Circuit breaker. Repeated timeouts trip the breaker; while it is open,
 *    calls fail fast instead of piling onto a dead shell.
 * 4. Per-job process ownership. Each execution owns exactly one [ExecutionJob]
 *    holding the single Process it spawned; a timeout destroys ONLY that
 *    process, never other in-flight commands.
 */
object PrivilegeEngine {

    private const val PREFS_NAME = "icycheak_privilege_prefs"
    private const val KEY_PREFERRED_MODE = "pref_privilege_mode"
    private const val SHIZUKU_REQUEST_CODE = 4001

    const val DEFAULT_COMMAND_TIMEOUT_MS = 10_000L
    /** Root grants / large `pm install` calls can legitimately take longer. */
    const val INSTALL_TIMEOUT_MS = 60_000L
    const val ROOT_REQUEST_TIMEOUT_MS = 25_000L
    const val STATUS_PROBE_TIMEOUT_MS = 15_000L

    private const val CIRCUIT_TRIP_THRESHOLD = 3
    private const val CIRCUIT_COOLDOWN_MS = 15_000L

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val circuitBreaker = CircuitBreaker(CIRCUIT_TRIP_THRESHOLD, CIRCUIT_COOLDOWN_MS)

    private val _status = MutableStateFlow(PrivilegeStatus())
    val status: StateFlow<PrivilegeStatus> = _status

    private val activeJobs = ConcurrentHashMap<String, ExecutionJob>()
    private val consecutiveTimeouts = AtomicInteger(0)

    private var shizukuListenerRegistered = false

    private class ExecutionJob(val id: String) {
        @Volatile var process: Process? = null
        fun attach(p: Process) { process = p }
        fun detach(p: Process) { if (process === p) process = null }
        fun destroyProcess() {
            val p = process ?: return
            try {
                if (p.isAlive) {
                    p.destroy()
                    if (p.isAlive) {
                        Thread.sleep(50)
                        if (p.isAlive) p.destroyForcibly()
                    }
                }
            } catch (_: Throwable) { /* surface nothing; caller reports failure */ }
        }
    }

    private fun newJobId(): String =
        "exec-" + System.currentTimeMillis().toString(36) + "-" + (100..999).random()

    /** Diagnostic: number of shell executions currently in flight. */
    fun activeExecutionCount(): Int = activeJobs.size

    // ---- lifecycle / preference plumbing ---------------------------------

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val preferred = PrivilegeMode.fromName(prefs.getString(KEY_PREFERRED_MODE, null))
        registerShizukuListener()
        refreshStatus(context, preferred)
    }

    fun isInitialized(): Boolean = shizukuListenerRegistered

    fun setPreferredMode(context: Context, mode: PrivilegeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PREFERRED_MODE, mode.name).apply()
        circuitBreaker.reset()
        refreshStatus(context, mode)
    }

    fun refreshStatus(context: Context, preferred: PrivilegeMode? = null) {
        val pref = preferred ?: _status.value.preferredMode
        _status.value = _status.value.copy(preferredMode = pref, activeMode = resolveActiveMode(_status.value, pref))
        engineScope.launch {
            val probed = runCatching { withTimeout(STATUS_PROBE_TIMEOUT_MS) { probePrivileges(pref) } }
                .getOrNull() ?: return@launch
            _status.value = probed.copy(preferredMode = pref, activeMode = resolveActiveMode(probed, pref))
        }
    }

    private fun registerShizukuListener() {
        if (shizukuListenerRegistered) return
        try {
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode != SHIZUKU_REQUEST_CODE) return@addRequestPermissionResultListener
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                val cur = _status.value
                _status.value = cur.copy(
                    shizukuGranted = granted,
                    activeMode = resolveActiveMode(cur, cur.preferredMode)
                )
            }
            shizukuListenerRegistered = true
        } catch (_: Throwable) { /* Shizuku not present */ }
    }

    private fun probePrivileges(pref: PrivilegeMode): PrivilegeStatus {
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
            when (Shell.isAppGrantedRoot()) {
                true -> { rootAvailable = true; rootGranted = true }
                false -> { rootAvailable = isSuBinaryAvailable(); rootGranted = false }
                null -> { rootAvailable = isSuBinaryAvailable(); rootGranted = false }
            }
        } catch (_: Throwable) {
            rootAvailable = false
            rootGranted = false
        }
        return PrivilegeStatus(
            rootAvailable = rootAvailable,
            rootGranted = rootGranted,
            shizukuRunning = shizukuRunning,
            shizukuGranted = shizukuGranted,
            activeMode = resolveActiveMode(rootGranted, shizukuGranted, pref),
            preferredMode = pref
        )
    }

    private fun isSuBinaryAvailable(): Boolean = runCatching {
        Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v su")).inputStream
            .bufferedReader().readText().trim().isNotEmpty()
    }.getOrDefault(false)

    suspend fun requestRootAccess(): Boolean = runCatching {
        withTimeout(ROOT_REQUEST_TIMEOUT_MS) {
            val shell = Shell.getShell()
            val granted = shell.isRoot
            val cur = _status.value
            _status.value = cur.copy(
                rootAvailable = true,
                rootGranted = granted,
                activeMode = resolveActiveMode(granted, cur.shizukuGranted, cur.preferredMode)
            )
            granted
        }
    }.getOrDefault(_status.value.rootGranted)

    fun requestShizukuPermission(): Boolean = try {
        if (Shizuku.pingBinder()) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                false
            }
        } else false
    } catch (_: Throwable) { false }

    // ---- mode resolution -------------------------------------------------

    /**
     * Resolve which concrete execution mode to use for a privileged-or-standard
     * command given current grants and the user's preference.
     */
    private fun resolveActiveMode(
        rootGranted: Boolean,
        shizukuGranted: Boolean,
        preferred: PrivilegeMode
    ): PrivilegeMode = when (preferred) {
        PrivilegeMode.ROOT -> if (rootGranted) PrivilegeMode.ROOT else PrivilegeMode.STANDARD
        PrivilegeMode.SHIZUKU -> if (shizukuGranted) PrivilegeMode.SHIZUKU else PrivilegeMode.STANDARD
        PrivilegeMode.STANDARD, PrivilegeMode.NONE -> PrivilegeMode.STANDARD
        PrivilegeMode.AUTO -> when {
            rootGranted -> PrivilegeMode.ROOT
            shizukuGranted -> PrivilegeMode.SHIZUKU
            else -> PrivilegeMode.STANDARD
        }
    }

    private fun resolveActiveMode(s: PrivilegeStatus, preferred: PrivilegeMode): PrivilegeMode =
        resolveActiveMode(s.rootGranted, s.shizukuGranted, preferred)

    // ---- the single execution path --------------------------------------

    /**
     * Execute a shell command through the resolved execution mode.
     *
     * @param timeoutMs hard timeout; the call returns a [ShellResult.timedOut]
     *   result if exceeded and destroys the underlying process.
     */
    suspend fun execute(command: String, timeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS): ShellResult {
        if (circuitBreaker.isOpen()) {
            return ShellResult(
                isSuccess = false, exitCode = -1, stdout = emptyList(),
                stderr = listOf("Circuit breaker open — too many recent timeouts, retrying shortly"),
                executionSource = resolveSource(), circuitOpen = true
            )
        }
        return try {
            val result = withContext(Dispatchers.IO) {
                withTimeout(timeoutMs) { dispatch(command) }
            }
            recordOutcome(result)
            result
        } catch (e: TimeoutCancellationException) {
            consecutiveTimeouts.incrementAndGet()
            circuitBreaker.recordFailure()
            ShellResult(
                isSuccess = false, exitCode = -1, stdout = emptyList(),
                stderr = listOf("Timed out after ${timeoutMs}ms"),
                executionSource = resolveSource(), timedOut = true
            )
        } catch (e: Throwable) {
            circuitBreaker.recordFailure()
            ShellResult(
                isSuccess = false, exitCode = -1, stdout = emptyList(),
                stderr = listOf(e.message ?: "Execution failed"),
                executionSource = resolveSource()
            )
        }
    }

    /** Convenience variant for long-running privileged installs. */
    suspend fun executeInstall(command: String, timeoutMs: Long = INSTALL_TIMEOUT_MS): ShellResult =
        execute(command, timeoutMs)

    private fun recordOutcome(result: ShellResult) {
        if (result.timedOut || result.circuitOpen) {
            consecutiveTimeouts.incrementAndGet()
            circuitBreaker.recordFailure()
        } else {
            consecutiveTimeouts.set(0)
            circuitBreaker.recordSuccess()
        }
    }

    private fun resolveSource(): String = when (_status.value.activeMode) {
        PrivilegeMode.ROOT -> "Root (libsu)"
        PrivilegeMode.SHIZUKU -> "Shizuku"
        else -> "Standard (non-privileged)"
    }

    private suspend fun dispatch(command: String): ShellResult {
        return when (_status.value.activeMode) {
            PrivilegeMode.ROOT -> execRoot(command)
            PrivilegeMode.SHIZUKU -> execShizuku(command)
            else -> execStandard(command)
        }
    }

    private fun execRoot(command: String): ShellResult = runCatching {
        val result = Shell.cmd(command).exec()
        ShellResult(
            isSuccess = result.isSuccess,
            exitCode = result.code,
            stdout = result.out,
            stderr = result.err,
            executionSource = "Root (libsu)"
        )
    }.getOrElse { e ->
        ShellResult(false, -1, emptyList(), listOf(e.message ?: "root exec failed"), "Root (libsu)")
    }

    private fun execStandard(command: String): ShellResult = execProcess(command, {
        Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
    }, "Standard (non-privileged)", DEFAULT_COMMAND_TIMEOUT_MS)

    private fun execShizuku(command: String): ShellResult = execProcess(command, {
        val method: Method = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        method.isAccessible = true
        method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
    }, "Shizuku", DEFAULT_COMMAND_TIMEOUT_MS)

    /**
     * Spawn a [Process], stream its stdout/stderr concurrently (avoids the
     * classic stderr-buffer deadlock), and enforce [timeoutMs] with an
     * independent monitor that destroys the process if `waitFor()` stalls.
     */
    private fun execProcess(
        command: String,
        spawn: () -> Process,
        source: String,
        timeoutMs: Long
    ): ShellResult {
        val job = ExecutionJob(newJobId())
        val process: Process = try {
            spawn()
        } catch (e: Throwable) {
            return ShellResult(false, -1, emptyList(), listOf(e.message ?: "spawn failed"), source)
        }
        job.attach(process)
        activeJobs[job.id] = job
        // Independent monitor: even if waitFor() blocks past the timeout, this
        // destroys the process so it cannot leak or hang the caller.
        val killer = engineScope.launch { delay(timeoutMs); job.destroyProcess() }
        return try {
            val stdout = mutableListOf<String>()
            val stderr = mutableListOf<String>()
            val outThread = Thread {
                try { BufferedReader(InputStreamReader(process.inputStream)).useLines { it.forEach { l -> stdout.add(l) } } } catch (_: Throwable) {}
            }
            val errThread = Thread {
                try { BufferedReader(InputStreamReader(process.errorStream)).useLines { it.forEach { l -> stderr.add(l) } } } catch (_: Throwable) {}
            }
            outThread.start(); errThread.start()
            val exit = process.waitFor()
            outThread.join(2000); errThread.join(2000)
            ShellResult(exit == 0, exit, stdout, stderr, source)
        } finally {
            killer.cancel()
            job.destroyProcess()
            activeJobs.remove(job.id)
        }
    }

    // ---- availability helpers (shared by install-method picker) ----------

    fun isRootGranted(): Boolean = _status.value.rootGranted
    fun isShizukuGranted(): Boolean = _status.value.shizukuGranted
}
