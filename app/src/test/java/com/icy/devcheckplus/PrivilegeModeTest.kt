package com.icy.devcheckplus

import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Privilege-mode fallback semantics (audit section 12):
 *  - AUTO tries Root → Shizuku → none, in that order;
 *  - an explicit ROOT or SHIZUKU selection NEVER silently downgrades to a
 *    different engine — without its grant the mode is NONE;
 *  - NONE stays NONE regardless of what is available.
 */
class PrivilegeModeTest {

    private fun resolve(root: Boolean, shizuku: Boolean, pref: PrivilegeMode) =
        PrivilegeManager.resolveActiveMode(root, shizuku, pref)

    @Test
    fun `auto prefers root over shizuku`() {
        assertEquals(PrivilegeMode.ROOT, resolve(root = true, shizuku = true, pref = PrivilegeMode.AUTO))
        assertEquals(PrivilegeMode.ROOT, resolve(root = true, shizuku = false, pref = PrivilegeMode.AUTO))
    }

    @Test
    fun `auto falls back to shizuku when root is not granted`() {
        assertEquals(PrivilegeMode.SHIZUKU, resolve(root = false, shizuku = true, pref = PrivilegeMode.AUTO))
    }

    @Test
    fun `auto with nothing granted is none`() {
        assertEquals(PrivilegeMode.NONE, resolve(root = false, shizuku = false, pref = PrivilegeMode.AUTO))
    }

    @Test
    fun `explicit root mode never downgrades to shizuku`() {
        // Even with Shizuku granted: the user asked for Root, so without root
        // the answer is NONE — never a silent engine swap.
        assertEquals(PrivilegeMode.NONE, resolve(root = false, shizuku = true, pref = PrivilegeMode.ROOT))
        assertEquals(PrivilegeMode.NONE, resolve(root = false, shizuku = false, pref = PrivilegeMode.ROOT))
        assertEquals(PrivilegeMode.ROOT, resolve(root = true, shizuku = false, pref = PrivilegeMode.ROOT))
    }

    @Test
    fun `explicit shizuku mode never downgrades to root`() {
        assertEquals(PrivilegeMode.NONE, resolve(root = true, shizuku = false, pref = PrivilegeMode.SHIZUKU))
        assertEquals(PrivilegeMode.NONE, resolve(root = false, shizuku = false, pref = PrivilegeMode.SHIZUKU))
        assertEquals(PrivilegeMode.SHIZUKU, resolve(root = false, shizuku = true, pref = PrivilegeMode.SHIZUKU))
    }

    @Test
    fun `none stays none even when everything is granted`() {
        assertEquals(PrivilegeMode.NONE, resolve(root = true, shizuku = true, pref = PrivilegeMode.NONE))
        assertEquals(PrivilegeMode.NONE, resolve(root = false, shizuku = false, pref = PrivilegeMode.NONE))
    }
}
