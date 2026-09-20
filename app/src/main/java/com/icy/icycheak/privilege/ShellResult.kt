package com.icy.icycheak.privilege

/**
 * Result of a single privileged/non-privileged shell execution.
 *
 * Every feature (Console, Dev Environment, app management, live telemetry,
 * updater install) goes through [PrivilegeEngine.execute], so they all get the
 * same timeout + circuit-breaker guarantees and the same uniform result shape.
 */
data class ShellResult(
    /** True when the process exited with code 0. */
    val isSuccess: Boolean,
    /** Process exit code (or -1 when the execution never produced one). */
    val exitCode: Int,
    /** Captured stdout, line by line. */
    val stdout: List<String>,
    /** Captured stderr, line by line. */
    val stderr: List<String>,
    /** Human-readable source, e.g. "Root (libsu)", "Shizuku", "Standard". */
    val executionSource: String,
    /** True when the hard timeout fired before the process finished. */
    val timedOut: Boolean = false,
    /** True when the call was rejected because the circuit breaker was open. */
    val circuitOpen: Boolean = false
) {
    /** stdout + stderr joined, used for the Console view and error messages. */
    val combinedOutput: String
        get() = buildString {
            if (stdout.isNotEmpty()) append(stdout.joinToString("\n"))
            if (stderr.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(stderr.joinToString("\n"))
            }
        }

    val isUnavailable: Boolean
        get() = circuitOpen || (timedOut && !isSuccess)
}
