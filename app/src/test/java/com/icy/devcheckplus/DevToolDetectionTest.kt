package com.icy.devcheckplus

import com.icy.devcheckplus.data.DevEnvironmentDataProvider
import com.icy.devcheckplus.data.DevTool
import com.icy.devcheckplus.data.ToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the Dev Environment detector: probe-script construction,
 * tagged-output parsing, and environment classification.
 *
 * The classification matrix mirrors the audit's false-negative requirements —
 * every scenario must produce a distinct, meaningful state, and none may
 * collapse into "Not installed".
 */
class DevToolDetectionTest {

    private val pip = DevEnvironmentDataProvider.SPECS.first { it.tool == DevTool.PIP }
    private val python = DevEnvironmentDataProvider.SPECS.first { it.tool == DevTool.PYTHON }
    private val node = DevEnvironmentDataProvider.SPECS.first { it.tool == DevTool.NODE }

    private fun classify(spec: com.icy.devcheckplus.data.DevToolSpec, lines: List<String>) =
        DevEnvironmentDataProvider.classify(
            spec,
            DevEnvironmentDataProvider.parseProbeOutput(lines),
            "TestShell"
        )

    /* ---------------------------------------------------------------- */
    /* Script construction                                               */
    /* ---------------------------------------------------------------- */

    @Test
    fun `probe script resolves executables with command -v`() {
        val script = DevEnvironmentDataProvider.buildProbeScript(node)
        assertTrue(script.contains("command -v"))
    }

    @Test
    fun `pip probe tries pip3 before pip and includes python -m pip fallback`() {
        val script = DevEnvironmentDataProvider.buildProbeScript(pip)
        // both standalone forms…
        assertTrue(script.contains("for c in pip3 pip;"))
        // …and BOTH module forms, so "python exists but bare pip does not"
        // can never report Not installed.
        assertTrue(script.contains("python3 -m pip --version"))
        assertTrue(script.contains("python -m pip --version"))
    }

    @Test
    fun `python probe tests python3 and python candidates`() {
        val script = DevEnvironmentDataProvider.buildProbeScript(python)
        assertTrue(script.contains("for c in python3 python;"))
    }

    @Test
    fun `probe script searches termux local-bin cargo and proot locations`() {
        val script = DevEnvironmentDataProvider.buildProbeScript(node)
        assertTrue(script.contains("/data/data/com.termux/files/usr/bin"))
        assertTrue(script.contains("\$HOME/.local/bin"))
        assertTrue(script.contains("/data/data/com.termux/files/home/.local/bin"))
        assertTrue(script.contains("\$HOME/.cargo/bin"))
        assertTrue(script.contains("installed-rootfs"))
    }

    @Test
    fun `probe script detects venvs via pyvenv cfg`() {
        val script = DevEnvironmentDataProvider.buildProbeScript(pip)
        assertTrue(script.contains("pyvenv.cfg"))
    }

    @Test
    fun `probe script reports permission-denied files`() {
        val script = DevEnvironmentDataProvider.buildProbeScript(node)
        assertTrue(script.contains("DENY|"))
    }

    @Test
    fun `module fallback only exists for tools that define one`() {
        assertFalse(DevEnvironmentDataProvider.buildProbeScript(node).contains("MOD|"))
        assertTrue(DevEnvironmentDataProvider.buildProbeScript(pip).contains("MOD|"))
    }

    /* ---------------------------------------------------------------- */
    /* Matrix case 1 — tool on the normal PATH                           */
    /* ---------------------------------------------------------------- */

    @Test
    fun `tool on current PATH is DIRECT and runnable`() {
        val result = classify(
            node,
            listOf(
                "DIRECT|/usr/bin/node",
                "DIRECT_VER|v20.11.1"
            )
        )
        assertEquals(ToolStatus.DIRECT, result.status)
        assertEquals("v20.11.1", result.version)
        assertEquals("/usr/bin/node", result.path)
        assertTrue(result.directlyRunnable)
        assertTrue(result.installed)
        assertEquals("Installed", result.statusLine)
    }

    /* ---------------------------------------------------------------- */
    /* Matrix case 2 — tool only in Termux PATH                          */
    /* ---------------------------------------------------------------- */

    @Test
    fun `tool only in termux is labeled installed in termux`() {
        val result = classify(
            node,
            listOf(
                "ENV|termux|/data/data/com.termux/files/usr/bin/node",
                "ENV_VER|termux|v22.2.0"
            )
        )
        assertEquals(ToolStatus.TERMUX, result.status)
        assertEquals("v22.2.0", result.version)
        assertEquals("Termux", result.environment)
        assertTrue(result.directlyRunnable)
        assertEquals("Installed in Termux", result.statusLine)
    }

