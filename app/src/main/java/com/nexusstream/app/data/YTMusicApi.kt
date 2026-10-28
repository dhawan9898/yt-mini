package com.nexusstream.app.data

import androidx.compose.ui.graphics.Color
import com.nexusstream.app.model.MediaContentType
import com.nexusstream.app.model.MediaItemModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * The YouTube Music flavour of InnerTube (music.youtube.com, WEB_REMIX client). Its `next` response
 * is what the official app's now-playing sheet is built from: the radio queue for UP NEXT, and the
 * browse ids behind the LYRICS and RELATED tabs.
 */
object YTMusicApi {
    private const val ORIGIN = "https://music.youtube.com"
    private const val NEXT = "$ORIGIN/youtubei/v1/next?prettyPrint=false"
    private const val BROWSE = "$ORIGIN/youtubei/v1/browse?prettyPrint=false"
    private const val CLIENT_NAME = "WEB_REMIX"
    private const val CLIENT_NAME_ID = "67"
    private const val CLIENT_VERSION = "1.20250101.01.00"

    /** The three now-playing tabs for one track. Browse ids are null when YT Music has no such tab. */
    class WatchInfo(
        val queue: List<MediaItemModel>,
        val continuation: String?,
        val lyricsBrowseId: String?,
        val relatedBrowseId: String?,
        /** Album, artists and credits of the track the radio was started from, when YT Music knows them. */
        val trackInfo: TrackInfo? = null
    )

    /** What the song menu needs: album, artists and the "View song credits" page. */
    data class TrackInfo(
        val albumId: String,
        val albumName: String,
        val artists: List<Artist>,
        val creditsBrowseId: String?
    )

    data class Artist(val name: String, val id: String)

    /** One "Performed by" / "Written by" ... section of the credits dialog. */
    data class CreditSection(val title: String, val names: List<String>)

    /** Plain (unsynced) lyrics as YT Music shows them, with the "Source: ..." credit line. */
    class PlainLyrics(val text: String, val source: String)

    enum class CardKind { SONG, ALBUM, PLAYLIST, ARTIST }

    /** One tile on the RELATED tab. [track] is set for songs / videos, [url] for everything else. */
    data class Card(
        val kind: CardKind,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String,
        val url: String = "",
        val track: MediaItemModel? = null
    )

    /**
     * A RELATED shelf ("You might also like", "Similar artists"...) or the "About the artist" text.
     * [isSongList] shelves are song rows; the others are square / round tiles.
     */
    data class Section(
        val title: String,
        val cards: List<Card>,
        val description: String = "",
        val isSongList: Boolean = false
    )

    /** Starts a YT Music radio from [videoId], the same request the app makes when a song starts. */
    fun watch(videoId: String): WatchInfo = parseWatch(post(NEXT, JSONObject()
        .put("videoId", videoId)
        .put("playlistId", "RDAMVM$videoId")
        .put("isAudioOnly", true)))

    /** The next page of a radio queue. */
    fun moreRadio(continuation: String): InnerTube.Page =
        parseQueueContinuation(post(NEXT, JSONObject().put("continuation", continuation).put("isAudioOnly", true)))

    fun lyrics(browseId: String): PlainLyrics? = parseLyrics(post(BROWSE, JSONObject().put("browseId", browseId)))

    fun related(browseId: String): List<Section> = parseRelated(post(BROWSE, JSONObject().put("browseId", browseId)))

    private fun post(endpoint: String, extra: JSONObject): JSONObject =
        InnerTube.post(endpoint, extra, ORIGIN, CLIENT_NAME, CLIENT_NAME_ID, CLIENT_VERSION)

