package com.icy.devcheckplus.data

import com.icy.devcheckplus.model.LogcatEntry
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.UNAVAILABLE_TIMED_OUT
import com.icy.devcheckplus.privilege.PrivilegeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LogcatDataProvider {

    /** Watchdog for one logcat dump; auto-refresh polls on a fixed cadence. */
    private const val LOGCAT_TIMEOUT_MS = 8_000L


    suspend fun fetchRecentLogs(maxLines: Int = 150): Pair<List<LogcatEntry>, String?> = withContext(Dispatchers.IO) {
        val privilegeState = PrivilegeManager.status.value
        val isPrivileged = privilegeState.activeMode != PrivilegeMode.NONE

        val command = "logcat -d -t $maxLines -v threadtime"
        val result = PrivilegeManager.executeCommand(command, timeoutMs = LOGCAT_TIMEOUT_MS)
        if (result.timedOut) {
            return@withContext Pair(emptyList(), UNAVAILABLE_TIMED_OUT)
        }

        if (!result.isSuccess || result.stdout.isEmpty()) {
            val errorMsg = if (!isPrivileged) {
                "Standard mode restricted: Android 6+ prevents apps from reading full system logcat without Root or Shizuku permission."
            } else {
                "Unable to read logcat: ${result.stderr.firstOrNull() ?: "Empty output"}"
            }
            return@withContext Pair(emptyList(), errorMsg)
        }

        val entries = mutableListOf<LogcatEntry>()
        for (line in result.stdout) {
            val entry = parseLogcatLine(line)
            if (entry != null) {
                entries.add(entry)
            }
        }

        // Return latest entries top-first or natural order
        Pair(entries.reversed(), null)
    }

    private fun parseLogcatLine(line: String): LogcatEntry? {
        val trimmed = line.trim()
        if (trimmed.startsWith("---------")) return null

        val parts = trimmed.split(Regex("\\s+"), limit = 6)
        if (parts.size >= 6) {
            val date = parts[0]
            val time = parts[1]
            val pid = parts[2]
            val tid = parts[3]
            val level = parts[4]
            val rest = parts[5]

            val tag = rest.substringBefore(":").trim()
            val msg = rest.substringAfter(":").trim()

            return LogcatEntry(
                timestamp = "$date $time",
                level = level,
                tag = tag,
                pid = "$pid/$tid",
                message = msg
            )
        } else {
            return LogcatEntry(
                timestamp = "",
                level = "I",
                tag = "System",
                pid = "",
                message = trimmed
            )
        }
    }
}
