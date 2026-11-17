package com.nexusstream.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Channel pictures by channel, shared by every screen. Some feeds (the official chart, related
 * videos, links opened from outside) list a video without its channel's picture; the first card
 * or watch page that needs it asks once, and every other card of that channel gets it too.
 */
object ChannelAvatars {
    private val _known = MutableStateFlow<Map<String, String>>(emptyMap())
    val known: StateFlow<Map<String, String>> = _known.asStateFlow()

    /** Channels being looked up, or looked up without success (not asked again this session). */
    private val asked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(3))

    private val CHANNEL_ID = Regex("""channel/(UC[\w-]{22})""")

    /** The same channel written as /channel/UC..., with or without "www." or "m.", gives one key. */
    internal fun key(channelUrl: String): String? {
        if (channelUrl.isBlank()) return null
        CHANNEL_ID.find(channelUrl)?.let { return it.groupValues[1] }
        return channelUrl.substringAfter("youtube.com/", channelUrl).trimEnd('/').lowercase().ifEmpty { null }
    }

    fun get(channelUrl: String): String? = key(channelUrl)?.let { _known.value[it] }

    /** Remembers a picture a feed already gave for [channelUrl]. */
    fun record(channelUrl: String, avatarUrl: String) {
        val k = key(channelUrl) ?: return
        if (avatarUrl.isEmpty() || _known.value[k] == avatarUrl) return
        _known.update { it + (k to avatarUrl) }
    }

    /** Looks the picture up in the background unless it is known or was already asked for. */
    fun request(channelUrl: String) {
        val k = key(channelUrl) ?: return
        if (_known.value.containsKey(k) || !asked.add(k)) return
        scope.launch {
            YouTubeRepository.channelAvatar(channelUrl).getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { record(channelUrl, it) }
        }
    }
}
