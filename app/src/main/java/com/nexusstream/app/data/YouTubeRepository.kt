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

/** Direct, short-lived stream URLs for one video. */
data class ResolvedStream(val audioUrl: String, val videoOnlyUrl: String?)

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
            shelves.map { (title, subtitle, query) ->
                async {
                    MusicShelf(
                        title,
                        subtitle,
                        runCatching {
                            searchItems(query, YoutubeSearchQueryHandlerFactory.MUSIC_SONGS, minSeconds = 1)
                        }.getOrDefault(emptyList())
                    )
                }
            }.awaitAll()
        }.filter { it.items.isNotEmpty() }
            .also { check(it.isNotEmpty()) { "No music results" } }
    }

    /** Resolves fresh direct stream URLs for a watch URL. Call right before playback. */
    suspend fun resolveStream(watchUrl: String): Result<ResolvedStream> = io {
        val info = StreamInfo.getInfo(youtube, watchUrl)
        val audio = info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
            .maxByOrNull(::audioScore)
            ?: error("No playable audio stream")
        val video = info.videoOnlyStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotEmpty() }
            .filter { it.height in 1..720 }
            .maxByOrNull(::videoScore)
        ResolvedStream(audioUrl = audio.content, videoOnlyUrl = video?.content)
    }

    // ---- internals ----

    private suspend fun <T> io(block: () -> T): Result<T> = withContext(Dispatchers.IO) {
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

    private fun formatCount(n: Long): String = when {
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
