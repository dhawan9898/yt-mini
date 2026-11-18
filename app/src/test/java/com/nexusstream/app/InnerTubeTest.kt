package com.nexusstream.app

import com.nexusstream.app.data.InnerTube
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InnerTubeTest {
    @Test
    fun sapisidHashMatchesWebsiteFormat() {
        // sha1("1700000000 abc https://www.youtube.com")
        val header = InnerTube.sapisidHash("X=1; SAPISID=abc; Y=2", nowSeconds = 1_700_000_000)!!
        assertEquals(true, header.startsWith("SAPISIDHASH 1700000000_"))
        assertEquals(40, header.substringAfter('_').length)
        assertNull(InnerTube.sapisidHash("X=1"))
    }

    @Test
    fun parsesVideosAndContinuation() {
        val json = JSONObject(
            """{"contents":[{"richItemRenderer":{"content":{"videoRenderer":{
              "videoId":"abcdefghijk","title":{"runs":[{"text":"Hello"}]},
              "ownerText":{"runs":[{"text":"Chan"}]},"lengthText":{"simpleText":"12:34"},
              "shortViewCountText":{"simpleText":"1M views"},"publishedTimeText":{"simpleText":"1 day ago"},
              "thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/a.jpg"}]}}}}},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"TOK"}}}}]}"""
        )
        val page = InnerTube.parse(json)
        assertEquals(1, page.items.size)
        assertEquals("Hello", page.items[0].title)
        assertEquals(754_000L, page.items[0].durationMs)
        assertEquals("TOK", page.continuation)
    }

    /** A lockup as YouTube's Home and "Up next" send them (trimmed from a real answer). */
    private fun lockup(id: String, duration: String?, channelId: String = "UC1nMQUe6G6tm5Q35Y7Un73g", views: String = "9.4M") = """
        {"lockupViewModel":{"contentId":"$id","contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
          "contentImage":{"thumbnailViewModel":{"image":{"sources":[{"url":"https://i.ytimg.com/vi/$id/hqdefault.jpg"}]},
            "overlays":[{"thumbnailBottomOverlayViewModel":{"badges":[
              ${if (duration != null) """{"thumbnailBadgeViewModel":{"icon":{"sources":[]},"text":"$duration"}}""" else ""}]}}]}},
          "metadata":{"lockupMetadataViewModel":{"title":{"content":"Title $id"},
            "image":{"decoratedAvatarViewModel":{"avatar":{"avatarViewModel":{"image":{"sources":[{"url":"https://yt3.ggpht.com/av=s68"}]}}},
              "rendererContext":{"commandContext":{"onTap":{"innertubeCommand":{"browseEndpoint":{"browseId":"$channelId","canonicalBaseUrl":"/@films"}}}}}}},
            "metadata":{"contentMetadataViewModel":{"metadataRows":[
              {"metadataParts":[{"text":{"content":"Sthenics Films"}}]},
              {"metadataParts":[{"text":{"content":"$views"}},{"text":{"content":"2 weeks ago"}}]}]}}}}}}
    """

    @Test
    fun lockupCarriesChannelPictureLinkAndDuration() {
        val item = InnerTube.parse(JSONObject("""{"items":[${lockup("RFahjn6vfYY", "11:13")}]}""")).items.single()
        assertEquals("https://yt3.ggpht.com/av=s68", item.channelAvatarUrl)
        assertEquals("https://www.youtube.com/channel/UC1nMQUe6G6tm5Q35Y7Un73g", item.channelUrl)
        assertEquals(673_000L, item.durationMs)
        assertEquals("Sthenics Films", item.artistOrChannel)
        assertEquals("9.4M views", item.viewsOrPlays)
        assertEquals("2 weeks ago", item.publishDate)
    }

    @Test
    fun lockupShortsAreLeftOutAndLiveIsMarked() {
        val page = InnerTube.parse(JSONObject("""{"items":[${lockup("aaaaaaaaaaa", "0:45")},${lockup("bbbbbbbbbbb", "LIVE")}]}"""))
        assertEquals(listOf("bbbbbbbbbbb"), page.items.map { it.id })
        assertEquals(true, page.items[0].isLive)
    }

    @Test
    fun watchNextReadsOnlyTheSuggestionColumn() {
        val json = JSONObject(
            """{"contents":{"twoColumnWatchNextResults":{
                 "results":{"results":{"contents":[${lockup("ccccccccccc", "3:00")}]}},
                 "secondaryResults":{"secondaryResults":{"results":[
                   ${lockup("dQw4w9WgXcQ", "3:33")},${lockup("RFahjn6vfYY", "11:13")},
                   {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"NEXT"}}}}]}}}}}"""
        )
        val page = InnerTube.parseWatchNext(json, "dQw4w9WgXcQ")
        assertEquals(listOf("RFahjn6vfYY"), page.items.map { it.id })
        assertEquals("NEXT", page.continuation)
    }
}
