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
class CommentsPage(val totalCount: Int, val paged: Paged<CommentModel>) {
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
    val loadReplies: (suspend () -> Result<List<CommentModel>>)? = null
)

/** One selectable video rendition (video-only stream). */
data class VideoOption(val height: Int, val url: String)

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
    val muxedUrl: String? = null
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
        appliedCountry = country
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
        if (country != appliedCountry) {
            appliedCountry = country
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

    private fun streamInfo(watchUrl: String): StreamInfo {
        cachedInfo?.takeIf { it.first == watchUrl }?.let { return it.second }
        return StreamInfo.getInfo(youtube, watchUrl).also { cachedInfo = watchUrl to it }
    }

    // Caches live in process memory: they last until the app is fully closed (process killed),
    // and are only replaced by an explicit refresh (Retry / pull-to-refresh).
    private const val SHORTS_BATCH = 4
    private const val SHORTS_PER_CHANNEL = 12
    private class Cached<T>(val at: Long, val value: T)
    private val feedCache = java.util.concurrent.ConcurrentHashMap<String, Cached<Paged<MediaItemModel>>>()
    private val shelfCache = java.util.concurrent.ConcurrentHashMap<String, Cached<List<MusicShelf>>>()

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
        fun page(p: InnerTube.Page): Paged<MediaItemModel> = Paged(p.items, p.continuation?.let { token ->
            { withContext(Dispatchers.IO) { runCatching { page(InnerTube.more(token)) } } }
        })
        val first = runCatching { InnerTube.home() }.getOrNull() ?: return null
        return if (first.items.size >= 4) page(first) else null
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
     * A feed of short vertical videos, built from "#shorts" search and user seed channels.
     * Features full vertical pagination so users can continuously swipe through Shorts.
     */
    suspend fun shortsFeed(
        seedChannels: List<String> = emptyList(),
        refresh: Boolean = false
    ): Result<Paged<MediaItemModel>> = io {
        if (!refresh) shortsCache.get()?.let { return@io it }

        // 1. Fetch trending search shorts paged for "#shorts"
        val primaryPaged = runCatching { fetchShortsSearchPaged("#shorts") }.getOrNull()

        // 2. In parallel, fetch channel shorts if seed channels are available
        val validChannels = seedChannels.filter { it.isNotBlank() }.distinct().take(4)
        val channelShortsList = if (validChannels.isNotEmpty()) {
            coroutineScope {
                validChannels.map { url ->
                    async { runCatching { channelShorts(url) }.getOrDefault(emptyList()) }
                }.awaitAll().flatten()
            }
        } else emptyList()

        val initialItems = (channelShortsList + (primaryPaged?.items.orEmpty())).distinctBy { it.id }

        val pagedResult = if (initialItems.isNotEmpty() && primaryPaged != null) {
            Paged(initialItems, primaryPaged.loadMore)
        } else {
            fetchShortsSearchPaged("trending shorts")
        }

        check(pagedResult.items.isNotEmpty()) { "No Shorts available right now" }
        shortsCache.set(pagedResult)
        pagedResult
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

    /** YT Music's own (unsynced) lyrics for [videoId], or null when it has none. */
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
        val video = if (audioOnly) null else info.videoStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && it.height in 1..720 }
            .maxByOrNull { it.height }
        if (video != null) {
            CastTarget(video.content, video.format?.mimeType ?: "video/mp4")
        } else {
            val audio = info.audioStreams
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
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

    /** Resolves fresh direct stream URLs for a watch URL. Call right before playback. */
    suspend fun resolveStream(watchUrl: String): Result<ResolvedStream> = io {
        val info = try {
            streamInfo(watchUrl)
        } catch (e: org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException) {
            throw IllegalStateException("Age-restricted video: YouTube only serves it to signed-in adult accounts, which this app cannot do yet")
        }
        // YouTube serves several language tracks (original, auto-dubbed, audio description).
        // Keep the best stream per (track, bitrate) and list the original track first.
        val audio = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
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
        check(audio.isNotEmpty()) { "No playable audio stream" }
        val video = info.videoOnlyStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && it.height > 0 }
            .groupBy { it.height }
            .mapValues { (_, streams) -> streams.maxByOrNull(::videoScore)!! }
            .values
            .sortedByDescending { it.height }
            .map { VideoOption(it.height, it.content) }
        val muxed = info.videoStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
            .maxByOrNull { it.height }
            ?.content
        ResolvedStream(audioOptions = audio, videoOptions = video, subtitles = subtitleOptions(info), muxedUrl = muxed)
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
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
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

    /** Top-level comments for a video (paged), with the total count when YouTube reports it. */
    suspend fun comments(watchUrl: String): Result<CommentsPage> = io {
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
