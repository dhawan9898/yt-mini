package com.nexusstream.app

import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reads video ids out of YouTube links. Pure logic. */
object DeepLinks {
    private val ID = "[\\w-]{11}"
    private val PATTERNS = listOf(
        Regex("""(?:youtube\.com|music\.youtube\.com)/watch\?(?:[^#\s]*&)?v=($ID)"""),
        Regex("""youtu\.be/($ID)"""),
        Regex("""youtube\.com/(?:shorts|embed|live|v)/($ID)""")
    )

    /** The 11-character video id in [text] (a URL or text containing one), or null. */
    fun videoIdFrom(text: String): String? =
        PATTERNS.firstNotNullOfOrNull { it.find(text)?.groupValues?.get(1) }
}

/** A request to open a video, set by the activity (notification tap, shared / opened link). */
data class VideoLaunch(val videoId: String, val title: String = "", val channel: String = "")

/** Bridge from intents to the Compose UI. */
object LaunchRequests {
    private val _video = MutableStateFlow<VideoLaunch?>(null)
    val video: StateFlow<VideoLaunch?> = _video.asStateFlow()

    fun requestVideo(launch: VideoLaunch) {
        _video.value = launch
    }

    fun consumeVideo() {
        _video.value = null
    }

    /** A page to open from an app shortcut or widget ("search", "subscriptions", "shorts", "music"). */
    private val _destination = MutableStateFlow<String?>(null)
    val destination: StateFlow<String?> = _destination.asStateFlow()

    fun requestDestination(destination: String) {
        _destination.value = destination
    }

    fun consumeDestination() {
        _destination.value = null
    }

    /** The YouTube home tab to show next (0 Home, 1 Shorts, 2 Subscriptions, 3 You). */
    val homeTab = MutableStateFlow<Int?>(null)

    /** A bare-bones item for a video we only know the id (and maybe title) of. */
    fun minimalItem(launch: VideoLaunch): MediaItemModel = MediaItemModel(
        id = launch.videoId,
        title = launch.title.ifBlank { "YouTube video" },
        artistOrChannel = launch.channel,
        audioUrl = "",
        videoUrl = "",
        artworkUrl = "https://i.ytimg.com/vi/${launch.videoId}/hqdefault.jpg",
        durationMs = 0L,
        sourceUrl = "https://www.youtube.com/watch?v=${launch.videoId}"
    )
}
