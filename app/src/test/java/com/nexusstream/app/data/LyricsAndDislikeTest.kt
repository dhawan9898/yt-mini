package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAndDislikeTest {

    @Test
    fun parsesLrcWithEndTimesFromTheNextLine() {
        val lines = LyricsRepository.parseLrc("[00:01.50] First\n[00:05.00] Second\n[01:02.345] Third")
        assertEquals(listOf("First", "Second", "Third"), lines.map { it.text })
        assertEquals(listOf(1500L, 5000L, 62345L), lines.map { it.startTimeMs })
        assertEquals(5000L, lines[0].endTimeMs)
        assertEquals(62345L + 4000L, lines[2].endTimeMs)
    }

    @Test
    fun lineWithSeveralTimestampsRepeats() {
        val lines = LyricsRepository.parseLrc("[00:10.00][00:30.00] Chorus")
        assertEquals(listOf(10_000L, 30_000L), lines.map { it.startTimeMs })
        assertEquals(listOf("Chorus", "Chorus"), lines.map { it.text })
    }

    @Test
    fun ignoresMetadataAndEmptyLines() {
        val lines = LyricsRepository.parseLrc("[ar:Someone]\n[ti:Song]\n[00:02.00]\n[00:03.00] Hello")
        assertEquals(listOf("Hello"), lines.map { it.text })
    }

    @Test
    fun cleansTitlesAndArtists() {
        assertEquals("Song", LyricsRepository.cleanTitle("Song (Official Video)"))
        assertEquals("Song", LyricsRepository.cleanTitle("Song [Lyrics]"))
        assertEquals("Real (feat. X)", LyricsRepository.cleanTitle("Real (feat. X)"))
        assertEquals("Artist", LyricsRepository.cleanArtist("Artist - Topic"))
    }

    @Test
    fun parsesDislikeCounts() {
        assertEquals(1234L, DislikeApi.parse("""{"id":"x","likes":10,"dislikes":1234}"""))
        assertNull(DislikeApi.parse("""{"likes":10}"""))
        assertNull(DislikeApi.parse("not json"))
        assertTrue(DislikeApi.parse("""{"dislikes":0}""") == 0L)
    }
}
