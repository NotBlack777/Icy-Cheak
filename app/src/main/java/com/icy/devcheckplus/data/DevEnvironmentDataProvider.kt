package com.icy.devcheckplus.data

import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.privilege.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

/** Broad category a development tool belongs to — drives the card's icon/tint. */
enum class DevToolKind {
    RUNTIME,
    PACKAGE_MANAGER,
    LANGUAGE,
    VCS,
    CONTAINER,
    TOOLCHAIN
}

/**
 * One development tool / runtime the detector knows how to probe.
 *
 * Everything is data, not UI: the screen maps [kind] to an icon and a tint colour.
 * [needsPrivilege] marks probes whose answer is only meaningful with elevation
 * (reading under Termux's `/data/data` home requires root/Shizuku).
 */
enum class DevTool(val label: String, val kind: DevToolKind, val needsPrivilege: Boolean = false) {
    NODE("Node.js", DevToolKind.RUNTIME),
    NPM("npm", DevToolKind.PACKAGE_MANAGER),
    PYTHON("Python", DevToolKind.RUNTIME),
    PIP("pip", DevToolKind.PACKAGE_MANAGER),
    GIT("Git", DevToolKind.VCS),
    JAVA("Java (JDK)", DevToolKind.RUNTIME),
    RUBY("Ruby", DevToolKind.RUNTIME),
    GO("Go", DevToolKind.LANGUAGE),
    RUST("Rust / cargo", DevToolKind.LANGUAGE),
    PHP("PHP", DevToolKind.LANGUAGE),
    DOCKER("Docker", DevToolKind.CONTAINER),
    TERMUX("Termux", DevToolKind.TOOLCHAIN, needsPrivilege = true)
}

/** The outcome of one tool probe. */
data class DevToolResult(
    val tool: DevTool,
    /** Human-readable version line. `null` means the tool was not found. */
    val version: String? = null,
    /** Absolute install path when the resolver found one. */
    val path: String? = null,
    /** Which shell engine answered (root, Shizuku, standard, watchdog). */
    val source: String? = null,
    /** True when the per-probe watchdog fired before the shell answered. */
    val timedOut: Boolean = false
) {
    val installed: Boolean get() = version != null

    /** Plain rendering for the export report ("Not installed", version, …). */
    fun toInfoItem(termuxNote: String? = null): InfoItem = InfoItem(
        title = tool.label,
        value = when {
            timedOut -> "Timed out"
            version != null -> version
            else -> "Not installed"
        },
        subtitle = when {
            timedOut -> "probe exceeded its watchdog"
            version != null -> path
            tool.needsPrivilege && termuxNote != null -> termuxNote
            else -> null
        },
        requiresPrivilege = false
    )
}

/**
 * Detects development CLIs / runtimes available from a device shell.
 *
 * Each probe is a single, cheap combined command — run the tool's own version
 * flag (which exits non-zero and prints nothing when the tool is absent) and, if
 * it spoke, resolve its absolute path with `command -v`. The whole thing is
 * prefixed with an `export PATH=…` that prepends Termux's bin directories, which
 * is what makes Termux-installed Node/Python/… discoverable: those live under
 * `/data/data/com.termux/files/usr/bin`, which no shell PATH contains by
 * default.
 *
 * Robustness rules (same as every elevated read in the app):
 *  - probes run in parallel on [Dispatchers.IO], each behind its own short
 *    watchdog via [PrivilegeManager.executeCommand], so a hung shell/`su`
 *    prompt can never block the screen — that probe reports "Timed out";
 *  - a missing tool is not an error: its command yields no stdout, which maps
 *    to "Not installed". Nothing here throws past the screen.
 */
object DevEnvironmentDataProvider {

    /** One probe may wait this long for the shell — short on purpose. */
    private const val PROBE_TIMEOUT_MS = 6_000L

    /** Where Termux keeps its user binaries (readable only with elevation). */
    private const val TERMUX_BIN_DIR = "/data/data/com.termux/files/usr/bin"

    /**
     * Directories worth prepending to PATH so a shell probe finds tools that
     * Android's default PATH (`/system/bin`, …) never contains. Directories that
     * do not exist are filtered out: they simply cannot be relevant on this
     * device, and `command -v` only consults directories that exist in PATH.
     */
    private val EXTRA_BIN_PATHS = listOf(
        TERMUX_BIN_DIR,
        "/data/data/com.termux/files/home/go/bin",
        "/data/data/com.termux/files/home/.cargo/bin"
    )

    private data class ToolCheck(
        val tool: DevTool,
        val command: String,
        val privileged: Boolean
    )

