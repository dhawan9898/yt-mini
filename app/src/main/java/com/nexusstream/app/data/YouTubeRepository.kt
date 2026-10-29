package com.nexusstream.app.data

import androidx.compose.ui.graphics.Color
import com.nexusstream.app.model.MediaContentType
import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

/**
 * One page of results. [next] loads the following page; it is null when there are no more.
 * Pages are immutable, so a cached [Paged] can be paged again from its first page.
 */
class Paged<T>(
    val items: List<T>,
    internal val loadMore: (suspend () -> Result<Paged<T>>)?
) {
    val hasMore: Boolean get() = loadMore != null
    suspend fun next(): Result<Paged<T>> = loadMore?.invoke() ?: Result.success(Paged(emptyList(), null))
}

/** What a search can be narrowed to. */
enum class SearchType(val label: String, val filter: String, val isMusic: Boolean = false) {
    ALL("All", YoutubeSearchQueryHandlerFactory.ALL),
    VIDEOS("Videos", YoutubeSearchQueryHandlerFactory.VIDEOS),
    CHANNELS("Channels", YoutubeSearchQueryHandlerFactory.CHANNELS),
    PLAYLISTS("Playlists", YoutubeSearchQueryHandlerFactory.PLAYLISTS),
    SONGS("Songs", YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, true),
    ALBUMS("Albums", YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS, true),
    ARTISTS("Artists", YoutubeSearchQueryHandlerFactory.MUSIC_ARTISTS, true),
    MUSIC_PLAYLISTS("Playlists", YoutubeSearchQueryHandlerFactory.MUSIC_PLAYLISTS, true);

    companion object {
        /** Filters shown on the YouTube search screen. */
        val video: List<SearchType> = listOf(ALL, VIDEOS, CHANNELS, PLAYLISTS)
        /** Filters shown on the YT Music search screen. */
        val music: List<SearchType> = listOf(SONGS, ALBUMS, ARTISTS, MUSIC_PLAYLISTS)
    }
}

data class ChannelResult(
    val name: String,
    val url: String,
    val avatarUrl: String,
    val subscribers: String,
    val description: String
)

data class PlaylistResult(
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val owner: String,
    val videoCount: Long
)

/** One search hit. */
sealed interface SearchResult {
    data class Video(val item: MediaItemModel) : SearchResult
    data class Channel(val channel: ChannelResult) : SearchResult
    data class Playlist(val playlist: PlaylistResult) : SearchResult
}

/** A channel's header info plus its first page of videos. Playlists load on demand. */
data class ChannelPage(
    val name: String,
    val url: String,
    val avatarUrl: String,
    val bannerUrl: String,
    val subscribers: String,
    val description: String,
    val videos: Paged<MediaItemModel>,
    val loadPlaylists: suspend () -> Result<Paged<PlaylistResult>>
)

data class PlaylistPage(
    val title: String,
    val owner: String,
    val videoCount: Long,
    val thumbnailUrl: String,
    val videos: Paged<MediaItemModel>
)

/** A titled row of tracks for the YT Music home screen. */
data class MusicShelf(val title: String, val subtitle: String, val items: List<MediaItemModel>)

/** Extra information shown on the watch page. Unknown numbers are negative. */
data class VideoDetails(
    val likes: Long,
    val views: Long,
    val uploadedText: String,
    val description: String,
    val subscribers: Long,
    val tags: List<String>,
    val related: List<MediaItemModel>,
    val chapters: List<Chapter> = emptyList(),
    val storyboard: Storyboard? = null
)

/** Sprite-sheet thumbnails shown while scrubbing the seek bar. */
class Storyboard(
    val urls: List<String>,
    private val frames: org.schabi.newpipe.extractor.stream.Frameset
) {
    /** (sprite page index, left, top, right, bottom) of the frame for [positionMs]. */
    fun boundsAt(positionMs: Long): IntArray? =
        runCatching { frames.getFrameBoundsAt(positionMs) }.getOrNull()?.takeIf { it.size >= 5 && it[0] in urls.indices }
}

/** Comments plus the video's total count (negative when unknown). [paged] loads further pages. */
class CommentsPage(
    val totalCount: Int,
    val paged: Paged<CommentModel>,
    /** Set when the signed-in account can post a top-level comment. */
    val createParams: String? = null,
    /** True when the list came from YouTube's own API (sorting, likes and replies work). */
    val interactive: Boolean = false
) {
    val items: List<CommentModel> get() = paged.items
}

/** A top-level comment (or a reply). [loadReplies] is null when there are no replies to fetch. */
data class CommentModel(
    val author: String,
    val avatarUrl: String,
    val text: String,
    val likes: Int,
    val date: String,
    val pinned: Boolean,
    val replyCount: Int = 0,
    val loadReplies: (suspend () -> Result<List<CommentModel>>)? = null,
    val id: String = "",
    val isLiked: Boolean = false,
    /** The creator hearted the comment. */
    val isHearted: Boolean = false,
    /** Toolbar actions for the signed-in account (null when signed out). */
    val likeAction: String? = null,
    val unlikeAction: String? = null,
    val replyParams: String? = null,
    val authorIsCreator: Boolean = false,
    val authorChannelId: String = ""
)

/** One selectable video rendition (video-only stream). */
data class VideoOption(val height: Int, val url: String)

/**
 * One video-only or audio-only stream with the byte ranges of its init segment and index
 * (`sidx` / WebM cues), which is what a DASH manifest needs to play it adaptively.
 */
data class DashTrack(
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val url: String,
    val initStart: Int,
    val initEnd: Int,
    val indexStart: Int,
    val indexEnd: Int,
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0,
    val audioTrackId: String? = null,
    val language: String = ""
) {
    val isAudio: Boolean get() = mimeType.startsWith("audio")
    /** The "1080p" number: the shorter side, so vertical videos are labelled like YouTube does. */
    val qualityHeight: Int get() = if (width > 0 && height > 0) minOf(width, height) else height
}

/**
 * One selectable audio rendition. [trackId] / [trackName] identify the language track
 * (null for single-track videos); [isOriginal] is false for auto-dubbed and descriptive tracks.
 */
data class AudioOption(
    val kbps: Int,
    val url: String,
    val trackId: String? = null,
    val trackName: String = "",
    val isOriginal: Boolean = true
)

/** A language / audio track of a video. */
data class AudioTrack(val id: String?, val name: String, val isOriginal: Boolean)

/** A stream URL (and its media type) that can be sent to a Cast receiver. */
data class CastTarget(val url: String, val mimeType: String)

/** A caption track. [mimeType] is a media type ExoPlayer can load (WebVTT or TTML). */
data class SubtitleOption(
    val languageTag: String,
    val label: String,
    val url: String,
    val mimeType: String,
    val autoGenerated: Boolean
)

/** A video chapter. */
data class Chapter(val title: String, val startMs: Long)

private val CHAPTER_LINE = Regex("""^\s*[\[(]?((?:\d{1,2}:)?\d{1,2}:\d{2})[\])]?\s*[-–—:|.]*\s*(.+?)\s*$""")

/**
 * Chapters written as timestamps in the description, YouTube's own rule: the first one is at
 * 0:00, there are at least three, and they go up. HTML (as NewPipe returns it) is stripped first.
 */
internal fun chaptersFromDescription(description: String, durationMs: Long = 0L): List<Chapter> {
    val text = description
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
    val found = text.lines().mapNotNull { line ->
        val match = CHAPTER_LINE.find(line) ?: return@mapNotNull null
        val parts = match.groupValues[1].split(':').map { it.toLong() }
        val seconds = parts.fold(0L) { total, part -> total * 60 + part }
        Chapter(match.groupValues[2], seconds * 1000L)
    }
    if (found.size < 3 || found.first().startMs != 0L) return emptyList()
    if (found.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) return emptyList()
    if (durationMs > 0 && found.last().startMs >= durationMs) return emptyList()
    return found
}

/**
 * A downloadable file with a human label such as "720p video" or "128 kbps audio".
 * When [audioUrl] is set, [url] is a video-only stream and the two are merged after downloading.
 */
data class DownloadOption(
    val label: String,
    val url: String,
    val extension: String,
    val isVideo: Boolean,
    val audioUrl: String? = null
) {
    val height: Int get() = label.substringBefore('p').toIntOrNull() ?: 0
}

/**
 * Direct, short-lived stream URLs for one video, with every quality that is available.
 * Video options are sorted best-first. Audio options list the original track first
 * (best bitrate first), then other language tracks.
 */
