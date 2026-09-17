package com.icy.devcheckplus.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import com.icy.devcheckplus.model.PartitionItem
import com.icy.devcheckplus.privilege.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object StorageDataProvider {

    suspend fun getStorageSections(context: Context): Pair<List<InfoSection>, List<PartitionItem>> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()

        // 1. Internal Storage overview
        val internalItems = mutableListOf<InfoItem>()
        val dataPath = Environment.getDataDirectory()
        val statFs = StatFs(dataPath.path)
        val blockSize = statFs.blockSizeLong
        val totalBlocks = statFs.blockCountLong
        val availBlocks = statFs.availableBlocksLong

        val totalBytes = totalBlocks * blockSize
        val freeBytes = availBlocks * blockSize
        val usedBytes = totalBytes - freeBytes
        val usedPct = if (totalBytes > 0) ((usedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt() else 0

        internalItems.add(InfoItem("Internal Storage Total", formatBytes(totalBytes)))
        internalItems.add(InfoItem("Internal Storage Used", "${formatBytes(usedBytes)} ($usedPct%)"))
        internalItems.add(InfoItem("Internal Storage Free", formatBytes(freeBytes)))
        internalItems.add(InfoItem("Data Mount Point", dataPath.absolutePath))

        // External Storage status
        val extState = Environment.getExternalStorageState()
        internalItems.add(InfoItem("External Media State", extState))
        val extDir = context.getExternalFilesDir(null)
        if (extDir != null) {
            internalItems.add(InfoItem("App External Storage", extDir.absolutePath))
        }

        sections.add(InfoSection("Internal Storage Summary", internalItems))

        // 2. Query partitions via `df -k`
        val partitions = queryPartitions()

        // 3. Largest folder scan in app-accessible or privileged storage
        val folderItems = mutableListOf<InfoItem>()
        val sampleFolders = listOf(
            File("/system"),
            File("/vendor"),
            File("/product"),
            File("/data/local/tmp"),
            context.cacheDir,
            context.filesDir
        )
        for (f in sampleFolders) {
            if (f.exists()) {
                val size = calculateDirectorySize(f)
                folderItems.add(InfoItem(f.absolutePath, formatBytes(size)))
            }
        }
        sections.add(InfoSection("System & App Directories Size", folderItems))

        Pair(sections, partitions)
    }

    private suspend fun queryPartitions(): List<PartitionItem> {
        val list = mutableListOf<PartitionItem>()
        // Execute df via privilege manager or runtime
        val res = PrivilegeManager.executeCommand("df -h 2>/dev/null || df")
        if (res.isSuccess && res.stdout.isNotEmpty()) {
            for (line in res.stdout) {
                val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (parts.size >= 5 && !parts[0].startsWith("Filesystem") && !parts[0].startsWith("tmpfs")) {
                    val fs = parts[0]
                    val total = parts[1]
                    val used = parts[2]
                    val free = parts[3]
                    val pct = parts[4].replace("%", "").toIntOrNull() ?: 0
                    val mount = if (parts.size >= 6) parts[5] else parts.last()

                    list.add(
                        PartitionItem(
                            mountPoint = mount,
                            filesystem = fs,
                            totalBytes = 0L,
                            usedBytes = 0L,
                            freeBytes = 0L,
                            totalFormatted = total,
                            usedFormatted = used,
                            freeFormatted = free,
                            usedPercent = pct
                        )
                    )
                }
            }
        }

        if (list.isEmpty()) {
            // Fallback: Add root and data partitions manually
            try {
                val rootStat = StatFs("/")
                val rTotal = rootStat.blockCountLong * rootStat.blockSizeLong
                val rFree = rootStat.availableBlocksLong * rootStat.blockSizeLong
                val rUsed = rTotal - rFree
                list.add(
                    PartitionItem(
                        mountPoint = "/",
                        filesystem = "rootfs",
                        totalBytes = rTotal,
                        usedBytes = rUsed,
                        freeBytes = rFree,
                        totalFormatted = formatBytes(rTotal),
                        usedFormatted = formatBytes(rUsed),
                        freeFormatted = formatBytes(rFree),
                        usedPercent = if (rTotal > 0) ((rUsed * 100) / rTotal).toInt() else 0
                    )
                )
            } catch (_: Exception) {}
        }

        return list
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        var size = 0L
        try {
            val files = dir.listFiles() ?: return 0L
            for (f in files) {
                size += if (f.isDirectory) {
                    // Avoid deep recursion
                    f.listFiles()?.sumOf { it.length() } ?: 0L
                } else {
                    f.length()
                }
            }
        } catch (_: Exception) {
        }
        return size
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb > 1024) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}
