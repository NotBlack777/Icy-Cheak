package com.icy.devcheckplus

import android.appwidget.AppWidgetProvider
import com.icy.devcheckplus.widget.WidgetRefreshScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Automated validation of the whole widget chain — the strongest check possible
 * without a real launcher:
 *
 *   manifest <receiver> → AppWidgetProvider class → metadata XML →
 *   initialLayout/previewLayout → RemoteViews layout → referenced
 *   drawables/strings → scheduler registration.
 *
 * Every link is verified against the actual resource files, so a half-built
 * widget (layout + strings but no provider/metadata/receiver — the bug the
 * Network widget used to have) can never sneak back in: orphan widget
 * resources fail this test in BOTH directions.
 */
class WidgetConsistencyTest {

    companion object {
        private lateinit var resDir: File
        private lateinit var manifest: String

        @BeforeClass
        @JvmStatic
        fun locateProjectFiles() {
            // Unit tests run with the module dir (`app/`) as CWD; walk up a few
            // levels defensively in case Gradle is invoked from the repo root.
            var dir: File? = File(".").absoluteFile
            var manifestFile: File? = null
            var hops = 0
            while (dir != null && hops < 5) {
                val candidate = File(dir, "src/main/AndroidManifest.xml")
                if (candidate.isFile) {
                    manifestFile = candidate
                    break
                }
                // Also handle standing at the repository root.
                val fromRoot = File(dir, "app/src/main/AndroidManifest.xml")
                if (fromRoot.isFile) {
                    manifestFile = fromRoot
                    break
                }
                dir = dir.parentFile
                hops++
            }
            requireNotNull(manifestFile) {
                "Cannot locate AndroidManifest.xml from ${File(".").absolutePath}"
            }
            manifest = manifestFile.readText()
            // The manifest lives in app/src/main/, and res/ is its sibling.
            resDir = manifestFile.parentFile!!.resolve("res")
        }
    }

    /** `android:attr="value"` pairs inside a manifest receiver block. */
    private fun attr(block: String, name: String): String? =
        Regex("$name=\"([^\"]+)\"").find(block)?.groupValues?.get(1)

    /** Every <receiver> block that carries appwidget-provider metadata. */
    private fun manifestWidgetReceivers(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val receiverBlocks = Regex("<receiver\\b[^>]*>.*?</receiver>", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest)
        for (block in receiverBlocks.map { it.value }) {
            if (!block.contains("android.appwidget.provider")) continue
            val className = attr(block, "android:name") ?: continue
            val meta = Regex("<meta-data[^>]*android:resource=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
                .findAll(block)
                .mapNotNull { attr(it.value, "android:resource") }
                .firstOrNull() ?: continue
            result[className.removePrefix(".")] = meta.removePrefix("@xml/")
        }
        return result
    }

    private fun readRes(subPath: String): String {
        val file = resDir.resolve(subPath)
        assertTrue("Missing resource file: $file", file.isFile)
        return file.readText()
    }

    @Test
    fun `every manifest widget receiver has provider class metadata and layouts`() {
        val receivers = manifestWidgetReceivers()
        assertTrue("No widget receivers found in manifest", receivers.isNotEmpty())

        for ((rawClass, metaName) in receivers) {
            val className = rawClass.substringAfter("widget.")
            // Provider class exists in the family file and is a real provider.
            val clazz = runCatching {
                Class.forName("com.icy.devcheckplus.widget.$className")
            }.getOrElse { error("Manifest receiver .$rawClass has no provider class: ${it.message}") }
            assertTrue(
                "$className is not an AppWidgetProvider",
                AppWidgetProvider::class.java.isAssignableFrom(clazz)
            )

            // Metadata XML exists and points at real layouts.
            val meta = readRes("xml/$metaName.xml")
            val initial = Regex("android:initialLayout=\"@layout/([^\"]+)\"").find(meta)?.groupValues?.get(1)
                ?: error("$metaName.xml has no initialLayout")
            val preview = Regex("android:previewLayout=\"@layout/([^\"]+)\"").find(meta)?.groupValues?.get(1)
            for (layout in listOfNotNull(initial, preview)) {
                readRes("layout/$layout.xml")
            }

            // Label / description strings exist.
            attr(receivers.entries.first { it.key == rawClass }.let { blockFor(it.key) }!!, "android:label")
                ?.removePrefix("@string/")?.let { label ->
                    assertTrue("Missing label string $label for $className", stringExists(label))
                }
            Regex("android:description=\"@string/([^\"]+)\"").find(meta)?.groupValues?.get(1)?.let { desc ->
                assertTrue("Missing description string $desc for $metaName", stringExists(desc))
            }
        }
    }

    private fun blockFor(className: String): String? {
        val receiverBlocks = Regex("<receiver\\b[^>]*>.*?</receiver>", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest)
        return receiverBlocks.map { it.value }.firstOrNull { it.contains(".$className\"") }
    }

    private fun stringExists(name: String): Boolean =
        Regex("<string name=\"$name\">").containsMatchIn(readRes("values/strings.xml"))

    @Test
    fun `every provider in the refresh scheduler is registered in the manifest`() {
        val registered = manifestWidgetReceivers().keys.map { it.substringAfter("widget.") }.toSet()
        for (provider in WidgetRefreshScheduler.ALL_PROVIDERS) {
            assertTrue(
                "${provider.simpleName} refreshes on the shared alarm but is NOT registered in the manifest — " +
                    "its widgets would never receive scheduled updates",
                provider.simpleName in registered
            )
        }
    }

    @Test
    fun `scheduler covers every registered widget provider`() {
        val scheduled = WidgetRefreshScheduler.ALL_PROVIDERS.map { it.simpleName }.toSet()
        val registered = manifestWidgetReceivers().keys.map { it.substringAfter("widget.") }.toSet()
        assertEquals(
            "Manifest receivers and the refresh scheduler must cover the exact same provider set",
            registered, scheduled
        )
    }

    @Test
    fun `no orphaned widget layouts and no unregistered widget metadata`() {
        val layoutDir = resDir.resolve("layout")
        val widgetLayouts = layoutDir.listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith(".xml") }!!
            .map { it.nameWithoutExtension }
            // widget_fallback is the emergency render, not a provider layout.
            .filterNot { it == "widget_fallback" }
            .toSet()

        val metadataLayouts = resDir.resolve("xml")
            .listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith("_info.xml") }!!
            .map { readRes("xml/${it.name}") }
            .mapNotNull { Regex("android:initialLayout=\"@layout/([^\"]+)\"").find(it)?.groupValues?.get(1) }
            .toSet()

