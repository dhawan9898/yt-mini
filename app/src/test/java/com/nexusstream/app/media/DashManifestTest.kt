package com.nexusstream.app.media

import com.nexusstream.app.data.DashTrack
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class DashManifestTest {
    private fun video(itag: Int, codecs: String, mime: String, w: Int, h: Int) = DashTrack(
        itag = itag, mimeType = mime, codecs = codecs, bitrate = h * 1000, url = "https://r1.googlevideo.com/videoplayback?id=x&itag=$itag",
        initStart = 0, initEnd = 740, indexStart = 741, indexEnd = 1500, width = w, height = h, fps = 30
    )

    @Test
    fun groupsByCodecFamilyAndKeepsRanges() {
        val xml = DashManifest.build(
            video = listOf(
                video(137, "avc1.640028", "video/mp4", 1920, 1080),
                video(136, "avc1.4d401f", "video/mp4", 1280, 720),
                video(248, "vp9", "video/webm", 1920, 1080)
            ),
            audio = listOf(
                DashTrack(140, "audio/mp4", "mp4a.40.2", 130_000, "https://r1.googlevideo.com/videoplayback?a=1&b=2",
                    0, 631, 632, 1000, language = "en")
            ),
            durationSec = 213
        )
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        val mpd = doc.documentElement
        assertEquals("PT213S", mpd.getAttribute("mediaPresentationDuration"))
        val sets = doc.getElementsByTagName("AdaptationSet")
        assertEquals(3, sets.length) // avc1 mp4, vp9 webm, audio
        val avc = sets.item(0) as Element
        assertEquals("video/mp4", avc.getAttribute("mimeType"))
        assertEquals(2, avc.getElementsByTagName("Representation").length)
        val audio = sets.item(2) as Element
        assertEquals("en", audio.getAttribute("lang"))
        val base = audio.getElementsByTagName("BaseURL").item(0).textContent
        assertEquals("https://r1.googlevideo.com/videoplayback?a=1&b=2", base) // & survived escaping
        val segment = audio.getElementsByTagName("SegmentBase").item(0) as Element
        assertEquals("632-1000", segment.getAttribute("indexRange"))
        assertEquals("0-631", (segment.getElementsByTagName("Initialization").item(0) as Element).getAttribute("range"))
    }

    @Test
    fun codecFamilies() {
        assertEquals("vp09", DashManifest.family("vp9"))
        assertEquals("vp09", DashManifest.family("vp09.00.40.08"))
        assertEquals("av01", DashManifest.family("av01.0.08M.08"))
    }

    @Test
    fun qualityHeightIsTheShorterSide() {
        assertEquals(1080, video(1, "avc1", "video/mp4", 1080, 1920).qualityHeight)
        assertEquals(720, video(1, "avc1", "video/mp4", 1280, 720).qualityHeight)
    }
}
