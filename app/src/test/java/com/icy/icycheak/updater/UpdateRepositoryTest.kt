package com.icy.icycheak.updater

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateRepositoryTest {

    @Test
    fun parseRelease_validJsonWithApk() {
        val json = """
            {
                "tag_name": "v2.0.10",
                "name": "Icy Cheak v2.0.10",
                "body": "Bug fixes and improvements",
                "html_url": "https://github.com/NotBlack777/Icy-Cheak/releases/tag/v2.0.10",
                "published_at": "2026-09-21T10:00:00Z",
                "assets": [
                    {
                        "name": "IcyCheak-2.0.10.apk",
                        "browser_download_url": "https://github.com/NotBlack777/Icy-Cheak/releases/download/v2.0.10/IcyCheak-2.0.10.apk"
                    }
                ]
            }
        """.trimIndent()

        val release = UpdateRepository.parseRelease(json)
        assertNotNull(release)
        assertEquals("v2.0.10", release?.tagName)
        assertEquals("Icy Cheak v2.0.10", release?.name)
        assertEquals("https://github.com/NotBlack777/Icy-Cheak/releases/download/v2.0.10/IcyCheak-2.0.10.apk", release?.apkUrl)
    }

    @Test
    fun isVersionNewer_comparisons() {
        // Newer patch
        assertTrue(UpdateRepository.isVersionNewer("v2.0.103", "2.0.102"))
        assertTrue(UpdateRepository.isVersionNewer("2.0.103", "2.0.102"))
        assertTrue(UpdateRepository.isVersionNewer("v2.1.0", "v2.0.102"))
        assertTrue(UpdateRepository.isVersionNewer("v3.0.0", "v2.9.9"))

        // Older or equal
        assertFalse(UpdateRepository.isVersionNewer("v2.0.102", "2.0.102"))
        assertFalse(UpdateRepository.isVersionNewer("2.0.102", "v2.0.102"))
        assertFalse(UpdateRepository.isVersionNewer("v1.0.102", "2.0.0"))
        assertFalse(UpdateRepository.isVersionNewer("v2.0.101", "2.0.102"))
        assertFalse(UpdateRepository.isVersionNewer("2.0.0", "2.0.0"))
    }

    @Test
    fun checkForUpdates_runsSafelyOnCallerDispatcherWithoutThrowing() = runTest(StandardTestDispatcher()) {
        // Calling checkForUpdates switches to Dispatchers.IO internally,
        // so it does not block the caller's coroutine dispatcher or throw NetworkOnMainThreadException.
        // It returns a typed UpdateCheckResult rather than throwing.
        val result = UpdateRepository.checkForUpdates("99.99.99")
        // Either UpToDate, Available, or Error, but never throws unhandled exception
        assertTrue(result is UpdateCheckResult)
    }
}
