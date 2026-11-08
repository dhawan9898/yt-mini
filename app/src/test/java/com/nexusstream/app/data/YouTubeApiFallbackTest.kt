package com.nexusstream.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeApiFallbackTest {
    private fun video(id: String, seconds: Long, live: Boolean = false) = YouTubeDataApi.Video(
        id = id, title = "T $id", channelId = "UC1234567890123456789012", channelTitle = "Chan", thumbnailUrl = "t.jpg",
        durationSeconds = seconds, viewCount = 1_500_000, likeCount = 10, commentCount = 3, publishedAtMs = 1_700_000_000_000L,
        description = "d", tags = listOf("x"), isLive = live, categoryId = "10"
    )

    @Test
    fun shortsAreNotRegularVideos() {
        assertFalse(YouTubeApiFallback.isRegularVideo(video("a", 45)))
        assertFalse(YouTubeApiFallback.isRegularVideo(video("a", 60)))
        assertTrue(YouTubeApiFallback.isRegularVideo(video("a", 61)))
        assertTrue("a live stream has no duration but is a video", YouTubeApiFallback.isRegularVideo(video("a", 0, live = true)))
    }

    @Test
    fun aVideoBecomesAModelTheAppCanPlay() {
        val m = YouTubeApiFallback.toModel(video("abc123def45", 3723), channelAvatar = "av.jpg", now = 1_700_000_000_000L + 3 * 86_400_000L)
        assertEquals("abc123def45", m.id)
        assertEquals("https://www.youtube.com/watch?v=abc123def45", m.sourceUrl)
        assertEquals("https://www.youtube.com/channel/UC1234567890123456789012", m.channelUrl)
        assertEquals(3_723_000L, m.durationMs)
        assertEquals("1.5M", m.viewsOrPlays)
        assertEquals("3 days ago", m.publishDate)
        assertEquals("av.jpg", m.channelAvatarUrl)
        assertEquals("", m.audioUrl)   // direct URLs are resolved at play time, as for extractor items
    }

    @Test
    fun topicChipsMapToYouTubeCategories() {
        assertEquals("10", YouTubeApiFallback.categoryIdFor("Music"))
        assertEquals("20", YouTubeApiFallback.categoryIdFor("Gaming"))
        assertNull(YouTubeApiFallback.categoryIdFor("All"))
        assertNull(YouTubeApiFallback.categoryIdFor("Live"))
    }

    @Test
    fun searchHitsBecomeSearchResults() {
        val v = YouTubeApiFallback.toSearchResult(YouTubeDataApi.SearchHit.VideoHit(video("abc123def45", 300)))
        assertTrue(v is SearchResult.Video)
        assertNull("a short is not listed", YouTubeApiFallback.toSearchResult(YouTubeDataApi.SearchHit.VideoHit(video("abc123def45", 30))))
        val ch = YouTubeApiFallback.toSearchResult(
            YouTubeDataApi.SearchHit.ChannelHit(YouTubeDataApi.Channel("UC9", "Name", "desc", "a.jpg", "", 2_000, 5, ""))
        ) as SearchResult.Channel
        assertEquals("https://www.youtube.com/channel/UC9", ch.channel.url)
        assertEquals("2.0K", ch.channel.subscribers)
        val pl = YouTubeApiFallback.toSearchResult(
            YouTubeDataApi.SearchHit.PlaylistHit(YouTubeDataApi.Playlist("PL1", "List", "Chan", "p.jpg", -1))
        ) as SearchResult.Playlist
        assertEquals("https://www.youtube.com/playlist?list=PL1", pl.playlist.url)
    }

    @Test
    fun pagesLoadTheNextOneOnDemand() = runBlocking {
        val first = YouTubeDataApi.Page(listOf(1, 2, 3), "T2")
        val paged = YouTubeApiFallback.pagedFrom(first, { n -> if (n % 2 == 1) "v$n" else null }) { token ->
            assertEquals("T2", token)
            YouTubeDataApi.Page(listOf(5, 6), null)
        }
        assertEquals(listOf("v1", "v3"), paged.items)
        assertTrue(paged.hasMore)
        val next = paged.next().getOrThrow()
        assertEquals(listOf("v5"), next.items)
        assertFalse(next.hasMore)
    }

    @Test
    fun aFailingNextPageIsAResultNotACrash() = runBlocking {
        val paged = YouTubeApiFallback.pagedFrom(YouTubeDataApi.Page(listOf(1), "T2"), { it }) { throw YouTubeApiException("quotaExceeded", 403, "q") }
        assertTrue(paged.next().isFailure)
    }

    @Test
    fun commentsKeepAuthorLikesAndReplyLoader() {
        val withReplies = YouTubeApiFallback.toComment(
            YouTubeDataApi.Comment("c1", "Ann", "a.jpg", "UCa", "Great", 4_000_000_000L, 1_700_000_000_000L, 2), now = 1_700_000_000_000L + 7_200_000L
        )
        assertEquals("Ann", withReplies.author)
        assertEquals(Int.MAX_VALUE, withReplies.likes)   // clamped, never negative or overflowing
        assertEquals("2 hours ago", withReplies.date)
        assertEquals(2, withReplies.replyCount)
        assertTrue(withReplies.loadReplies != null)
        val none = YouTubeApiFallback.toComment(YouTubeDataApi.Comment("c2", "Bob", "", "", "Hi", 0, 0, 0), now = 1L)
        assertNull(none.loadReplies)
        assertEquals("", none.date)
    }
}