    /** One-shot parallel scan of every tool. Order is [DevTool] declaration order. */
    suspend fun scanTools(): List<DevToolResult> = withContext(Dispatchers.IO) {
        val checks = buildChecks()
        coroutineScope {
            checks.map { check ->
                async {
                    runCatching { runProbe(check) }.getOrElse { failure ->
                        DevToolResult(
                            tool = check.tool,
                            source = "error — ${failure.message ?: failure.javaClass.simpleName}",
                            timedOut = false
                        )
                    }
                }
            }.awaitAll()
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Probe construction                                                */
    /* ------------------------------------------------------------------ */

    private fun buildChecks(): List<ToolCheck> = listOf(
        versionCheck(DevTool.NODE, "node -v", "node"),
        versionCheck(DevTool.NPM, "npm -v", "npm"),
        versionCheck(
            DevTool.PYTHON,
            "(python3 --version 2>&1 || python --version 2>&1) 2>/dev/null",
            "python3",
            primaryLead = "python3",
            fallbackLead = "python"
        ),
        versionCheck(
            DevTool.PIP,
            "(pip3 --version 2>/dev/null || pip --version 2>/dev/null)",
            "pip3",
            primaryLead = "pip3",
            fallbackLead = "pip"
        ),
        versionCheck(DevTool.GIT, "git --version", "git"),
        // java prints its version to stderr, so redirect it onto stdout.
        versionCheck(DevTool.JAVA, "java -version 2>&1", "java"),
        versionCheck(DevTool.RUBY, "ruby -v", "ruby"),
        versionCheck(DevTool.GO, "go version", "go"),
        versionCheck(DevTool.RUST, "(cargo -V 2>/dev/null || rustc -V 2>/dev/null)", "cargo", primaryLead = "cargo", fallbackLead = "rustc"),
        versionCheck(DevTool.PHP, "php -v", "php"),
        versionCheck(DevTool.DOCKER, "docker -v", "docker"),
        // Termux presence: a non-empty listing of its bin directory means the
        // termux-app package exists there. Only meaningful with elevation.
        ToolCheck(
            tool = DevTool.TERMUX,
            command = "ls $TERMUX_BIN_DIR 2>/dev/null | head -n 40",
            privileged = true
        )
    )

    /** Builds `version-flag | head -n1; command -v <bin>` with Termux PATH prepended. */
    private fun versionCheck(
        tool: DevTool,
        versionCmd: String,
        whichBin: String,
        primaryLead: String = whichBin,
        fallbackLead: String? = null
    ): ToolCheck {
        val resolve = if (fallbackLead != null) {
            // python3 then python (or pip3 then pip): first resolver that wins.
            "command -v $primaryLead 2>/dev/null || command -v $fallbackLead 2>/dev/null"
        } else {
            "command -v $whichBin 2>/dev/null"
        }
        // Colons are only printed across two lines of stdout:
        //   line 0 — version (already collapsed to one line by `head -n1`),
        //   line 1 — absolute path when the resolver found it.
        return ToolCheck(
            tool = tool,
            command = withExtraPath("$versionCmd 2>/dev/null | head -n1; $resolve"),
            privileged = false
        )
    }

    /** Prefixes [cmd] with an `export PATH` that includes the Termux bin dirs. */
    private fun withExtraPath(cmd: String): String {
        val existing = EXTRA_BIN_PATHS.filter { File(it).isDirectory }
        if (existing.isEmpty()) return cmd
        val joined = existing.joinToString(":")
        return "export PATH=\"$joined:\$PATH\" 2>/dev/null; $cmd"
    }

    /* ------------------------------------------------------------------ */
    /*  Execution                                                         */
    /* ------------------------------------------------------------------ */

    private suspend fun runProbe(check: ToolCheck): DevToolResult {
        val result = PrivilegeManager.executeCommand(check.command, timeoutMs = PROBE_TIMEOUT_MS)
        return if (result.timedOut) {
            DevToolResult(tool = check.tool, source = result.executionSource, timedOut = true)
        } else {
            parse(check, result.stdout, result.executionSource)
        }
    }

    private fun parse(check: ToolCheck, stdout: List<String>, source: String): DevToolResult {
        if (check.tool == DevTool.TERMUX) {
            val entries = stdout.map { it.trim() }.filter { it.isNotBlank() }
            return if (entries.isEmpty()) {
                DevToolResult(tool = check.tool, source = source)
            } else {
                DevToolResult(
                    tool = check.tool,
                    version = "${entries.size} binaries in PATH",
                    path = TERMUX_BIN_DIR,
                    source = source
                )
            }
        }

        val lines = stdout.map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            // The version flag printed nothing → the tool simply is not installed.
            return DevToolResult(tool = check.tool, source = source)
        }
        return DevToolResult(
            tool = check.tool,
            version = lines.first(),
            path = lines.getOrNull(1),
            source = source
        )
    }
}
