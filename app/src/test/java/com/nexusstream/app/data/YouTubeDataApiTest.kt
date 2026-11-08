package com.nexusstream.app.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class YouTubeDataApiTest {
    private lateinit var server: MockWebServer
    private var clock = 1_700_000_000_000L
    private lateinit var quota: QuotaTracker

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        quota = QuotaTracker(MemoryQuotaStore(), dailyLimit = 1_000, now = { clock })
        YouTubeDataApi.configureForTest("TEST_KEY", server.url("/youtube/v3/").toString(), quota)
    }

    @After
    fun tearDown() {
        server.shutdown()
        YouTubeDataApi.configure("")
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200).setBody(body)

    private val videoJson = """
        {"items":[{"id":"abc123def45","snippet":{"title":"A video","channelId":"UC1","channelTitle":"Chan",
          "publishedAt":"2024-05-01T12:30:45Z","description":"0:00 Intro\n1:30 Main","tags":["a","b"],"categoryId":"10",
          "liveBroadcastContent":"none","thumbnails":{"default":{"url":"d.jpg"},"high":{"url":"h.jpg"}}},
          "contentDetails":{"duration":"PT1H2M3S"},"statistics":{"viewCount":"12345","likeCount":"99","commentCount":"7"}}],
         "nextPageToken":"NEXT"}
    """.trimIndent()

    // ------------------------------------------------------------- pure parsing

    @Test
    fun durationsAreParsed() {
        assertEquals(3723L, YouTubeDataApi.parseIsoDuration("PT1H2M3S"))
        assertEquals(45L, YouTubeDataApi.parseIsoDuration("PT45S"))
        assertEquals(600L, YouTubeDataApi.parseIsoDuration("PT10M"))
        assertEquals(93_600L, YouTubeDataApi.parseIsoDuration("P1DT2H"))
        assertEquals(0L, YouTubeDataApi.parseIsoDuration("P0D"))
        assertEquals(0L, YouTubeDataApi.parseIsoDuration("garbage"))
        assertEquals(0L, YouTubeDataApi.parseIsoDuration(""))
    }

    @Test
    fun timesAreParsedAsUtc() {
        assertEquals(1_714_566_645_000L, YouTubeDataApi.parseIsoTime("2024-05-01T12:30:45Z"))
        assertEquals(1_714_566_645_000L, YouTubeDataApi.parseIsoTime("2024-05-01T12:30:45.123Z"))
        assertEquals(0L, YouTubeDataApi.parseIsoTime(""))
        assertEquals(0L, YouTubeDataApi.parseIsoTime("not a time"))
    }

    @Test
    fun videoFieldsAreMapped() {
        val v = YouTubeDataApi.parseVideos(JSONObject(videoJson)).single()
        assertEquals("abc123def45", v.id)
        assertEquals("A video", v.title)
        assertEquals("UC1", v.channelId)
        assertEquals("h.jpg", v.thumbnailUrl)
        assertEquals(3723L, v.durationSeconds)
        assertEquals(12345L, v.viewCount)
        assertEquals(99L, v.likeCount)
        assertEquals(listOf("a", "b"), v.tags)
        assertFalse(v.isLive)
    }

    @Test
    fun hiddenCountsAreNegative() {
        val json = JSONObject("""{"items":[{"id":"x","snippet":{"title":"t"},"contentDetails":{"duration":"PT1M"},"statistics":{"viewCount":"5"}}]}""")
        val v = YouTubeDataApi.parseVideos(json).single()
        assertEquals(-1L, v.likeCount)
        assertEquals(-1L, v.commentCount)
    }

    @Test
    fun liveStreamsAreRecognised() {
        val json = JSONObject("""{"items":[{"id":"x","snippet":{"title":"t","liveBroadcastContent":"live"},"contentDetails":{"duration":"P0D"}}]}""")
        assertTrue(YouTubeDataApi.parseVideos(json).single().isLive)
    }

    @Test
    fun channelHidesSubscribersWhenAsked() {
        val json = JSONObject("""{"items":[{"id":"UC1","snippet":{"title":"C"},"statistics":{"hiddenSubscriberCount":true,"subscriberCount":"0","videoCount":"3"},
            "contentDetails":{"relatedPlaylists":{"uploads":"UU1"}},"brandingSettings":{"image":{"bannerExternalUrl":"b.jpg"}}}]}""")
        val c = YouTubeDataApi.parseChannels(json).single()
        assertEquals(-1L, c.subscriberCount)
        assertEquals("UU1", c.uploadsPlaylistId)
        assertEquals("b.jpg", c.bannerUrl)
    }

    @Test
    fun commentsKeepTheirInlineReplies() {
        val json = JSONObject("""{"items":[{"snippet":{"totalReplyCount":2,"topLevelComment":{"id":"c1","snippet":{"authorDisplayName":"Ann","textDisplay":"Great","likeCount":4,"publishedAt":"2024-05-01T00:00:00Z","authorChannelId":{"value":"UCa"}}}},
            "replies":{"comments":[{"id":"c1.r1","snippet":{"authorDisplayName":"Bob","textDisplay":"Agreed","likeCount":1,"publishedAt":"2024-05-02T00:00:00Z"}}]}}]}""")
        val c = YouTubeDataApi.parseCommentThreads(json).single()
        assertEquals("Ann", c.author)
        assertEquals(2L, c.replyCount)
        assertEquals("UCa", c.authorChannelId)
        assertEquals("Bob", c.replies.single().author)
    }

    // ------------------------------------------------------------- quota

    @Test
    fun quotaCountsRefusesAndResetsAtPacificMidnight() {
        val q = QuotaTracker(MemoryQuotaStore(), dailyLimit = 250, now = { clock })
        assertTrue(q.tryCharge(100))
        assertTrue(q.tryCharge(100))
        assertFalse("100 more does not fit", q.tryCharge(100))
        assertEquals(50, q.remaining())
        assertTrue(q.tryCharge(50))
        assertEquals(0, q.remaining())
        clock += 26L * 3_600_000L // a day later in any time zone
        assertEquals(250, q.remaining())
    }

    @Test
    fun marksExhaustedUsesUpTheDay() {
        val q = QuotaTracker(MemoryQuotaStore(), dailyLimit = 100, now = { clock })
        q.markExhausted()
        assertEquals(0, q.remaining())
        assertFalse(q.tryCharge(1))
    }

    @Test
    fun quotaSurvivesARestartThroughTheStore() {
        val store = MemoryQuotaStore()
        QuotaTracker(store, dailyLimit = 100, now = { clock }).tryCharge(30)
        assertEquals(70, QuotaTracker(store, dailyLimit = 100, now = { clock }).remaining())
    }

    @Test
    fun expensiveCallsStopBeforeTheWholeDayIsSpent() {
        val q = QuotaTracker(MemoryQuotaStore(), dailyLimit = 1_000, expensiveLimit = 300, now = { clock })
        assertTrue(q.tryCharge(100))
        assertTrue(q.tryCharge(100))
        assertTrue(q.tryCharge(100))
        assertFalse("a fourth search is over the expensive limit", q.tryCharge(100))
        assertFalse(q.canAfford(100))
        assertTrue("1-unit calls still fit", q.canAfford(1))
        assertTrue(q.tryCharge(1))
        assertEquals(699, q.remaining())
    }

    @Test
    fun canSearchTurnsOffWhileCheapCallsKeepWorking() {
        val q = QuotaTracker(MemoryQuotaStore(), dailyLimit = 1_000, expensiveLimit = 150, now = { clock })
        YouTubeDataApi.configureForTest("TEST_KEY", server.url("/youtube/v3/").toString(), q)
        assertTrue(YouTubeDataApi.canSearch)
        server.enqueue(ok("""{"items":[]}"""))
        YouTubeDataApi.search("one", listOf(YouTubeDataApi.SearchKind.VIDEO))
        assertFalse("101 used: another search (101) is over 150", YouTubeDataApi.canSearch)
        assertTrue(YouTubeDataApi.canAfford(1))
        assertTrue(YouTubeDataApi.isAvailable)
    }

    // ------------------------------------------------------------- requests

    @Test
    fun trendingSendsTheKeyAndParsesThePage() {
        server.enqueue(ok(videoJson))
        val page = YouTubeDataApi.trending("IN", categoryId = "10")
        val request = server.takeRequest()
        assertEquals("/youtube/v3/videos", request.requestUrl!!.encodedPath)
        assertEquals("mostPopular", request.requestUrl!!.queryParameter("chart"))
        assertEquals("IN", request.requestUrl!!.queryParameter("regionCode"))
        assertEquals("10", request.requestUrl!!.queryParameter("videoCategoryId"))
        assertEquals("TEST_KEY", request.requestUrl!!.queryParameter("key"))
        assertEquals(1, page.items.size)
        assertEquals("NEXT", page.nextPageToken)
        assertEquals(1, quota.used())
    }

    @Test
    fun anIdenticalRequestIsServedFromTheCacheWithoutCost() {
        server.enqueue(ok(videoJson))
        YouTubeDataApi.videos(listOf("abc123def45"))
        YouTubeDataApi.videos(listOf("abc123def45"))
        assertEquals(1, server.requestCount)
        assertEquals(1, quota.used())
    }

    @Test
    fun aServerErrorIsRetriedThenSucceeds() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"code":503,"message":"busy","errors":[{"reason":"backendError"}]}}"""))
        server.enqueue(ok(videoJson))
        val videos = YouTubeDataApi.videos(listOf("abc123def45"))
        assertEquals(1, videos.size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun aPermanentErrorIsNotRetried() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"code":404,"message":"nope","errors":[{"reason":"videoNotFound"}]}}"""))
        try {
            YouTubeDataApi.videos(listOf("zzz"))
            throw AssertionError("expected an exception")
        } catch (e: YouTubeApiException) {
            assertEquals("videoNotFound", e.reason)
            assertEquals(404, e.httpCode)
        }
        assertEquals(1, server.requestCount)
        assertTrue("a 404 does not switch the API off", YouTubeDataApi.isAvailable)
    }

    @Test
    fun quotaExceededSwitchesTheApiOffForTheDay() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":403,"message":"quota","errors":[{"reason":"quotaExceeded"}]}}"""))
        try {
            YouTubeDataApi.trending("US")
            throw AssertionError("expected an exception")
        } catch (e: YouTubeApiException) {
            assertTrue(e.isQuota)
        }
        assertFalse(YouTubeDataApi.isAvailable)
        val before = server.requestCount
        try { YouTubeDataApi.trending("GB"); throw AssertionError("expected an exception") } catch (e: YouTubeApiException) { assertTrue(e.isQuota) }
        assertEquals("no request once the day is spent", before, server.requestCount)
    }

    @Test
    fun aRejectedKeyDisablesTheApiForTheSession() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":400,"message":"API key not valid","errors":[{"reason":"keyInvalid"}]}}"""))
        try { YouTubeDataApi.trending("US"); throw AssertionError("expected an exception") } catch (e: YouTubeApiException) { assertTrue(e.isKeyProblem) }
        assertFalse(YouTubeDataApi.isAvailable)
    }

    @Test
    fun searchCostsOneHundredPlusTheVideoDetails() {
        server.enqueue(ok("""{"items":[
            {"id":{"kind":"youtube#video","videoId":"abc123def45"},"snippet":{"title":"A video"}},
            {"id":{"kind":"youtube#channel","channelId":"UC9"},"snippet":{"channelTitle":"Some channel","description":"d"}},
            {"id":{"kind":"youtube#playlist","playlistId":"PL1"},"snippet":{"title":"A list","channelTitle":"Chan"}}],"nextPageToken":"N2"}"""))
        server.enqueue(ok(videoJson))
        val page = YouTubeDataApi.search("lofi", listOf(YouTubeDataApi.SearchKind.VIDEO, YouTubeDataApi.SearchKind.CHANNEL, YouTubeDataApi.SearchKind.PLAYLIST))
        assertEquals(3, page.items.size)
        assertTrue(page.items[0] is YouTubeDataApi.SearchHit.VideoHit)
        assertTrue(page.items[1] is YouTubeDataApi.SearchHit.ChannelHit)
        assertTrue(page.items[2] is YouTubeDataApi.SearchHit.PlaylistHit)
        assertEquals("N2", page.nextPageToken)
        assertEquals(101, quota.used())
    }

    @Test
    fun searchIsRefusedWhenTheBudgetCannotCoverIt() {
        val small = QuotaTracker(MemoryQuotaStore(), dailyLimit = 50, now = { clock })
        YouTubeDataApi.configureForTest("TEST_KEY", server.url("/youtube/v3/").toString(), small)
        try { YouTubeDataApi.search("x", listOf(YouTubeDataApi.SearchKind.VIDEO)); throw AssertionError("expected an exception") } catch (e: YouTubeApiException) { assertTrue(e.isQuota) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun playlistVideosKeepThePlaylistOrderAndDropMissingOnes() {
        server.enqueue(ok("""{"items":[{"contentDetails":{"videoId":"missing00000"}},{"contentDetails":{"videoId":"abc123def45"}}],"nextPageToken":"P2"}"""))
        server.enqueue(ok(videoJson))
        val page = YouTubeDataApi.playlistVideos("PL1")
        assertEquals(listOf("abc123def45"), page.items.map { it.id })
        assertEquals("P2", page.nextPageToken)
    }

    @Test
    fun moreFromTheChannelUsesItsUploadsPlaylistAndSkipsTheCurrentVideoAndShorts() {
        server.enqueue(ok("""{"items":[{"contentDetails":{"videoId":"abc123def45"}},{"contentDetails":{"videoId":"other0000001"}},{"contentDetails":{"videoId":"short0000001"}}]}"""))
        server.enqueue(ok("""{"items":[
            {"id":"abc123def45","snippet":{"title":"current","channelId":"UC1234567890123456789012"},"contentDetails":{"duration":"PT10M"}},
            {"id":"other0000001","snippet":{"title":"another","channelId":"UC1234567890123456789012"},"contentDetails":{"duration":"PT5M"}},
            {"id":"short0000001","snippet":{"title":"a short","channelId":"UC1234567890123456789012"},"contentDetails":{"duration":"PT30S"}}]}"""))
        val current = YouTubeDataApi.Video("abc123def45", "current", "UC1234567890123456789012", "Chan", "", 600, 1, 1, 1, 0, "", emptyList(), false, "")
        val more = YouTubeApiFallback.moreFromChannel(current)
        assertEquals("UU1234567890123456789012", server.takeRequest().requestUrl!!.queryParameter("playlistId"))
        assertEquals(listOf("other0000001"), more.map { it.id })
    }

    @Test
    fun withoutAKeyNothingIsSent() {
        YouTubeDataApi.configure("")
        assertFalse(YouTubeDataApi.isAvailable)
        try { YouTubeDataApi.trending("US"); throw AssertionError("expected an exception") } catch (e: YouTubeApiException) { assertEquals("disabled", e.reason) }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun missingChannelGivesNull() {
        server.enqueue(ok("""{"items":[]}"""))
        assertNull(YouTubeDataApi.channelById("UCnone"))
    }
}
