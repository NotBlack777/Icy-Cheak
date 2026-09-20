package com.icy.icycheak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import com.icy.icycheak.ui.components.shareDeviceReport
import com.icy.icycheak.ui.navigation.AppNavigation
import com.icy.icycheak.ui.theme.IcyCheakTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action
        val startRoute = if (action == "com.icy.icycheak.action.OPEN_CONSOLE") "console" else "dashboard"
        setContent {
            IcyCheakTheme {
                AppNavigation(startRoute = startRoute)
            }
            if (action == "com.icy.icycheak.action.EXPORT_REPORT") {
                LaunchedEffect(Unit) { shareDeviceReport(this@MainActivity) }
            }
        }
    }
}
