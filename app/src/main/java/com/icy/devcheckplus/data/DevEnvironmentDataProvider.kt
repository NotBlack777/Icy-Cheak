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
    TOOLCHAIN,
    PYTHON_TOOLING
}

enum class DevTool(val label: String, val kind: DevToolKind) {
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
    TERMUX("Termux", DevToolKind.TOOLCHAIN),
    UV("uv", DevToolKind.PYTHON_TOOLING),
    AIDER("aider", DevToolKind.PYTHON_TOOLING),
    YARN("Yarn", DevToolKind.PACKAGE_MANAGER),
    POETRY("Poetry", DevToolKind.PYTHON_TOOLING)
}

/* ------------------------------------------------------------------ */
/*  Environment classification                                         */
/* ------------------------------------------------------------------ */

/**
 * Where a detected tool actually lives. The point of this detector is to never
 * collapse all of these into one "Installed"/"Not installed" pair: a pip that
 * only exists inside an Ubuntu proot rootfs is a fundamentally different answer
 * from one the current shell can run.
 */
enum class ToolStatus {
    /** On the probing shell's own PATH — directly runnable right now. */
    DIRECT,

    /** Inside Termux's prefix (`/data/data/com.termux/files/usr`). */
    TERMUX,

    /** Inside a Python virtual environment (pyvenv.cfg detected). */
    VENV,

    /** Inside a proot-distro rootfs (Ubuntu etc.) — needs proot to run. */
    PROOT,

    /** Executable exists in a known bin dir (~/.local/bin, ~/.cargo/bin, …)
     *  that the probing shell does not have on its PATH. */
    OTHER_PATH_DIR,

    /** No standalone launcher, but `python -m <module>` works. */
    PYTHON_MODULE,

    /** A file exists at a known location but is not executable by this shell. */
    PERMISSION_DENIED,

    NOT_INSTALLED
}

/**
 * One fully classified probe outcome.
 *
 * @param versionProbeFailed the executable was found but its version command
 *        produced nothing (non-zero exit / no output) — the tool IS there.
 */
data class DevToolResult(
    val tool: DevTool,
    val status: ToolStatus = ToolStatus.NOT_INSTALLED,
    val version: String? = null,
    val path: String? = null,
    val environment: String? = null,
    val directlyRunnable: Boolean = false,
    val versionProbeFailed: Boolean = false,
    val timedOut: Boolean = false,
    val executionSource: String? = null,
    val error: String? = null
) {
    /** Anything detected anywhere counts as present for the header tally. */
    val installed: Boolean
        get() = status != ToolStatus.NOT_INSTALLED

    /** Human one-liner for the card's status row. */
    val statusLine: String
        get() = when {
            timedOut -> "Timed out"
            error != null -> "Probe error"
            versionProbeFailed -> "Installed — version check failed"
            else -> when (status) {
                ToolStatus.DIRECT -> "Installed"
                ToolStatus.TERMUX -> "Installed in Termux"
                ToolStatus.VENV -> "Installed in Python venv"
                ToolStatus.PROOT -> "Installed in ${environment ?: "Ubuntu / proot"}"
                ToolStatus.OTHER_PATH_DIR -> "Installed but not on current PATH"
                ToolStatus.PYTHON_MODULE -> "Python module — no standalone command"
                ToolStatus.PERMISSION_DENIED -> "Detected — permission denied"
                ToolStatus.NOT_INSTALLED -> "Not installed"
            }
        }

    /** Detail row: where it lives and whether the current shell can run it. */
    val environmentLine: String?
        get() = when {
            timedOut || error != null || status == ToolStatus.NOT_INSTALLED -> null
            else -> buildString {
                environment?.let { append("Source: ").append(it) }
                when (status) {
                    ToolStatus.PYTHON_MODULE ->
                        append(if (isEmpty()) "Runnable via python -m" else " • runnable via python -m")
                    ToolStatus.PERMISSION_DENIED ->
                        append(if (isEmpty()) "Not executable by this shell" else " • not executable by this shell")
                    ToolStatus.PROOT ->
                        append(if (isEmpty()) "Not directly runnable from Android shell" else " • not directly runnable from Android shell")
                    else ->
                        if (directlyRunnable) {
                            append(if (isEmpty()) "Directly runnable from the current shell" else " • directly runnable")
                        } else {
                            append(if (isEmpty()) "Not directly runnable from the current shell" else " • not directly runnable")
                        }
                }
            }.takeIf { it.isNotBlank() }
        }

    fun toInfoItem(termuxNote: String? = null): InfoItem = InfoItem(
        title = tool.label,
        value = statusLine,
        subtitle = when {
            timedOut -> "probe exceeded its watchdog"
            error != null -> error
            path != null && environmentLine != null -> "$path • $environmentLine"
            path != null -> path
            environmentLine != null -> environmentLine
            tool == DevTool.TERMUX && termuxNote != null -> termuxNote
            else -> null
        },
        requiresPrivilege = false
    )
}

