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

    // ---- language tracks ----

    private val multiTrack = ResolvedStream(
        audioOptions = listOf(
            AudioOption(128, "orig128", trackId = "ja.4", trackName = "Japanese original", isOriginal = true),
            AudioOption(48, "orig48", trackId = "ja.4", trackName = "Japanese original", isOriginal = true),
            // The auto-dub has a higher bitrate, which must NOT make it the default.
            AudioOption(256, "dub256", trackId = "en.10", trackName = "English (auto-dubbed)", isOriginal = false),
            AudioOption(128, "dub128", trackId = "en.10", trackName = "English (auto-dubbed)", isOriginal = false)
        ),
        videoOptions = emptyList()
    )

    @Test
    fun defaultAudioIsTheOriginalTrackEvenIfADubHasHigherBitrate() {
        assertEquals("orig128", multiTrack.pickAudio(null)?.url)
    }

    @Test
    fun pinnedTrackIsPlayedAtItsBestBitrate() {
        assertEquals("dub256", multiTrack.pickAudio(null, "en.10")?.url)
    }

    @Test
    fun unknownTrackFallsBackToOriginal() {
        assertEquals("orig128", multiTrack.pickAudio(null, "fr.99")?.url)
    }

    @Test
    fun bitrateIsChosenWithinTheSelectedTrack() {
        assertEquals("orig48", multiTrack.pickAudio(60)?.url)
        assertEquals("dub128", multiTrack.pickAudio(200, "en.10")?.url)
    }

    @Test
    fun bitrateListBelongsToTheSelectedTrack() {
        assertEquals(listOf(128, 48), multiTrack.kbpsFor(null))
        assertEquals(listOf(256, 128), multiTrack.kbpsFor("en.10"))
    }

    @Test
    fun tracksListHasOneEntryPerTrackWithOriginalFirst() {
        val tracks = multiTrack.audioTracks
        assertEquals(listOf("ja.4", "en.10"), tracks.map { it.id })
        assertEquals(listOf(true, false), tracks.map { it.isOriginal })
    }

    @Test
    fun singleTrackVideosStillWork() {
        assertEquals(1, stream.audioTracks.size)
        assertEquals(listOf(160, 128, 48), stream.kbpsFor(null))
    }
}
