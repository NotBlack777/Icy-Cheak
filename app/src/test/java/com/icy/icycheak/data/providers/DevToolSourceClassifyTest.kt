package com.icy.icycheak.data.providers

import com.icy.icycheak.model.DevToolSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM unit tests for [classifyToolSourceFromPath] — pure function, no Android.
 * Validates that the NEW Dev Environment scanner correctly identifies WHERE a
 * tool lives, fixing the old codebase's bug of collapsing everything into
 * "Not installed".
 */
class DevToolSourceClassifyTest {

    private val termux = "/data/data/com.termux/files/usr"
    private val home = "/data/data/com.termux/files/home"

    // ---- Termux ----------------------------------------------------------

    @Test
    fun termuxPrefix_detected() {
        assertEquals(
            DevToolSource.TERMUX,
            classifyToolSourceFromPath("$termux/bin/python3", termux, home)
        )
    }

    @Test
    fun termuxPackageName_detected() {
        assertEquals(
            DevToolSource.TERMUX,
            classifyToolSourceFromPath("/data/app/com.termux/usr/bin/node", termux, home)
        )
    }

    // ---- proot rootfs (Ubuntu, Debian, Arch, etc.) ----------------------

    @Test
    fun ubuntuProot_detected() {
        assertEquals(
            DevToolSource.PROOT_ROOTFS,
            classifyToolSourceFromPath("$home/ubuntu/usr/bin/python3", termux, home)
        )
    }

    @Test
    fun debianProot_detected() {
        assertEquals(
            DevToolSource.PROOT_ROOTFS,
            classifyToolSourceFromPath("$home/debian/usr/bin/gcc", termux, home)
        )
    }

    @Test
    fun archProot_detected() {
        assertEquals(
            DevToolSource.PROOT_ROOTFS,
            classifyToolSourceFromPath("$home/archlinux/usr/bin/make", termux, home)
        )
    }

    @Test
    fun fedoraProot_detected() {
        assertEquals(
            DevToolSource.PROOT_ROOTFS,
            classifyToolSourceFromPath("$home/fedora/usr/bin/dnf", termux, home)
        )
    }

    @Test
    fun kaliProot_detected() {
        assertEquals(
            DevToolSource.PROOT_ROOTFS,
            classifyToolSourceFromPath("$home/kali/usr/bin/nmap", termux, home)
        )
    }

    @Test
    fun prootKeyword_detected() {
        assertEquals(
            DevToolSource.PROOT_ROOTFS,
            classifyToolSourceFromPath("/some/proot/path/bin/tool", termux, home)
        )
    }

    // ---- Python venv -----------------------------------------------------

    @Test
    fun venv_detected() {
        assertEquals(
            DevToolSource.PYTHON_VENV,
            classifyToolSourceFromPath("$home/project/venv/bin/pip", termux, home)
        )
    }

    @Test
    fun dotVenv_detected() {
        assertEquals(
            DevToolSource.PYTHON_VENV,
            classifyToolSourceFromPath("$home/project/.venv/bin/python", termux, home)
        )
    }

    @Test
    fun envBin_detected() {
        assertEquals(
            DevToolSource.PYTHON_VENV,
            classifyToolSourceFromPath("$home/myenv/env/bin/flask", termux, home)
        )
    }

    @Test
    fun virtualenvInPath_detected() {
        assertEquals(
            DevToolSource.PYTHON_VENV,
            classifyToolSourceFromPath("/opt/virtualenvs/myenv/bin/python", termux, home)
        )
    }

    // ---- ~/.local/bin ----------------------------------------------------

    @Test
    fun localBin_detected() {
        assertEquals(
            DevToolSource.LOCAL_BIN,
            classifyToolSourceFromPath("$home/.local/bin/pipx", termux, home)
        )
    }

    @Test
    fun localShare_detected() {
        assertEquals(
            DevToolSource.LOCAL_BIN,
            classifyToolSourceFromPath("$home/.local/share/something/bin/tool", termux, home)
        )
    }

    // ---- current shell PATH (system directories) -------------------------

    @Test
    fun systemBin_detected() {
        assertEquals(
            DevToolSource.CURRENT_SHELL_PATH,
            classifyToolSourceFromPath("/system/bin/sh", termux, home)
        )
    }

    @Test
    fun vendorBin_detected() {
        assertEquals(
            DevToolSource.CURRENT_SHELL_PATH,
            classifyToolSourceFromPath("/vendor/bin/tool", termux, home)
        )
    }

    @Test
    fun usrBin_detected() {
        assertEquals(
            DevToolSource.CURRENT_SHELL_PATH,
            classifyToolSourceFromPath("/usr/bin/git", termux, home)
        )
    }

    @Test
    fun sbin_detected() {
        assertEquals(
            DevToolSource.CURRENT_SHELL_PATH,
            classifyToolSourceFromPath("/sbin/ip", termux, home)
        )
    }

    @Test
    fun binRoot_detected() {
        assertEquals(
            DevToolSource.CURRENT_SHELL_PATH,
            classifyToolSourceFromPath("/bin/ls", termux, home)
        )
    }

    // ---- OTHER / fallback ------------------------------------------------

    @Test
    fun unknownPath_classifiedAsOther() {
        assertEquals(
            DevToolSource.OTHER,
            classifyToolSourceFromPath("/opt/custom/bin/tool", termux, home)
        )
    }

    @Test
    fun emptyPath_classifiedAsOther() {
        assertEquals(
            DevToolSource.OTHER,
            classifyToolSourceFromPath("", termux, home)
        )
    }

    // ---- precedence: proot wins over termux when both match --------------

    @Test
    fun prootTakesPrecedenceOverTermux() {
        // A path that contains both "ubuntu" and "com.termux" should be proot
        assertEquals(
            DevToolSource.PROOT_ROOTFS,
            classifyToolSourceFromPath(
                "/data/data/com.termux/files/home/ubuntu/usr/bin/python3",
                termux, home
            )
        )
    }

    // ---- case insensitivity ----------------------------------------------

    @Test
    fun uppercaseUbuntu_detected() {
        assertEquals(
            DevToolSource.PROOT_ROOTFS,
            classifyToolSourceFromPath("$home/Ubuntu/usr/bin/python3", termux, home)
        )
    }

    @Test
    fun mixedCaseVenv_detected() {
        assertEquals(
            DevToolSource.PYTHON_VENV,
            classifyToolSourceFromPath("$home/project/VENV/bin/python", termux, home)
        )
    }
}