package com.icy.icycheak.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.icy.icycheak.BuildConfig
import com.icy.icycheak.data.providers.DeviceRepository
import com.icy.icycheak.data.providers.ExportStore
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.model.ExportRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Build a full text + JSON device report, persist it (for the Compare view), and
 * hand both files to the system share sheet via a FileProvider URI.
 */
suspend fun shareDeviceReport(context: Context) {
    val optIn = AppSettings.publicIpOptIn.first()
    val report = DeviceRepository.buildReport(context, optIn)
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val ts = System.currentTimeMillis()
        val name = "report_$ts"
        val txt = File(dir, "$name.txt").apply { writeText(report.text) }
        val json = File(dir, "$name.json").apply { writeText(report.json) }
        ExportStore.save(ExportRecord(name = name, path = txt.absolutePath, timestamp = ts, json = report.json))

        val uris = ArrayList<Uri>().apply {
            add(FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", txt))
            add(FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", json))
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TITLE, "Icy Cheak Device Report")
        }
        val chooser = Intent.createChooser(intent, "Share device report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
