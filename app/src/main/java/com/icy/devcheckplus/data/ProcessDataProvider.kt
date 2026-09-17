package com.icy.devcheckplus.data

import com.icy.devcheckplus.model.ProcessItem
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.privilege.PrivilegeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProcessDataProvider {

    suspend fun getProcesses(): Pair<List<ProcessItem>, String?> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ProcessItem>()
        val privilegeState = PrivilegeManager.status.value
        val isPrivileged = privilegeState.activeMode != PrivilegeMode.NONE

        if (!isPrivileged) {
            return@withContext Pair(
                emptyList(),
                "Unavailable — reading running processes requires Root or Shizuku on modern Android."
            )
        }

        val result = PrivilegeManager.executeCommand("ps -A -o USER,PID,%CPU,RSS,STAT,NAME 2>/dev/null || ps -ef 2>/dev/null || ps")
        if (!result.isSuccess || result.stdout.isEmpty()) {
            return@withContext Pair(
                emptyList(),
                "Failed to execute process inspector command: ${result.stderr.firstOrNull() ?: "Unknown error"}"
            )
        }

        val lines = result.stdout
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue
            val tokens = line.split(Regex("\\s+"))
            if (tokens.size >= 6) {
                val user = tokens[0]
                val pid = tokens[1].toIntOrNull() ?: 0
                val cpu = tokens[2]
                val rss = tokens[3]
                val stat = tokens[4]
                val name = tokens.subList(5, tokens.size).joinToString(" ")

                list.add(
                    ProcessItem(
                        pid = pid,
                        user = user,
                        name = name,
                        cpuPercent = if (cpu.contains("%")) cpu else "$cpu%",
                        memRss = formatRss(rss),
                        status = stat
                    )
                )
            } else if (tokens.size >= 4) {
                val user = tokens[0]
                val pid = tokens[1].toIntOrNull() ?: 0
                val name = tokens.last()
                list.add(
                    ProcessItem(
                        pid = pid,
                        user = user,
                        name = name,
                        cpuPercent = "N/A",
                        memRss = "N/A",
                        status = "R"
                    )
                )
            }
        }

        // Sort descending by PID or memory
        list.sortBy { it.pid }

        Pair(list, null)
    }

    private fun formatRss(rawRss: String): String {
        val kb = rawRss.toLongOrNull() ?: return rawRss
        return if (kb > 1024) {
            "${kb / 1024} MB"
        } else {
            "$kb KB"
        }
    }
}
