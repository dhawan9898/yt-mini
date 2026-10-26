package com.nexusstream.app.ui

import com.nexusstream.app.model.MediaItemModel

/** Pages that open on top of the YouTube home screen (a back stack of these is kept in [NexusApp]). */
sealed interface Route {
    data object Search : Route
    data class Channel(val url: String, val name: String, val avatarUrl: String) : Route
    data class Playlist(val url: String, val title: String) : Route
    data object AllSubscriptions : Route
}

/** Navigation actions handed to YouTube screens. */
class YouTubeNav(
    val openSearch: () -> Unit,
    val openChannel: (url: String, name: String, avatarUrl: String) -> Unit,
    val openPlaylist: (url: String, title: String) -> Unit,
    val openAllSubscriptions: () -> Unit,
    val back: () -> Unit
) {
    /** Opens the channel an item belongs to; does nothing when its channel URL is unknown. */
    fun openChannelOf(item: MediaItemModel) {
        if (item.channelUrl.isNotBlank()) openChannel(item.channelUrl, item.artistOrChannel, item.channelAvatarUrl)
    }
}
