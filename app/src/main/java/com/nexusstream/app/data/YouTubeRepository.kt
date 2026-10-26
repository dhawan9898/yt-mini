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
enum class SearchType(val label: String, val filter: String) {
    ALL("All", YoutubeSearchQueryHandlerFactory.ALL),
    VIDEOS("Videos", YoutubeSearchQueryHandlerFactory.VIDEOS),
    CHANNELS("Channels", YoutubeSearchQueryHandlerFactory.CHANNELS),
    PLAYLISTS("Playlists", YoutubeSearchQueryHandlerFactory.PLAYLISTS)
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
    val chapters: List<Chapter> = emptyList()
)

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

/** A downloadable file with a human label such as "720p video" or "128 kbps audio". */
data class DownloadOption(val label: String, val url: String, val extension: String, val isVideo: Boolean)

/**
 * Direct, short-lived stream URLs for one video, with every quality that is available.
 * Video options are sorted best-first. Audio options list the original track first
 * (best bitrate first), then other language tracks.
 */
data class ResolvedStream(
    val audioOptions: List<AudioOption>,
    val videoOptions: List<VideoOption>,
    val subtitles: List<SubtitleOption> = emptyList()
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

    private val initialized: Boolean by lazy {
        NewPipe.init(
            NewPipeDownloader.instance,
            Localization("en", "US"),
            ContentCountry("US")
        )
        true
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
    suspend fun search(query: String, type: SearchType): Result<Paged<SearchResult>> = io {
        searchPaged(query, type.filter) { toSearchResult(it) }
    }

    /** Autocomplete suggestions for a partially typed query. */
    suspend fun suggestions(query: String): Result<List<String>> = io {
        youtube.suggestionExtractor.suggestionList(query).orEmpty()
    }

    /** Channel header plus first page of videos. [channelUrl] is a youtube.com/channel/... URL. */
    suspend fun channelPage(channelUrl: String): Result<ChannelPage> = io {
        val info = ChannelInfo.getInfo(youtube, channelUrl)
        val avatar = bestImage(info.avatars)
        val canonicalUrl = "https://www.youtube.com/channel/${info.id}"
        val videosTab = info.tabs.firstOrNull { ChannelTabs.VIDEOS in it.contentFilters }
        val playlistsTab = info.tabs.firstOrNull { ChannelTabs.PLAYLISTS in it.contentFilters }

        val videos = videosTab?.let { tab ->
            tabPaged(tab) { item ->
                (item as? StreamInfoItem)
                    ?.takeIf { it.duration > 0 && !it.isShortFormContent }
                    ?.let { toModel(it, false, info.name, avatar, canonicalUrl) }
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
    suspend fun playlistPage(playlistUrl: String): Result<PlaylistPage> = io {
        val info = PlaylistInfo.getInfo(youtube, playlistUrl)
        fun map(item: StreamInfoItem): MediaItemModel? = if (item.duration > 0) toModel(item, false) else null
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
     * Newest uploads from the given subscriptions, newest first. Uses each channel's public RSS
     * feed (real publish times); subscriptions saved without a channel URL fall back to a name search.
     */
    suspend fun subscriptionFeed(
        subs: List<LibraryRepository.ChannelSub>,
        refresh: Boolean = false
    ): Result<List<MediaItemModel>> = io {
        if (!refresh) cachedSubscriptionFeed(subs)?.let { return@io it }
        coroutineScope {
            subs.take(30).map { sub ->
                async { runCatching { feedFor(sub) }.getOrDefault(emptyList()) }
            }.awaitAll().flatten()
        }.distinctBy { it.id }.sortedByDescending { it.publishedAtMs }
            .also { subFeedCache = subKey(subs) to it }
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
        val info = streamInfo(watchUrl)
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
        ResolvedStream(audioOptions = audio, videoOptions = video, subtitles = subtitleOptions(info))
    }

    /** Every file that can be saved offline: muxed video renditions, then audio-only renditions. */
    suspend fun downloadOptions(watchUrl: String): Result<List<DownloadOption>> = io {
        val info = streamInfo(watchUrl)
        val videos = info.videoStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && it.height > 0 }
            .groupBy { it.height }
            .mapValues { (_, streams) -> streams.maxByOrNull(::videoScore)!! }
            .values
            .sortedByDescending { it.height }
            .map { DownloadOption("${it.height}p video", it.content, it.format?.suffix ?: "mp4", true) }
        val playableAudio = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
        // Save the original-language audio, not an auto-dub.
        val originalTrackId = (playableAudio.firstOrNull { isOriginalTrack(it) } ?: playableAudio.firstOrNull())?.audioTrackId
        val audios = playableAudio
            .filter { it.audioTrackId == originalTrackId }
            .groupBy { it.averageBitrate }
            .mapValues { (_, streams) -> streams.maxByOrNull(::audioScore)!! }
            .values
            .sortedByDescending { it.averageBitrate }
            .map { DownloadOption("${it.averageBitrate} kbps audio", it.content, it.format?.suffix ?: "m4a", false) }
        (videos + audios).also { check(it.isNotEmpty()) { "No downloadable streams" } }
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
            related = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration > 60 && !it.isShortFormContent }
                .mapNotNull { toModel(it, isMusic = false) }
                .distinctBy { it.id },
            chapters = info.streamSegments.orEmpty()
                .filter { !it.title.isNullOrBlank() }
                .map { Chapter(it.title, it.startTimeSeconds * 1000L) }
        )
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

    /** Every file that can be saved offline: muxed video renditions, then audio-only renditions. */
    suspend fun downloadOptions(watchUrl: String): Result<List<DownloadOption>> = io {
        val info = streamInfo(watchUrl)
        val videos = info.videoStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && it.height > 0 }
            .groupBy { it.height }
            .mapValues { (_, streams) -> streams.maxByOrNull(::videoScore)!! }
            .values
            .sortedByDescending { it.height }
            .map { DownloadOption("${it.height}p video", it.content, it.format?.suffix ?: "mp4", true) }
        val playableAudio = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
        // Save the original-language audio, not an auto-dub.
        val originalTrackId = (playableAudio.firstOrNull { isOriginalTrack(it) } ?: playableAudio.firstOrNull())?.audioTrackId
        val audios = playableAudio
            .filter { it.audioTrackId == originalTrackId }
            .groupBy { it.averageBitrate }
            .mapValues { (_, streams) -> streams.maxByOrNull(::audioScore)!! }
            .values
            .sortedByDescending { it.averageBitrate }
            .map { DownloadOption("${it.averageBitrate} kbps audio", it.content, it.format?.suffix ?: "m4a", false) }
        (videos + audios).also { check(it.isNotEmpty()) { "No downloadable streams" } }
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
            related = info.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration > 60 && !it.isShortFormContent }
                .mapNotNull { toModel(it, isMusic = false) }
                .distinctBy { it.id },
            chapters = info.streamSegments.orEmpty()
                .filter { !it.title.isNullOrBlank() }
                .map { Chapter(it.title, it.startTimeSeconds * 1000L) }
        )
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

    /** Top-level comments for a video, with the total count when YouTube reports it. */
    suspend fun comments(watchUrl: String): Result<CommentsPage> = io {
        val info = CommentsInfo.getInfo(youtube, watchUrl)
        CommentsPage(
            totalCount = info.commentsCount,
            items = info.relatedItems.map { c ->
                CommentModel(
                    author = c.uploaderName.orEmpty(),
                    avatarUrl = bestImage(c.uploaderAvatars),
                    text = c.commentText?.content.orEmpty(),
                    likes = c.likeCount,
                    date = c.textualUploadDate.orEmpty(),
                    pinned = c.isPinned
                )
            }
        )
    }

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

    private fun toSearchResult(item: InfoItem): SearchResult? = when (item) {
        is StreamInfoItem ->
            if (item.duration > 0 && !item.isShortFormContent) toModel(item, false)?.let { SearchResult.Video(it) } else null
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
            channelUrl = channelUrl ?: item.uploaderUrl.orEmpty()
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
        // Prefer mp4 (avc1) containers, then the tallest resolution.
        val mp4 = if (s.format?.suffix == "mp4") 10_000 else 0
        return mp4 + s.height
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
