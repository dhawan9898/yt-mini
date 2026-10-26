package com.nexusstream.app.ui.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class DescriptionLinksTest {

    private fun values(text: String, kind: DescriptionLink.Kind) =
        DescriptionLinks.find(text).filter { it.kind == kind }.map { it.value }

    @Test
    fun findsMinuteAndHourTimestamps() {
        val text = "0:00 Intro\n3:45 Main part\n1:02:03 Outro"
        assertEquals(
            listOf("0", "225000", "3723000"),
            values(text, DescriptionLink.Kind.TIMESTAMP)
        )
    }

    @Test
    fun linkRangesCoverExactlyTheTimestamp() {
        val text = "Jump to 12:34 now"
        val link = DescriptionLinks.find(text).single()
        assertEquals("12:34", text.substring(link.start, link.end))
    }

    @Test
    fun findsUrlsAndTrimsTrailingPunctuation() {
        val text = "Visit https://example.com/page, or (https://example.org/x)."
        assertEquals(
            listOf("https://example.com/page", "https://example.org/x"),
            values(text, DescriptionLink.Kind.URL)
        )
    }

    @Test
    fun numbersInsideUrlsAreNotTimestamps() {
        val text = "See https://example.com/watch?t=1:23:45 for more"
        assertEquals(emptyList<String>(), values(text, DescriptionLink.Kind.TIMESTAMP))
    }

    @Test
    fun plainNumbersAndTimesLikeDecimalsAreIgnored() {
        assertEquals(emptyList<DescriptionLink>(), DescriptionLinks.find("Chapter 12 costs 3.50 and ratio 5:99"))
    }
}
