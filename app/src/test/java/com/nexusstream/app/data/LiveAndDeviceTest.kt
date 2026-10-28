package com.nexusstream.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LiveAndDeviceTest {
    @Test
    fun liveChatStartPrefersFullChat() {
        val next = JSONObject(
            """{"contents":{"x":{"conversationBar":{"liveChatRenderer":{
              "isReplay":true,
              "continuations":[{"reloadContinuationData":{"continuation":"TOP"}}],
              "header":{"liveChatHeaderRenderer":{"viewSelector":{"sortFilterSubMenuRenderer":{"subMenuItems":[
                {"continuation":{"reloadContinuationData":{"continuation":"TOP"}}},
                {"continuation":{"reloadContinuationData":{"continuation":"ALL"}}}]}}}}}}}}}"""
        )
        val start = LiveChat.parseStart(next)!!
        assertEquals("ALL", start.continuation)
        assertTrue(start.replay)
        assertEquals("TOP", start.topChat)
    }

    @Test
    fun liveChatParsesMessagesAndReplayOffsets() {
        val root = JSONObject(
            """{"continuationContents":{"liveChatContinuation":{
              "continuations":[{"liveChatReplayContinuationData":{"continuation":"NEXT","timeoutMs":2000}}],
              "actions":[
                {"replayChatItemAction":{"videoOffsetTimeMsec":"65000","actions":[
                  {"addChatItemAction":{"item":{"liveChatTextMessageRenderer":{"id":"m1",
                    "authorName":{"simpleText":"Ana"},"message":{"runs":[{"text":"hi "},{"text":"there"}]},
                    "authorBadges":[{"liveChatAuthorBadgeRenderer":{"icon":{"iconType":"MODERATOR"}}}]}}}}]}},
                {"addChatItemAction":{"item":{"liveChatPaidMessageRenderer":{"id":"m2",
                  "authorName":{"simpleText":"Bo"},"purchaseAmountText":{"simpleText":"$5.00"},
                  "message":{"runs":[{"text":"thanks"}]}}}}}
              ]}}}"""
        )
        val page = LiveChat.parse(root)
        assertEquals(2, page.messages.size)
        assertEquals("hi there", page.messages[0].text)
        assertEquals(65000L, page.messages[0].offsetMs)
        assertTrue(page.messages[0].isModerator)
        assertEquals("$5.00", page.messages[1].amount)
        assertEquals("NEXT", page.continuation)
        assertEquals(2000L, page.pollAfterMs)
    }

    @Test
    fun liveChatWithoutContinuationWaitsLonger() {
        val page = LiveChat.parse(JSONObject("{}"))
        assertTrue(page.messages.isEmpty())
        assertNull(page.continuation)
        assertEquals(5000L, page.pollAfterMs)
    }

    @Test
    fun tvBindAnswerGivesSessionIds() {
        val text = "123\n[[0,[\"c\",\"SID123\",\"\",8]],[1,[\"S\",\"GS456\"]],[2,[\"loungeStatus\",{\"devices\":\"[]\"}]]]\n45\n[[3,[\"noop\"]]]"
        val (sid, gsession) = TvLink.parseBind(text)
        assertEquals("SID123", sid)
        assertEquals("GS456", gsession)
    }

    @Test
    fun tvBindWithoutJsonGivesNothing() {
        val (sid, gsession) = TvLink.parseBind("Unknown SID")
        assertNull(sid)
        assertNull(gsession)
    }

    @Test
    fun timedLyricsParse() {
        val root = JSONObject(
            """{"contents":{"elementRenderer":{"newElement":{"type":{"componentType":{"model":{"timedLyricsModel":{"lyricsData":{
              "sourceMessage":"Source: LyricFind",
              "timedLyricsData":[
                {"lyricLine":"First","cueRange":{"startTimeMilliseconds":"1000","endTimeMilliseconds":"2500"}},
                {"lyricLine":"Second","cueRange":{"startTimeMilliseconds":"2500","endTimeMilliseconds":"4000"}}]}}}}}}}}}"""
        )
        val (lines, source) = YTMusicApi.parseTimedLyrics(root)!!
        assertEquals(2, lines.size)
        assertEquals(1000L, lines[0].startTimeMs)
        assertEquals("Second", lines[1].text)
        assertEquals("Source: LyricFind", source)
        assertNull(YTMusicApi.parseTimedLyrics(JSONObject("{}")))
    }

    @Test
    fun watchTimeAddsPerDayAndDropsOldDays() {
        val today = LocalDate.of(2026, 10, 28)
        var totals = WatchTime.add(null, today, 60_000)
        totals = WatchTime.add(totals.toString(), today, 30_000)
        assertEquals(90_000L, totals.getLong("2026-10-28"))
        val withOld = JSONObject().put("2026-01-01", 5L).toString()
        assertFalse(WatchTime.add(withOld, today, 1000).has("2026-01-01"))
    }

    @Test
    fun uploadCreateBodyCarriesMetadata() {
        val body = VideoUpload.createBody("scotty", "front", "", "desc", VideoUpload.Privacy.UNLISTED)
        assertEquals("scotty", body.getJSONObject("resourceId").getJSONObject("scottyResourceId").getString("id"))
        val meta = body.getJSONObject("initialMetadata")
        assertEquals("Untitled video", meta.getJSONObject("title").getString("newTitle"))
        assertEquals("UNLISTED", meta.getJSONObject("privacy").getString("newPrivacy"))
        assertEquals("abc", VideoUpload.parseVideoId(JSONObject("""{"contents":{"videoId":"abc"}}""")))
        assertNull(VideoUpload.parseVideoId(JSONObject("{}")))
    }

    @Test
    fun shortsMixSpreadsChannelShorts() {
        val mixed = YouTubeRepository.mixIn((1..10).map { "r$it" }, listOf("a", "b", "c"), every = 5)
        assertEquals(listOf("r1", "r2", "r3", "r4", "r5", "a", "r6", "r7", "r8", "r9", "r10", "b", "c"), mixed)
        assertEquals(listOf("r1"), YouTubeRepository.mixIn(listOf("r1"), emptyList(), every = 5))
    }
}
