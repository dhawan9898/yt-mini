package com.nexusstream.app.data

import com.nexusstream.app.model.MediaItemModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * YT Music's own browse pages (WEB_REMIX): Home with its mood chips, Explore, New releases,
 * Charts, Moods & genres, artist, album, playlist and podcast pages, the account library and
 * Recap. Every page is a header plus shelves, parsed by [YTMusicApi.parseShelves].
 */
object YTMusicPages {
    const val HOME = "FEmusic_home"
    const val EXPLORE = "FEmusic_explore"
    const val NEW_RELEASES = "FEmusic_new_releases"
    const val CHARTS = "FEmusic_charts"
    const val MOODS = "FEmusic_moods_and_genres"
    const val PODCASTS = "FEmusic_podcasts"
    const val RECAP = "FEmusic_listening_review"
    const val HISTORY = "FEmusic_history"

    /** Library tabs, as the app's Library chips show them. */
    enum class LibraryTab(val label: String, val browseId: String) {
        PLAYLISTS("Playlists", "FEmusic_liked_playlists"),
        SONGS("Songs", "FEmusic_liked_videos"),
        ALBUMS("Albums", "FEmusic_liked_albums"),
        ARTISTS("Artists", "FEmusic_library_corpus_track_artists"),
        SUBSCRIPTIONS("Subscriptions", "FEmusic_library_corpus_artists"),
        PODCASTS("Podcasts", "FEmusic_library_non_music_audio_list")
    }

    data class Chip(val title: String, val browseId: String, val params: String, val selected: Boolean)

    /** Page header: artist (round picture, subscribers), album or playlist (square cover). */
    data class Header(
        val title: String,
        val subtitle: String,
        val secondSubtitle: String,
        val description: String,
        val thumbnailUrl: String,
        val isArtist: Boolean,
        /** Artist channel id, for Subscribe. */
        val channelId: String = "",
        val subscriberText: String = "",
        val subscribed: Boolean = false,
        /** The playlist the Play / Shuffle buttons start ("OLAK5uy_…" for albums). */
        val playlistId: String = "",
        /** The artist's radio ("RDEM…"). */
        val radioPlaylistId: String = "",
        val shufflePlaylistId: String = "",
        /** Album artist, linked. */
        val artistName: String = "",
        val artistId: String = ""
    )

    data class Page(
        val header: Header?,
        val sections: List<YTMusicApi.Section>,
        val chips: List<Chip>,
        val continuation: String?
    ) {
        /** Every song on the page in order (album tracks, top songs...). */
        val songs: List<MediaItemModel>
            get() = sections.filter { it.isSongList }.flatMap { s -> s.cards.mapNotNull { it.track } }.distinctBy { it.id }
    }

    fun browse(browseId: String, params: String = ""): Page {
        val page = parsePage(YTMusicApi.post(YTMusicApi.BROWSE, JSONObject().put("browseId", browseId).apply {
            if (params.isNotEmpty()) put("params", params)
        }))
        return if (browseId.startsWith("MPRE")) albumized(page, browseId) else page
    }

    /**
     * Album track rows have no artwork or album of their own: give every track the album cover,
     * the album id / name (for the song menu) and the album artist when the row names none.
     */
    internal fun albumized(page: Page, albumId: String): Page {
        val header = page.header ?: return page
        val sections = page.sections.map { section ->
            if (!section.isSongList) return@map section
            section.copy(cards = section.cards.map { card ->
                val t = card.track ?: return@map card
                val artist = t.artistOrChannel.ifBlank { header.artistName }
                card.copy(
                    thumbnailUrl = header.thumbnailUrl.ifEmpty { card.thumbnailUrl },
                    subtitle = card.subtitle.ifBlank { artist },
                    track = t.copy(
                        artworkUrl = header.thumbnailUrl.ifEmpty { t.artworkUrl },
                        artistOrChannel = artist,
                        albumId = albumId,
                        albumName = header.title,
                        channelUrl = t.channelUrl.ifEmpty {
                            header.artistId.takeIf { it.startsWith("UC") }?.let(YTMusicApi::artistUrl).orEmpty()
                        }
                    )
                )
            })
        }
        return page.copy(sections = sections)
    }

    /** YT Music search result tabs (the same `params` ytmusicapi sends). */
    enum class SearchFilter(val label: String, val params: String) {
        SONGS("Songs", "EgWKAQIIAWoMEA4QChADEAQQCRAF"),
        VIDEOS("Videos", "EgWKAQIQAWoMEA4QChADEAQQCRAF"),
        ALBUMS("Albums", "EgWKAQIYAWoMEA4QChADEAQQCRAF"),
        ARTISTS("Artists", "EgWKAQIgAWoMEA4QChADEAQQCRAF"),
        COMMUNITY_PLAYLISTS("Community playlists", "EgeKAQQoAEABagwQDhAKEAMQBBAJEAU%3D"),
        FEATURED_PLAYLISTS("Featured playlists", "EgeKAQQoADgBagwQDhAKEAMQBBAJEAU%3D"),
        PODCASTS("Podcasts", "EgWKAQJQAWoMEA4QChADEAQQCRAF"),
        EPISODES("Episodes", "EgWKAQJIAWoMEA4QChADEAQQCRAF"),
        PROFILES("Profiles", "EgWKAQJYAWoMEA4QChADEAQQCRAF")
    }

