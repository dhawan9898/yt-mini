package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAndDislikeTest {

    @Test
    fun parsesEnhancedLrcWordTimings() {
        val line = LyricsRepository.parseLrc("[00:12.00] <00:12.00> Hello <00:12.50> big <00:13.00> world <00:14.00>\n[00:20.00] Next").first()
        assertEquals("Hello big world", line.text)
        assertEquals(listOf("Hello", "big", "world"), line.words.map { it.word })
        assertEquals(listOf(12_000L, 12_500L, 13_000L), line.words.map { it.startTimeMs })
        assertEquals(listOf(12_500L, 13_000L, 14_000L), line.words.map { it.endTimeMs })
    }

    @Test
    fun plainLrcLinesHaveNoWords() {
        assertTrue(LyricsRepository.parseLrc("[00:01.00] Just a line").single().words.isEmpty())
    }

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

    @Test
    fun parsesSponsorBlockSegments() {
        val json = """
            [
                {"category":"sponsor","actionType":"skip","segment":[10.5, 30.2],"UUID":"abc-123"},
                {"category":"intro","actionType":"skip","segment":[0.0, 5.0],"UUID":"def-456"}
            ]
        """
        val segments = SponsorBlockApi.parse(json)
        assertEquals(2, segments.size)
        assertEquals("def-456", segments[0].uuid)
        assertEquals(0L, segments[0].startMs)
        assertEquals(5000L, segments[0].endMs)
        assertEquals("intro", segments[0].category)

        assertEquals("abc-123", segments[1].uuid)
        assertEquals(10500L, segments[1].startMs)
        assertEquals(30200L, segments[1].endMs)
        assertEquals("sponsor", segments[1].category)
    }

    @Test
    fun handlesInvalidSponsorBlockJson() {
        assertTrue(SponsorBlockApi.parse("not json").isEmpty())
        assertTrue(SponsorBlockApi.parse("[]").isEmpty())
    }
}