    /* ---------------------------------------------------------------- */
    /* Matrix case 3 — tool only in ~\.local\bin                          */
    /* ---------------------------------------------------------------- */

    @Test
    fun `tool only in local bin is installed but not on current path`() {
        val result = classify(
            pip,
            listOf(
                "ENV|localbin|/data/data/com.termux/files/home/.local/bin/pip3",
                "ENV_VER|localbin|pip 24.0 from /data/data/com.termux/files/usr/lib/python3.12/site-packages/pip (python 3.12)"
            )
        )
        assertEquals(ToolStatus.OTHER_PATH_DIR, result.status)
        assertEquals("~/.local/bin", result.environment)
        assertTrue(result.directlyRunnable)
        assertEquals("Installed but not on current PATH", result.statusLine)
    }

    /* ---------------------------------------------------------------- */
    /* Matrix cases 4+5 — python exists but bare pip does not; pip only  */
    /* works via `python -m pip`                                         */
    /* ---------------------------------------------------------------- */

    @Test
    fun `pip reachable only as python module is classified as module`() {
        val result = classify(
            pip,
            listOf("MOD|python3 -m pip|pip 23.3.1 from /usr/lib/python3/dist-packages/pip (python 3.11)")
        )
        assertEquals(ToolStatus.PYTHON_MODULE, result.status)
        assertFalse(result.directlyRunnable)
        assertTrue(result.installed)
        // module location is extracted from pip's own output
        assertEquals("/usr/lib/python3/dist-packages/pip", result.path)
        assertNotNull(result.environmentLine)
        assertTrue(result.environmentLine!!.contains("python -m"))
        assertEquals("Python module — no standalone command", result.statusLine)
    }

    @Test
    fun `pip with no probe output at all is the only not-installed case`() {
        val result = classify(pip, emptyList())
        assertEquals(ToolStatus.NOT_INSTALLED, result.status)
        assertFalse(result.installed)
        assertEquals("Not installed", result.statusLine)
    }

    /* ---------------------------------------------------------------- */
    /* Matrix case 6 — pip inside a venv                                 */
    /* ---------------------------------------------------------------- */

    @Test
    fun `tool resolved inside a venv is classified as venv via pyvenv cfg`() {
        val result = classify(
            pip,
            listOf(
                "DIRECT|/data/data/com.termux/files/home/.venv/bin/pip3",
                "DIRECT_VER|pip 24.0 from /data/data/com.termux/files/home/.venv/lib/python3.12/site-packages/pip (python 3.12)",
                "VENV|/data/data/com.termux/files/home/.venv"
            )
        )
        assertEquals(ToolStatus.VENV, result.status)
        assertTrue(result.directlyRunnable)
        assertEquals("Installed in Python venv", result.statusLine)
        assertTrue(result.environment!!.contains(".venv"))
    }

    @Test
    fun `tool in venv found via env dirs is classified as venv`() {
        val result = classify(
            pip,
            listOf(
                "ENV|venv|/data/data/com.termux/files/home/.venv/bin/pip3",
                "ENV_VER|venv|pip 24.0 from /x (python 3.12)",
                "VENV|/data/data/com.termux/files/home/.venv"
            )
        )
        assertEquals(ToolStatus.VENV, result.status)
    }

    /* ---------------------------------------------------------------- */
    /* Matrix case 7 — tool exists in Ubuntu / proot only                */
    /* ---------------------------------------------------------------- */

    @Test
    fun `tool only in proot rootfs is installed in ubuntu and not runnable`() {
        val result = classify(
            pip,
            listOf(
                "ENV|proot|/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu/usr/bin/pip3",
                "PROOT_DISTRO|ubuntu"
            )
        )
        assertEquals(ToolStatus.PROOT, result.status)
        assertFalse(result.directlyRunnable)
        assertTrue(result.installed)
        assertEquals("Ubuntu proot", result.environment)
        assertEquals("Installed in Ubuntu proot", result.statusLine)
        val envLine = result.environmentLine
        assertNotNull(envLine)
        assertTrue(envLine!!.contains("not directly runnable"))
    }

    /* ---------------------------------------------------------------- */
    /* Matrix case 8 — binary exists but version command fails           */
    /* ---------------------------------------------------------------- */

