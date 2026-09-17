package com.icy.devcheckplus.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.icy.devcheckplus.model.InstalledAppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppsDataProvider {

    suspend fun getInstalledApps(context: Context): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS
        val packages = try {
            pm.getInstalledPackages(flags)
        } catch (_: Exception) {
            emptyList<PackageInfo>()
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val appList = mutableListOf<InstalledAppItem>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val appName = try {
                appInfo.loadLabel(pm).toString()
            } catch (_: Exception) {
                pkg.packageName
            }

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val apkFile = File(appInfo.sourceDir)
            val apkSize = if (apkFile.exists()) formatFileSize(apkFile.length()) else "Unknown"

            val permissions = pkg.requestedPermissions?.toList() ?: emptyList()
            val firstInstall = dateFormat.format(Date(pkg.firstInstallTime))
            val lastUpdate = dateFormat.format(Date(pkg.lastUpdateTime))

            val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }

            appList.add(
                InstalledAppItem(
                    appName = appName,
                    packageName = pkg.packageName,
                    versionName = pkg.versionName ?: "N/A",
                    versionCode = vCode,
                    isSystemApp = isSystem,
                    apkSizeFormatted = apkSize,
                    firstInstallTime = firstInstall,
                    lastUpdateTime = lastUpdate,
                    permissions = permissions
                )
            )
        }

        appList.sortBy { it.appName.lowercase() }
        appList
    }

    private fun formatFileSize(bytes: Long): String {
        val mb = bytes.toDouble() / (1024 * 1024)
        return if (mb >= 1.0) {
            String.format("%.1f MB", mb)
        } else {
            val kb = bytes / 1024
            "$kb KB"
        }
    }
}
