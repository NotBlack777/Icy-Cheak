package com.icy.icycheak.privilege

/**
 * Pure, framework-free circuit breaker used by [PrivilegeEngine].
 *
 * Kept independent of Android so it can be unit-tested on the JVM with a fake
 * clock. The breaker opens after [tripThreshold] consecutive failures and stays
 * open for [cooldownMs]; while open, every privileged call is rejected fast
 * instead of hanging on a shell that is clearly wedged.
 *
 * Once the cooldown elapses the breaker moves to a half-open state: the next
 * call is allowed through and a single success resets it, a single failure
 * re-opens it.
 */
class CircuitBreaker(
    private val tripThreshold: Int = 3,
    private val cooldownMs: Long = 10_000L
) {
    private var consecutiveFailures = 0
    @Volatile private var openUntil = 0L

    /** True if the breaker is currently open and should reject new calls. */
    fun isOpen(now: Long = System.currentTimeMillis()): Boolean {
        if (openUntil > now) return true
        if (openUntil != 0L) {
            // Cooldown elapsed → half-open: allow the next call, reset counter.
            openUntil = 0L
            consecutiveFailures = 0
        }
        return false
    }

    fun recordSuccess() {
        consecutiveFailures = 0
        openUntil = 0L
    }

    fun recordFailure(now: Long = System.currentTimeMillis()) {
        consecutiveFailures++
        if (consecutiveFailures >= tripThreshold) {
            openUntil = now + cooldownMs
        }
    }

    fun consecutiveFailures(): Int = consecutiveFailures
    fun openUntil(): Long = openUntil

    /** Test/diagnostic helper. */
    fun reset() {
        consecutiveFailures = 0
        openUntil = 0L
    }
}
