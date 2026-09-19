package com.icy.devcheckplus

import com.icy.devcheckplus.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for the in-app updater's decision logic — the parts that must
 * never claim an update exists when it doesn't (or vice versa).
 */
class UpdaterLogicTest {

    @Test
    fun `version comparison is numeric not lexicographic`() {
        // Lexicographically "1.0.9" > "1.0.10" — that classic bug must not happen.
        assertTrue(UpdateChecker.compareVersions("1.0.10", "1.0.9") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.9", "1.0.10") < 0)
        assertTrue(UpdateChecker.compareVersions("2.0.0", "1.9.9") > 0)
    }

    @Test
    fun `missing components count as zero`() {
        assertEquals(0, UpdateChecker.compareVersions("1.2", "1.2.0"))
        assertEquals(0, UpdateChecker.compareVersions("1", "1.0.0.0"))
        assertTrue(UpdateChecker.compareVersions("1.2.1", "1.2") > 0)
    }

    @Test
    fun `v prefixes and suffixes are normalised`() {
        assertEquals("1.0.42", UpdateChecker.normalizeVersion("v1.0.42"))
        assertEquals("1.0.42", UpdateChecker.normalizeVersion("V1.0.42"))
        assertEquals(0, UpdateChecker.compareVersions("v1.2.3", "1.2.3"))
        // -beta suffix is ignored by design (documented on compareVersions).
        assertEquals(0, UpdateChecker.compareVersions("1.2.3-beta", "1.2.3"))
    }

    @Test
    fun `isNewer agrees with compareVersions`() {
        assertTrue(UpdateChecker.isNewer("1.1.0", "1.0.9"))
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.0.9"))
    }

    @Test
    fun `garbage versions never throw and never report an update`() {
        assertFalse(UpdateChecker.isNewer("", ""))
        assertFalse(UpdateChecker.isNewer("latest", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("...", "1.0.0"))
        assertEquals(0, UpdateChecker.compareVersions("", ""))
    }

    @Test
    fun `release payload parsing picks the apk asset`() {
        val json = """
            {
              "tag_name": "v1.2.0",
              "name": "Icy Cheak v1.2.0",
              "body": "Notes here",
              "html_url": "https://github.com/NotBlack777/Icy-Cheak/releases/tag/v1.2.0",
              "published_at": "2026-01-01T00:00:00Z",
              "assets": [
                { "name": "IcyCheak-1.2.0.apk",
                  "browser_download_url": "https://example.com/IcyCheak-1.2.0.apk",
                  "size": 1234567 },
                { "name": "source.zip",
                  "browser_download_url": "https://example.com/source.zip",
                  "size": 1 }
              ]
            }
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json)
        assertNotNull(info)
        assertEquals("v1.2.0", info!!.tagName)
        assertEquals("1.2.0", info.versionName)
        assertNotNull(info.apk)
        assertEquals("IcyCheak-1.2.0.apk", info.apk!!.name)
        assertEquals(1234567L, info.apk!!.sizeBytes)
    }

    @Test
    fun `release without apk parses but offers nothing to download`() {
        val json = """{ "tag_name": "v1.2.0", "body": "", "assets": [] }"""
        val info = UpdateChecker.parseRelease(json)
        assertNotNull(info)
        assertNull(info!!.apk)
    }

    @Test
    fun `unparseable payloads degrade to null instead of crashing`() {
        assertNull(UpdateChecker.parseRelease(""))
        assertNull(UpdateChecker.parseRelease("{"))
        assertNull(UpdateChecker.parseRelease("""{ "name": "no tag" }"""))
    }
}
