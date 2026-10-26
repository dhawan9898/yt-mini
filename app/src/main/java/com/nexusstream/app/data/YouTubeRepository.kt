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
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

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
    val related: List<MediaItemModel>
)

/** Comments plus the video's total count (negative when unknown). */
data class CommentsPage(val totalCount: Int, val items: List<CommentModel>)

/** A top-level comment on a video. */
data class CommentModel(
    val author: String,
    val avatarUrl: String,
    val text: String,
    val likes: Int,
    val date: String,
    val pinned: Boolean
)

/** One selectable video rendition (video-only stream). */
data class VideoOption(val height: Int, val url: String)

/** One selectable audio rendition. */
data class AudioOption(val kbps: Int, val url: String)

/** A downloadable file with a human label such as "720p video" or "128 kbps audio". */
data class DownloadOption(val label: String, val url: String, val extension: String, val isVideo: Boolean)

/**
 * Direct, short-lived stream URLs for one video, with every quality that is available.
 * Both lists are sorted best-first.
 */
data class ResolvedStream(
    val audioOptions: List<AudioOption>,
    val videoOptions: List<VideoOption>
) {
    /** Highest rendition not above [maxHeight]; null selects the default (720p cap, saves data). */
    fun pickVideo(maxHeight: Int?): VideoOption? {
        val cap = maxHeight ?: DEFAULT_MAX_HEIGHT
        return videoOptions.firstOrNull { it.height <= cap } ?: videoOptions.lastOrNull()
    }

    /** Closest rendition at or below [kbps]; null selects the best. */
    fun pickAudio(kbps: Int?): AudioOption? {
        if (kbps == null) return audioOptions.firstOrNull()
        return audioOptions.firstOrNull { it.kbps <= kbps } ?: audioOptions.lastOrNull()
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

    /** Last video feed loaded, used for "Up next" suggestions on the watch screen. */
    @Volatile
    var lastVideoFeed: List<MediaItemModel> = emptyList()
        private set

    /** Home feed (category = "All") or a topic feed for [category]. */
    suspend fun videoFeed(category: String): Result<List<MediaItemModel>> = io {
        val query = if (category == "All") "trending videos today" else category
        searchItems(query, YoutubeSearchQueryHandlerFactory.VIDEOS, minSeconds = 61)
            .also { lastVideoFeed = it }
    }

    suspend fun searchVideos(query: String): Result<List<MediaItemModel>> = io {
        searchItems(query, YoutubeSearchQueryHandlerFactory.VIDEOS, minSeconds = 61)
            .also { if (it.isNotEmpty()) lastVideoFeed = it }
    }

    suspend fun searchSongs(query: String): Result<List<MediaItemModel>> = io {
        searchItems(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, minSeconds = 1)
    }

    /** Home shelves for YT Music; [mood] (blank for none) narrows every shelf to that mood. */
    suspend fun musicShelves(mood: String): Result<List<MusicShelf>> = io {
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
    }

    /** Resolves fresh direct stream URLs for a watch URL. Call right before playback. */
    suspend fun resolveStream(watchUrl: String): Result<ResolvedStream> = io {
        val info = streamInfo(watchUrl)
        val audio = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
            .groupBy { it.averageBitrate }
            .mapValues { (_, streams) -> streams.maxByOrNull(::audioScore)!! }
            .values
            .sortedByDescending { it.averageBitrate }
            .map { AudioOption(it.averageBitrate, it.content) }
        check(audio.isNotEmpty()) { "No playable audio stream" }
        val video = info.videoOnlyStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() && it.height > 0 }
            .groupBy { it.height }
            .mapValues { (_, streams) -> streams.maxByOrNull(::videoScore)!! }
            .values
            .sortedByDescending { it.height }
            .map { VideoOption(it.height, it.content) }
        ResolvedStream(audioOptions = audio, videoOptions = video)
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
        val audios = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
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
                .distinctBy { it.id }
        )
    }

    /** Recent videos from the given channels (matched by channel name search). */
    suspend fun latestFrom(channels: List<String>): Result<List<MediaItemModel>> = io {
        coroutineScope {
            channels.take(8).map { name ->
                async {
                    runCatching {
                        searchItems(name, YoutubeSearchQueryHandlerFactory.VIDEOS, minSeconds = 61)
                            .filter { it.artistOrChannel.equals(name, ignoreCase = true) }
                            .take(3)
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }
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

    private fun toModel(item: StreamInfoItem, isMusic: Boolean): MediaItemModel? {
        val id = VIDEO_ID.find(item.url)?.groupValues?.get(1) ?: return null
        val (primary, secondary) = colorsFor(id)
        return MediaItemModel(
            id = id,
            title = item.name,
            artistOrChannel = item.uploaderName.orEmpty(),
            audioUrl = "",
            videoUrl = "",
            artworkUrl = bestImage(item.thumbnails),
            durationMs = item.duration.coerceAtLeast(0) * 1000L,
            dominantColor = primary,
            secondaryColor = secondary,
            viewsOrPlays = formatCount(item.viewCount),
            contentType = if (isMusic) MediaContentType.MUSIC_TRACK else MediaContentType.YOUTUBE_VIDEO,
            channelAvatarUrl = bestImage(item.uploaderAvatars),
            publishDate = item.textualUploadDate.orEmpty(),
            sourceUrl = item.url
        )
    }

    private fun bestImage(images: List<Image>): String =
        images.maxByOrNull { it.height }?.url.orEmpty()

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