    fun search(query: String, filter: SearchFilter): Page =
        parsePage(YTMusicApi.post(SEARCH, JSONObject().put("query", query).put("params", filter.params)))

    private const val SEARCH = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"

    fun more(continuation: String): Page =
        parsePage(YTMusicApi.post(YTMusicApi.BROWSE, JSONObject().put("continuation", continuation)))

    /** The tracks of any playable list (album "OLAK…", mix "RDTMAK…", artist radio "RDEM…"). */
    fun watchPlaylist(playlistId: String): List<MediaItemModel> {
        val root = YTMusicApi.post(YTMusicApi.NEXT, JSONObject().put("playlistId", playlistId).put("isAudioOnly", true))
        return YTMusicApi.parseWatch(root).queue
    }

    internal fun parsePage(root: JSONObject): Page {
        val lists = ArrayList<JSONObject>()
        var headerNode: JSONObject? = root.optJSONObject("header")
        root.optJSONObject("contents")?.let { contents ->
            contents.optJSONObject("tabbedSearchResultsRenderer")?.optJSONArray("tabs")?.let { tabs ->
                selectedTab(tabs)?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.let(lists::add)
            }
            contents.optJSONObject("singleColumnBrowseResultsRenderer")?.optJSONArray("tabs")?.let { tabs ->
                selectedTab(tabs)?.optJSONObject("content")?.optJSONObject("sectionListRenderer")?.let(lists::add)
            }
            contents.optJSONObject("twoColumnBrowseResultsRenderer")?.let { two ->
                two.optJSONArray("tabs")?.let(::selectedTab)?.optJSONObject("content")
                    ?.optJSONObject("sectionListRenderer")?.let(lists::add)
                two.optJSONObject("secondaryContents")?.optJSONObject("sectionListRenderer")?.let(lists::add)
            }
            contents.optJSONObject("sectionListRenderer")?.let(lists::add)
        }
        root.optJSONObject("continuationContents")?.let { c ->
            (c.optJSONObject("sectionListContinuation") ?: c.optJSONObject("musicShelfContinuation")
                ?: c.optJSONObject("gridContinuation") ?: c.optJSONObject("musicPlaylistShelfContinuation"))?.let(lists::add)
        }

        val sections = ArrayList<YTMusicApi.Section>()
        val chips = ArrayList<Chip>()
        var continuation: String? = null
        for (list in lists) {
            val contents = list.optJSONArray("contents") ?: list.optJSONArray("items")
            if (contents != null) {
                // Album and playlist pages put their header first in the section list.
                for (i in 0 until contents.length()) {
                    val node = contents.optJSONObject(i) ?: continue
                    if (headerNode == null && HEADER_KEYS.any(node::has)) headerNode = node
                }
                val first = contents.optJSONObject(0)
                sections += YTMusicApi.parseShelves(when {
                    // A shelf / grid continuation holds bare items: wrap them as one shelf.
                    first?.has("musicResponsiveListItemRenderer") == true ->
                        JSONArray().put(JSONObject().put("musicShelfRenderer", JSONObject().put("contents", contents)))
                    first?.has("musicTwoRowItemRenderer") == true || first?.has("musicNavigationButtonRenderer") == true ->
                        JSONArray().put(JSONObject().put("gridRenderer", JSONObject().put("items", contents)))
                    else -> contents
                })
                // A shelf's own continuation (long library lists) is the page's next page too.
                for (i in 0 until contents.length()) {
                    val node = contents.optJSONObject(i) ?: continue
                    val shelf = node.optJSONObject("musicShelfRenderer") ?: node.optJSONObject("musicPlaylistShelfRenderer")
                        ?: node.optJSONObject("gridRenderer")
                        ?: node.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")?.optJSONObject(0)?.let {
                            it.optJSONObject("gridRenderer") ?: it.optJSONObject("musicShelfRenderer")
                        }
                    shelf?.let(::continuationOf)?.let { continuation = it }
                }
            }
            continuationOf(list)?.let { continuation = it }
            list.optJSONObject("header")?.optJSONObject("chipCloudRenderer")?.optJSONArray("chips")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val chip = arr.optJSONObject(i)?.optJSONObject("chipCloudChipRenderer") ?: continue
                    val browse = chip.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                    chips += Chip(
                        title = InnerTube.text(chip.optJSONObject("text")),
                        browseId = browse?.optString("browseId").orEmpty(),
                        params = browse?.optString("params").orEmpty(),
                        selected = chip.optBoolean("isSelected")
                    )
                }
            }
        }
        val header = headerNode?.let(::parseHeader)
        val albumPlaylist = if (header != null && header.playlistId.isEmpty() && !header.isArtist) {
            ALBUM_PLAYLIST.find(root.toString())?.value.orEmpty()
        } else ""
        return Page(
            header = header?.let { if (albumPlaylist.isNotEmpty()) it.copy(playlistId = albumPlaylist) else it },
            sections = sections,
            chips = chips.filter { it.title.isNotBlank() },
            continuation = continuation
        )
    }

    private val HEADER_KEYS = listOf(
        "musicImmersiveHeaderRenderer", "musicVisualHeaderRenderer", "musicDetailHeaderRenderer",
        "musicResponsiveHeaderRenderer", "musicEditablePlaylistDetailHeaderRenderer", "musicHeaderRenderer"
    )
    private val ALBUM_PLAYLIST = Regex("""OLAK5uy_[A-Za-z0-9_-]{30,40}""")

    private fun selectedTab(tabs: JSONArray): JSONObject? {
        var first: JSONObject? = null
        for (i in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer") ?: continue
            if (first == null) first = tab
            if (tab.optBoolean("selected")) return tab
        }
        return first
    }

    private fun continuationOf(node: JSONObject): String? {
        node.optJSONArray("continuations")?.optJSONObject(0)?.let { c ->
            (c.optJSONObject("nextContinuationData") ?: c.optJSONObject("reloadContinuationData"))
                ?.optString("continuation")?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val items = node.optJSONArray("contents") ?: node.optJSONArray("items") ?: return null
        val last = items.optJSONObject(items.length() - 1)?.optJSONObject("continuationItemRenderer") ?: return null
        return last.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")
            ?.takeIf { it.isNotEmpty() }
    }

    internal fun parseHeader(node: JSONObject): Header? {
        node.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")?.let { editable ->
            editable.optJSONObject("header")?.let { return parseHeader(it) }
        }
        val h = HEADER_KEYS.firstNotNullOfOrNull(node::optJSONObject) ?: return null
        val isArtist = node.has("musicImmersiveHeaderRenderer") || node.has("musicVisualHeaderRenderer")
        val thumbnail = thumbnailOf(h.optJSONObject("thumbnail"))
            .ifEmpty { thumbnailOf(h.optJSONObject("foregroundThumbnail")) }
        val subscribe = h.optJSONObject("subscriptionButton")?.optJSONObject("subscribeButtonRenderer")
        val description = h.optJSONObject("description")?.let { d ->
            d.optJSONObject("musicDescriptionShelfRenderer")?.optJSONObject("description")?.let(InnerTube::text)
                ?: InnerTube.text(d)
        }.orEmpty()
        val strapline = h.optJSONObject("straplineTextOne")
        val artistRun = strapline?.optJSONArray("runs")?.optJSONObject(0)
        fun buttonPlaylist(key: String) = h.optJSONObject(key)?.optJSONObject("buttonRenderer")
            ?.optJSONObject("navigationEndpoint")?.let { it.optJSONObject("watchEndpoint") ?: it.optJSONObject("watchPlaylistEndpoint") }
            ?.optString("playlistId").orEmpty()
        var playId = ""
        h.optJSONArray("buttons")?.let { buttons ->
            for (i in 0 until buttons.length()) {
                buttons.optJSONObject(i)?.optJSONObject("musicPlayButtonRenderer")?.optJSONObject("playNavigationEndpoint")
                    ?.let { it.optJSONObject("watchEndpoint") ?: it.optJSONObject("watchPlaylistEndpoint") }
                    ?.optString("playlistId")?.takeIf { it.isNotEmpty() }?.let { playId = it }
            }
        }
        return Header(
            title = InnerTube.text(h.optJSONObject("title")),
            subtitle = InnerTube.text(h.optJSONObject("subtitle")),
            secondSubtitle = InnerTube.text(h.optJSONObject("secondSubtitle")),
            description = description.trim(),
            thumbnailUrl = thumbnail,
            isArtist = isArtist,
            channelId = subscribe?.optString("channelId").orEmpty(),
            subscriberText = InnerTube.text(subscribe?.optJSONObject("subscriberCountText")),
            subscribed = subscribe?.optBoolean("subscribed") == true,
            playlistId = playId,
            radioPlaylistId = buttonPlaylist("startRadioButton"),
            shufflePlaylistId = buttonPlaylist("playButton"),
            artistName = InnerTube.text(strapline),
            artistId = artistRun?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
        )
    }

    private fun thumbnailOf(node: JSONObject?): String {
        node ?: return ""
        val inner = node.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
            ?: node.optJSONObject("croppedSquareThumbnailRenderer")?.optJSONObject("thumbnail")
            ?: node
        return InnerTube.lastThumbnail(inner)
    }
}
