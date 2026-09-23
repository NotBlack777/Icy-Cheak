package com.icy.icycheak.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.icy.icycheak.data.settings.AppSettings
import com.icy.icycheak.ui.theme.AppSpacing
import kotlinx.coroutines.launch

/**
 * One-time, per-feature permission/privilege rationale. Shown the first time a
 * screen that needs root/Shizuku/a runtime permission is opened — explains what
 * the screen unlocks rather than dropping the user into a bare prompt.
 */
@Composable
fun OnboardingNote(feature: String, title: String, body: String) {
    val scope = rememberCoroutineScope()
    var seen by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(feature) {
        seen = AppSettings.isOnboardingSeen(feature)
        loaded = true
    }

    if (loaded && !seen) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.extraSmall)
                )
                TextButton(onClick = {
                    seen = true
                    scope.launch { AppSettings.markOnboardingSeen(feature) }
                }) { Text("Got it") }
            }
        }
    }
}
