package com.nexusstream.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.util.Base64

class YouTubeWebTest {
    private fun decode(params: String) = Base64.getDecoder().decode(URLDecoder.decode(params, "UTF-8")).map { it.toInt() and 0xFF }

    @Test
    fun searchParamsMatchYouTubeEncoding() {
        // Sort by upload date, this week, videos only: CAISBAgDEAE= on the website.
        val p = YouTubeWeb.searchParams(YouTubeWeb.SearchFilters(sort = YouTubeWeb.SortBy.UPLOAD_DATE, date = YouTubeWeb.UploadDate.WEEK), type = 1)
        assertEquals(listOf(0x08, 0x02, 0x12, 0x04, 0x08, 0x03, 0x10, 0x01), decode(p))
        // Live + 4K features.
        val f = YouTubeWeb.searchParams(YouTubeWeb.SearchFilters(features = setOf(YouTubeWeb.Feature.LIVE, YouTubeWeb.Feature.K4)))
        assertEquals(listOf(0x12, 0x04, 0x40, 0x01, 0x70, 0x01), decode(f))
        assertTrue(YouTubeWeb.SearchFilters().isDefault)
    }

    @Test
    fun parsesChannelTabWithChipsShortsAndPosts() {
        val json = JSONObject(
            """
            {"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{"richGridRenderer":{
              "header":{"feedFilterChipBarRenderer":{"contents":[
                {"chipCloudChipRenderer":{"text":{"simpleText":"Latest"},"isSelected":true}},
                {"chipCloudChipRenderer":{"text":{"simpleText":"Popular"},"navigationEndpoint":{"continuationCommand":{"token":"POP"}}}}]}},
              "contents":[
                {"richItemRenderer":{"content":{"shortsLockupViewModel":{"entityId":"shorts-shelf-item-abcdefghijk",
                  "onTap":{"innertubeCommand":{"reelWatchEndpoint":{"videoId":"abcdefghijk"}}},
                  "overlayMetadata":{"primaryText":{"content":"A short"},"secondaryText":{"content":"1M views"}},
                  "thumbnail":{"sources":[{"url":"https://i/s.jpg"}]}}}}},
                {"richItemRenderer":{"content":{"backstagePostThreadRenderer":{"post":{"backstagePostRenderer":{"postId":"Ugx1",
                  "authorText":{"runs":[{"text":"Chan"}]},"contentText":{"runs":[{"text":"Hello "},{"text":"fans"}]},
                  "publishedTimeText":{"runs":[{"text":"2 days ago"}]},"voteCount":{"simpleText":"1.2K"},
                  "backstageAttachment":{"pollRenderer":{"choices":[{"text":{"runs":[{"text":"Yes"}]}},{"text":{"runs":[{"text":"No"}]}}]}}}}}}}},
                {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"MORE"}}}}
              ]}}}}]}}}
            """
        )
        val tab = YouTubeWeb.parseTab(json)
        assertEquals(listOf("Popular" to "POP"), tab.chips)
        assertEquals("abcdefghijk", tab.shorts.single().id)
        assertEquals("A short", tab.shorts.single().title)
        val post = tab.posts.single()
        assertEquals("Hello fans", post.text)
        assertEquals(listOf("Yes", "No"), post.pollChoices)
        assertEquals("MORE", tab.continuation)
    }

    @Test
    fun parsesReelSequenceAndOverlay() {
        val seq = YouTubeWeb.parseReelSequence(JSONObject(
            """{"entries":[{"command":{"reelWatchEndpoint":{"videoId":"aaaaaaaaaaa"}}},{"command":{"reelWatchEndpoint":{"videoId":"bbbbbbbbbbb"}}}],
               "continuationEndpoint":{"continuationCommand":{"token":"NEXT"}}}"""
        ))
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), seq.first)
        assertEquals("NEXT", seq.second)
        val info = YouTubeWeb.parseReelInfo("aaaaaaaaaaa", JSONObject(
            """{"overlay":{"reelPlayerOverlayRenderer":{"metapanel":{"reelMetapanelViewModel":{"metadataItems":[
               {"reelChannelBarViewModel":{"channelName":{"content":"@maker"},"onTap":{"innertubeCommand":{"browseEndpoint":{"browseId":"UCaaaaaaaaaaaaaaaaaaaaaa"}}}}},
               {"shortsVideoTitleViewModel":{"text":{"content":"Cool trick"}}}]}}}}}"""
        ))!!
        assertEquals("Cool trick", info.title)
        assertEquals("@maker", info.artistOrChannel)
        assertEquals("https://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaaaa", info.channelUrl)
    }

    @Test
    fun parsesHomeChips() {
        val feed = YouTubeWeb.feed(JSONObject(
            """{"header":{"feedTabbedHeaderRenderer":{}},"contents":{"chips":[
               {"chipCloudChipRenderer":{"text":{"runs":[{"text":"All"}]}}},
               {"chipCloudChipRenderer":{"text":{"runs":[{"text":"Music"}]},"navigationEndpoint":{"continuationCommand":{"token":"MUSIC"}}}}]}}"""
        ))
        assertEquals(listOf("All" to "", "Music" to "MUSIC"), feed.chips)
    }
}
