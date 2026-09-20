package com.icy.icycheak.data.providers

import android.content.Context
import com.icy.icycheak.model.ProcessInfo
import com.icy.icycheak.privilege.PrivilegeEngine
import kotlinx.coroutines.delay
import java.io.File

object ProcessProvider {

    /**
     * Returns running processes. Requires elevated privilege (root/Shizuku) because
     * reading other processes' /proc/<pid>/stat needs it. The note explains the
     * requirement instead of silently returning "Unavailable".
     */
    suspend fun getProcesses(context: Context): Pair<List<ProcessInfo>, String?> {
        if (!PrivilegeEngine.status.value.hasElevated) {
            return emptyList() to "Process list requires root or Shizuku"
        }
        // Snapshot the pid/user/rss/name table.
        val ps = PrivilegeEngine.execute("ps -A -o pid,user,rss,comm 2>/dev/null", 6000)
        if (!ps.isSuccess && ps.stdout.isEmpty()) return emptyList() to "Could not read process list"

        val base = ps.stdout.mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 4) return@mapNotNull null
            val pid = parts[0].toIntOrNull() ?: return@mapNotNull null
            val user = parts[1]
            val rss = parts[2].toLongOrNull()?.let { it * 1024 } ?: 0L
            val name = parts.drop(3).joinToString(" ")
            pid to Triple(user, rss, name)
        }.toMap()

        // Two-sample CPU measurement over /proc.
        val total1 = readCpuTotalTicks()
        val samples1 = base.keys.mapNotNull { pid -> pid to readProcTicks(pid) }.toMap()
        delay(450)
        val total2 = readCpuTotalTicks()
        val samples2 = base.keys.mapNotNull { pid -> pid to readProcTicks(pid) }.toMap()

        val totalDelta = (total2 - total1).coerceAtLeast(1)
        val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val list = base.mapNotNull { (pid, triple) ->
            val t1 = samples1[pid] ?: return@mapNotNull null
            val t2 = samples2[pid] ?: return@mapNotNull null
            val procDelta = (t2 - t1).coerceAtLeast(0)
            val cpu = (100f * procDelta / totalDelta).coerceIn(0f, 100f * numCores)
            ProcessInfo(
                pid = pid, name = triple.third, user = triple.first,
                rssBytes = triple.second, cpuPercent = cpu, state = ""
            )
        }.sortedByDescending { it.cpuPercent }.take(120)
        return list to null
    }

    private fun readCpuTotalTicks(): Long {
        val line = File("/proc/stat").readText().lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return 1
        return line.split(Regex("\\s+")).drop(2).mapNotNull { it.toLongOrNull() }.sum().coerceAtLeast(1)
    }

    private fun readProcTicks(pid: Int): Long {
        return runCatching {
            val stat = File("/proc/$pid/stat").readText()
            val idx = stat.lastIndexOf(')')
            val rest = stat.substring(idx + 1).trim().split(Regex("\\s+"))
            val utime = rest.getOrNull(11)?.toLongOrNull() ?: 0L
            val stime = rest.getOrNull(12)?.toLongOrNull() ?: 0L
            utime + stime
        }.getOrDefault(0L)
    }
}
