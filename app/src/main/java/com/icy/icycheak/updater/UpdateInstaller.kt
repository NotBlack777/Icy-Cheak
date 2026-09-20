package com.icy.icycheak.updater

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.icy.icycheak.BuildConfig
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.data.settings.InstallMethod
import com.icy.icycheak.privilege.PrivilegeEngine
import java.io.File

data class InstallResult(
    val success: Boolean,
    val message: String,
    /** When true the caller should offer the Package Installer fallback. */
    val shouldFallback: Boolean
)

object UpdateInstaller {

    private fun updatesDir(context: Context): File =
        File(context.cacheDir, "updates").also { it.mkdirs() }

    /** Download an APK to the app cache, reporting progress 0..100. */
    fun downloadApk(context: Context, url: String, onProgress: (Int) -> Unit): File? = runCatching {
        val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        val total = conn.contentLength.takeIf { it > 0 } ?: -1
        val out = File(updatesDir(context), "icycheak_update.apk")
        conn.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(8192)
                var read: Int
                var done = 0L
                while (input.read(buf).also { read = it } > 0) {
                    output.write(buf, 0, read)
                    done += read
                    if (total > 0) onProgress(((done * 100) / total).toInt())
                }
            }
        }
        conn.disconnect()
        onProgress(100)
        out
    }.getOrNull()

    /** Silent root install through the shared engine (hard timeout + breaker). */
    suspend fun installViaRoot(apkFile: File): InstallResult {
        val cmd = "pm install -r \"${apkFile.absolutePath}\""
        val res = PrivilegeEngine.executeInstall(cmd, PrivilegeEngine.INSTALL_TIMEOUT_MS)
        return if (res.isSuccess) {
            InstallResult(true, "Installed via root.", false)
        } else {
            val stderr = res.stderr.joinToString(" ")
            val friendly = when {
                stderr.contains("INSTALL_FAILED_VERSION_DOWNGRADE") -> "Downgrade blocked — current version is newer."
                stderr.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE") -> "Insufficient storage."
                stderr.contains("INSTALL_FAILED_INVALID_APK") -> "Invalid APK."
                stderr.contains("INSTALL_FAILED_ALREADY_EXISTS") -> "Already installed."
                res.timedOut -> "Install timed out after ${PrivilegeEngine.INSTALL_TIMEOUT_MS / 1000}s."
                else -> "Root install failed: ${stderr.take(120)}"
            }
            InstallResult(false, friendly, true)
        }
    }

    /** Package Installer intent — the universal fallback, available everywhere. */
    fun packageInstallerIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context, "${BuildConfig.APPLICATION_ID}.fileprovider", apkFile
        )
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }
    }

    /**
     * Shizuku install: create a system PackageInstaller session through Shizuku's
     * elevated binder and commit it. Still goes through Android's install
     * confirmation UI (Shizuku does NOT grant silent installs here); if the
     * device/ROM doesn't support it, we report failure with a fallback flag.
     */
    fun installViaShizuku(context: Context, apkFile: File): InstallResult {
        return try {
            val helper = Class.forName("rikka.shizuku.SystemServiceHelper")
            val binder = helper.getMethod("getSystemService", String::class.java)
                .invoke(null, "package") as IBinder
            val stub = Class.forName("android.content.pm.IPackageInstaller\$Stub")
            val installer = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)

            val paramsCtor = Class.forName("android.content.pm.PackageInstaller\$SessionParams")
                .getConstructor(Int::class.javaPrimitiveType)
            // MODE_FULL_PACKAGE = 2
            val params = paramsCtor.newInstance(2)
            val sessionId = installer.javaClass.getMethod("createSession", params.javaClass)
                .invoke(installer, params) as Int
            val session = installer.javaClass.getMethod("openSession", Int::class.javaPrimitiveType)
                .invoke(installer, sessionId)

            val pfd = session.javaClass.getMethod(
                "openWrite", String::class.java, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType
            ).invoke(session, "icycheak", 0L, -1L) as ParcelFileDescriptor
            pfd.fileDescriptor.let { fd ->
                apkFile.inputStream().use { src ->
                    java.io.FileOutputStream(fd).use { dst -> src.copyTo(dst) }
                }
            }
            session.javaClass.getMethod("fsync", ParcelFileDescriptor::class.java).invoke(session, pfd)
            pfd.close()

            val intent = Intent(context, context.javaClass).apply { action = "com.icy.icycheak.INSTALL_RESULT" }
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            )
            session.javaClass.getMethod("commit", android.content.IntentSender::class.java)
                .invoke(session, pi.intentSender)
            InstallResult(true, "Shizuku install session committed — confirm in the system UI.", false)
        } catch (e: Throwable) {
            InstallResult(false, "Shizuku install not available: ${e.message?.take(120)}", true)
        }
    }
}