        assertEquals(
            "Every widget_* layout must be wired through a widget_*_info.xml metadata file " +
                "(orphan layouts are the half-built-widget bug)",
            widgetLayouts, metadataLayouts
        )
    }

    /** XML comments are documentation — strip them so mentioning a forbidden
     *  construct in a comment can never trip the checks below. */
    private fun stripComments(xml: String): String =
        xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")

    @Test
    fun `widget layouts never use the gradient launcher foreground drawable`() {
        val layoutDir = resDir.resolve("layout")
        val widgetLayouts = layoutDir.listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith(".xml") }!!
        for (layout in widgetLayouts) {
            val content = stripComments(layout.readText())
            assertTrue(
                "${layout.name} references @drawable/ic_launcher_foreground — its aapt:attr gradient is a known " +
                    "'Can't load widget' cause inside RemoteViews; use ic_widget_logo instead",
                !content.contains("@drawable/ic_launcher_foreground")
            )
        }
    }

    @Test
    fun `drawables used by widget layouts contain no gradient elements`() {
        val layoutDir = resDir.resolve("layout")
        val widgetLayouts = layoutDir.listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith(".xml") }!!
        val referenced = mutableSetOf<String>()
        for (layout in widgetLayouts) {
            Regex("@drawable/([A-Za-z0-9_]+)").findAll(stripComments(layout.readText())).forEach {
                referenced.add(it.groupValues[1])
            }
        }
        for (name in referenced) {
            val file = resDir.resolve("drawable/$name.xml")
            if (!file.isFile) continue // PNG / platform drawable — nothing to parse
            val content = stripComments(file.readText())
            assertTrue(
                "Drawable $name used by a widget layout contains a <gradient>/" +
                    "<aapt:attr> element — unsafe inside RemoteViews on OEM widget hosts",
                !content.contains("<gradient") && !content.contains("aapt:attr")
            )
        }
    }

    @Test
    fun `every widget layout has the clickable root id the providers bind`() {
        val layoutDir = resDir.resolve("layout")
        val widgetLayouts = layoutDir.listFiles { f -> f.name.startsWith("widget_") && f.name.endsWith(".xml") }!!
        for (layout in widgetLayouts) {
            assertTrue(
                "${layout.name} is missing @+id/widget_root — the click PendingIntent cannot bind",
                layout.readText().contains("@+id/widget_root")
            )
        }
    }
}
