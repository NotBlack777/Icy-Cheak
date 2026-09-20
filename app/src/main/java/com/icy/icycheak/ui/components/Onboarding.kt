package com.icy.icycheak.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.icy.icycheak.data.settings.AppSettings
import kotlinx.coroutines.runBlocking

/**
 * One-time, per-feature permission/privilege rationale. Shown the first time a
 * screen that needs root/Shizuku/a runtime permission is opened — explains what
 * the screen unlocks rather than dropping the user into a bare prompt.
 */
@Composable
fun OnboardingNote(feature: String, title: String, body: String) {
    val seen = runBlocking { AppSettings.isOnboardingSeen(feature) }
    var dismissed by remember { mutableStateOf(seen) }
    if (!dismissed) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                TextButton(onClick = {
                    dismissed = true
                    runBlocking { AppSettings.markOnboardingSeen(feature) }
                }) { Text("Got it") }
            }
        }
    }
}