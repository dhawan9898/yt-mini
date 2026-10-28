package com.nexusstream.app.media

import com.nexusstream.app.data.DashTrack

/**
 * Writes a static DASH manifest for one video from its separate video-only and audio-only
 * streams, the way YouTube's own players see them. ExoPlayer then switches video quality with
 * the network (each stream's `sidx` / cues index gives it the segment boundaries).
 *
 * Streams of one container and codec family share an AdaptationSet, since ExoPlayer adapts
 * only within a set; it picks the set the device decodes best.
 */
object DashManifest {
    fun build(video: List<DashTrack>, audio: List<DashTrack>, durationSec: Long): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" minBufferTime="PT1.500S"""")
        if (durationSec > 0) append(""" mediaPresentationDuration="PT${durationSec}S"""")
        append(">\n<Period>\n")
        var setId = 0
        for ((key, tracks) in video.groupBy { it.mimeType to family(it.codecs) }) {
            append("""<AdaptationSet id="${setId++}" contentType="video" mimeType="${key.first}" subsegmentAlignment="true">""").append('\n')
            tracks.sortedBy { it.bitrate }.forEach { t ->
                append("""<Representation id="${t.itag}" codecs="${xml(t.codecs)}" bandwidth="${t.bitrate.coerceAtLeast(1)}" width="${t.width}" height="${t.height}"""")
                if (t.fps > 0) append(""" frameRate="${t.fps}"""")
                append(">")
                segment(t)
                append("</Representation>\n")
            }
            append("</AdaptationSet>\n")
        }
        for ((key, tracks) in audio.groupBy { it.mimeType to family(it.codecs) }) {
            val lang = tracks.firstNotNullOfOrNull { it.language.takeIf(String::isNotEmpty) }
            append("""<AdaptationSet id="${setId++}" contentType="audio" mimeType="${key.first}" subsegmentAlignment="true"""")
            if (lang != null) append(""" lang="${xml(lang)}"""")
            append(">\n")
            tracks.sortedBy { it.bitrate }.forEach { t ->
                append("""<Representation id="${t.itag}" codecs="${xml(t.codecs)}" bandwidth="${t.bitrate.coerceAtLeast(1)}" audioSamplingRate="48000">""")
                append("""<AudioChannelConfiguration schemeIdUri="urn:mpeg:dash:23003:3:audio_channel_configuration:2011" value="2"/>""")
                segment(t)
                append("</Representation>\n")
            }
            append("</AdaptationSet>\n")
        }
        append("</Period>\n</MPD>\n")
    }

    private fun StringBuilder.segment(t: DashTrack) {
        append("<BaseURL>").append(xml(t.url)).append("</BaseURL>")
        append("""<SegmentBase indexRange="${t.indexStart}-${t.indexEnd}"><Initialization range="${t.initStart}-${t.initEnd}"/></SegmentBase>""")
    }

    /** "avc1", "vp09", "av01", "mp4a", "opus": streams that can switch seamlessly. */
    internal fun family(codecs: String): String = codecs.substringBefore('.').lowercase().let { if (it == "vp9") "vp09" else it }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
