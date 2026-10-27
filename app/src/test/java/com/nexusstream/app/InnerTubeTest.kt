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
}
