package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

class DownloadOptionsTest {
    // Real YouTube itags: 137 = mp4/avc1 1080p30, 299 = mp4/avc1 1080p60, 248 = webm/vp9 1080p,
    // 136 = mp4/avc1 720p, 140 = m4a audio, 251 = webm/opus audio.
    private fun video(itag: Int, url: String, format: MediaFormat): VideoStream =
        VideoStream.Builder()
            .setId("v$itag")
            .setContent(url, true)
            .setMediaFormat(format)
            .setIsVideoOnly(true)
            .setItagItem(ItagItem.getItag(itag))
            .build()

    private fun audio(itag: Int, url: String, format: MediaFormat, bitrate: Int): AudioStream =
        AudioStream.Builder()
            .setId("a$itag")
            .setContent(url, true)
            .setMediaFormat(format)
            .setAverageBitrate(bitrate)
            .setItagItem(ItagItem.getItag(itag))
            .build()

    private val m4a = audio(140, "https://a/m4a", MediaFormat.M4A, 128)
    private val opus = audio(251, "https://a/opus", MediaFormat.WEBMA_OPUS, 160)

    @Test
    fun anMp4VideoIsPairedWithTheBestM4aAudio() {
        val options = YouTubeRepository.mergedDownloadOptions(
            listOf(video(137, "https://v/1080", MediaFormat.MPEG_4)),
            listOf(m4a, audio(140, "https://a/m4a-low", MediaFormat.M4A, 48))
        )
        val option = options.getValue(1080)
        assertEquals("mp4", option.extension)
        assertEquals("https://v/1080", option.url)
        assertEquals("https://a/m4a", option.audioUrl) // the 128 kbps track, not the 48
        assertTrue(option.isVideo)
    }

    @Test
    fun noMatchingAudioMeansNoOption() {
        // An mp4 video needs m4a audio; webm audio cannot be muxed into it.
        assertTrue(YouTubeRepository.mergedDownloadOptions(listOf(video(137, "https://v/1080", MediaFormat.MPEG_4)), listOf(opus)).isEmpty())
        assertTrue(YouTubeRepository.mergedDownloadOptions(listOf(video(137, "https://v/1080", MediaFormat.MPEG_4)), emptyList()).isEmpty())
        assertTrue(YouTubeRepository.mergedDownloadOptions(emptyList(), listOf(m4a)).isEmpty())
    }

    @Test
    fun webmVideoUsesWebmAudio() {
        val option = YouTubeRepository.mergedDownloadOptions(
            listOf(video(248, "https://v/webm1080", MediaFormat.WEBM)),
            listOf(m4a, opus)
        ).getValue(1080)
        assertEquals("webm", option.extension)
        assertEquals("https://a/opus", option.audioUrl)
    }

    @Test
    fun theHigherFrameRateWinsForTheSameHeight() {
        val options = YouTubeRepository.mergedDownloadOptions(
            listOf(video(137, "https://v/30", MediaFormat.MPEG_4), video(299, "https://v/60", MediaFormat.MPEG_4)),
            listOf(m4a)
        )
        assertEquals(1, options.size)
        assertEquals("https://v/60", options.getValue(1080).url)
        assertEquals("1080p60 video", options.getValue(1080).label)
    }

    @Test
    fun eachHeightGetsItsOwnOption() {
        val options = YouTubeRepository.mergedDownloadOptions(
            listOf(video(137, "https://v/1080", MediaFormat.MPEG_4), video(136, "https://v/720", MediaFormat.MPEG_4)),
            listOf(m4a)
        )
        assertEquals(setOf(1080, 720), options.keys)
        assertNull(options[480])
    }
}
