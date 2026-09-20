package com.icy.icycheak.data.providers

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.os.UserHandle
import android.os.storage.StorageManager
import com.icy.icycheak.model.InfoRow
import com.icy.icycheak.model.StorageAppUsage
import com.icy.icycheak.model.StorageInfo
import com.icy.icycheak.model.StoragePartition
import com.icy.icycheak.privilege.PrivilegeEngine
import java.io.File
import java.util.UUID

object StorageProvider {

    suspend fun getStorageInfo(context: Context): StorageInfo {
        val dataDir = Environment.getDataDirectory()
        val stat = android.os.StatFs(dataDir.path)
        val total = stat.totalBytes
        val free = stat.freeBytes
        val used = (total - free).coerceAtLeast(0)

        val partitions = readPartitions()

        // Top consumers: APK size is always readable; deeper data-size via
        // StorageStatsManager when the platform allows it, otherwise APK only.
        val pm = context.packageManager
        val ssm = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val user = android.os.Process.myUserHandle()
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val consumers = apps.mapNotNull { ai ->
            runCatching {
                val apkSize = File(ai.sourceDir ?: return@mapNotNull null).length()
                var dataSize = 0L
                try {
                    val stats = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, user)
                    dataSize = stats.dataBytes + stats.cacheBytes
                } catch (_: Throwable) { /* needs usage-stats permission; APK only */ }
                val totalSize = apkSize + dataSize
                if (totalSize <= 0) null else StorageAppUsage(
                    packageName = ai.packageName,
                    label = pm.getApplicationLabel(ai).toString(),
                    sizeBytes = totalSize,
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }.getOrNull()
        }.sortedByDescending { it.sizeBytes }.take(15)

        val privilegeNote = if (partitions.isEmpty())
            "Partition breakdown needs root/Shizuku; showing internal totals only."
        else null

        return StorageInfo(
            internalTotal = total, internalUsed = used, internalFree = free,
            partitions = partitions, topConsumers = consumers, privilegeNote = privilegeNote
        )
    }

    private suspend fun readPartitions(): List<StoragePartition> {
        val res = PrivilegeEngine.execute("df -h 2>/dev/null", 5000)
        if (!res.isSuccess && res.stdout.isEmpty()) return emptyList()
        return res.stdout.mapNotNull { line ->
            val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            // columns: Filesystem  Size  Used  Avail  Use%  Mounted-on
            if (parts.size < 6) return@mapNotNull null
            val mount = parts[5]
            if (!mount.startsWith("/")) return@mapNotNull null
            val used = parseHuman(parts[2])
            val total = parseHuman(parts[1])
            if (total <= 0) return@mapNotNull null
            StoragePartition(mount = mount, totalBytes = total, usedBytes = used)
        }.filter { it.mount in setOf("/", "/system", "/vendor", "/data", "/cache", "/storage", "/persist") }
            .distinctBy { it.mount }
    }

    private fun parseHuman(s: String): Long {
        val num = s.trim().toDoubleOrNull() ?: return 0L
        return when {
            s.endsWith("K", true) -> (num * 1024).toLong()
            s.endsWith("M", true) -> (num * 1024 * 1024).toLong()
            s.endsWith("G", true) -> (num * 1024 * 1024 * 1024).toLong()
            s.endsWith("T", true) -> (num * 1024 * 1024 * 1024 * 1024).toLong()
            else -> num.toLong()
        }
    }

}
