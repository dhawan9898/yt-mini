package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolvedStreamTest {

    private val stream = ResolvedStream(
        audioOptions = listOf(AudioOption(160, "a160"), AudioOption(128, "a128"), AudioOption(48, "a48")),
        videoOptions = listOf(
            VideoOption(1080, "v1080"),
            VideoOption(720, "v720"),
            VideoOption(360, "v360")
        )
    )

    @Test
    fun autoVideoCapsAt720p() {
        assertEquals(720, stream.pickVideo(null)?.height)
    }

    @Test
    fun pinnedVideoQualityIsHonoured() {
        assertEquals(1080, stream.pickVideo(1080)?.height)
        assertEquals(360, stream.pickVideo(360)?.height)
    }

    @Test
    fun videoFallsBackToLowestWhenCapIsBelowEverything() {
        assertEquals(360, stream.pickVideo(144)?.height)
    }

    @Test
    fun videoPickReturnsNullWithoutVideoStreams() {
        assertNull(ResolvedStream(stream.audioOptions, emptyList()).pickVideo(null))
    }

    @Test
    fun autoAudioPicksBest() {
        assertEquals(160, stream.pickAudio(null)?.kbps)
    }

    @Test
    fun pinnedAudioPicksClosestAtOrBelow() {
        assertEquals(128, stream.pickAudio(130)?.kbps)
        assertEquals(48, stream.pickAudio(48)?.kbps)
    }

    @Test
    fun audioFallsBackToLowestWhenBelowEverything() {
        assertEquals(48, stream.pickAudio(10)?.kbps)
    }
}
