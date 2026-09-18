package com.icy.devcheckplus.data

import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.icy.devcheckplus.model.InfoItem
import com.icy.devcheckplus.model.InfoSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CodecDataProvider {
    suspend fun getCodecSections(): List<InfoSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<InfoSection>()

        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val infos = codecList.codecInfos

            val overviewItems = mutableListOf<InfoItem>()
            overviewItems.add(InfoItem("Total Codecs", "${infos.size}"))
            overviewItems.add(InfoItem("Encoders", "${infos.count { it.isEncoder }}"))
            overviewItems.add(InfoItem("Decoders", "${infos.count { !it.isEncoder }}"))
            overviewItems.add(InfoItem("Hardware Accelerated", "${infos.count { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.isHardwareAccelerated else false }} (Android Q+)"))
            sections.add(InfoSection("Overview", overviewItems))

            // Group by MIME type
            val mimeGroups = mutableMapOf<String, MutableList<String>>()
            infos.forEach { codec ->
                codec.supportedTypes.forEach { mime ->
                    mimeGroups.getOrPut(mime) { mutableListOf() }.add(codec.name + if (codec.isEncoder) " (enc)" else " (dec)")
                }
            }

            val mimeItems = mimeGroups.entries.sortedBy { it.key }.map { (mime, codecs) ->
                InfoItem(mime, "${codecs.size} codecs: ${codecs.take(3).joinToString(", ")}${if (codecs.size > 3) " +${codecs.size - 3} more" else ""}")
            }
            sections.add(InfoSection("MIME Types (${mimeGroups.size})", mimeItems.take(30)))

            // HDR codec support
            val hdrItems = mutableListOf<InfoItem>()
            val hdrMimes = listOf("video/hevc", "video/avc", "video/av01", "video/vp9")
            hdrMimes.forEach { mime ->
                val hasCodec = mimeGroups.containsKey(mime)
                hdrItems.add(InfoItem(mime, if (hasCodec) "Supported (${mimeGroups[mime]?.size} codecs)" else "Not found"))
            }
            // Check for HDR10, HDR10+, Dolby Vision via MediaFormat keys if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                hdrItems.add(InfoItem("HDR10", "Check via codec capabilities — requires API 24+"))
                hdrItems.add(InfoItem("HDR10+", "Check via codec capabilities — requires API 29+"))
                hdrItems.add(InfoItem("Dolby Vision", "Check via codec capabilities"))
            }
            sections.add(InfoSection("HDR Codec Support", hdrItems))

            // Audio codecs
            val audioMimes = mimeGroups.filter { it.key.startsWith("audio/") }
            val audioItems = audioMimes.entries.map { (mime, codecs) ->
                InfoItem(mime, "${codecs.size} codecs")
            }
            sections.add(InfoSection("Audio Codecs (${audioMimes.size})", audioItems))

            // Video codecs detailed
            val videoMimes = mimeGroups.filter { it.key.startsWith("video/") }
            val videoItems = videoMimes.entries.map { (mime, codecs) ->
                InfoItem(mime, "${codecs.size} codecs")
            }
            sections.add(InfoSection("Video Codecs (${videoMimes.size})", videoItems))

        } catch (e: Exception) {
            sections.add(InfoSection("Codecs", listOf(InfoItem("Error", e.message ?: "Failed to read codecs"))))
        }

        sections
    }
}
