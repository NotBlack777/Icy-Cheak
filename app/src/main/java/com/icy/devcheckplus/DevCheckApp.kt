package com.icy.devcheckplus

import android.app.Application
import com.icy.devcheckplus.privilege.PrivilegeManager
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
        PrivilegeManager.init(this)
    }
}