data class ResolvedStream(
    val audioOptions: List<AudioOption>,
    val videoOptions: List<VideoOption>,
    val subtitles: List<SubtitleOption> = emptyList(),
    /** Set for live streams: the HLS playlist to play (the option lists are then empty). */
    val hlsUrl: String? = null,
    /** A single combined video + audio file (usually 360p): used when the merged streams will not play. */
    val muxedUrl: String? = null,
    /** Streams for adaptive (DASH) playback; empty when YouTube gave no index ranges. */
    val dashVideo: List<DashTrack> = emptyList(),
    val dashAudio: List<DashTrack> = emptyList(),
    val durationSec: Long = 0
) {
    /** Highest rendition not above [maxHeight]; null selects the default (720p cap, saves data). */
    fun pickVideo(maxHeight: Int?): VideoOption? {
        val cap = maxHeight ?: DEFAULT_MAX_HEIGHT
        return videoOptions.firstOrNull { it.height <= cap } ?: videoOptions.lastOrNull()
    }

    /** Distinct audio tracks, original first. */
    val audioTracks: List<AudioTrack>
        get() = audioOptions.distinctBy { it.trackId }
            .map { AudioTrack(it.trackId, it.trackName, it.isOriginal) }

    /** The track played by default: the original audio, never an auto-dub. */
    private fun defaultTrackId(): String? =
        (audioOptions.firstOrNull { it.isOriginal } ?: audioOptions.firstOrNull())?.trackId

    /** Bitrates (best first) offered by the given track; null means the default track. */
    fun kbpsFor(trackId: String?): List<Int> {
        val id = resolveTrack(trackId)
        return audioOptions.filter { it.trackId == id }.map { it.kbps }.distinct().sortedDescending()
    }

    private fun resolveTrack(trackId: String?): String? =
        trackId?.takeIf { wanted -> audioOptions.any { it.trackId == wanted } } ?: defaultTrackId()

    /**
     * Audio for the chosen [trackId] (null or unknown = original track) at the closest bitrate
     * at or below [kbps] (null = best).
     */
    fun pickAudio(kbps: Int?, trackId: String? = null): AudioOption? {
        val id = resolveTrack(trackId)
        val forTrack = audioOptions.filter { it.trackId == id }.ifEmpty { audioOptions }
            .sortedByDescending { it.kbps }
        if (kbps == null) return forTrack.firstOrNull()
        return forTrack.firstOrNull { it.kbps <= kbps } ?: forTrack.lastOrNull()
    }

    companion object {
        const val DEFAULT_MAX_HEIGHT = 720
    }
}

/**
 * Live YouTube / YouTube Music data (search, feeds) and stream URL resolution,
 * backed by NewPipeExtractor. All calls are main-safe suspend functions returning [Result].
 */
object YouTubeRepository {

    private val youtube get() = ServiceList.YouTube

    private var appliedCountry: String = "US"

    private val initialized: Boolean by lazy {
        val country = AppSettings.contentCountry.value
        appliedCountry = country + "|" + AppSettings.appLanguage.value
        NewPipe.init(
            NewPipeDownloader.instance,
            Localization(ContentRegions.languageFor(country), country),
            ContentCountry(country)
        )
        true
    }

    /** Loads the extractor in the background so the first screen does not pay for it. */
    suspend fun warmUp() {
        io { initialized }
    }

    /** Switches the content country / language (Settings) and drops caches filled for the old one. */
    suspend fun applyRegion(country: String): Result<Unit> = io {
        check(initialized)
        val key = country + "|" + AppSettings.appLanguage.value
        if (key != appliedCountry) {
            appliedCountry = key
            NewPipe.setupLocalization(
                Localization(ContentRegions.languageFor(country), country),
                ContentCountry(country)
            )
            feedCache.clear()
            shelfCache.clear()
            subFeedCache = null
            cachedInfo = null
        }
    }

    /** The most recent StreamInfo, shared by playback, downloads and the watch page. */
    @Volatile
    private var cachedInfo: Pair<String, StreamInfo>? = null

    private val infoInFlight = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<StreamInfo>>()

    /**
     * The extractor's full info for a video. The watch page and the player ask for the same video
     * at the same moment; the second caller waits for the first request instead of making its own.
     */
    private fun streamInfo(watchUrl: String, forceRefresh: Boolean = false): StreamInfo {
        if (!forceRefresh) {
            cachedInfo?.takeIf { it.first == watchUrl }?.let { return it.second }
        } else {
            if (cachedInfo?.first == watchUrl) cachedInfo = null
        }
        val mine = java.util.concurrent.CompletableFuture<StreamInfo>()
        val running = if (!forceRefresh) infoInFlight.putIfAbsent(watchUrl, mine) else null
        if (running != null) {
            return try {
                running.get()
            } catch (e: java.util.concurrent.ExecutionException) {
                throw e.cause ?: e
            }
        }
        try {
            val info = StreamInfo.getInfo(youtube, watchUrl)
            cachedInfo = watchUrl to info
            mine.complete(info)
            return info
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
            throw e
        } finally {
            infoInFlight.remove(watchUrl)
        }
    }

    // Caches live in process memory: they last until the app is fully closed (process killed),
    // and are only replaced by an explicit refresh (Retry / pull-to-refresh).
    private const val SHORTS_BATCH = 4
    private const val SHORTS_PER_CHANNEL = 12
    private class Cached<T>(val at: Long, val value: T)
    private class LruStore<K, V>(private val maxEntries: Int) {
        private val map = object : java.util.LinkedHashMap<K, V>(maxEntries + 1, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxEntries
        }
        @Synchronized operator fun get(key: K): V? = map[key]
        @Synchronized operator fun set(key: K, value: V) { map[key] = value }
        @Synchronized fun clear() { map.clear() }
    }
    private val feedCache = LruStore<String, Cached<Paged<MediaItemModel>>>(maxEntries = 12)
    private val shelfCache = LruStore<String, Cached<List<MusicShelf>>>(maxEntries = 12)

    /** Cached home feed for [category], if loaded earlier in this process. */
    fun cachedVideoFeed(category: String): Paged<MediaItemModel>? =
        feedCache[category]?.value

    /** Cached music shelves for [mood], if loaded earlier in this process. */
    fun cachedMusicShelves(mood: String): List<MusicShelf>? =
        shelfCache[mood]?.value

    /** Last video feed loaded, used for "Up next" suggestions on the watch screen. */
    @Volatile
    var lastVideoFeed: List<MediaItemModel> = emptyList()
        private set

