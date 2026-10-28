package com.nexusstream.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeAndChaptersTest {
    @Test
    fun keepsPositionPartWay() {
        val all = WatchPositions.put(JSONObject(), "v1", 600_000, 1_800_000, 1)
        assertEquals(600_000L, all.getJSONObject("v1").getLong("p"))
    }

    @Test
    fun forgetsBarelyStartedOrFinished() {
        val all = WatchPositions.put(JSONObject(), "v1", 600_000, 1_800_000, 1)
        WatchPositions.put(all, "v1", 1_795_000, 1_800_000, 2)
        assertFalse(all.has("v1"))
        WatchPositions.put(all, "v2", 2_000, 1_800_000, 3)
        assertFalse(all.has("v2"))
    }

    @Test
    fun chaptersFromDescriptionTimestamps() {
        val chapters = chaptersFromDescription(
            "Great video<br>0:00 Intro<br>1:30 - Setup<br>(12:05) The build<br>1:02:00 Outro",
            durationMs = 4_000_000
        )
        assertEquals(listOf("Intro", "Setup", "The build", "Outro"), chapters.map { it.title })
        assertEquals(listOf(0L, 90_000L, 725_000L, 3_720_000L), chapters.map { it.startMs })
    }

    @Test
    fun noChaptersWithoutZeroStartOrTooFew() {
        assertTrue(chaptersFromDescription("1:00 a\n2:00 b\n3:00 c").isEmpty())
        assertTrue(chaptersFromDescription("0:00 a\n2:00 b").isEmpty())
    }
}
