package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TakeoutParsingTest {

    @Test
    fun csvLineHandlesQuotedCommasAndQuotes() {
        assertEquals(
            listOf("a", "b, c", "say \"hi\"", ""),
            TakeoutParsing.parseCsvLine("a,\"b, c\",\"say \"\"hi\"\"\",")
        )
    }

    @Test
    fun parsesSubscriptionsCsv() {
        val csv = "Channel Id,Channel Url,Channel Title\r\n" +
            "UCabcdefghijklmnopqrstuv,http://www.youtube.com/channel/UCabcdefghijklmnopqrstuv,Some Channel\r\n" +
            "UCzzzzzzzzzzzzzzzzzzzzzz,http://www.youtube.com/channel/UCzzzzzzzzzzzzzzzzzzzzzz,\"Tom, Dick & Harry\"\r\n"
        val subs = TakeoutParsing.parseSubscriptionsCsv(csv)
        assertEquals(listOf("Some Channel", "Tom, Dick & Harry"), subs.map { it.name })
        assertEquals("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv", subs.first().channelUrl)
    }

    @Test
    fun subscriptionsCsvWithoutExpectedColumnsIsEmpty() {
        assertTrue(TakeoutParsing.parseSubscriptionsCsv("foo,bar\n1,2").isEmpty())
        assertTrue(TakeoutParsing.parseSubscriptionsCsv("").isEmpty())
    }

    @Test
    fun subscriptionsCsvSkipsRowsWithoutTitle() {
        val csv = "Channel Id,Channel Url,Channel Title\nUCabcdefghijklmnopqrstuv,u,\n"
        assertTrue(TakeoutParsing.parseSubscriptionsCsv(csv).isEmpty())
    }

    private val history = """
        [
          {"header":"YouTube","title":"Watched Cool Video","titleUrl":"https://www.youtube.com/watch?v=abc123DEF45",
           "subtitles":[{"name":"Some Channel","url":"https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv"}],
           "time":"2026-10-20T10:00:00.000Z"},
          {"header":"YouTube Music","title":"Watched A Song","titleUrl":"https://music.youtube.com/watch?v=musicMUSIC1"},
          {"header":"YouTube","title":"Watched a video that has been removed"},
          {"header":"YouTube","title":"Watched Cool Video","titleUrl":"https://www.youtube.com/watch?v=abc123DEF45"},
          {"header":"YouTube","title":"Watched Other","titleUrl":"https://www.youtube.com/watch?v=zzz999YYY88&t=5s"}
        ]
    """.trimIndent()

    @Test
    fun parsesHistorySkippingMusicRemovedAndDuplicates() {
        val items = TakeoutParsing.parseWatchHistory(history)
        assertEquals(listOf("abc123DEF45", "zzz999YYY88"), items.map { it.id })
        assertEquals("Cool Video", items.first().title)
        assertEquals("Some Channel", items.first().artistOrChannel)
        assertEquals("https://www.youtube.com/watch?v=abc123DEF45", items.first().sourceUrl)
    }

    @Test
    fun invalidHistoryJsonIsEmpty() {
        assertTrue(TakeoutParsing.parseWatchHistory("not json").isEmpty())
    }
}
