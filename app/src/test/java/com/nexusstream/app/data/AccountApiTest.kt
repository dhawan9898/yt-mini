package com.nexusstream.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountApiTest {
    @Test
    fun bellParamsMatchYouTubeJs() {
        // channel_id=1 "UCabc", pref_id=2 {index=2 (ALL)}, number_0=3 0, number_1=4 4
        assertEquals("CgVVQ2FiYxICCAIYACAE", AccountApi.bellParams("UCabc", NotifyLevel.ALL))
    }

    @Test
    fun protoVarintsSpanBytes() {
        val bytes = Proto().int(1, 300).toByteArray().map { it.toInt() and 0xFF }
        assertEquals(listOf(0x08, 0xAC, 0x02), bytes)
    }

    @Test
    fun addToPlaylistOptions() {
        val root = JSONObject(
            """{"contents":[{"addToPlaylistRenderer":{"playlists":[
              {"playlistAddToOptionRenderer":{"playlistId":"WL","title":{"simpleText":"Watch later"},
                "containsSelectedVideos":"NONE","privacy":"PRIVATE",
                "addToPlaylistServiceEndpoint":{"playlistEditEndpoint":{"playlistId":"WL","actions":[{"action":"ACTION_ADD_VIDEO","addedVideoId":"abcdefghijk"}]}}}},
              {"playlistAddToOptionRenderer":{"playlistId":"PLx","title":{"runs":[{"text":"Road trip"}]},
                "containsSelectedVideos":"ALL","privacy":"PUBLIC"}}
            ]}}]}"""
        )
        val list = AccountApi.parseAddToPlaylist(root)
        assertEquals(listOf("WL", "PLx"), list.map { it.id })
        assertEquals("Road trip", list[1].title)
        assertFalse(list[0].containsVideo)
        assertTrue(list[1].containsVideo)
        assertEquals("ACTION_ADD_VIDEO", list[0].addEndpoint!!.getJSONArray("actions").getJSONObject(0).getString("action"))
        assertNull(list[1].removeEndpoint)
    }

    @Test
    fun playlistPageEntriesKeepSetVideoId() {
        val root = JSONObject(
            """{"contents":{"playlistVideoListRenderer":{"contents":[
              {"playlistVideoRenderer":{"videoId":"abcdefghijk","setVideoId":"S1","title":{"runs":[{"text":"First"}]},
                "shortBylineText":{"runs":[{"text":"Chan","navigationEndpoint":{"browseEndpoint":{"browseId":"UC1","canonicalBaseUrl":"/@chan"}}}]},
                "lengthSeconds":"45","lengthText":{"simpleText":"0:45"},
                "videoInfo":{"runs":[{"text":"1.2M views"},{"text":" • "},{"text":"3 years ago"}]}}},
              {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"NEXT"}}}}
            ]}}}"""
        )
        val (entries, next) = AccountApi.parsePlaylistPage(root)
        assertEquals("NEXT", next)
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals("S1", e.setVideoId)
        assertEquals("First", e.item.title)
        assertEquals("Chan", e.item.artistOrChannel)
        assertEquals("https://www.youtube.com/@chan", e.item.channelUrl)
        assertEquals(45_000L, e.item.durationMs)
        assertEquals("1.2M views", e.item.viewsOrPlays)
        assertEquals("3 years ago", e.item.publishDate)
    }

    @Test
    fun parsesNotificationMenu() {
        val json = JSONObject(
            """
            {"actions":[{"openPopupAction":{"popup":{"multiPageMenuRenderer":{"sections":[{"multiPageMenuNotificationSectionRenderer":{"items":[
              {"notificationRenderer":{"notificationId":"n1","shortMessage":{"simpleText":"Channel uploaded: New video"},
                "sentTimeText":{"simpleText":"3 hours ago"},"thumbnail":{"thumbnails":[{"url":"https://a/1.jpg"}]},
                "videoThumbnail":{"thumbnails":[{"url":"https://v/1.jpg"}]},
                "navigationEndpoint":{"watchEndpoint":{"videoId":"vid123"}},"read":false}},
              {"continuationItemRenderer":{"continuationEndpoint":{"getNotificationMenuEndpoint":{"ctoken":"MORE"}}}}
            ]}}]}}}}]}
            """
        )
        val page = AccountApi.parseNotifications(json)
        val n = page.items.single()
        assertEquals("vid123", n.videoId)
        assertEquals("Channel uploaded: New video", n.message)
        assertEquals("3 hours ago", n.time)
        assertEquals("https://v/1.jpg", n.thumbnailUrl)
        assertFalse(n.read)
        assertEquals("MORE", page.continuation)
    }

    @Test
    fun parsesChannelSwitcher() {
        val json = JSONObject(
            """
            {"actions":[{"getMultiPageMenuAction":{"menu":{"multiPageMenuRenderer":{"sections":[
              {"accountSectionListRenderer":{"header":{"googleAccountHeaderRenderer":{"email":{"simpleText":"me@example.com"}}},
                "contents":[{"accountItemSectionRenderer":{"contents":[
                  {"accountItem":{"accountName":{"simpleText":"Me"},"channelHandle":{"simpleText":"@me"},"isSelected":true,
                    "accountPhoto":{"thumbnails":[{"url":"https://p/me.jpg"}]},
                    "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[{"accountStateToken":{}}]}}}},
                  {"accountItem":{"accountName":{"simpleText":"My Brand"},"channelHandle":{"simpleText":"@brand"},"isSelected":false,
                    "accountPhoto":{"thumbnails":[{"url":"https://p/b.jpg"}]},
                    "serviceEndpoint":{"selectActiveIdentityEndpoint":{"supportedTokens":[{"pageIdToken":{"pageId":"1234"}}]}}}}
                ]}}]}}
            ]}}}}]}
            """
        )
        val list = AccountApi.parseAccounts(json)
        assertEquals(2, list.size)
        assertTrue(list[0].selected)
        assertEquals(GoogleAccount.Identity(0, ""), list[0].identity)
        assertEquals("My Brand", list[1].name)
        assertEquals(GoogleAccount.Identity(0, "1234"), list[1].identity)
        assertEquals("me@example.com", list[1].group)
    }
}
