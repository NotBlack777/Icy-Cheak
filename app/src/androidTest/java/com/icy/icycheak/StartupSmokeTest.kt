package com.icy.icycheak

import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icy.icycheak.data.providers.DevEnvironmentProvider
import com.icy.icycheak.data.settings.AmbientStyle
import com.icy.icycheak.data.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Application + launcher Activity lifecycle in a fresh app
 * process. A compile-only build cannot catch object-initialization crashes.
 */
@RunWith(AndroidJUnit4::class)
class StartupSmokeTest {
    @Test
    fun coldStart_reachesResumedAndLoadsDefaultSettings() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        // connectedAndroidTest installs a clean package in normal CI runs. Remove
        // the file explicitly as well so this test continues to cover the first
        // launch path when it is run repeatedly on a reused emulator.
        targetContext.preferencesDataStoreFile("icycheak_settings").delete()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }

            // These reads prove that the first-use DataStore path is live, not
            // merely that the Activity object was constructed.
            runBlocking {
                assertEquals("#FF8A00", AppSettings.accentHex.first())
                assertEquals("sunset", AppSettings.gradientPresetId.first())
                assertEquals(false, AppSettings.customGradientEnabled.first())
                assertEquals(0xFFFF8A00.toLong(), AppSettings.customGradientA.first())
                assertEquals(0xFFE9408A.toLong(), AppSettings.customGradientB.first())
                assertEquals(AmbientStyle.AURORA.name, AppSettings.ambientStyleId.first())
                assertEquals(false, AppSettings.oledMode.first())
                assertEquals("SYSTEM", AppSettings.darkModeName.first())
                assertEquals(true, AppSettings.liquidGlass.first())
                assertEquals(true, AppSettings.hapticsEnabled.first())
                assertEquals(1000L, AppSettings.refreshRateMs.first())
                assertEquals(true, AppSettings.liveGraphsEnabled.first())
                assertEquals("PACKAGE", AppSettings.lastInstallMethod.first())
                assertEquals(false, AppSettings.publicIpOptIn.first())

                val theme = AppSettings.themeConfig.first()
                assertEquals("sunset", theme.gradientPresetId)
                assertEquals(AmbientStyle.AURORA, theme.ambientStyle)
                assertEquals(false, theme.oled)
            }
        }
    }

    @Test
    fun representativeUserFlows_exerciseDevEnvironmentAndSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)

            runBlocking {
                // Exercise Dev Environment scan
                val tools = DevEnvironmentProvider.getDevTools()
                assertNotNull(tools)
                assertTrue("Dev tools list should not be empty", tools.isNotEmpty())

                // Exercise changing and reading Settings state
                AppSettings.setRefreshRate(2000L)
                assertEquals(2000L, AppSettings.refreshRateMs.first())
                AppSettings.setRefreshRate(1000L)
                assertEquals(1000L, AppSettings.refreshRateMs.first())
            }
        }
    }
}
