package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormatCountTest {

    @Before
    fun useEnglishLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun smallNumbersAreShownAsIs() {
        assertEquals("999", YouTubeRepository.formatCount(999))
    }

    @Test
    fun thousandsMillionsAndBillions() {
        assertEquals("1.2K", YouTubeRepository.formatCount(1_234))
        assertEquals("504.0K", YouTubeRepository.formatCount(504_000))
        assertEquals("2.5M", YouTubeRepository.formatCount(2_500_000))
        assertEquals("1.1B", YouTubeRepository.formatCount(1_100_000_000))
    }

    @Test
    fun unknownCountsAreBlank() {
        assertEquals("", YouTubeRepository.formatCount(-1))
    }
}