/* ------------------------------------------------------------------ */
/*  Tool specifications                                                */
/* ------------------------------------------------------------------ */

/** One `python -m <module>` fallback form for tools whose standalone launcher may not exist. */
internal data class ToolModuleProbe(
    val launcher: String,
    val module: String,
    val versionArgs: String
)

/**
 * Declarative description of how to detect one tool. Adding a tool is adding one
 * of these — no shell scripting anywhere else.
 *
 * @param commandCandidates executable names tried in order (`pip3` before `pip`).
 * @param versionArgs arguments that print the version (stdout+stderr are merged).
 * @param moduleProbes `python -m` fallbacks, only run when no standalone binary
 *        was found anywhere — this is what keeps "python exists but bare pip
 *        doesn't" from reporting Not installed.
 */
internal data class DevToolSpec(
    val tool: DevTool,
    val commandCandidates: List<String>,
    val versionArgs: String,
    val moduleProbes: List<ToolModuleProbe> = emptyList()
)

/* ------------------------------------------------------------------ */
/*  Probe report — raw parsed shell output                             */
/* ------------------------------------------------------------------ */

internal data class EnvHit(val env: String, val path: String)
internal data class ModuleHit(val form: String, val version: String)

/** Everything the probe script printed, split by tag. Pure data — no Android. */
internal data class ProbeReport(
    val directPath: String? = null,
    val directVersion: String? = null,
    val envHits: List<EnvHit> = emptyList(),
    val envVersions: Map<String, String> = emptyMap(),
    val deniedPaths: List<String> = emptyList(),
    val moduleHits: List<ModuleHit> = emptyList(),
    val venvRoots: List<String> = emptyList(),
    val prootDistro: String? = null
)

/* ------------------------------------------------------------------ */
/*  Provider                                                           */
/* ------------------------------------------------------------------ */

/**
 * Dev Environment detector.
 *
 * Every tool is probed with ONE shell script ([buildProbeScript]) that emits
 * tagged lines; the classification is done afterwards by pure Kotlin
 * ([parseProbeOutput] + [classify]) so the whole decision tree is unit-testable
 * without a device. Probes for all tools run in parallel, each behind the
 * privilege engine's per-command watchdog ([PROBE_TIMEOUT_MS]).
 *
 * The script deliberately distinguishes:
 *  A. on the probing shell's own PATH,
 *  B. inside Termux's prefix,
 *  C. inside a Python venv (pyvenv.cfg),
 *  D. inside ~/.local/bin / ~/.cargo/bin / ~/go/bin (Termux home included),
 *  E. inside a proot-distro rootfs (Ubuntu etc.),
 *  F. installed as a Python module with no standalone launcher,
 *  G. present but not executable (permission denied),
 * and never reports "Not installed" merely because one probe form failed —
 * every candidate, every environment and every module form is tried first.
 */
object DevEnvironmentDataProvider {

    private const val PROBE_TIMEOUT_MS = 6_000L

    const val TERMUX_BIN_DIR = "/data/data/com.termux/files/usr/bin"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    private const val PROOT_ROOTFS_GLOB = "/data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs"

    internal val SPECS: List<DevToolSpec> = listOf(
        DevToolSpec(DevTool.NODE, listOf("node", "nodejs"), "-v"),
        DevToolSpec(DevTool.NPM, listOf("npm"), "-v"),
        DevToolSpec(DevTool.PYTHON, listOf("python3", "python"), "--version"),
        DevToolSpec(
            DevTool.PIP,
            listOf("pip3", "pip"),
            "--version",
            moduleProbes = listOf(
                ToolModuleProbe("python3", "pip", "--version"),
                ToolModuleProbe("python", "pip", "--version")
            )
        ),
        DevToolSpec(DevTool.GIT, listOf("git"), "--version"),
        DevToolSpec(DevTool.JAVA, listOf("java"), "-version"),
        DevToolSpec(DevTool.RUBY, listOf("ruby"), "-v"),
        DevToolSpec(DevTool.GO, listOf("go"), "version"),
        DevToolSpec(DevTool.RUST, listOf("cargo", "rustc"), "-V"),
        DevToolSpec(DevTool.PHP, listOf("php"), "-v"),
        DevToolSpec(DevTool.DOCKER, listOf("docker"), "--version"),
        DevToolSpec(DevTool.UV, listOf("uv"), "--version"),
        DevToolSpec(
            DevTool.AIDER,
            listOf("aider"),
            "--version",
            moduleProbes = listOf(
                ToolModuleProbe("python3", "aider", "--version"),
                ToolModuleProbe("python", "aider", "--version")
            )
        ),
        DevToolSpec(DevTool.YARN, listOf("yarn"), "--version"),
        DevToolSpec(
            DevTool.POETRY,
            listOf("poetry"),
            "--version",
            moduleProbes = listOf(ToolModuleProbe("python3", "poetry", "--version"))
        )
    )