    internal fun parseWatch(root: JSONObject): WatchInfo {
        val tabs = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            ?.optJSONObject("tabbedRenderer")
            ?.optJSONObject("watchNextTabbedResultsRenderer")
            ?.optJSONArray("tabs")
        val queueTab = tabs?.optJSONObject(0)?.optJSONObject("tabRenderer")
        val panel = queueTab?.optJSONObject("content")
            ?.optJSONObject("musicQueueRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("playlistPanelRenderer")
        val renderers = panel?.let(::panelRenderers).orEmpty()
        val seed = root.optJSONObject("currentVideoEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")
        val seedRenderer = renderers.firstOrNull { it.optString("videoId") == seed } ?: renderers.firstOrNull()
        return WatchInfo(
            queue = renderers.mapNotNull(::panelTrack),
            continuation = panel?.optJSONArray("continuations")?.let(::continuationToken),
            lyricsBrowseId = tabBrowseId(tabs, "MPLY"),
            relatedBrowseId = tabBrowseId(tabs, "MPTR"),
            trackInfo = seedRenderer?.let(::trackInfo)
        )
    }

    /** The playlistPanelVideoRenderers of a queue panel (unwrapping the song / video counterparts). */
    private fun panelRenderers(panel: JSONObject): List<JSONObject> {
        val contents = panel.optJSONArray("contents") ?: return emptyList()
        return (0 until contents.length()).mapNotNull { i ->
            val entry = contents.optJSONObject(i) ?: return@mapNotNull null
            entry.optJSONObject("playlistPanelVideoRenderer")
                ?: entry.optJSONObject("playlistPanelVideoWrapperRenderer")?.optJSONObject("primaryRenderer")
                    ?.optJSONObject("playlistPanelVideoRenderer")
        }.filter { !it.has("unplayableText") }
    }

    /** A queue entry as a track, with its album and artist when the byline links them. */
    internal fun panelTrack(p: JSONObject): MediaItemModel? {
        val id = p.optString("videoId").takeIf { it.length == 11 } ?: return null
        val info = trackInfo(p)
        val artist = info.artists.joinToString(", ") { it.name }
            .ifEmpty { InnerTube.text(p.optJSONObject("shortBylineText")) }
        val seconds = InnerTube.parseDuration(InnerTube.text(p.optJSONObject("lengthText")))
        return track(id, InnerTube.text(p.optJSONObject("title")), artist,
            InnerTube.lastThumbnail(p.optJSONObject("thumbnail")), seconds).copy(
            channelUrl = info.artists.firstOrNull { it.id.isNotEmpty() }?.let { artistUrl(it.id) }.orEmpty(),
            albumId = info.albumId,
            albumName = info.albumName
        )
    }

    /** Album / artists from a track's byline runs, credits id from its menu. */
    internal fun trackInfo(renderer: JSONObject): TrackInfo {
        val runs = (renderer.optJSONObject("longBylineText") ?: renderer.optJSONObject("shortBylineText"))
            ?.optJSONArray("runs") ?: JSONArray()
        var albumId = ""
        var albumName = ""
        val artists = ArrayList<Artist>()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text")
            if (text.isBlank() || text.trim() == "•" || text.trim() == "&" || text.trim() == ",") continue
            val browseId = run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            when {
                browseId.startsWith("MPRE") -> { albumId = browseId; albumName = text }
                browseId.startsWith("UC") -> artists += Artist(text, browseId)
                // Unlinked runs after the first separator are views / years / durations, not artists.
                browseId.isEmpty() && artists.isEmpty() && albumId.isEmpty() && i == 0 -> artists += Artist(text, "")
            }
        }
        return TrackInfo(albumId, albumName, artists, menuBrowseId(renderer, "MPTC"))
    }

