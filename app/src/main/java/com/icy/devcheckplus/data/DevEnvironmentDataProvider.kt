package com.icy.devcheckplus.data

import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.privilege.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

enum class DevToolKind {
    RUNTIME,
    PACKAGE_MANAGER,
    LANGUAGE,
    VCS,
    CONTAINER,
    TOOLCHAIN
}

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

data class DevToolResult(
    val tool: DevTool,
    val version: String? = null,
    val path: String? = null,
    val source: String? = null,
    val timedOut: Boolean = false
) {
    val installed: Boolean get() = version != null

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

object DevEnvironmentDataProvider {

    private const val PROBE_TIMEOUT_MS = 6_000L
    private const val TERMUX_BIN_DIR = "/data/data/com.termux/files/usr/bin"

    // Always include these in PATH construction — existence is tested inside shell, not via File.isDirectory from app sandbox
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
        versionCheck(DevTool.JAVA, "java -version 2>&1", "java"),
        versionCheck(DevTool.RUBY, "ruby -v", "ruby"),
        versionCheck(DevTool.GO, "go version", "go"),
        versionCheck(DevTool.RUST, "(cargo -V 2>/dev/null || rustc -V 2>/dev/null)", "cargo", primaryLead = "cargo", fallbackLead = "rustc"),
        versionCheck(DevTool.PHP, "php -v", "php"),
        versionCheck(DevTool.DOCKER, "docker -v", "docker"),
        // Termux presence: check via shell, not File.isDirectory sandbox — test existence inside privilege engine
        ToolCheck(
            tool = DevTool.TERMUX,
            command = "if [ -d $TERMUX_BIN_DIR ]; then ls $TERMUX_BIN_DIR 2>/dev/null | head -n 40; echo '__TERMUX_EXISTS__'; else echo '__TERMUX_MISSING__'; fi",
            privileged = true
        )
    )

    private fun versionCheck(
        tool: DevTool,
        versionCmd: String,
        whichBin: String,
        primaryLead: String = whichBin,
        fallbackLead: String? = null
    ): ToolCheck {
        val resolve = if (fallbackLead != null) {
            "command -v $primaryLead 2>/dev/null || command -v $fallbackLead 2>/dev/null"
        } else {
            "command -v $whichBin 2>/dev/null"
        }
        return ToolCheck(
            tool = tool,
            command = withExtraPath("$versionCmd 2>/dev/null | head -n1; $resolve"),
            privileged = false
        )
    }

    /**
     * FIXED: Do NOT use File.isDirectory from app sandbox — it cannot see /data/data/com.termux due to sandbox.
     * Instead construct PATH inside shell execution context, testing [ -d dir ] via privilege engine.
     */
    private fun withExtraPath(cmd: String): String {
        val dirsSpaceSeparated = EXTRA_BIN_PATHS.joinToString(" ")
        return "for d in $dirsSpaceSeparated; do [ -d \$d ] 2>/dev/null && export PATH=\$d:\$PATH 2>/dev/null; done; $cmd"
    }

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
            val trimmed = stdout.map { it.trim() }.filter { it.isNotBlank() }
            val hasExistsMarker = trimmed.any { it == "__TERMUX_EXISTS__" }
            val hasMissingMarker = trimmed.any { it == "__TERMUX_MISSING__" }
            val entries = trimmed.filter { it != "__TERMUX_EXISTS__" && it != "__TERMUX_MISSING__" }
            return if (hasMissingMarker || (!hasExistsMarker && entries.isEmpty())) {
                DevToolResult(tool = check.tool, source = source)
            } else {
                // FIXED: Report as Installed / Detected, not "X binaries in PATH" which is not a version
                val status = if (entries.isNotEmpty()) {
                    "Installed (${entries.size} binaries)"
                } else {
                    "Installed"
                }
                DevToolResult(
                    tool = check.tool,
                    version = status,
                    path = TERMUX_BIN_DIR,
                    source = source
                )
            }
        }

        val lines = stdout.map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
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
