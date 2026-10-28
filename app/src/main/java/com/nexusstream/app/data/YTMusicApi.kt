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
    internal const val NEXT = "$ORIGIN/youtubei/v1/next?prettyPrint=false"
    internal const val BROWSE = "$ORIGIN/youtubei/v1/browse?prettyPrint=false"
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

    /**
     * One tile or row of a YT Music shelf. [track] is set for songs / videos, [url] for everything
     * else; [browseId] opens the item's own YT Music page (album "MPREb…", artist "UC…", playlist "VL…").
     */
    data class Card(
        val kind: CardKind,
        val title: String,
        val subtitle: String,
        val thumbnailUrl: String,
        val url: String = "",
        val track: MediaItemModel? = null,
        val browseId: String = ""
    )

    /** A tile of the Moods & genres / Explore button grid; opens [browseId] with [params]. */
    data class NavButton(val title: String, val browseId: String, val params: String, val color: Long)

    /**
     * A RELATED shelf ("You might also like", "Similar artists"...) or the "About the artist" text.
     * [isSongList] shelves are song rows; the others are square / round tiles.
     */
    data class Section(
        val title: String,
        val cards: List<Card>,
        val description: String = "",
        val isSongList: Boolean = false,
        /** Small line above the title ("Start radio from a song", the user's name...). */
        val strapline: String = "",
        val buttons: List<NavButton> = emptyList(),
        /** "More" / "Show all" target. */
        val moreBrowseId: String = "",
        val moreParams: String = "",
        /** Song rows stacked per column (Quick picks uses 4). */
        val rowsPerColumn: Int = 1,
        /** Tiles laid out as a wrapping grid instead of a row (library pages). */
        val isGrid: Boolean = false
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

    /**
     * Time-synced lyrics from the label (LyricFind / Musixmatch), as the YT Music app shows them.
     * Only the Android client gets the timings, like ytmusicapi's `get_lyrics(timestamps=True)`.
     */
    fun timedLyrics(browseId: String): Pair<List<com.nexusstream.app.model.LyricLine>, String>? =
        parseTimedLyrics(InnerTube.post(BROWSE, JSONObject().put("browseId", browseId), ORIGIN, "ANDROID_MUSIC", "21", "7.21.50"))

    internal fun parseTimedLyrics(root: JSONObject): Pair<List<com.nexusstream.app.model.LyricLine>, String>? {
        val data = root.optJSONObject("contents")?.optJSONObject("elementRenderer")?.optJSONObject("newElement")
            ?.optJSONObject("type")?.optJSONObject("componentType")?.optJSONObject("model")
            ?.optJSONObject("timedLyricsModel")?.optJSONObject("lyricsData") ?: return null
        val raw = data.optJSONArray("timedLyricsData") ?: return null
        val lines = ArrayList<com.nexusstream.app.model.LyricLine>()
        for (i in 0 until raw.length()) {
            val l = raw.optJSONObject(i) ?: continue
            val cue = l.optJSONObject("cueRange") ?: return null
            lines += com.nexusstream.app.model.LyricLine(
                startTimeMs = cue.optString("startTimeMilliseconds").toLongOrNull() ?: 0L,
                endTimeMs = cue.optString("endTimeMilliseconds").toLongOrNull() ?: 0L,
                text = l.optString("lyricLine")
            )
        }
        return if (lines.isEmpty()) null else lines to data.optString("sourceMessage")
    }

    fun related(browseId: String): List<Section> = parseRelated(post(BROWSE, JSONObject().put("browseId", browseId)))

    internal fun post(endpoint: String, extra: JSONObject): JSONObject =
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

    internal fun parseRelated(root: JSONObject): List<Section> =
        sectionContents(root)?.let(::parseShelves).orEmpty()

    /**
     * The shelves of a YT Music section list: carousels, song shelves, tile grids, button grids
     * (Moods & genres) and description text. Shared by Related, Home, Explore, artist, album and
     * library pages.
     */
    internal fun parseShelves(contents: JSONArray): List<Section> {
        val sections = ArrayList<Section>()
        for (i in 0 until contents.length()) {
            val node = contents.optJSONObject(i) ?: continue
            (node.optJSONObject("musicCarouselShelfRenderer") ?: node.optJSONObject("musicImmersiveCarouselShelfRenderer"))?.let { shelf ->
                val header = shelf.optJSONObject("header")?.let {
                    it.optJSONObject("musicCarouselShelfBasicHeaderRenderer") ?: it.optJSONObject("musicImmersiveCarouselShelfHeaderRenderer")
                }
                val title = InnerTube.text(header?.optJSONObject("title"))
                val more = header?.optJSONObject("moreContentButton")?.optJSONObject("buttonRenderer")
                    ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                val items = shelf.optJSONArray("contents") ?: JSONArray()
                var rows = 0
                val buttons = ArrayList<NavButton>()
                val cards = (0 until items.length()).mapNotNull { j ->
                    val item = items.optJSONObject(j) ?: return@mapNotNull null
                    item.optJSONObject("musicNavigationButtonRenderer")?.let { navButton(it)?.let(buttons::add); return@mapNotNull null }
                    item.optJSONObject("musicResponsiveListItemRenderer")?.let { rows++; listItem(it) }
                        ?: item.optJSONObject("musicTwoRowItemRenderer")?.let(::twoRowItem)
                }
                if (cards.isNotEmpty() || buttons.isNotEmpty()) sections += Section(
                    title = title,
                    cards = cards,
                    isSongList = rows > 0,
                    strapline = InnerTube.text(header?.optJSONObject("strapline")),
                    buttons = buttons,
                    moreBrowseId = more?.optString("browseId").orEmpty(),
                    moreParams = more?.optString("params").orEmpty(),
                    rowsPerColumn = if (rows > 0) shelf.optInt("numItemsPerColumn", 1).coerceIn(1, 4) else 1
                )
            }
            (node.optJSONObject("musicShelfRenderer") ?: node.optJSONObject("musicPlaylistShelfRenderer"))?.let { shelf ->
                val items = shelf.optJSONArray("contents") ?: JSONArray()
                val cards = (0 until items.length()).mapNotNull { j ->
                    items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer")?.let(::listItem)
                }
                val more = shelf.optJSONObject("bottomEndpoint")?.optJSONObject("browseEndpoint")
                    ?: shelf.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                if (cards.isNotEmpty()) sections += Section(
                    title = InnerTube.text(shelf.optJSONObject("title")),
                    cards = cards,
                    isSongList = true,
                    moreBrowseId = more?.optString("browseId").orEmpty(),
                    moreParams = more?.optString("params").orEmpty()
                )
            }
            node.optJSONObject("gridRenderer")?.let { grid ->
                val items = grid.optJSONArray("items") ?: JSONArray()
                val buttons = ArrayList<NavButton>()
                val cards = (0 until items.length()).mapNotNull { j ->
                    val item = items.optJSONObject(j) ?: return@mapNotNull null
                    item.optJSONObject("musicNavigationButtonRenderer")?.let { navButton(it)?.let(buttons::add); return@mapNotNull null }
                    item.optJSONObject("musicTwoRowItemRenderer")?.let(::twoRowItem)
                }
                val title = InnerTube.text(grid.optJSONObject("header")?.optJSONObject("gridHeaderRenderer")?.optJSONObject("title"))
                if (cards.isNotEmpty() || buttons.isNotEmpty()) sections += Section(title, cards, buttons = buttons, isGrid = true)
            }
            node.optJSONObject("itemSectionRenderer")?.optJSONArray("contents")?.let { sections += parseShelves(it) }
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

    private fun navButton(b: JSONObject): NavButton? {
        val title = InnerTube.text(b.optJSONObject("buttonText")).ifEmpty { return null }
        val browse = b.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint") ?: return null
        val color = b.optJSONObject("solid")?.optLong("leftStripeColor") ?: 0L
        return NavButton(title, browse.optString("browseId"), browse.optString("params"), color)
    }

    /** A song row (title / artist columns), as used by "You might also like". */
    private fun listItem(r: JSONObject): Card? {
        val columns = r.optJSONArray("flexColumns") ?: return null
        fun column(i: Int) = columns.optJSONObject(i)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
        val titleRuns = column(0)
        val title = InnerTube.text(titleRuns)
        val thumbnail = InnerTube.lastThumbnail(r.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail"))
        // Second column is "Artist" or "Artist • Album" (or "Song • Artist • Album" in search).
        val byline = (1 until columns.length()).joinToString(" • ") { InnerTube.text(column(it)) }.trim(' ', '•')
        val videoId = r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.length == 11 }
            ?: titleRuns?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf { it.length == 11 }
        if (videoId == null) {
            // An artist, album or playlist row (library and search lists).
            val browse = r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint") ?: return null
            return browseCard(browse, title, byline, thumbnail, "")
        }
        val runs = JSONArray()
        for (c in 1 until columns.length()) column(c)?.optJSONArray("runs")?.let { for (k in 0 until it.length()) runs.put(it.opt(k)) }
        val info = trackInfo(JSONObject().put("longBylineText", JSONObject().put("runs", runs)).put("menu", r.optJSONObject("menu") ?: JSONObject()))
        val skip = setOf("Song", "Video", "Episode", "Podcast")
        val artist = info.artists.filter { it.name !in skip }.joinToString(", ") { it.name }
            .ifEmpty { byline.split(" • ").firstOrNull { it.isNotBlank() && it !in skip }.orEmpty().trim() }
        val fixed = r.optJSONArray("fixedColumns")?.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")
        val seconds = InnerTube.parseDuration(InnerTube.text(fixed))
        val item = track(videoId, title, artist, thumbnail, seconds).copy(
            channelUrl = info.artists.firstOrNull { it.id.isNotEmpty() }?.let { artistUrl(it.id) }.orEmpty(),
            albumId = info.albumId,
            albumName = info.albumName,
            isExplicit = r.optJSONArray("badges")?.toString()?.contains("MUSIC_EXPLICIT_BADGE") == true
        )
        return Card(CardKind.SONG, title, byline, thumbnail, track = item)
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
        nav?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId")?.takeIf { it.isNotEmpty() }?.let { id ->
            // Mixes ("My Supermix", "Discover mix") play from their radio playlist.
            return Card(CardKind.PLAYLIST, title, subtitle, thumbnail, url = playlistUrl(id), browseId = "VL$id")
        }
        val browse = nav?.optJSONObject("browseEndpoint") ?: return null
        // Albums also carry their playable playlist ("OLAK5uy_...").
        val playPlaylistId = r.optJSONObject("thumbnailOverlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")?.optJSONObject("playNavigationEndpoint")
            ?.let { it.optJSONObject("watchPlaylistEndpoint") ?: it.optJSONObject("watchEndpoint") }
            ?.optString("playlistId").orEmpty()
        return browseCard(browse, title, subtitle, thumbnail, playPlaylistId)
    }

    /** A card that opens a YT Music page: artist, album, playlist or podcast. */
    private fun browseCard(browse: JSONObject, title: String, subtitle: String, thumbnail: String, playPlaylistId: String): Card? {
        val browseId = browse.optString("browseId").ifEmpty { return null }
        val pageType = browse.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
        return when {
            pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL" || browseId.startsWith("UC") ->
                Card(CardKind.ARTIST, title, subtitle, thumbnail, url = artistUrl(browseId), browseId = browseId)
            pageType == "MUSIC_PAGE_TYPE_ALBUM" || pageType == "MUSIC_PAGE_TYPE_AUDIOBOOK" || browseId.startsWith("MPRE") ->
                Card(CardKind.ALBUM, title, subtitle, thumbnail, url = musicPageUrl(browseId, playPlaylistId), browseId = browseId)
            browseId.startsWith("VL") || pageType == "MUSIC_PAGE_TYPE_PLAYLIST" -> {
                val id = browseId.removePrefix("VL").ifEmpty { playPlaylistId }
                Card(CardKind.PLAYLIST, title, subtitle, thumbnail, url = playlistUrl(id), browseId = "VL$id")
            }
            // Podcast shows ("MPSP...") and other music pages open as browse pages.
            browseId.startsWith("MP") || browseId.startsWith("FE") ->
                Card(CardKind.PLAYLIST, title, subtitle, thumbnail, url = musicPageUrl(browseId, ""), browseId = browseId)
            else -> null
        }
    }

    /** music.youtube.com page link; albums also carry their playable playlist, for the YouTube page fallback. */
    internal fun musicPageUrl(browseId: String, playlistId: String): String =
        "https://music.youtube.com/browse/$browseId" + if (playlistId.isNotEmpty()) "?list=$playlistId" else ""

    internal fun playlistUrl(id: String) = "https://www.youtube.com/playlist?list=$id"

    internal fun sectionContents(root: JSONObject): JSONArray? =
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

    internal fun track(id: String, title: String, artist: String, thumbnail: String, seconds: Long) = MediaItemModel(
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
