package com.icy.devcheckplus

import android.app.Application
import com.icy.devcheckplus.data.AppSettingsStore
import com.icy.devcheckplus.data.LiveMetricsPoller
import com.icy.devcheckplus.privilege.PrivilegeManager
import com.icy.devcheckplus.widget.MetricsWidgetProvider
import com.topjohnwu.superuser.Shell

class DevCheckApp : Application() {
    companion object {
        init {
            // Configure libsu defaults
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_REDIRECT_STDERR)
                    .setTimeout(10)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Load persisted appearance/privacy prefs before the first frame so the
        // theme (Dark / OLED / dynamic colour) is correct on cold start.
        AppSettingsStore.init(this)
        PrivilegeManager.init(this)
        // One shared telemetry ticker for the whole process: it starts when the
        // first screen subscribes and stops shortly after the last one pauses.
        LiveMetricsPoller.init(this)
        // Repaint any placed widget when the app is opened: a handful of local
        // reads, so the widget is never stale right after a cold start.
        MetricsWidgetProvider.refreshAll(this)
    }
}
