package com.icy.icycheak.data.providers

import com.icy.icycheak.model.DevTool
import com.icy.icycheak.model.DevToolSource
import com.icy.icycheak.privilege.PrivilegeEngine
import java.io.File

/**
 * The OLD code collapsed every non-default-PATH case into "Not installed".
 * The NEW code identifies exactly WHERE a tool lives. This classifier is a pure
 * function so it can be unit-tested on the JVM without Android.
 *
 * Precedence (most specific first): proot/Ubuntu rootfs → Python venv →
 * ~/.local/bin → Termux → current shell PATH → not found.
 */
fun classifyToolSourceFromPath(
    path: String,
    termuxPrefix: String = "/data/data/com.termux/files/usr",
    home: String = "/data/data/com.termux/files/home"
): DevToolSource {
    val p = path.lowercase()
    return when {
        p.contains("ubuntu") || p.contains("/debian/") || p.contains("/archlinux/") ||
            p.contains("/arch/") || p.contains("proot") || p.contains("/fedora/") ||
            p.contains("/kali/") -> DevToolSource.PROOT_ROOTFS
        p.contains("/.venv/") || p.contains("/venv/") || p.contains("/env/bin") ||
            p.contains("virtualenv") -> DevToolSource.PYTHON_VENV
        p.contains("/.local/bin") || p.contains("/.local/share") -> DevToolSource.LOCAL_BIN
        p.startsWith(termuxPrefix) || p.contains("com.termux") -> DevToolSource.TERMUX
        p.startsWith("/system") || p.startsWith("/vendor") || p.startsWith("/sbin") ||
            p.startsWith("/bin") || p.startsWith("/usr") -> DevToolSource.CURRENT_SHELL_PATH
        else -> DevToolSource.OTHER
    }
}

object DevEnvironmentProvider {

    private val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    private val TERMUX_HOME = "/data/data/com.termux/files/home"

    private val TOOLS = listOf(
        "node" to "Node.js",
        "npm" to "npm",
        "python3" to "Python 3",
        "python" to "Python",
        "pip3" to "pip3",
        "pip" to "pip",
        "ruby" to "Ruby",
        "gem" to "RubyGems",
        "go" to "Go",
        "git" to "Git",
        "java" to "Java",
        "javac" to "javac",
        "adb" to "Android SDK (adb)",
        "cargo" to "Rust (cargo)",
        "rustc" to "Rust (rustc)",
        "php" to "PHP",
        "perl" to "Perl",
        "composer" to "Composer",
        "docker" to "Docker",
        "flutter" to "Flutter",
        "gcc" to "GCC"
    )

    suspend fun getDevTools(): List<DevTool> {
        return TOOLS.map { (bin, name) -> detect(bin, name) }
    }

    private suspend fun detect(bin: String, name: String): DevTool {
        // 1) Current shell PATH.
        val which = PrivilegeEngine.execute("command -v $bin 2>/dev/null", 4000)
        val whichPath = which.stdout.firstOrNull { it.isNotBlank() }
        if (!whichPath.isNullOrBlank()) {
            val src = classifyToolSourceFromPath(whichPath, TERMUX_PREFIX, TERMUX_HOME)
            return DevTool(bin, name, true, src, whichPath, versionFor(bin, whichPath), "")
        }

        // 2) Known fixed locations (Termux, ~/.local/bin, venvs, proot rootfs).
        val candidates = buildList {
            add("$TERMUX_PREFIX/bin/$bin")
            add("$TERMUX_HOME/.local/bin/$bin")
            add("$TERMUX_HOME/venv/bin/$bin")
            add("$TERMUX_HOME/.venv/bin/$bin")
            add("$TERMUX_HOME/env/bin/$bin")
            add("$TERMUX_HOME/ubuntu/usr/bin/$bin")
            add("$TERMUX_HOME/debian/usr/bin/$bin")
            // generic proot distros: ~/<distro>/usr/bin/<bin>
            runCatching {
                File(TERMUX_HOME).listFiles { f -> f.isDirectory }?.forEach { d ->
                    add("${d.absolutePath}/usr/bin/$bin")
                }
            }
        }
        for (cand in candidates) {
            if (File(cand).exists()) {
                val src = classifyToolSourceFromPath(cand, TERMUX_PREFIX, TERMUX_HOME)
                return DevTool(bin, name, true, src, cand, versionFor(bin, cand), "")
            }
        }

        // 3) Python module with no standalone launcher (e.g. `python -m pip`).
        val py = resolvePython()
        if (py != null) {
            val mod = PrivilegeEngine.execute("$py -m $bin --version 2>&1 || $py -c \"import $bin\" 2>&1", 5000)
            if (mod.isSuccess || mod.stdout.any { it.contains(name, true) } || mod.stderr.any { it.contains(name, true) }) {
                return DevTool(bin, name, true, DevToolSource.PYTHON_MODULE, null,
                    versionFromOutput(mod.combinedOutput, name), "Installed as a Python module (no standalone launcher)")
            }
        }

        return DevTool(bin, name, false, DevToolSource.NOT_FOUND, null, null, "")
    }

    private suspend fun resolvePython(): String? {
        val which = PrivilegeEngine.execute("command -v python3 2>/dev/null || command -v python 2>/dev/null", 4000)
        val path = which.stdout.firstOrNull { it.isNotBlank() }
        if (!path.isNullOrBlank()) return path
        val termuxPy = "$TERMUX_PREFIX/bin/python3"
        return if (File(termuxPy).exists()) termuxPy else null
    }

    private fun versionFor(bin: String, path: String): String? {
        val res = PrivilegeEngine.execute("$path --version 2>&1 || $path -v 2>&1 || $path version 2>&1", 5000)
        val out = res.stdout.firstOrNull { it.isNotBlank() } ?: res.stderr.firstOrNull { it.isNotBlank() }
        return versionFromOutput(out ?: "", bin)
    }

    private fun versionFromOutput(out: String, name: String): String? {
        if (out.isBlank()) return null
        // Try to find a semver-like token anywhere in the output.
        val regex = Regex("(\\d+\\.\\d+(\\.\\d+)?([\\w.\\-]+)?)")
        return regex.find(out)?.value
    }
}
