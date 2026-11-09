package com.nexusstream.app.data

import com.nexusstream.app.model.MediaItemModel

/**
 * Pure ranking for the personal part of Home: which channels to pull from, and how their newest
 * videos are mixed with the general chart. Kept free of Android and network so it can be tested.
 */
internal object ForYou {

    /** Weight of one watched video and one liked video when scoring a channel. */
    private const val WATCHED = 1
    private const val LIKED = 3
    private const val SUBSCRIBED = 4

    /**
     * Channel urls to draw from, best first: subscribed channels, channels you like, channels you
     * watch most. Blank urls (unknown channel) are skipped.
     */
    fun channelsToFollow(
        subscribed: List<String>,
        history: List<MediaItemModel>,
        liked: List<MediaItemModel>,
        limit: Int = 8
    ): List<String> {
        val score = LinkedHashMap<String, Int>()
        fun add(url: String, points: Int) {
            if (url.isNotBlank()) score[url] = (score[url] ?: 0) + points
        }
        subscribed.forEach { add(it, SUBSCRIBED) }
        liked.forEach { add(it.channelUrl, LIKED) }
        history.forEach { add(it.channelUrl, WATCHED) }
        return score.entries.sortedByDescending { it.value }.map { it.key }.take(limit)
    }

    /**
     * [personal] (one list per channel, best channel first) is interleaved round-robin, then the
     * [chart] is folded in after every [every] personal videos. Already watched videos are dropped.
     */
    fun blend(personal: List<List<MediaItemModel>>, chart: List<MediaItemModel>, seen: Set<String>, every: Int = 2): List<MediaItemModel> {
        val lists = personal.map { list -> list.filter { it.id !in seen } }
        val rounds = lists.maxOfOrNull { it.size } ?: 0
        val mine = ArrayList<MediaItemModel>()
        for (i in 0 until rounds) lists.forEach { l -> l.getOrNull(i)?.let { mine += it } }
        val rest = chart.filter { it.id !in seen }.iterator()
        val out = ArrayList<MediaItemModel>(mine.size + chart.size)
        mine.forEachIndexed { i, item ->
            out += item
            if ((i + 1) % every == 0 && rest.hasNext()) out += rest.next()
        }
        while (rest.hasNext()) out += rest.next()
        return out.distinctBy { it.id }
    }
}
