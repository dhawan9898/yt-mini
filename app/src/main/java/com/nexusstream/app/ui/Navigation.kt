package com.nexusstream.app.ui

import com.nexusstream.app.model.MediaItemModel

/** Pages that open on top of the YouTube home screen (a back stack of these is kept in [NexusApp]). */
sealed interface Route {
    data object Search : Route
    data class Channel(val url: String, val name: String, val avatarUrl: String, val music: Boolean = false) : Route
    data class Playlist(val url: String, val title: String, val music: Boolean = false) : Route
    data object AllSubscriptions : Route
    data object History : Route
    data object WatchLater : Route
    data object Liked : Route
    data object Downloads : Route
    data object Playlists : Route
    data class LocalPlaylist(val id: String) : Route
    data object Notifications : Route
    data object NotificationSettings : Route
    data object Settings : Route
    data object SignIn : Route
    data object Upload : Route
    data object TimeWatched : Route
    /** An account page YouTube serves by browse id (Your clips "FEclips", Your movies "FEstorefront"). */
    data class AccountFeed(val browseId: String, val title: String, val emptyMessage: String) : Route
    /**
     * A YT Music page (artist "UC…", album "MPREb…", playlist "VL…", Explore, charts, moods...).
     * [fallback] is the YouTube-backed page shown if YT Music can't load it.
     */
    data class MusicBrowse(val browseId: String, val title: String, val params: String = "", val fallback: Route? = null) : Route
}

/** Navigation actions handed to YouTube screens. */
class YouTubeNav(
    val openSearch: () -> Unit,
    val openChannel: (url: String, name: String, avatarUrl: String) -> Unit,
    val openPlaylist: (url: String, title: String) -> Unit,
    val openAllSubscriptions: () -> Unit,
    /** YT Music: an artist page / an album or playlist page whose tracks play as audio. */
    val openMusicChannel: (url: String, name: String, avatarUrl: String) -> Unit,
    val openMusicPlaylist: (url: String, title: String) -> Unit,
    /** Opens any other page (library lists, downloads, local playlists). */
    val open: (Route) -> Unit,
    val back: () -> Unit
) {
    fun openMusicPage(browseId: String, title: String, params: String = "") = open(Route.MusicBrowse(browseId, title, params))

    /** Opens the channel an item belongs to; does nothing when its channel URL is unknown. */
    fun openChannelOf(item: MediaItemModel) {
        if (item.channelUrl.isNotBlank()) openChannel(item.channelUrl, item.artistOrChannel, item.channelAvatarUrl)
    }
}

/**
 * The YT Music page for a channel / playlist / album link, or null when the link has no id
 * YT Music can browse. Artists open as artist pages, albums as album pages.
 */
internal fun musicRouteFor(url: String, title: String): Route.MusicBrowse? {
    Regex("""music\.youtube\.com/browse/([\w-]+)""").find(url)?.let {
        return Route.MusicBrowse(it.groupValues[1], title)
    }
    Regex("""/channel/(UC[\w-]{22})""").find(url)?.let {
        return Route.MusicBrowse(it.groupValues[1], title)
    }
    Regex("""[?&]list=([\w-]+)""").find(url)?.let {
        val id = it.groupValues[1]
        // Radio / mix lists ("RD…") have no playlist page on YT Music other than the mix itself.
        return Route.MusicBrowse("VL$id", title)
    }
    return null
}

/** The YouTube playlist page for a link that names a playlist ("list=…"), used when YT Music can't open it. */
internal fun playlistFallbackFor(url: String, title: String): Route.Playlist? =
    Regex("""[?&]list=([\w-]+)""").find(url)?.let {
        Route.Playlist("https://www.youtube.com/playlist?list=${it.groupValues[1]}", title, music = true)
    }