    @Test
    fun `binary present with empty version is still installed`() {
        val result = classify(
            node,
            listOf("DIRECT|/usr/bin/node", "DIRECT_VER|")
        )
        assertEquals(ToolStatus.DIRECT, result.status)
        assertTrue(result.installed)
        assertTrue(result.versionProbeFailed)
        assertNull(result.version)
        assertEquals("Installed — version check failed", result.statusLine)
    }

    /* ---------------------------------------------------------------- */
    /* Matrix case 9 — file exists but permission denied                 */
    /* ---------------------------------------------------------------- */

    @Test
    fun `non executable file is reported as permission denied not missing`() {
        val result = classify(
            node,
            listOf("DENY|/data/data/com.termux/files/usr/bin/node")
        )
        assertEquals(ToolStatus.PERMISSION_DENIED, result.status)
        assertTrue(result.installed) // detected — deliberately NOT "Not installed"
        assertFalse(result.directlyRunnable)
        assertEquals("/data/data/com.termux/files/usr/bin/node", result.path)
        assertEquals("Detected — permission denied", result.statusLine)
    }

    /* ---------------------------------------------------------------- */
    /* Matrix case 10 — probe timed out (handled one layer up)           */
    /* ---------------------------------------------------------------- */

    @Test
    fun `timed out probe keeps its own distinct state`() {
        val timedOut = com.icy.devcheckplus.data.DevToolResult(
            tool = DevTool.NODE,
            timedOut = true,
            executionSource = "Watchdog"
        )
        assertEquals("Timed out", timedOut.statusLine)
        assertFalse(timedOut.installed)
    }

    /* ---------------------------------------------------------------- */
    /* Matrix case 11 — unusual PATH: pristine resolution wins           */
    /* ---------------------------------------------------------------- */

    @Test
    fun `direct hit takes priority over environment hits`() {
        val result = classify(
            node,
            listOf(
                "DIRECT|/system/bin/node",
                "DIRECT_VER|v18.0.0",
                "ENV|termux|/data/data/com.termux/files/usr/bin/node",
                "ENV_VER|termux|v22.0.0"
            )
        )
        assertEquals(ToolStatus.DIRECT, result.status)
        assertEquals("v18.0.0", result.version)
    }

    /* ---------------------------------------------------------------- */
    /* Matrix case 12 — root HOME differs: termux binary found on the    */
    /* answering shell's own PATH classifies as termux, not generic PATH  */
    /* ---------------------------------------------------------------- */

    @Test
    fun `termux binary on root PATH is still classified as termux`() {
        val result = classify(
            node,
            listOf(
                "DIRECT|/data/data/com.termux/files/usr/bin/node",
                "DIRECT_VER|v22.2.0"
            )
        )
        assertEquals(ToolStatus.TERMUX, result.status)
        assertEquals("Termux", result.environment)
        assertTrue(result.directlyRunnable)
    }

    /* ---------------------------------------------------------------- */
    /* Parser robustness                                                 */
    /* ---------------------------------------------------------------- */

    @Test
    fun `parser ignores junk lines without tags`() {
        val report = DevEnvironmentDataProvider.parseProbeOutput(
            listOf(
                "warning: something chatty from the shell",
                "",
                "|no-tag",
                "DIRECT|/usr/bin/git",
                "DIRECT_VER|git version 2.43.0"
            )
        )
        assertEquals("/usr/bin/git", report.directPath)
        assertEquals("git version 2.43.0", report.directVersion)
    }

    @Test
    fun `first hit wins when a tool appears in several environments`() {
        val report = DevEnvironmentDataProvider.parseProbeOutput(
            listOf(
                "ENV|localbin|/home/x/.local/bin/aider",
                "ENV_VER|localbin|aider 0.50",
                "ENV|proot|/rootfs/ubuntu/usr/bin/aider"
            )
        )
        val result = DevEnvironmentDataProvider.classify(
            DevEnvironmentDataProvider.SPECS.first { it.tool == DevTool.AIDER },
            report,
            "TestShell"
        )
        assertEquals(ToolStatus.OTHER_PATH_DIR, result.status)
        assertEquals("/home/x/.local/bin/aider", result.path)
    }

    @Test
    fun `every dev tool has a spec or a dedicated probe`() {
        val specTools = DevEnvironmentDataProvider.SPECS.map { it.tool }.toSet()
        val missing = DevTool.values().filterNot { it in specTools || it == DevTool.TERMUX }
        assertTrue("Tools without any probe: $missing", missing.isEmpty())
    }
}
