package com.icy.devcheckplus.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import com.icy.devcheckplus.model.InstalledAppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppsDataProvider {

    /** Rendered icon size; large enough to stay crisp on high-density screens. */
    private const val ICON_SIZE_PX = 96

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

    /**
     * Decoding an app icon is the single most expensive thing this screen can do
     * (resource open + vector/raster render). It used to happen inside the item
     * composable, on the main thread, for every visible row and again on every
     * scroll back into view.
     *
     * Icons are now decoded lazily on [Dispatchers.IO] and kept in a small
     * byte-bounded [LruCache], so a fling over the package list re-uses decoded
     * bitmaps instead of re-decoding them, and the main thread never blocks.
     */
    private val iconCache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Returns a cached (or freshly decoded) 96 dp icon for [packageName]. */
    suspend fun loadAppIcon(context: Context, packageName: String): Bitmap? = withContext(Dispatchers.IO) {
        iconCache.get(packageName)?.let { return@withContext it }
        val drawable = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Throwable) {
            null
        } ?: return@withContext null
        val bitmap = try {
            drawable.toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX, config = Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            null
        } ?: return@withContext null
        iconCache.put(packageName, bitmap)
        bitmap
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