    /** The browse id behind a menu entry whose id starts with [prefix] (e.g. "MPTC" = song credits). */
    private fun menuBrowseId(renderer: JSONObject, prefix: String): String? {
        val items = renderer.optJSONObject("menu")?.optJSONObject("menuRenderer")?.optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val id = items.optJSONObject(i)?.optJSONObject("menuNavigationItemRenderer")
                ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if (id.startsWith(prefix)) return id
        }
        return null
    }

    /** "View song credits": the sections of the credits dialog for an "MPTC..." browse id. */
    fun credits(browseId: String): List<CreditSection> = parseCredits(post(BROWSE, JSONObject().put("browseId", browseId)))

    internal fun parseCredits(root: JSONObject): List<CreditSection> {
        val sections = root.optJSONArray("onResponseReceivedActions")?.optJSONObject(0)
            ?.optJSONObject("openPopupAction")?.optJSONObject("popup")
            ?.optJSONObject("dismissableDialogRenderer")?.optJSONArray("sections") ?: return emptyList()
        return (0 until sections.length()).mapNotNull { i ->
            val section = sections.optJSONObject(i)?.optJSONObject("dismissableDialogContentSectionRenderer")
                ?: return@mapNotNull null
            val title = InnerTube.text(section.optJSONObject("title"))
            val runs = section.optJSONObject("subtitle")?.optJSONArray("runs") ?: JSONArray()
            val names = (0 until runs.length() step 2).mapNotNull { j ->
                runs.optJSONObject(j)?.optString("text")?.trim()?.takeIf { it.isNotEmpty() }
            }
            if (title.isBlank() || names.isEmpty()) null else CreditSection(title, names)
        }
    }

    /** The playable playlist ("OLAK5uy_...") of an album browse id ("MPREb_..."). */
    fun albumPlaylistId(albumBrowseId: String): String? {
        val raw = post(BROWSE, JSONObject().put("browseId", albumBrowseId)).toString()
        return ALBUM_PLAYLIST.find(raw)?.value
    }

    private val ALBUM_PLAYLIST = Regex("""OLAK5uy_[A-Za-z0-9_-]{30,40}""")

    internal fun artistUrl(channelId: String) = "https://www.youtube.com/channel/$channelId"

    internal fun parseQueueContinuation(root: JSONObject): InnerTube.Page {
        val panel = root.optJSONObject("continuationContents")?.optJSONObject("playlistPanelContinuation")
            ?: return InnerTube.Page(emptyList(), null)
        return InnerTube.Page(
            panelRenderers(panel).mapNotNull(::panelTrack),
            panel.optJSONArray("continuations")?.let(::continuationToken)
        )
    }

    internal fun parseLyrics(root: JSONObject): PlainLyrics? {
        val shelf = sectionContents(root)?.let { contents ->
            (0 until contents.length()).firstNotNullOfOrNull {
                contents.optJSONObject(it)?.optJSONObject("musicDescriptionShelfRenderer")
            }
        } ?: return null
        val text = InnerTube.text(shelf.optJSONObject("description")).trim()
        if (text.isEmpty()) return null
        return PlainLyrics(text, InnerTube.text(shelf.optJSONObject("footer")).trim())
    }

    internal fun parseRelated(root: JSONObject): List<Section> {
        val contents = sectionContents(root) ?: return emptyList()
        val sections = ArrayList<Section>()
        for (i in 0 until contents.length()) {
            val node = contents.optJSONObject(i) ?: continue
            node.optJSONObject("musicCarouselShelfRenderer")?.let { shelf ->
                val title = InnerTube.text(shelf.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")?.optJSONObject("title"))
                val items = shelf.optJSONArray("contents") ?: JSONArray()
                var rows = 0
                val cards = (0 until items.length()).mapNotNull { j ->
                    val item = items.optJSONObject(j) ?: return@mapNotNull null
                    item.optJSONObject("musicResponsiveListItemRenderer")?.let { rows++; listItem(it) }
                        ?: item.optJSONObject("musicTwoRowItemRenderer")?.let(::twoRowItem)
                }
                if (cards.isNotEmpty()) sections += Section(title, cards, isSongList = rows > 0)
            }
            node.optJSONObject("musicDescriptionShelfRenderer")?.let { shelf ->
                val text = InnerTube.text(shelf.optJSONObject("description")).trim()
                if (text.isNotEmpty()) {
                    val title = InnerTube.text(shelf.optJSONObject("header")).ifBlank { "About the artist" }
                    sections += Section(title, emptyList(), description = text)
                }
            }
        }
        return sections
    }

    /** A song row (title / artist columns), as used by "You might also like". */
    private fun listItem(r: JSONObject): Card? {
        val columns = r.optJSONArray("flexColumns") ?: return null
        fun column(i: Int) = columns.optJSONObject(i)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
        val titleRuns = column(0)
        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.length == 11 }
            ?: titleRuns?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.length == 11 }
            ?: return null
        val title = InnerTube.text(titleRuns)
        // Second column is "Artist" or "Artist • Album"; the artist is what the app shows.
        val byline = InnerTube.text(column(1))
        val artist = byline.substringBefore(" • ").trim()
        val thumbnail = InnerTube.lastThumbnail(r.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail"))
        val fixed = r.optJSONArray("fixedColumns")?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")
        val seconds = InnerTube.parseDuration(InnerTube.text(fixed))
        return Card(CardKind.SONG, title, byline, thumbnail, track = track(videoId, title, artist, thumbnail, seconds))
    }

    /** A square / round tile: album, playlist, artist or music video. */
    private fun twoRowItem(r: JSONObject): Card? {
        val title = InnerTube.text(r.optJSONObject("title"))
        if (title.isEmpty()) return null
        val subtitle = InnerTube.text(r.optJSONObject("subtitle"))
        val thumbnail = InnerTube.lastThumbnail(r.optJSONObject("thumbnailRenderer")
            ?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail"))
        val nav = r.optJSONObject("navigationEndpoint")
        nav?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.length == 11 }?.let { id ->
            val artist = subtitle.split(" • ").firstOrNull { it.isNotBlank() && it != "Video" && it != "Song" }.orEmpty()
            return Card(CardKind.SONG, title, subtitle, thumbnail, track = track(id, title, artist, thumbnail, 0))
        }
        val browse = nav?.optJSONObject("browseEndpoint") ?: return null
        val browseId = browse.optString("browseId")
        val pageType = browse.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
        // Albums open through their playable playlist ("OLAK5uy_..."), which the playlist page understands.
        val playPlaylistId = r.optJSONObject("thumbnailOverlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId").orEmpty()
        return when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" || browseId.startsWith("UC") ->
                Card(CardKind.ARTIST, title, subtitle, thumbnail, url = "https://www.youtube.com/channel/$browseId")
            pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPRE") ->
                playPlaylistId.takeIf { it.isNotEmpty() }?.let {
                    Card(CardKind.ALBUM, title, subtitle, thumbnail, url = playlistUrl(it))
                }
            browseId.startsWith("VL") || pageType == "MUSIC_PAGE_TYPE_PLAYLIST" -> {
                val id = browseId.removePrefix("VL").ifEmpty { playPlaylistId }
                id.takeIf { it.isNotEmpty() }?.let { Card(CardKind.PLAYLIST, title, subtitle, thumbnail, url = playlistUrl(it)) }
            }
            else -> null
        }
    }

    private fun playlistUrl(id: String) = "https://www.youtube.com/playlist?list=$id"

    private fun sectionContents(root: JSONObject): JSONArray? =
        root.optJSONObject("contents")?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")

    private fun tabBrowseId(tabs: JSONArray?, prefix: String): String? {
        tabs ?: return null
        for (i in 0 until tabs.length()) {
            val id = tabs.optJSONObject(i)?.optJSONObject("tabRenderer")?.optJSONObject("endpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            if (id.startsWith(prefix)) return id
        }
        return null
    }

    private fun continuationToken(continuations: JSONArray): String? {
        val c = continuations.optJSONObject(0) ?: return null
        val data = c.optJSONObject("nextRadioContinuationData") ?: c.optJSONObject("nextContinuationData")
        return data?.optString("continuation")?.takeIf { it.isNotEmpty() }
    }

    private fun asTrack(item: MediaItemModel) = item.copy(
        contentType = MediaContentType.MUSIC_TRACK,
        sourceUrl = "https://www.youtube.com/watch?v=${item.id}"
    )

    private fun track(id: String, title: String, artist: String, thumbnail: String, seconds: Long) = MediaItemModel(
        id = id,
        title = title,
        artistOrChannel = artist,
        audioUrl = "",
        videoUrl = "",
        artworkUrl = thumbnail.ifEmpty { "https://i.ytimg.com/vi/$id/hqdefault.jpg" },
        durationMs = seconds * 1000L,
        dominantColor = Color(0xFF6C5CE7),
        secondaryColor = Color(0xFFFF7675),
        viewsOrPlays = "",
        contentType = MediaContentType.MUSIC_TRACK,
        publishDate = "",
        sourceUrl = "https://www.youtube.com/watch?v=$id"
    )
}