    /**
     * Home feed (category "All") or a topic feed. For "All", recent uploads from
     * [personalChannels] (subscriptions / most-watched) are mixed in with trending videos.
     */
    suspend fun videoFeedPaged(
        category: String,
        personalChannels: List<String>,
        refresh: Boolean = false
    ): Result<Paged<MediaItemModel>> = io {
        if (!refresh) cachedVideoFeed(category)?.let { cached ->
            lastVideoFeed = cached.items
            return@io cached
        }
        if (category == "All" && GoogleAccount.isSignedIn()) {
            personalHome()?.let { personal ->
                lastVideoFeed = personal.items
                feedCache[category] = Cached(System.currentTimeMillis(), personal)
                return@io personal
            }
        }
        // A topic chip from the signed-in Home feed: YouTube's own feed for that topic.
        _homeChips.value.firstOrNull { it.first == category && it.second.isNotEmpty() }?.let { (_, token) ->
            runCatching { webFeed(YouTubeWeb.continuation(token)) }.getOrNull()?.takeIf { it.items.isNotEmpty() }?.let {
                lastVideoFeed = it.items
                feedCache[category] = Cached(System.currentTimeMillis(), it)
                return@io it
            }
        }
        // Explore destinations: the topic's own YouTube page.
        YouTubeWeb.exploreDestinations.firstOrNull { it.first == category }?.let { (_, browseId) ->
            runCatching { webFeed(YouTubeWeb.explore(browseId)) }.getOrNull()?.takeIf { it.items.size >= 4 }?.let {
                lastVideoFeed = it.items
                feedCache[category] = Cached(System.currentTimeMillis(), it)
                return@io it
            }
        }
        val query = if (category == "All") "trending videos today" else category
        val base = searchPaged(query, YoutubeSearchQueryHandlerFactory.VIDEOS) { videoOnly(it) }
        val first = if (category == "All" && personalChannels.isNotEmpty()) {
            val personal = coroutineScope {
                personalChannels.take(3).map { name ->
                    async {
                        runCatching {
                            searchItems(name, YoutubeSearchQueryHandlerFactory.VIDEOS, minSeconds = 61)
                                .filter { it.artistOrChannel.equals(name, ignoreCase = true) }
                                .take(3)
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll()
            }
            interleave(personal + listOf(base.items)).distinctBy { it.id }
        } else {
            base.items
        }
        Paged(first, base.loadMore).also {
            lastVideoFeed = it.items
            feedCache[category] = Cached(System.currentTimeMillis(), it)
        }
    }

    /** Typed, paged search (videos, channels, playlists). */
    /** The signed-in account's own Home feed; null when it is empty or the request fails. */
    private fun personalHome(): Paged<MediaItemModel>? {
        val first = runCatching { YouTubeWeb.home() }.getOrNull() ?: return null
        if (first.chips.size > 1) _homeChips.value = first.chips
        return if (first.items.size >= 4) webFeed(first) else null
    }

    private fun webFeed(feed: YouTubeWeb.Feed): Paged<MediaItemModel> = Paged(feed.items, feed.continuation?.let { token ->
        { withContext(Dispatchers.IO) { runCatching { webFeed(YouTubeWeb.continuation(token)) } } }
    })

    private val _homeChips = kotlinx.coroutines.flow.MutableStateFlow<List<Pair<String, String>>>(emptyList())
    /** Topic chips of the signed-in Home feed (label to feed token); empty when signed out. */
    val homeChips: kotlinx.coroutines.flow.StateFlow<List<Pair<String, String>>> = _homeChips

    /** Search with YouTube's filters (sort, upload date, length, features). */
    suspend fun filteredSearch(query: String, type: SearchType, filters: YouTubeWeb.SearchFilters): Result<Paged<SearchResult>> = io {
        fun page(p: YouTubeWeb.ResultPage): Paged<SearchResult> = Paged(p.results, p.continuation?.let { token ->
            { io { page(YouTubeWeb.searchMore(token)) } }
        })
        page(YouTubeWeb.search(query, filters, videosOnly = type == SearchType.VIDEOS)).also {
            check(it.items.isNotEmpty()) { "No results" }
        }
    }

    /** One channel tab (Home, Shorts, Live, Releases, Posts...), or a sort chip / next page via [continuation]. */
    suspend fun channelTab(channelUrl: String, tab: YouTubeWeb.ChannelTab, continuation: String? = null): Result<YouTubeWeb.TabPage> = io {
        if (continuation != null) return@io YouTubeWeb.channelTabMore(continuation)
        val id = Regex("""/channel/(UC[\w-]{22})""").find(channelUrl)?.groupValues?.get(1)
            ?: AccountApi.channelId(channelUrl) ?: error("Channel not found")
        YouTubeWeb.channelTab(id, tab)
    }

    /** Title and channel of a short that came from the Shorts sequence without them. */
    suspend fun shortInfo(videoId: String): Result<MediaItemModel> = io {
        YouTubeWeb.reelInfo(videoId) ?: error("No details for this short")
    }

    /** Forget cached feeds, e.g. after signing in or out. */
    fun clearFeedCaches() {
        feedCache.clear()
        subFeedCache = null
        shortsCache.set(null)
    }

    suspend fun search(query: String, type: SearchType): Result<Paged<SearchResult>> = io {
        searchPaged(query, type.filter) { toSearchResult(it, type.isMusic) }
    }

    private val shortsCache = java.util.concurrent.atomic.AtomicReference<Paged<MediaItemModel>?>(null)

    /** The cached Shorts feed, if loaded earlier in this process. */
    fun cachedShorts(): Paged<MediaItemModel>? = shortsCache.get()

    /**
     * The Shorts feed: YouTube's own Shorts sequence (recommended Shorts, personal when signed in)
     * started from a popular short, with a few Shorts from your channels mixed in now and then.
     * When the sequence ends or fails, "#shorts" search pages continue it.
     */
    suspend fun shortsFeed(
        seedChannels: List<String> = emptyList(),
        refresh: Boolean = false
    ): Result<Paged<MediaItemModel>> = io {
        if (!refresh) shortsCache.get()?.let { return@io it }

        coroutineScope {
            val search = async { runCatching { fetchShortsSearchPaged("#shorts") }.getOrNull() }
            // Only a couple of your channels, one short each: the feed is recommendations first.
            val channels = seedChannels.filter { it.isNotBlank() }.distinct().shuffled().take(3)
            val fromChannels = channels.map { url ->
                async { runCatching { channelShorts(url).shuffled().take(1) }.getOrDefault(emptyList()) }
            }
            val searchPaged = search.await()
            val searchItems = searchPaged?.items.orEmpty().shuffled()
            val seed = searchItems.firstOrNull()
            val sequence = seed?.let { runCatching { YouTubeWeb.reelSequence(it.id, null) }.getOrNull() }
            val recommended = sequence?.first.orEmpty().map(::bareShort)
            val mine = fromChannels.awaitAll().flatten()

            val base = (listOfNotNull(seed) + recommended.ifEmpty { searchItems.drop(1) }).distinctBy { it.id }
            val initial = mixIn(base, mine, every = 5)
            check(initial.isNotEmpty()) { "No Shorts available right now" }

            val fallback = searchPaged?.loadMore
            val token = sequence?.second
            val paged = if (token != null) {
                Paged(initial, reelPages(token, fallback))
            } else {
                withReelSequence(Paged(initial, fallback))
            }
            shortsCache.set(paged)
            paged
        }
    }

    private fun bareShort(id: String): MediaItemModel =
        InnerTube.build(id, "", "", "https://i.ytimg.com/vi/$id/oar2.jpg", "", 0, "", "", "", false).copy(isShort = true)

    /** [extra] spread through [base], one after every [every] items. */
    internal fun <T> mixIn(base: List<T>, extra: List<T>, every: Int): List<T> {
        if (extra.isEmpty()) return base
        val out = ArrayList<T>(base.size + extra.size)
        var next = 0
        base.forEachIndexed { i, item ->
            out += item
            if ((i + 1) % every == 0 && next < extra.size) out += extra[next++]
        }
        while (next < extra.size) out += extra[next++]
        return out
    }

    /** Following pages of the Shorts sequence from [token], then [fallback] when it runs out. */
    private fun reelPages(token: String, fallback: (suspend () -> Result<Paged<MediaItemModel>>)?): suspend () -> Result<Paged<MediaItemModel>> = {
        io {
            val (ids, more) = runCatching { YouTubeWeb.reelSequence(null, token) }.getOrDefault(emptyList<String>() to null)
            if (ids.isEmpty()) {
                fallback?.invoke()?.getOrThrow() ?: Paged(emptyList(), null)
            } else {
                Paged(ids.map(::bareShort), if (more != null) reelPages(more, fallback) else fallback)
            }
        }
    }

    /**
     * Continues the feed with the Shorts player's own sequence (what YouTube plays after a short),
     * falling back to more search pages when the sequence ends or fails.
     */
    private fun withReelSequence(base: Paged<MediaItemModel>): Paged<MediaItemModel> {
        val seed = base.items.firstOrNull()?.id ?: return base
        fun next(token: String?, fallback: (suspend () -> Result<Paged<MediaItemModel>>)?): suspend () -> Result<Paged<MediaItemModel>> = {
            io {
                val (ids, more) = runCatching { YouTubeWeb.reelSequence(if (token == null) seed else null, token) }.getOrDefault(emptyList<String>() to null)
                if (ids.isEmpty()) {
                    fallback?.invoke()?.getOrThrow() ?: Paged(emptyList(), null)
                } else {
                    val items = ids.map { id -> InnerTube.build(id, "", "", "https://i.ytimg.com/vi/$id/oar2.jpg", "", 0, "", "", "", false).copy(isShort = true) }
                    Paged(items, if (more != null) next(more, fallback) else fallback)
                }
            }
        }
        return Paged(base.items, next(null, base.loadMore))
    }

    private fun fetchShortsSearchPaged(query: String): Paged<MediaItemModel> {
        val handler = youtube.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val first = SearchInfo.getInfo(youtube, handler)
        fun mapShort(item: InfoItem): MediaItemModel? {
            if (item !is StreamInfoItem) return null
            val isShort = item.isShortFormContent || item.duration in 1..90 || item.name.contains("#short", ignoreCase = true)
            if (!isShort && item.duration > 90) return null
            return toModel(item, false)?.copy(isShort = true)
        }
        return pagedOf(
            first.relatedItems.mapNotNull { mapShort(it) },
            if (first.hasNextPage()) first.nextPage else null
        ) { page ->
            val more = SearchInfo.getMoreItems(youtube, handler, page)
            more.items.mapNotNull { mapShort(it) } to (if (more.hasNextPage()) more.nextPage else null)
        }
    }

    /** The first page of one channel's Shorts tab. */
    private fun channelShorts(channelUrl: String): List<MediaItemModel> {
        val info = ChannelInfo.getInfo(youtube, channelUrl)
        val tab = info.tabs.firstOrNull { ChannelTabs.SHORTS in it.contentFilters } ?: return emptyList()
        val avatar = bestImage(info.avatars)
        val canonical = "https://www.youtube.com/channel/${info.id}"
        return ChannelTabInfo.getInfo(youtube, tab).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .take(SHORTS_PER_CHANNEL)
            .mapNotNull { toModel(it, false, info.name, avatar, canonical)?.copy(isShort = true) }
    }

    /** A radio queue plus the token for its next page (null when it cannot be extended). */
    class Radio(val tracks: List<MediaItemModel>, val continuation: String?)

    // YT Music's `next` answer for a track, shared by the UP NEXT, LYRICS and RELATED tabs.
    private val musicWatchCache = java.util.concurrent.ConcurrentHashMap<String, YTMusicApi.WatchInfo>()

    private fun musicWatch(videoId: String): YTMusicApi.WatchInfo =
        musicWatchCache[videoId] ?: YTMusicApi.watch(videoId).also {
            if (musicWatchCache.size > 50) musicWatchCache.clear()
            musicWatchCache[videoId] = it
        }

    /** "Start radio": songs related to [item], for a music queue. */
    suspend fun radio(item: MediaItemModel): Result<List<MediaItemModel>> = startRadio(item).map { it.tracks }

    /**
     * The official YT Music radio for [item] ("RDAMVM" + id), falling back to the YouTube radio and
     * then to the watch page's related videos.
     */
    suspend fun startRadio(item: MediaItemModel): Result<Radio> = io {
        val music = runCatching { musicWatch(item.id) }.getOrNull()
        val musicTracks = music?.queue.orEmpty().filter { it.id != item.id }
        if (musicTracks.isNotEmpty()) return@io Radio(musicTracks.map(::withColors), music?.continuation)
        val innerTubeRadio = runCatching { InnerTube.radio(item.id).items }.getOrNull().orEmpty()
        if (innerTubeRadio.isNotEmpty()) {
            return@io Radio(innerTubeRadio.filter { it.id != item.id }, null)
        }
        val related = details(item.watchUrlOrSource()).getOrThrow().related
        Radio(related.map { it.copy(contentType = MediaContentType.MUSIC_TRACK) }, null)
            .also { check(it.tracks.isNotEmpty()) { "No related songs found" } }
    }

    /** The next page of a radio started with [startRadio]. */
    suspend fun moreRadio(continuation: String): Result<Radio> = io {
        val page = YTMusicApi.moreRadio(continuation)
        Radio(page.items.map(::withColors), page.continuation)
    }

    /** The RELATED tab: YT Music's shelves for [item], or its radio as a single shelf when unavailable. */
    suspend fun musicRelated(item: MediaItemModel): Result<List<YTMusicApi.Section>> = io {
        val sections = runCatching {
            musicWatch(item.id).relatedBrowseId?.let { YTMusicApi.related(it) }
        }.getOrNull().orEmpty().map { section ->
            section.copy(cards = section.cards.map { card -> card.copy(track = card.track?.let(::withColors)) })
        }
        sections.ifEmpty {
            val tracks = radio(item).getOrThrow()
            listOf(YTMusicApi.Section("You might also like", tracks.map {
                YTMusicApi.Card(YTMusicApi.CardKind.SONG, it.title, it.artistOrChannel, it.artworkUrl, track = it)
            }, isSongList = true))
        }
    }

    /** Album, artists and credits id of a song, as the YT Music song menu shows them. */
    suspend fun musicTrackInfo(item: MediaItemModel): Result<YTMusicApi.TrackInfo> = io {
        musicWatch(item.id).trackInfo ?: error("YT Music has no details for this song")
    }

    // ---- YT Music pages (home, explore, artist, album, library) ----

    private val musicPageCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, YTMusicPages.Page>>()

    fun cachedMusicPage(browseId: String, params: String = ""): YTMusicPages.Page? =
        musicPageCache["$browseId|$params"]?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000L }?.second

    suspend fun musicPage(browseId: String, params: String = "", refresh: Boolean = false): Result<YTMusicPages.Page> = io {
        if (!refresh) cachedMusicPage(browseId, params)?.let { return@io it }
        YTMusicPages.browse(browseId, params).also {
            check(it.sections.isNotEmpty() || it.header != null) { "Nothing to show here yet" }
            musicPageCache["$browseId|$params"] = System.currentTimeMillis() to it
        }
    }

    /** YT Music's own search, one result tab at a time. */
    suspend fun musicSearch(query: String, filter: YTMusicPages.SearchFilter): Result<YTMusicPages.Page> = io {
        YTMusicPages.search(query, filter).also { check(it.sections.isNotEmpty()) { "No results" } }
    }

    suspend fun musicPageMore(continuation: String): Result<YTMusicPages.Page> = io { YTMusicPages.more(continuation) }

    /** The tracks of an album, mix or radio playlist, ready to play. */
    suspend fun musicPlaylistTracks(playlistId: String): Result<List<MediaItemModel>> = io {
        YTMusicPages.watchPlaylist(playlistId).also { check(it.isNotEmpty()) { "This list has no playable songs" } }
    }

    /** "View song credits". */
    suspend fun songCredits(creditsBrowseId: String): Result<List<YTMusicApi.CreditSection>> = io {
        YTMusicApi.credits(creditsBrowseId).also { check(it.isNotEmpty()) { "No credits for this song" } }
    }

    /** The playlist page URL of a YT Music album ("MPREb_..."). */
    suspend fun albumPlaylistUrl(albumId: String): Result<String> = io {
        val id = YTMusicApi.albumPlaylistId(albumId) ?: error("Album not found")
        "https://www.youtube.com/playlist?list=$id"
    }

    /** YT Music's own (unsynced) lyrics for [videoId], or null when it has none. */
    fun musicTimedLyrics(videoId: String): Pair<List<com.nexusstream.app.model.LyricLine>, String>? = runCatching {
        musicWatch(videoId).lyricsBrowseId?.let { YTMusicApi.timedLyrics(it) }
    }.getOrNull()

    fun musicLyrics(videoId: String): YTMusicApi.PlainLyrics? = runCatching {
        musicWatch(videoId).lyricsBrowseId?.let { YTMusicApi.lyrics(it) }
    }.getOrNull()

    private fun withColors(item: MediaItemModel): MediaItemModel {
        val (primary, secondary) = colorsFor(item.id)
        return item.copy(dominantColor = primary, secondaryColor = secondary)
    }

    private fun MediaItemModel.watchUrlOrSource(): String = sourceUrl.ifEmpty { "https://www.youtube.com/watch?v=$id" }

    /** A single file a Cast receiver can play: the best combined video (up to 720p), else audio. */
    suspend fun castTarget(watchUrl: String, audioOnly: Boolean): Result<CastTarget> = io {
        val info = streamInfo(watchUrl)
        val video = if (audioOnly) null else info.videoStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && it.height in 1..720 }.maxByOrNull { it.height }


        if (video != null) {
            CastTarget(video.content, video.format?.mimeType ?: "video/mp4")
        } else {
            val audio = info.audioStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
    
                .maxByOrNull(::audioScore)
                ?: error("Nothing this video offers can be cast")
            CastTarget(audio.content, audio.format?.mimeType ?: "audio/mp4")
        }
    }

    /** Autocomplete suggestions for a partially typed query. */
    suspend fun suggestions(query: String): Result<List<String>> = io {
        youtube.suggestionExtractor.suggestionList(query).orEmpty()
    }

    /** Channel header plus first page of videos. [channelUrl] is a youtube.com/channel/... URL. */
    suspend fun channelPage(channelUrl: String, music: Boolean = false): Result<ChannelPage> = io {
        val info = ChannelInfo.getInfo(youtube, channelUrl)
        val avatar = bestImage(info.avatars)
        val canonicalUrl = "https://www.youtube.com/channel/${info.id}"
        val videosTab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
        val playlistsTab = info.tabs.firstOrNull { ChannelTabs.PLAYLISTS in it.contentFilters }

        val videos = videosTab?.let { tab ->
            tabPaged(tab) { item ->
                (item as? StreamInfoItem)
                    ?.takeIf { it.duration > 0 && (music || !it.isShortFormContent) }
                    ?.let { toModel(it, music, info.name, avatar, canonicalUrl) }
            }
        } ?: Paged(emptyList(), null)

        ChannelPage(
            name = info.name,
            url = canonicalUrl,
            avatarUrl = avatar,
            bannerUrl = info.banners.maxByOrNull { it.width }?.url.orEmpty(),
            subscribers = formatCount(info.subscriberCount),
            description = info.description.orEmpty(),
            videos = videos,
            loadPlaylists = {
                io {
                    playlistsTab?.let { tab ->
                        tabPaged(tab) { item -> (item as? PlaylistInfoItem)?.let { toPlaylistResult(it) } }
                    } ?: Paged(emptyList(), null)
                }
            }
        )
    }

    /** Playlist header plus first page of videos. */
    suspend fun playlistPage(playlistUrl: String, music: Boolean = false): Result<PlaylistPage> = io {
        val info = PlaylistInfo.getInfo(youtube, playlistUrl)
        fun map(item: StreamInfoItem): MediaItemModel? = if (item.duration > 0) toModel(item, music) else null
        val videos = pagedOf(
            info.relatedItems.mapNotNull { map(it) },
            if (info.hasNextPage()) info.nextPage else null
        ) { page ->
            val more = PlaylistInfo.getMoreItems(youtube, playlistUrl, page)
            more.items.mapNotNull { map(it) } to (if (more.hasNextPage()) more.nextPage else null)
        }
        PlaylistPage(
            title = info.name,
            owner = info.uploaderName.orEmpty(),
            videoCount = info.streamCount,
            thumbnailUrl = bestImage(info.thumbnails),
            videos = videos
        )
    }

    /**
     * Newest uploads from subscriptions. When signed in with Google, retrieves the authentic
     * personalized subscriptions feed from YouTube's InnerTube API. Otherwise falls back to
     * public RSS feeds of locally saved subscriptions.
     */
    suspend fun subscriptionFeed(
        subs: List<LibraryRepository.ChannelSub>,
        refresh: Boolean = false
    ): Result<List<MediaItemModel>> = io {
        if (!refresh) cachedSubscriptionFeed(subs)?.let { return@io it }

        if (GoogleAccount.isSignedIn()) {
            val googleFeed = runCatching { InnerTube.subscriptions().items }.getOrNull().orEmpty()
            if (googleFeed.isNotEmpty()) {
                // Sync subscribed channels in background
                runCatching {
                    val channels = InnerTube.channels()
                    if (channels.isNotEmpty()) {
                        LibraryRepository.syncGoogleSubscriptions(channels)
                    } else {
                        LibraryRepository.syncSubscriptionsFromFeed(googleFeed)
                    }
                }
                val sorted = googleFeed.distinctBy { it.id }.sortedByDescending { it.publishedAtMs }
                subFeedCache = subKey(subs) to sorted
                return@io sorted
            }
        }

        if (subs.isEmpty()) {
            return@io emptyList()
        }

        coroutineScope {
            subs.take(30).map { sub ->
                async { runCatching { feedFor(sub) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten()
        }.distinctBy { it.id }.sortedByDescending { it.publishedAtMs }
            .also { subFeedCache = subKey(subs) to it }
    }

    /** Forces a refresh of subscribed channels from Google Account into LibraryRepository. */
    suspend fun refreshGoogleSubscriptions(): Result<List<LibraryRepository.ChannelSub>> = io {
        if (!GoogleAccount.isSignedIn()) return@io emptyList()
        val channels = runCatching { InnerTube.channels() }.getOrDefault(emptyList())
        if (channels.isNotEmpty()) {
            LibraryRepository.syncGoogleSubscriptions(channels)
            channels
        } else {
            val feed = runCatching { InnerTube.subscriptions().items }.getOrDefault(emptyList())
            LibraryRepository.syncSubscriptionsFromFeed(feed)
            LibraryRepository.subscriptions.value
        }
    }

    private var subFeedCache: Pair<String, List<MediaItemModel>>? = null
    private fun subKey(subs: List<LibraryRepository.ChannelSub>) = subs.joinToString("|") { it.name + it.channelUrl }

    /** The subscriptions feed loaded earlier in this process for exactly these channels. */
    fun cachedSubscriptionFeed(subs: List<LibraryRepository.ChannelSub>): List<MediaItemModel>? =
        subFeedCache?.takeIf { it.first == subKey(subs) }?.second

    suspend fun searchSongs(query: String): Result<List<MediaItemModel>> = io {
        searchItems(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, minSeconds = 1)
    }

    /** Home shelves for YT Music; [mood] (blank for none) narrows every shelf to that mood. */
    suspend fun musicShelves(mood: String, refresh: Boolean = false): Result<List<MusicShelf>> = io {
        if (!refresh) cachedMusicShelves(mood)?.let { return@io it }
        val shelves = listOf(
            Triple("Quick Picks", "Songs trending right now", "top hits this week"),
            Triple("Top Charts", "Most played worldwide", "top 50 global songs"),
            Triple("Chill & Focus", "Easy listening", "chill focus songs")
        ).map { (title, subtitle, base) ->
            val query = if (mood.isBlank()) base else "$mood songs"
            Triple(title, subtitle, query)
        }
        coroutineScope {
            val songShelves = shelves.map { (title, subtitle, query) ->
                async {
                    MusicShelf(
                        title,
                        subtitle,
                        runCatching {
                            searchItems(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, minSeconds = 1)
                        }.getOrDefault(emptyList())
                    )
                }
            }
            val albums = async {
                val query = if (mood.isBlank()) "new albums ${java.time.Year.now().value}" else "$mood albums"
                MusicShelf(
                    "Albums for you",
                    "Fresh releases to explore",
                    runCatching { searchAlbums(query) }.getOrDefault(emptyList())
                )
            }
            songShelves.awaitAll() + albums.await()
        }.filter { it.items.isNotEmpty() }
            .also { check(it.isNotEmpty()) { "No music results" } }
            .also { shelfCache[mood] = Cached(System.currentTimeMillis(), it) }
    }

    /** Streams resolved ahead of time (the next Shorts), each used once and only while fresh. */
    private class Prefetch(val atMs: Long, val stream: kotlinx.coroutines.Deferred<ResolvedStream>)
    private val prefetched = java.util.concurrent.ConcurrentHashMap<String, Prefetch>()
    // Two at a time, so preloading never crowds out the video you are about to watch.
    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val prefetchScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    private const val PREFETCH_TTL_MS = 30 * 60_000L

    /**
     * Starts resolving [watchUrl] in the background so playing it later starts at once. Does
     * nothing when it is already resolved or on the way.
     */
    fun prefetchStream(watchUrl: String) {
        if (!initialized || watchUrl.isBlank()) return
        val now = System.currentTimeMillis()
        prefetched.entries.removeIf { now - it.value.atMs > PREFETCH_TTL_MS }
        if (prefetched.size > 12) return
        prefetched.computeIfAbsent(watchUrl) {
            Prefetch(now, prefetchScope.async { fetchStream(watchUrl) })
        }
    }

    /** The preloaded streams of [watchUrl] (waiting for them if still loading), leaving them for playback. */
    suspend fun prefetchedStream(watchUrl: String): ResolvedStream? =
        prefetched[watchUrl]?.let { runCatching { it.stream.await() }.getOrNull() }

    /** Resolves fresh direct stream URLs for a watch URL. Call right before playback. */
    suspend fun resolveStream(watchUrl: String, forceRefresh: Boolean = false): Result<ResolvedStream> = io {
        if (forceRefresh) {
            prefetched.remove(watchUrl)
            if (cachedInfo?.first == watchUrl) cachedInfo = null
        }
        val cached = if (!forceRefresh) {
            prefetched.remove(watchUrl)
            ?.takeIf { System.currentTimeMillis() - it.atMs < PREFETCH_TTL_MS }
            ?.let { runCatching { it.stream.await() }.getOrNull() }
        } else null
        cached ?: fetchStream(watchUrl, forceRefresh)
    }

    private fun fetchStream(watchUrl: String, forceRefresh: Boolean = false): ResolvedStream {
        val info = try {
            streamInfo(watchUrl, forceRefresh)
        } catch (e: org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException) {
            throw IllegalStateException("Age-restricted video: YouTube only serves it to signed-in adult accounts, which this app cannot do yet")
        } catch (e: org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException) {
            throw IllegalStateException("YouTube is blocking video playback from this network right now. Try again later or on another network (for example mobile data instead of Wi-Fi).")
        }
        try {
            return extractResolvedStream(info)
        } catch (e: Throwable) {
            if (cachedInfo?.first == watchUrl) {
                cachedInfo = null
            }
            throw e
        }
    }

    internal fun extractResolvedStream(info: StreamInfo): ResolvedStream {
        // YouTube serves several language tracks (original, auto-dubbed, audio description).
        // Keep the best stream per (track, bitrate) and list the original track first.
        val progressiveAudio = info.audioStreams.filter { it.content.isNotEmpty() && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.content.startsWith("http")) }

            .groupBy { it.audioTrackId to it.averageBitrate }
            .mapValues { (_, streams) -> streams.maxByOrNull(::audioScore)!! }
            .values
            .map { stream ->
                AudioOption(
                    kbps = stream.averageBitrate,
                    url = stream.content,
                    trackId = stream.audioTrackId,
                    trackName = trackLabel(stream),
                    isOriginal = isOriginalTrack(stream)
                )
            }
            .sortedWith(
                compareByDescending<AudioOption> { it.isOriginal }
                    .thenBy { it.trackId.orEmpty() }
                    .thenByDescending { it.kbps }
            )
        val progressiveMuxed = info.videoStreams.filter { it.content.isNotEmpty() && (it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP || it.content.startsWith("http")) }
        val muxed = progressiveMuxed.maxByOrNull { it.height }?.content


        val audio = if (progressiveAudio.isNotEmpty()) {
            progressiveAudio
        } else {
            // If no separate audio streams exist (common on Reels/Shorts), use muxed progressive streams as audio options.
            progressiveMuxed.map { stream ->
                AudioOption(
                    kbps = 128,
                    url = stream.content,
                    trackId = "muxed_${stream.height}p",
                    trackName = "Default",
                    isOriginal = true
                )
            }
        }.sortedWith(
            compareByDescending<AudioOption> { it.isOriginal }
                .thenBy { it.trackId.orEmpty() }
                .thenByDescending { it.kbps }
        )
        val videoOnly = info.videoOnlyStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && it.height > 0 }
            .groupBy { it.height }
            .mapValues { (_, streams) -> streams.maxByOrNull(::videoScore)!! }
            .values
            .sortedByDescending { it.height }
            .map { VideoOption(it.height, it.content) }
        val video = if (videoOnly.isNotEmpty()) {
            videoOnly
        } else {
            progressiveMuxed
                .filter { it.height > 0 }
                .sortedByDescending { it.height }
                .map { VideoOption(it.height, it.content) }
        }
        val hls = info.hlsUrl?.takeIf { it.isNotBlank() }
        check(audio.isNotEmpty() || muxed != null || hls != null) { "No playable audio stream" }




        val dashVideo = info.videoOnlyStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && hasIndex(it.initEnd, it.indexEnd) }
            .mapNotNull { v ->
                DashTrack(
                    itag = v.itag, mimeType = v.format?.mimeType ?: return@mapNotNull null, codecs = v.codec.orEmpty(),
                    bitrate = v.bitrate, url = v.content, initStart = v.initStart, initEnd = v.initEnd,
                    indexStart = v.indexStart, indexEnd = v.indexEnd, width = v.width, height = v.height, fps = v.fps
                )
            }
            .filter { it.codecs.isNotEmpty() && it.height > 0 }
        val dashAudio = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && hasIndex(it.initEnd, it.indexEnd) }
            .mapNotNull { a ->
                DashTrack(
                    itag = a.itag, mimeType = a.format?.mimeType ?: return@mapNotNull null, codecs = a.codec.orEmpty(),
                    bitrate = a.bitrate.takeIf { it > 0 } ?: (a.averageBitrate * 1000), url = a.content,
                    initStart = a.initStart, initEnd = a.initEnd, indexStart = a.indexStart, indexEnd = a.indexEnd,
                    audioTrackId = a.audioTrackId, language = a.audioLocale?.toLanguageTag().orEmpty()
                )
            }
            .filter { it.codecs.isNotEmpty() }
        return ResolvedStream(
            audioOptions = audio, videoOptions = video, subtitles = subtitleOptions(info), hlsUrl = hls, muxedUrl = muxed,
            dashVideo = dashVideo, dashAudio = dashAudio, durationSec = info.duration
        )
    }

