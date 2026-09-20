package com.icy.icycheak.privilege

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [CircuitBreaker] — no Android or coroutines dependency.
 * Uses the explicit [CircuitBreaker.isOpen], [recordFailure], [recordSuccess]
 * overloads that accept a `now` timestamp so we don't depend on wall clock.
 */
class CircuitBreakerTest {

    private lateinit var cb: CircuitBreaker

    @Before
    fun setup() {
        // threshold=3, cooldown=10s
        cb = CircuitBreaker(tripThreshold = 3, cooldownMs = 10_000L)
    }

    // ---- initial state ---------------------------------------------------

    @Test
    fun startsClosed() {
        assertFalse(cb.isOpen(now = 0L))
        assertEquals(0, cb.consecutiveFailures())
        assertEquals(0L, cb.openUntil())
    }

    // ---- threshold tripping ----------------------------------------------

    @Test
    fun staysClosedBelowThreshold() {
        cb.recordFailure(now = 100L)  // 1
        cb.recordFailure(now = 200L)  // 2
        assertFalse(cb.isOpen(now = 300L))
        assertEquals(2, cb.consecutiveFailures())
    }

    @Test
    fun opensAfterThresholdExceeded() {
        cb.recordFailure(now = 100L)  // 1
        cb.recordFailure(now = 200L)  // 2
        cb.recordFailure(now = 300L)  // 3 → tripped
        assertTrue(cb.isOpen(now = 400L))
        assertEquals(10_300L, cb.openUntil()) // 300 + 10_000
    }

    @Test
    fun opensExactlyAtThreshold() {
        repeat(3) { cb.recordFailure(now = it * 100L) }
        assertTrue(cb.isOpen(now = 500L))
    }

    // ---- cooldown & half-open transition ---------------------------------

    @Test
    fun staysOpenDuringCooldown() {
        repeat(3) { cb.recordFailure(now = it * 100L) } // tripped at t=200
        assertTrue(cb.isOpen(now = 5_000L))  // well within 10s cooldown
        assertTrue(cb.isOpen(now = 10_199L)) // just before expiry
    }

    @Test
    fun transitionsToHalfOpenAfterCooldown() {
        repeat(3) { cb.recordFailure(now = it * 100L) } // openUntil = 200 + 10000 = 10200
        // Query at t=10300 (> 10200): isOpen should return false and reset
        assertFalse(cb.isOpen(now = 10_300L))
        assertEquals(0, cb.consecutiveFailures())
        assertEquals(0L, cb.openUntil())
    }

    // ---- half-open: success resets, failure re-opens ---------------------

    @Test
    fun halfOpen_successResetsFully() {
        repeat(3) { cb.recordFailure(now = it * 100L) }
        // Cooldown expires
        assertFalse(cb.isOpen(now = 11_000L))
        cb.recordSuccess()
        assertEquals(0, cb.consecutiveFailures())
        assertEquals(0L, cb.openUntil())
        assertFalse(cb.isOpen(now = 20_000L))
    }

    @Test
    fun halfOpen_failureReOpens() {
        repeat(3) { cb.recordFailure(now = it * 100L) }
        // Cooldown expires → half-open
        assertFalse(cb.isOpen(now = 11_000L))
        // A new failure at t=11_000 re-opens with fresh cooldown
        cb.recordFailure(now = 11_000L)
        assertTrue(cb.isOpen(now = 11_500L))
        assertEquals(21_000L, cb.openUntil()) // 11_000 + 10_000
    }

    // ---- recordSuccess resets failure counter ----------------------------

    @Test
    fun successResetsFailureCount() {
        cb.recordFailure(now = 100L)
        cb.recordFailure(now = 200L)
        assertEquals(2, cb.consecutiveFailures())
        cb.recordSuccess()
        assertEquals(0, cb.consecutiveFailures())
        // Two more failures alone don't trip (threshold=3)
        cb.recordFailure(now = 300L)
        cb.recordFailure(now = 400L)
        assertFalse(cb.isOpen(now = 500L))
    }

    // ---- manual reset ----------------------------------------------------

    @Test
    fun resetClearsEverything() {
        repeat(5) { cb.recordFailure(now = it * 100L) }
        assertTrue(cb.isOpen(now = 1_000L))
        cb.reset()
        assertFalse(cb.isOpen(now = 2_000L))
        assertEquals(0, cb.consecutiveFailures())
        assertEquals(0L, cb.openUntil())
    }

    // ---- different threshold values --------------------------------------

    @Test
    fun thresholdOne_opensOnFirstFailure() {
        val cb1 = CircuitBreaker(tripThreshold = 1, cooldownMs = 5_000L)
        cb1.recordFailure(now = 0L)
        assertTrue(cb1.isOpen(now = 100L))
    }

    @Test
    fun thresholdFive_needsFiveFailures() {
        val cb5 = CircuitBreaker(tripThreshold = 5, cooldownMs = 1_000L)
        repeat(4) { cb5.recordFailure(now = it * 100L) }
        assertFalse(cb5.isOpen(now = 500L))
        cb5.recordFailure(now = 500L)
        assertTrue(cb5.isOpen(now = 600L))
    }

    // ---- multiple open-close cycles --------------------------------------

    @Test
    fun multipleTripsEachWithCorrectCooldown() {
        // First trip
        repeat(3) { cb.recordFailure(now = it * 100L) } // openUntil = 10200
        assertTrue(cb.isOpen(now = 5_000L))
        // Cooldown expires
        assertFalse(cb.isOpen(now = 11_000L))
        // Fail again immediately → re-opens
        cb.recordFailure(now = 11_000L)
        assertTrue(cb.isOpen(now = 15_000L))  // 11000+10000 = 21000
        assertFalse(cb.isOpen(now = 22_000L)) // cooldown elapsed
    }
}