    suspend fun scanTools(): List<DevToolResult> = withContext(Dispatchers.IO) {
        val probes: List<Pair<DevTool, suspend () -> DevToolResult>> = buildList {
            SPECS.forEach { spec -> add(spec.tool to { runProbe(spec) }) }
            add(DevTool.TERMUX to { runTermuxProbe() })
        }
        coroutineScope {
            probes.map { (tool, block) ->
                async {
                    runCatching { block() }.getOrElse { failure ->
                        DevToolResult(
                            tool = tool,
                            status = ToolStatus.NOT_INSTALLED,
                            error = "Probe error: ${failure.message ?: failure.javaClass.simpleName}"
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun runProbe(spec: DevToolSpec): DevToolResult {
        val result = PrivilegeManager.executeCommand(buildProbeScript(spec), timeoutMs = PROBE_TIMEOUT_MS)
        return if (result.timedOut) {
            DevToolResult(tool = spec.tool, timedOut = true, executionSource = result.executionSource)
        } else {
            classify(spec, parseProbeOutput(result.stdout), result.executionSource)
        }
    }

    /** Termux presence keeps its own dedicated probe (it is not one binary). */
    private suspend fun runTermuxProbe(): DevToolResult {
        val command = "if [ -d $TERMUX_BIN_DIR ]; then ls $TERMUX_BIN_DIR 2>/dev/null | head -n 40; " +
            "echo '__TERMUX_EXISTS__'; else echo '__TERMUX_MISSING__'; fi"
        val result = PrivilegeManager.executeCommand(command, timeoutMs = PROBE_TIMEOUT_MS)
        if (result.timedOut) {
            return DevToolResult(tool = DevTool.TERMUX, timedOut = true, executionSource = result.executionSource)
        }
        val trimmed = result.stdout.map { it.trim() }.filter { it.isNotBlank() }
        val hasExistsMarker = trimmed.any { it == "__TERMUX_EXISTS__" }
        val hasMissingMarker = trimmed.any { it == "__TERMUX_MISSING__" }
        val entries = trimmed.filter { it != "__TERMUX_EXISTS__" && it != "__TERMUX_MISSING__" }
        return if (hasMissingMarker || (!hasExistsMarker && entries.isEmpty())) {
            DevToolResult(tool = DevTool.TERMUX, executionSource = result.executionSource)
        } else {
            DevToolResult(
                tool = DevTool.TERMUX,
                status = ToolStatus.TERMUX,
                version = if (entries.isNotEmpty()) "Installed (${entries.size} binaries)" else "Installed",
                path = TERMUX_BIN_DIR,
                environment = "Termux prefix",
                directlyRunnable = true,
                executionSource = result.executionSource
            )
        }
    }

    /* -------------------------------------------------------------- */
    /*  Script building — POSIX sh only, one script per tool           */
    /* -------------------------------------------------------------- */

    /**
     * Builds the POSIX-sh probe for one tool. Emits tagged lines:
     * ```
     * DIRECT|<path>            found on the probing shell's pristine PATH
     * DIRECT_VER|<line>        its version output (may be empty)
     * ENV|<env>|<path>         found in an extra environment's bin dir
     * ENV_VER|<env>|<line>     version from that path (may be empty)
     * PROOT_DISTRO|<name>      the rootfs a proot hit lives in
     * MOD|<launcher -m mod>|<line>   python-module fallback worked
     * VENV|<root>              resolved path sits in a dir with pyvenv.cfg
     * DENY|<path>              file exists there but is not executable
     * ```
     * Pure function of [spec] — the whole script is unit-testable.
     */
    internal fun buildProbeScript(spec: DevToolSpec): String {
        val candidates = spec.commandCandidates.joinToString(" ")
        val vArgs = spec.versionArgs

        // Environment dir table: name + dirs, in classification priority order.
        // $HOME-based entries make this correct even when the elevated shell has
        // a different HOME/PATH than a normal app shell (test matrix #12).
        val envTable = listOf(
            "termux" to listOf(TERMUX_BIN_DIR),
            "venv" to listOf("\$HOME/.venv/bin", "$TERMUX_HOME/.venv/bin"),
            "localbin" to listOf("\$HOME/.local/bin", "$TERMUX_HOME/.local/bin"),
            "cargo" to listOf("\$HOME/.cargo/bin", "$TERMUX_HOME/.cargo/bin"),
            "gobin" to listOf("\$HOME/go/bin", "$TERMUX_HOME/go/bin")
        )

        val sb = StringBuilder()
        sb.append("found=0\n")

        // Stage 1 — pristine PATH of the answering shell.
        sb.append("for c in $candidates; do\n")
        sb.append("  p=\$(command -v \$c 2>/dev/null)\n")
        sb.append("  if [ -n \"\$p\" ]; then\n")
        sb.append("    echo \"DIRECT|\$p\"\n")
        sb.append("    v=\$( (\$c $vArgs) 2>&1 | head -n 1 )\n")
        sb.append("    echo \"DIRECT_VER|\$v\"\n")
        sb.append("    found=1\n")
        sb.append("    break\n")
        sb.append("  fi\n")
        sb.append("done\n")

        // venv classifier for any resolved path (arg 1 = executable path).
        sb.append("venv_check() {\n")
        sb.append("  vp=\$(dirname \"\$1\")\n")
        sb.append("  vp=\$(dirname \"\$vp\")\n")
        sb.append("  if [ -f \"\$vp/pyvenv.cfg\" ]; then echo \"VENV|\$vp\"; fi\n")
        sb.append("}\n")
        sb.append("if [ \"\$found\" = 1 ] && [ -n \"\$p\" ]; then venv_check \"\$p\"; fi\n")

        // Stage 2 — known bin dirs not on the pristine PATH.
        sb.append("if [ \"\$found\" = 0 ]; then\n")
        sb.append("for c in $candidates; do\n")
        envTable.forEach { (name, dirs) ->
            dirs.forEach { dir ->
                sb.append("  if [ \"\$found\" = 0 ] && [ -d $dir ]; then\n")
                sb.append("    f=\"$dir/\$c\"\n")
                sb.append("    if [ -x \"\$f\" ]; then\n")
                sb.append("      echo \"ENV|$name|\$f\"\n")
                sb.append("      v=\$( (\"\$f\" $vArgs) 2>&1 | head -n 1 )\n")
                sb.append("      echo \"ENV_VER|$name|\$v\"\n")
                sb.append("      venv_check \"\$f\"\n")
                sb.append("      found=1\n")
                sb.append("    elif [ -e \"\$f\" ]; then\n")
                sb.append("      echo \"DENY|\$f\"\n")
                sb.append("    fi\n")
                sb.append("  fi\n")
            }
        }
        sb.append("  if [ \"\$found\" = 1 ]; then break; fi\n")
        sb.append("done\n")
        sb.append("fi\n")

        // Stage 3 — proot-distro rootfs binaries (glibc: present, not runnable).
        sb.append("if [ \"\$found\" = 0 ]; then\n")
        sb.append("for c in $candidates; do\n")
        sb.append("  for rootfs in $PROOT_ROOTFS_GLOB/*; do\n")
        sb.append("    [ -d \"\$rootfs\" ] || continue\n")
        sb.append("    for bindir in \"\$rootfs/usr/bin\" \"\$rootfs/usr/local/bin\" \"\$rootfs/bin\"; do\n")
        sb.append("      f=\"\$bindir/\$c\"\n")
        sb.append("      if [ -e \"\$f\" ]; then\n")
        sb.append("        echo \"ENV|proot|\$f\"\n")
        sb.append("        echo \"PROOT_DISTRO|\$(basename \"\$rootfs\")\"\n")
        sb.append("        found=1\n")
        sb.append("        break\n")
        sb.append("      fi\n")
        sb.append("    done\n")
        sb.append("    if [ \"\$found\" = 1 ]; then break; fi\n")
        sb.append("  done\n")
        sb.append("  if [ \"\$found\" = 1 ]; then break; fi\n")
        sb.append("done\n")
        sb.append("fi\n")

        // Stage 4 — python-module fallbacks (pip may exist only as `python -m pip`).
        if (spec.moduleProbes.isNotEmpty()) {
            sb.append("if [ \"\$found\" = 0 ]; then\n")
            spec.moduleProbes.forEach { probe ->
                sb.append("  if [ \"\$found\" = 0 ] && command -v ${probe.launcher} >/dev/null 2>&1; then\n")
                sb.append("    v=\$( (${probe.launcher} -m ${probe.module} ${probe.versionArgs}) 2>&1 | head -n 1 )\n")
                sb.append("    case \"\$v\" in *\"No module named\"*) : ;; *)\n")
                sb.append("      if [ -n \"\$v\" ]; then echo \"MOD|${probe.launcher} -m ${probe.module}|\$v\"; found=1; fi\n")
                sb.append("    ;; esac\n")
                sb.append("  fi\n")
            }
            sb.append("fi\n")
        }

        sb.append("exit 0\n")
        return sb.toString()
    }

    /* -------------------------------------------------------------- */
    /*  Parsing — tagged stdout lines → ProbeReport                    */
    /* -------------------------------------------------------------- */

    private fun splitTag(line: String): Pair<String, String>? {
        val index = line.indexOf('|')
        if (index <= 0) return null
        return line.substring(0, index) to line.substring(index + 1)
    }

    /** Pure parser: tagged lines from the probe script into a [ProbeReport]. */
    internal fun parseProbeOutput(stdout: List<String>): ProbeReport {
        var directPath: String? = null
        var directVersion: String? = null
        val envHits = mutableListOf<EnvHit>()
        val envVersions = mutableMapOf<String, String>()
        val denied = mutableListOf<String>()
        val modules = mutableListOf<ModuleHit>()
        val venvRoots = mutableListOf<String>()
        var prootDistro: String? = null

        stdout.forEach { raw ->
            val line = raw.trim()
            val (tag, value) = splitTag(line) ?: return@forEach
            when (tag) {
                "DIRECT" -> if (directPath == null && value.isNotBlank()) directPath = value
                "DIRECT_VER" -> if (directVersion == null) directVersion = value.trim()
                "ENV" -> {
                    val sep = value.indexOf('|')
                    if (sep > 0) envHits.add(EnvHit(value.substring(0, sep), value.substring(sep + 1)))
                }
                "ENV_VER" -> {
                    val sep = value.indexOf('|')
                    if (sep > 0) envVersions.putIfAbsent(value.substring(0, sep), value.substring(sep + 1).trim())
                }
                "PROOT_DISTRO" -> if (prootDistro == null && value.isNotBlank()) prootDistro = value.trim()
                "MOD" -> {
                    val sep = value.indexOf('|')
                    if (sep > 0 && value.substring(sep + 1).isNotBlank()) {
                        modules.add(ModuleHit(value.substring(0, sep), value.substring(sep + 1).trim()))
                    }
                }
                "VENV" -> if (value.isNotBlank()) venvRoots.add(value.trim().trimEnd('/'))
                "DENY" -> if (value.isNotBlank()) denied.add(value.trim())
            }
        }

        return ProbeReport(
            directPath = directPath,
            directVersion = directVersion,
            envHits = envHits,
            envVersions = envVersions,
            deniedPaths = denied,
            moduleHits = modules,
            venvRoots = venvRoots,
            prootDistro = prootDistro
        )
    }

    /* -------------------------------------------------------------- */
    /*  Classification — report → DevToolResult                        */
    /* -------------------------------------------------------------- */

    private fun String.isUnder(dir: String): Boolean =
        this == dir || startsWith(if (dir.endsWith('/')) dir else "$dir/")

    /** Pure classifier: the decision tree that turns a report into a result. */
    internal fun classify(spec: DevToolSpec, report: ProbeReport, executionSource: String): DevToolResult {
        val tool = spec.tool

        // 1. On the probing shell's own PATH — but classify *where* that is.
        val directPath = report.directPath
        if (directPath != null) {
            val venvRoot = report.venvRoots.firstOrNull { directPath.isUnder(it) }
            return when {
                venvRoot != null -> DevToolResult(
                    tool = tool,
                    status = ToolStatus.VENV,
                    version = report.directVersion?.takeIf { it.isNotBlank() },
                    path = directPath,
                    environment = "Python venv ($venvRoot)",
                    directlyRunnable = true,
                    versionProbeFailed = report.directVersion.isNullOrBlank(),
                    executionSource = executionSource
                )
                directPath.isUnder(TERMUX_BIN_DIR) -> DevToolResult(
                    tool = tool,
                    status = ToolStatus.TERMUX,
                    version = report.directVersion?.takeIf { it.isNotBlank() },
                    path = directPath,
                    environment = "Termux",
                    directlyRunnable = true,
                    versionProbeFailed = report.directVersion.isNullOrBlank(),
                    executionSource = executionSource
                )
                else -> DevToolResult(
                    tool = tool,
                    status = ToolStatus.DIRECT,
                    version = report.directVersion?.takeIf { it.isNotBlank() },
                    path = directPath,
                    environment = "Current shell PATH",
                    directlyRunnable = true,
                    versionProbeFailed = report.directVersion.isNullOrBlank(),
                    executionSource = executionSource
                )
            }
        }

        // 2. Known environment bin dirs / proot rootfs.
        val hit = report.envHits.firstOrNull()
        if (hit != null) {
            val version = report.envVersions[hit.env]?.takeIf { it.isNotBlank() }
            return when (hit.env) {
                "termux" -> DevToolResult(
                    tool = tool,
                    status = ToolStatus.TERMUX,
                    version = version,
                    path = hit.path,
                    environment = "Termux",
                    directlyRunnable = true,
                    versionProbeFailed = version == null,
                    executionSource = executionSource
                )
                "proot" -> {
                    val distro = report.proootDistro
                    DevToolResult(
                        tool = tool,
                        status = ToolStatus.PROOT,
                        version = null,
                        path = hit.path,
                        environment = if (distro != null) {
                            "${distro.replaceFirstChar { c -> c.uppercase() }} proot"
                        } else {
                            "Ubuntu / proot"
                        },
                        directlyRunnable = false,
                        executionSource = executionSource
                    )
                }
                "venv" -> DevToolResult(
                    tool = tool,
                    status = ToolStatus.VENV,
                    version = version,
                    path = hit.path,
                    environment = "Python venv",
                    directlyRunnable = true,
                    versionProbeFailed = version == null,
                    executionSource = executionSource
                )
                "localbin" -> DevToolResult(
                    tool = tool,
                    status = ToolStatus.OTHER_PATH_DIR,
                    version = version,
                    path = hit.path,
                    environment = "~/.local/bin",
                    directlyRunnable = true,
                    versionProbeFailed = version == null,
                    executionSource = executionSource
                )
                "cargo" -> DevToolResult(
                    tool = tool,
                    status = ToolStatus.OTHER_PATH_DIR,
                    version = version,
                    path = hit.path,
                    environment = "~/.cargo/bin",
                    directlyRunnable = true,
                    versionProbeFailed = version == null,
                    executionSource = executionSource
                )
                "gobin" -> DevToolResult(
                    tool = tool,
                    status = ToolStatus.OTHER_PATH_DIR,
                    version = version,
                    path = hit.path,
                    environment = "~/go/bin",
                    directlyRunnable = true,
                    versionProbeFailed = version == null,
                    executionSource = executionSource
                )
                else -> DevToolResult(
                    tool = tool,
                    status = ToolStatus.OTHER_PATH_DIR,
                    version = version,
                    path = hit.path,
                    environment = hit.env,
                    directlyRunnable = true,
                    versionProbeFailed = version == null,
                    executionSource = executionSource
                )
            }
        }

        // 3. Python module without a standalone launcher.
        val module = report.moduleHits.firstOrNull()
        if (module != null) {
            // pip-style output embeds the module location:
            // "pip 24.0 from /usr/lib/python3/dist-packages/pip (python 3.11)"
            val modulePath = Regex("from\\s+(\\S+)").find(module.version)?.groupValues?.getOrNull(1)
            return DevToolResult(
                tool = tool,
                status = ToolStatus.PYTHON_MODULE,
                version = module.version,
                path = modulePath ?: module.form,
                environment = "Python module (${module.form})",
                directlyRunnable = false,
                executionSource = executionSource
            )
        }

        // 4. Exists somewhere but not executable.
        val denied = report.deniedPaths.firstOrNull()
        if (denied != null) {
            return DevToolResult(
                tool = tool,
                status = ToolStatus.PERMISSION_DENIED,
                path = denied,
                environment = "file exists but is not executable",
                directlyRunnable = false,
                executionSource = executionSource
            )
        }

        // 5. Genuinely not found by any probe form.
        return DevToolResult(tool = tool, status = ToolStatus.NOT_INSTALLED, executionSource = executionSource)
    }
}
