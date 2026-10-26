package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class FeedParsingTest {

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015">
          <title>Some Channel</title>
          <entry>
            <id>yt:video:abc123DEF45</id>
            <yt:videoId>abc123DEF45</yt:videoId>
            <title>Tom &amp; Jerry &quot;Live&quot;</title>
            <author><name>Some Channel</name></author>
            <published>2026-10-20T10:00:00+00:00</published>
            <media:group><media:community><media:statistics views="1234"/></media:community></media:group>
          </entry>
          <entry>
            <yt:videoId>zzz999YYY88</yt:videoId>
            <title>No stats</title>
            <author><name>Some Channel</name></author>
            <published>2026-10-21T10:00:00+00:00</published>
          </entry>
          <entry>
            <title>Broken: no id</title>
            <published>2026-10-21T10:00:00+00:00</published>
          </entry>
        </feed>
    """.trimIndent()

    @Test
    fun parsesEntriesAndSkipsBrokenOnes() {
        val entries = FeedParsing.parseFeed(xml)
        assertEquals(listOf("abc123DEF45", "zzz999YYY88"), entries.map { it.videoId })
    }

    @Test
    fun unescapesTitlesAndReadsFields() {
        val first = FeedParsing.parseFeed(xml).first()
        assertEquals("Tom & Jerry \"Live\"", first.title)
        assertEquals("Some Channel", first.channelName)
        assertEquals(1234L, first.views)
        assertEquals(OffsetDateTime.parse("2026-10-20T10:00:00+00:00").toInstant().toEpochMilli(), first.publishedAtMs)
    }

    @Test
    fun missingStatisticsMeansUnknownViews() {
        assertEquals(-1L, FeedParsing.parseFeed(xml)[1].views)
    }

    @Test
    fun extractsChannelId() {
        assertEquals(
            "UCabcdefghijklmnopqrstuv",
            FeedParsing.channelId("https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv")
        )
        assertNull(FeedParsing.channelId("https://www.youtube.com/@handle"))
    }

    @Test
    fun relativeTimeUsesLargestUnit() {
        val now = 10_000_000_000L
        assertEquals("just now", FeedParsing.relativeTime(now - 30_000, now))
        assertEquals("1 minute ago", FeedParsing.relativeTime(now - 60_000, now))
        assertEquals("5 hours ago", FeedParsing.relativeTime(now - 5 * 3_600_000L, now))
        assertEquals("2 days ago", FeedParsing.relativeTime(now - 2 * 86_400_000L, now))
        assertEquals("3 weeks ago", FeedParsing.relativeTime(now - 21 * 86_400_000L, now))
    }

    @Test
    fun futureTimesClampToJustNow() {
        assertEquals("just now", FeedParsing.relativeTime(2_000, 1_000))
    }
}
