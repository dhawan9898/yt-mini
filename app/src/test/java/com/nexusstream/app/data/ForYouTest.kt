package com.nexusstream.app.data

import com.nexusstream.app.model.MediaItemModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ForYouTest {
    private fun v(id: String, channelUrl: String = "") = MediaItemModel(
        id = id, title = id, artistOrChannel = "c", audioUrl = "", videoUrl = "", artworkUrl = "", durationMs = 1000, channelUrl = channelUrl
    )

    @Test fun subscribedAndLikedChannelsOutrankOneOffWatches() {
        val out = ForYou.channelsToFollow(
            subscribed = listOf("sub"),
            history = listOf(v("1", "once"), v("2", "often"), v("3", "often"), v("4", "often")),
            liked = listOf(v("5", "liked"))
        )
        assertEquals(listOf("sub", "liked", "often", "once"), out)
    }

    @Test fun unknownChannelsAreSkippedAndLimitApplies() {
        val history = (1..20).map { v("h$it", "ch$it") } + v("x", "")
        assertEquals(8, ForYou.channelsToFollow(emptyList(), history, emptyList()).size)
        assertEquals(emptyList<String>(), ForYou.channelsToFollow(emptyList(), listOf(v("x", "")), emptyList()))
    }

    @Test fun blendInterleavesChannelsAndFoldsInTheChart() {
        val personal = listOf(listOf(v("a1"), v("a2")), listOf(v("b1"), v("b2")))
        val chart = listOf(v("c1"), v("c2"), v("c3"))
        val ids = ForYou.blend(personal, chart, seen = emptySet()).map { it.id }
        assertEquals(listOf("a1", "b1", "c1", "a2", "b2", "c2", "c3"), ids)
    }

    @Test fun watchedVideosAndDuplicatesAreDropped() {
        val personal = listOf(listOf(v("a1"), v("a2")))
        val chart = listOf(v("a2"), v("c1"), v("seen"))
        val ids = ForYou.blend(personal, chart, seen = setOf("a1", "seen")).map { it.id }
        assertEquals(listOf("a2", "c1"), ids)
    }
}
