package com.icy.icycheak.data.providers

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.icy.icycheak.BuildConfig
import com.icy.icycheak.model.AppInfo
import java.io.File

object InstalledAppsProvider {

    fun getInstalledApps(context: Context): List<AppInfo> = runCatching {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_META_DATA
        pm.getInstalledPackages(flags).mapNotNull { pi ->
            val ai = pi.applicationInfo ?: return@mapNotNull null
            val apkSize = runCatching { File(ai.sourceDir).length() }.getOrDefault(0L)
            AppInfo(
                packageName = pi.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                versionName = pi.versionName ?: "—",
                versionCode = PackageInfoCompat.getLongVersionCode(pi),
                apkSizeBytes = apkSize,
                installTime = pi.firstInstallTime,
                updateTime = pi.lastUpdateTime,
                isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                permissions = pi.requestedPermissions?.toList() ?: emptyList(),
                isIcyCheak = pi.packageName == BuildConfig.APPLICATION_ID
            )
        }.sortedWith(compareBy({ !it.isSystem }, { it.label.lowercase() }))
    }.getOrDefault(emptyList())
}
