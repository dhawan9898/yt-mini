package com.nexusstream.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchExtrasTest {
    @Test
    fun parsesEndScreenTiles() {
        val json = JSONObject(
            """
            {"endscreen":{"endscreenRenderer":{"elements":[
              {"endscreenElementRenderer":{"style":"VIDEO","title":{"simpleText":"Next one"},"image":{"thumbnails":[{"url":"https://i/1.jpg"}]},
                "left":0.05,"top":0.1,"width":0.4,"aspectRatio":1.7778,"startMs":"100000","endMs":"120000",
                "endpoint":{"watchEndpoint":{"videoId":"abc"}}}},
              {"endscreenElementRenderer":{"style":"WEBSITE","title":{"simpleText":"Shop"},"left":0.5,"top":0.1,"width":0.2,"aspectRatio":1,
                "startMs":"100000","endMs":"120000","endpoint":{"urlEndpoint":{"url":"https://www.youtube.com/redirect?q=https%3A%2F%2Fexample.com%2F"}}}}
            ]}}}
            """
        )
        val tiles = WatchExtras.parseEndScreen(json)
        assertEquals(2, tiles.size)
        assertEquals("abc", tiles[0].videoId)
        assertEquals(100_000L, tiles[0].startMs)
        assertEquals(0.4f, tiles[0].width, 0.001f)
        assertEquals("https://example.com/", tiles[1].url)
    }

    @Test
    fun parsesHeatmapAndFindsJumpAhead() {
        val json = JSONObject(
            """
            {"frameworkUpdates":{"entityBatchUpdate":{"mutations":[{"payload":{"macroMarkersListEntity":{"markersList":{"markerType":"MARKER_TYPE_HEATMAP","markers":[
              {"startMillis":"0","durationMillis":"10000","intensityScoreNormalized":1},
              {"startMillis":"10000","durationMillis":"10000","intensityScoreNormalized":0.2},
              {"startMillis":"20000","durationMillis":"10000","intensityScoreNormalized":0.3},
              {"startMillis":"30000","durationMillis":"10000","intensityScoreNormalized":0.9},
              {"startMillis":"40000","durationMillis":"10000","intensityScoreNormalized":0.4}
            ]}}}}]}}}
            """
        )
        val heat = WatchExtras.parseHeatmap(json)
        assertEquals(5, heat.size)
        assertEquals(30_000L, WatchExtras.jumpAheadTarget(heat, 5_000))
        assertNull(WatchExtras.jumpAheadTarget(heat, 35_000))
    }

    @Test
    fun parsesJson3Transcript() {
        val json = JSONObject("""{"events":[{"tStartMs":0,"dDurationMs":2000,"segs":[{"utf8":"Hello"},{"utf8":" world"}]},{"tStartMs":2000,"segs":[{"utf8":"\n"}]},{"tStartMs":2500,"dDurationMs":1000,"segs":[{"utf8":"Bye"}]}]}""")
        val lines = WatchExtras.parseTranscript(json)
        assertEquals(listOf("Hello world", "Bye"), lines.map { it.text })
        assertEquals(2500L, lines[1].startMs)
    }

    @Test
    fun translatedUrlAddsTlang() {
        assertTrue(WatchExtras.translatedUrl("https://www.youtube.com/api/timedtext?v=x&lang=en&fmt=vtt", "es").contains("tlang=es"))
    }
}