    /**
     * Every file that can be saved offline, best video first, then audio-only renditions.
     *
     * YouTube only serves a single-file (video + audio) stream up to 360p. Higher qualities come
     * as separate video-only and audio-only streams; those options carry [DownloadOption.audioUrl]
     * and are merged into one file after downloading (see [StreamMuxer]).
     */
    suspend fun downloadOptions(watchUrl: String): Result<List<DownloadOption>> = io {
        val info = streamInfo(watchUrl)
        val playableAudio = info.audioStreams

        // Save the original-language audio, not an auto-dub.
        val originalTrackId = (playableAudio.firstOrNull { isOriginalTrack(it) } ?: playableAudio.firstOrNull())?.audioTrackId
        val originalAudio = playableAudio.filter { it.audioTrackId == originalTrackId }
        val progressive = info.videoStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && it.height > 0 }
            .groupBy { it.height }
            .mapValues { (_, streams) -> streams.maxByOrNull(::videoScore)!! }
            .mapValues { (_, it) -> DownloadOption(videoLabel(it), it.content, it.format?.suffix ?: "mp4", true) }
        val merged = mergedDownloadOptions(info.videoOnlyStreams, originalAudio)
        // A ready-made single file beats merging at the same height.
        val videos = (merged + progressive).values.sortedByDescending { it.height }
        val audios = originalAudio
            .groupBy { it.averageBitrate }
            .mapValues { (_, streams) -> streams.maxByOrNull(::audioScore)!! }
            .values
            .sortedByDescending { it.averageBitrate }
            .map { DownloadOption("${it.averageBitrate} kbps audio", it.content, it.format?.suffix ?: "m4a", false) }
        (videos + audios).also { check(it.isNotEmpty()) { "No downloadable streams" } }
    }

    /**
     * Pairs each video-only height with an audio stream the phone's muxer can put in the same
     * container: H.264 MP4 + AAC (m4a) into MP4, or VP9 WebM + Opus WebM into WebM. AV1-only
     * heights are skipped because MediaMuxer can't write AV1 into MP4 on older Android versions.
     */
    internal fun mergedDownloadOptions(videoOnly: List<VideoStream>, audio: List<AudioStream>): Map<Int, DownloadOption> {
        val m4a = audio.filter { it.format?.suffix == "m4a" }.maxByOrNull { it.averageBitrate }
        val webmAudio = audio.filter { it.format?.suffix == "webm" }.maxByOrNull { it.averageBitrate }
        return videoOnly
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && it.height > 0 }
            .mapNotNull { video ->
                val codec = video.codec.orEmpty()
                val suffix = video.format?.suffix
                when {
                    suffix == "mp4" && codec.startsWith("avc1") && m4a != null -> Triple(video, m4a, "mp4")
                    suffix == "webm" && (codec.startsWith("vp9") || codec.startsWith("vp09")) && webmAudio != null ->
                        Triple(video, webmAudio, "webm")
                    else -> null
                }
            }
            .groupBy { it.first.height }
            .mapValues { (_, pairs) ->
                // Prefer the higher frame rate, then H.264 (plays everywhere).
                val (video, audioStream, ext) = pairs.maxByOrNull { (v, _, e) -> v.fps * 10 + (if (e == "mp4") 1 else 0) }!!
                DownloadOption(videoLabel(video), video.content, ext, true, audioUrl = audioStream.content)
            }
    }

    private fun videoLabel(s: VideoStream): String = if (s.fps > 30) "${s.height}p${s.fps} video" else "${s.height}p video"

    /** A full video item for a watch URL (used when a link or notification only gives the id). */
    suspend fun videoFromUrl(watchUrl: String): Result<MediaItemModel> = io {
        val id = VIDEO_ID.find(watchUrl)?.groupValues?.get(1) ?: error("Not a video link")
        val info = streamInfo(watchUrl)
        val (primary, secondary) = colorsFor(id)
        MediaItemModel(
            id = id,
            title = info.name,
            artistOrChannel = info.uploaderName.orEmpty(),
            audioUrl = "",
            videoUrl = "",
            artworkUrl = bestImage(info.thumbnails),
            durationMs = info.duration.coerceAtLeast(0) * 1000L,
            dominantColor = primary,
            secondaryColor = secondary,
            viewsOrPlays = formatCount(info.viewCount),
            channelAvatarUrl = bestImage(info.uploaderAvatars),
            publishDate = info.textualUploadDate.orEmpty(),
            sourceUrl = watchUrl,
            channelUrl = info.uploaderUrl.orEmpty(),
            isLive = info.streamType == StreamType.LIVE_STREAM
        )
    }

    /** Likes, views, description, tags and related videos for the watch page. */
    suspend fun details(watchUrl: String): Result<VideoDetails> = io {
        val info = streamInfo(watchUrl)
        VideoDetails(
            likes = info.likeCount,
            views = info.viewCount,
            uploadedText = info.textualUploadDate.orEmpty(),
            description = info.description?.content.orEmpty(),
            subscribers = info.uploaderSubscriberCount,
            tags = info.tags.orEmpty(),
            related = relatedFor(info),
            storyboard = info.previewFrames
                .filter { it.frameWidth >= 100 }
                .maxByOrNull { it.frameWidth }
                ?.let { Storyboard(it.urls, it) },
            chapters = info.streamSegments.orEmpty()
                .filter { !it.title.isNullOrBlank() }
                .map { Chapter(it.title, it.startTimeSeconds * 1000L) }
                .ifEmpty { chaptersFromDescription(info.description?.content.orEmpty(), info.duration * 1000L) }
        )
    }

    /**
     * Suggestions: the extractor's related list (often short or generic) mixed with videos that
     * match this video's title and more from the same channel, so the list is relevant and long.
     */
    private fun relatedFor(info: StreamInfo): List<MediaItemModel> {
        val own = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration > 60 && !it.isShortFormContent }
            .mapNotNull { toModel(it, isMusic = false) }
        val byTitle = runCatching {
            searchItems(info.name.replace(Regex("[\\[(].*?[\\])]"), " ").take(70), YoutubeSearchQueryHandlerFactory.VIDEOS, 60)
        }.getOrDefault(emptyList())
        val byChannel = runCatching {
            searchItems(info.uploaderName.orEmpty(), YoutubeSearchQueryHandlerFactory.VIDEOS, 60)
                .filter { it.artistOrChannel == info.uploaderName }
        }.getOrDefault(emptyList())
        return interleave(listOf(own, byTitle, byChannel))
            .distinctBy { it.id }
            .filter { it.id != info.id }
    }

    /** Tracks of an album / playlist, as playable music items. */
    suspend fun playlistTracks(playlistUrl: String): Result<List<MediaItemModel>> = io {
        PlaylistInfo.getInfo(youtube, playlistUrl).relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration > 0 }
            .mapNotNull { toModel(it, isMusic = true) }
            .distinctBy { it.id }
            .also { check(it.isNotEmpty()) { "This album has no playable tracks" } }
    }

    /**
     * Top-level comments for a video (paged), with the total count when YouTube reports it.
     * YouTube's own comments API comes first (Top / Newest, post, reply, like); NewPipe's
     * read-only comments stand in if it fails.
     */
    suspend fun comments(watchUrl: String, sort: CommentsApi.Sort = CommentsApi.Sort.TOP): Result<CommentsPage> = io {
        val videoId = Regex("""[?&]v=([\w-]{11})""").find(watchUrl)?.groupValues?.get(1)
        if (videoId != null) {
            runCatching {
                val first = CommentsApi.load(videoId, sort)
                // The header's sort tokens are what the website uses; prefer them for Newest.
                val page = if (sort == CommentsApi.Sort.NEWEST) {
                    first.sortTokens[CommentsApi.Sort.NEWEST]?.let { CommentsApi.more(it, videoId) } ?: first
                } else first
                check(page.comments.isNotEmpty() || page.totalCount == 0) { "no comments parsed" }
                fun paged(r: CommentsApi.Result): Paged<CommentModel> = Paged(
                    r.comments,
                    r.continuation?.let { token -> suspend { io { paged(CommentsApi.more(token, videoId)) } } }
                )
                CommentsPage(
                    totalCount = first.totalCount.takeIf { it >= 0 } ?: page.totalCount,
                    paged = paged(page),
                    createParams = if (GoogleAccount.isSignedIn()) first.createParams ?: CommentsApi.createParams(videoId) else null,
                    interactive = true
                )
            }.onSuccess { return@io it }
        }
        val info = CommentsInfo.getInfo(youtube, watchUrl)
        val paged = pagedOf(
            info.relatedItems.map { toComment(it, watchUrl, withReplies = true) },
            if (info.hasNextPage()) info.nextPage else null
        ) { page ->
            val more = CommentsInfo.getMoreItems(youtube, watchUrl, page)
            more.items.map { toComment(it, watchUrl, withReplies = true) } to
                (if (more.hasNextPage()) more.nextPage else null)
        }
        CommentsPage(totalCount = info.commentsCount, paged = paged)
    }

    private fun toComment(c: CommentsInfoItem, watchUrl: String, withReplies: Boolean): CommentModel {
        val repliesPage = c.replies
        return CommentModel(
            author = c.uploaderName.orEmpty(),
            avatarUrl = bestImage(c.uploaderAvatars),
            text = c.commentText?.content.orEmpty(),
            likes = c.likeCount,
            date = c.textualUploadDate.orEmpty(),
            pinned = c.isPinned,
            replyCount = c.replyCount,
            loadReplies = if (withReplies && repliesPage != null) {
                suspend {
                    io {
                        CommentsInfo.getMoreItems(youtube, watchUrl, repliesPage).items
                            .map { toComment(it, watchUrl, withReplies = false) }
                    }
                }
            } else {
                null
            }
        )
    }

    /** Caption tracks ExoPlayer can load (WebVTT / TTML), manual captions first. */
    private fun subtitleOptions(info: StreamInfo): List<SubtitleOption> = info.subtitles.orEmpty()
        .filter { it.content.isNotEmpty() }
        .mapNotNull { stream ->
            val mime = stream.format?.mimeType ?: return@mapNotNull null
            if (mime != "text/vtt" && mime != "application/ttml+xml") return@mapNotNull null
            SubtitleOption(
                languageTag = stream.languageTag.orEmpty(),
                label = stream.displayLanguageName.orEmpty().ifBlank { stream.languageTag.orEmpty() } +
                    if (stream.isAutoGenerated) " (auto-generated)" else "",
                url = stream.content,
                mimeType = mime,
                autoGenerated = stream.isAutoGenerated
            )
        }
        // Prefer WebVTT when a language is offered in both formats.
        .sortedBy { if (it.mimeType == "text/vtt") 0 else 1 }
        .distinctBy { it.languageTag to it.autoGenerated }
        .sortedWith(compareBy<SubtitleOption> { it.autoGenerated }.thenBy { it.label })

    // ---- internals ----

    private fun searchAlbums(query: String): List<MediaItemModel> {
        val handler = youtube.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS), "")
        return SearchInfo.getInfo(youtube, handler).relatedItems
            .filterIsInstance<PlaylistInfoItem>()
            .map { album ->
                val key = album.url.hashCode().toString().removePrefix("-")
                val (primary, secondary) = colorsFor(key)
                MediaItemModel(
                    id = "pl_$key",
                    title = album.name,
                    artistOrChannel = album.uploaderName.orEmpty(),
                    audioUrl = "",
                    videoUrl = "",
                    artworkUrl = bestImage(album.thumbnails),
                    durationMs = 0L,
                    dominantColor = primary,
                    secondaryColor = secondary,
                    contentType = MediaContentType.MUSIC_TRACK,
                    sourceUrl = album.url,
                    isCollection = true
                )
            }
    }


    private suspend fun <T> io(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        runCatching {
            check(initialized)
            block()
        }
    }

    private fun searchItems(query: String, filter: String, minSeconds: Long): List<MediaItemModel> {
        val handler = youtube.searchQHFactory.fromQuery(query, listOf(filter), "")
        val page = SearchInfo.getInfo(youtube, handler)
        val isMusic = filter != YoutubeSearchQueryHandlerFactory.VIDEOS
        return page.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .filter { it.duration >= minSeconds && !it.isShortFormContent }
            .mapNotNull { toModel(it, isMusic) }
            .distinctBy { it.id }
    }

    private fun <T> pagedOf(
        items: List<T>,
        next: Page?,
        fetchMore: (Page) -> Pair<List<T>, Page?>
    ): Paged<T> {
        val loader: (suspend () -> Result<Paged<T>>)? = next?.let { page ->
            suspend {
                io { fetchMore(page) }.map { (more, nextPage) -> pagedOf(more, nextPage, fetchMore) }
            }
        }
        return Paged(items, loader)
    }

    private fun <T> searchPaged(query: String, filter: String, map: (InfoItem) -> T?): Paged<T> {
        val handler = youtube.searchQHFactory.fromQuery(query, listOf(filter), "")
        val first = SearchInfo.getInfo(youtube, handler)
        return pagedOf(
            first.relatedItems.mapNotNull(map),
            if (first.hasNextPage()) first.nextPage else null
        ) { page ->
            val more = SearchInfo.getMoreItems(youtube, handler, page)
            more.items.mapNotNull(map) to (if (more.hasNextPage()) more.nextPage else null)
        }
    }

    private fun <T> tabPaged(tab: ListLinkHandler, map: (InfoItem) -> T?): Paged<T> {
        val first = ChannelTabInfo.getInfo(youtube, tab)
        return pagedOf(
            first.relatedItems.mapNotNull(map),
            if (first.hasNextPage()) first.nextPage else null
        ) { page ->
            val more = ChannelTabInfo.getMoreItems(youtube, tab, page)
            more.items.mapNotNull(map) to (if (more.hasNextPage()) more.nextPage else null)
        }
    }

    private fun videoOnly(item: InfoItem): MediaItemModel? =
        (item as? StreamInfoItem)
            ?.takeIf { it.duration > 60 && !it.isShortFormContent }
            ?.let { toModel(it, false) }

    private fun toSearchResult(item: InfoItem, music: Boolean = false): SearchResult? = when (item) {
        is StreamInfoItem ->
            if ((item.duration > 0 || item.streamType == StreamType.LIVE_STREAM) && (music || !item.isShortFormContent)) {
                toModel(item, music)?.let { SearchResult.Video(it) }
            } else {
                null
            }
        is ChannelInfoItem -> SearchResult.Channel(
            ChannelResult(
                name = item.name,
                url = item.url,
                avatarUrl = bestImage(item.thumbnails),
                subscribers = formatCount(item.subscriberCount),
                description = item.description.orEmpty()
            )
        )
        is PlaylistInfoItem -> SearchResult.Playlist(toPlaylistResult(item))
        else -> null
    }

    private fun toPlaylistResult(item: PlaylistInfoItem) = PlaylistResult(
        title = item.name,
        url = item.url,
        thumbnailUrl = bestImage(item.thumbnails),
        owner = item.uploaderName.orEmpty(),
        videoCount = item.streamCount
    )

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val out = mutableListOf<T>()
        var index = 0
        while (lists.any { index < it.size }) {
            lists.forEach { if (index < it.size) out += it[index] }
            index++
        }
        return out
    }

    private fun feedFor(sub: LibraryRepository.ChannelSub): List<MediaItemModel> {
        val channelId = FeedParsing.channelId(sub.channelUrl)
            ?: return searchItems(sub.name, YoutubeSearchQueryHandlerFactory.VIDEOS, minSeconds = 61)
                .filter { it.artistOrChannel.equals(sub.name, ignoreCase = true) }
                .take(3)

        val request = okhttp3.Request.Builder()
            .url(FeedParsing.feedUrl(channelId))
            .header("User-Agent", NewPipeDownloader.USER_AGENT)
            .build()
        val xml = NewPipeDownloader.instance.client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Feed HTTP ${response.code}" }
            response.body?.string().orEmpty()
        }
        val now = System.currentTimeMillis()
        return FeedParsing.parseFeed(xml)
            .filter { !it.title.contains("#shorts", ignoreCase = true) }
            .take(10)
            .map { entry ->
                val (primary, secondary) = colorsFor(entry.videoId)
                MediaItemModel(
                    id = entry.videoId,
                    title = entry.title,
                    artistOrChannel = entry.channelName.ifBlank { sub.name },
                    audioUrl = "",
                    videoUrl = "",
                    artworkUrl = "https://i.ytimg.com/vi/${entry.videoId}/hqdefault.jpg",
                    durationMs = 0L,
                    dominantColor = primary,
                    secondaryColor = secondary,
                    viewsOrPlays = formatCount(entry.views),
                    contentType = MediaContentType.YOUTUBE_VIDEO,
                    channelAvatarUrl = sub.avatarUrl,
                    publishDate = FeedParsing.relativeTime(entry.publishedAtMs, now),
                    sourceUrl = "https://www.youtube.com/watch?v=${entry.videoId}",
                    channelUrl = sub.channelUrl,
                    publishedAtMs = entry.publishedAtMs
                )
            }
    }

    private fun toModel(
        item: StreamInfoItem,
        isMusic: Boolean,
        channelName: String? = null,
        channelAvatar: String? = null,
        channelUrl: String? = null
    ): MediaItemModel? {
        val id = VIDEO_ID.find(item.url)?.groupValues?.get(1) ?: return null
        val (primary, secondary) = colorsFor(id)
        return MediaItemModel(
            id = id,
            title = item.name,
            artistOrChannel = channelName ?: item.uploaderName.orEmpty(),
            audioUrl = "",
            videoUrl = "",
            artworkUrl = bestImage(item.thumbnails),
            durationMs = item.duration.coerceAtLeast(0) * 1000L,
            dominantColor = primary,
            secondaryColor = secondary,
            viewsOrPlays = formatCount(item.viewCount),
            contentType = if (isMusic) MediaContentType.MUSIC_TRACK else MediaContentType.YOUTUBE_VIDEO,
            channelAvatarUrl = channelAvatar ?: bestImage(item.uploaderAvatars),
            publishDate = item.textualUploadDate.orEmpty(),
            sourceUrl = item.url,
            channelUrl = channelUrl ?: item.uploaderUrl.orEmpty(),
            isLive = item.streamType == StreamType.LIVE_STREAM
        )
    }

    private fun bestImage(images: List<Image>): String =
        images.maxByOrNull { it.height }?.url.orEmpty()

    /** Original-language audio: no track type reported (single track) or explicitly ORIGINAL. */
    private fun isOriginalTrack(s: AudioStream): Boolean =
        s.audioTrackType == null || s.audioTrackType == AudioTrackType.ORIGINAL

    private fun trackLabel(s: AudioStream): String =
        s.audioTrackName?.takeIf { it.isNotBlank() }
            ?: s.audioLocale?.displayLanguage?.takeIf { it.isNotBlank() }
            ?: "Audio"

    private fun hasIndex(initEnd: Int, indexEnd: Int) = initEnd > 0 && indexEnd > initEnd

    private fun audioScore(s: AudioStream): Int {
        // Prefer m4a (broadest codec support), then the highest bitrate.
        val m4a = if (s.format?.suffix == "m4a") 1_000_000 else 0
        return m4a + s.averageBitrate
    }

    private fun videoScore(s: VideoStream): Int {
        // H.264 plays on every phone (hardware decoder); VP9 on most; AV1 only on recent ones.
        val codec = s.codec.orEmpty()
        val preference = when {
            codec.startsWith("avc1") -> 3
            codec.startsWith("vp9") || codec.startsWith("vp09") -> 2
            codec.startsWith("av01") || codec.startsWith("av1") -> 1
            else -> 0
        }
        return preference * 10_000 + s.height
    }

    fun formatCount(n: Long): String = when {
        n < 0 -> ""
        n >= 1_000_000_000 -> "%.1fB".format(n / 1_000_000_000.0)
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1fK".format(n / 1_000.0)
        else -> n.toString()
    }

    /** Stable accent colors derived from the video id (artwork palette extraction not available). */
    private fun colorsFor(id: String): Pair<Color, Color> {
        val h = abs(id.hashCode())
        val primary = Color.hsv((h % 360).toFloat(), 0.55f, 0.55f)
        val secondary = Color.hsv(((h / 360) % 360).toFloat(), 0.50f, 0.65f)
        return primary to secondary
    }

    private val VIDEO_ID = Regex("[?&]v=([\\w-]{11})")
}
