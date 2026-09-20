package com.icy.icycheak.data.providers

import com.icy.icycheak.model.CrashEntry
import com.icy.icycheak.privilege.PrivilegeEngine
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object CrashLogProvider {

    private val TS = Pattern.compile("^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+)")

    /**
     * Reads the device's own crash / ANR history via logcat (crash + system
     * buffers). Requires root/Shizuku because reading other apps' crash logs
     * needs elevated access. Returns a note explaining the requirement instead
     * of a bare "Unavailable".
     */
    suspend fun getCrashLog(): Pair<List<CrashEntry>, String?> {
        if (!PrivilegeEngine.status.value.hasElevated) {
            return emptyList<CrashEntry>() to "Crash / ANR log requires root or Shizuku"
        }
        val crash = PrivilegeEngine.execute("logcat -b crash -d -v threadtime -t 300 2>/dev/null", 8000)
        val anr = PrivilegeEngine.execute("logcat -b system -d -v threadtime -t 500 2>/dev/null | grep -i 'anr in\\|ANR ' ", 8000)
        val lines = (crash.stdout + anr.stdout).filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList() to null

        val entries = mutableListOf<CrashEntry>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val isCrash = line.contains("FATAL EXCEPTION", true) || line.contains("AndroidRuntime: Crash", true)
            val isAnr = line.contains("ANR in", true) || (line.contains("ANR ", true) && line.contains("android", true))
            if (isCrash || isAnr) {
                val time = parseTime(line)
                val type = if (isAnr) "anr" else "crash"
                // search following lines for package + exception message
                val window = lines.subList(i, (i + 18).coerceAtMost(lines.size))
                val pkg = window.firstNotNullOfOrNull { l ->
                    Regex("Process:\\s*([\\w.]+)").find(l)?.groupValues?.getOrNull(1)
                } ?: window.firstNotNullOfOrNull { l ->
                    Regex("at\\s+([\\w.]+)/").find(l)?.groupValues?.getOrNull(1)
                }
                val msg = window.firstNotNullOfOrNull { l ->
                    Regex("(?:FATAL EXCEPTION:\\s*)?(java\\.[\\w.]+|android\\.[\\w.]+|\\w+Exception)[ :]*(.*)")
                        .find(l)?.let { m -> (m.groupValues[1] + " " + m.groupValues.getOrNull(2).orEmpty()).trim() }
                } ?: (if (isAnr) "Application Not Responding" else "Crash")
                val snippet = window.take(6).joinToString("\n")
                entries.add(CrashEntry(time = time, type = type, packageName = pkg ?: "unknown", message = msg.take(160), snippet = snippet))
                i += 18
            } else i++
        }
        return entries.sortedByDescending { it.time }.take(80) to null
    }

    private fun parseTime(line: String): Long {
        val m = TS.matcher(line)
        if (!m.find()) return System.currentTimeMillis()
        val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
        return runCatching { fmt.parse(m.group(1))?.time ?: System.currentTimeMillis() }.getOrDefault(System.currentTimeMillis())
    }
}
