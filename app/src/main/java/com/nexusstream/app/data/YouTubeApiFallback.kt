package com.nexusstream.app.data

import com.nexusstream.app.model.MediaContentType
import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns [YouTubeDataApi] answers into the app's own models, so [YouTubeRepository] can fall back
 * to the official API when the extractor fails (YouTube changed a page, a request was blocked)
 * and use its trending chart, which is more dependable than a search for "trending".
 *
 * Everything here blocks and is meant to run inside the repository's IO block.
 */
internal object YouTubeApiFallback {

    private const val SHORT_MAX_SECONDS = 60L

    /** App topic chip -> YouTube video category id (chips with no category use the general chart). */
    private val CATEGORY_IDS = mapOf(
        "Music" to "10", "Gaming" to "20", "News" to "25", "Sports" to "17",
        "Learning" to "27", "Fashion & Beauty" to "26"
    )

    fun categoryIdFor(category: String): String? = CATEGORY_IDS[category]

    private fun watchUrl(id: String) = "https://www.youtube.com/watch?v=$id"
    private fun channelUrl(id: String) = "https://www.youtube.com/channel/$id"
    private fun playlistUrl(id: String) = "https://www.youtube.com/playlist?list=$id"

    /** Shorts have their own feed; the rest of the app lists videos longer than a minute (and live streams). */
    internal fun isRegularVideo(v: YouTubeDataApi.Video): Boolean = v.isLive || v.durationSeconds > SHORT_MAX_SECONDS

    private fun compact(n: Long): String = if (n >= 0) YouTubeRepository.formatCount(n) else ""

    /** Channel avatars by channel id, filled one batch per page (1 unit) so cards can show the channel icon. */
    private val avatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Fetches the avatars of the channels behind [items] (videos or search hits) that are not known yet. */
    private fun prefetchAvatars(items: List<*>) {
        val ids = items.mapNotNull {
            when (it) {
                is YouTubeDataApi.Video -> it.channelId
                is YouTubeDataApi.SearchHit.VideoHit -> it.video.channelId
                else -> null
            }
        }.filter { it.isNotEmpty() && it !in avatarCache }.distinct().take(50)
        if (ids.isEmpty()) return
        runCatching { YouTubeDataApi.channels(ids) }.getOrNull()?.forEach { if (it.avatarUrl.isNotEmpty()) avatarCache[it.id] = it.avatarUrl }
    }

    fun toModel(v: YouTubeDataApi.Video, channelAvatar: String = avatarCache[v.channelId].orEmpty(), now: Long = System.currentTimeMillis()): MediaItemModel {
        val (primary, secondary) = YouTubeRepository.colorsFor(v.id)
        return MediaItemModel(
            id = v.id,
            title = v.title,
            artistOrChannel = v.channelTitle,
            audioUrl = "",
            videoUrl = "",
            artworkUrl = v.thumbnailUrl,
            durationMs = v.durationSeconds.coerceAtLeast(0) * 1000L,
            dominantColor = primary,
            secondaryColor = secondary,
            viewsOrPlays = compact(v.viewCount),
            contentType = MediaContentType.YOUTUBE_VIDEO,
            channelAvatarUrl = channelAvatar,
            publishDate = if (v.publishedAtMs > 0) FeedParsing.relativeTime(v.publishedAtMs, now) else "",
            description = v.description,
            sourceUrl = watchUrl(v.id),
            channelUrl = if (v.channelId.isNotEmpty()) channelUrl(v.channelId) else "",
            publishedAtMs = v.publishedAtMs,
            isLive = v.isLive
        )
    }

    // ------------------------------------------------------------------ feeds and search

    /** The official most-popular chart (optionally for one topic) as a pageable feed. */
    fun trendingFeed(region: String, category: String): Paged<MediaItemModel> {
        val categoryId = categoryIdFor(category)
        val first = YouTubeDataApi.trending(region, categoryId)
        return pagedFrom(first, { v -> if (isRegularVideo(v)) toModel(v) else null }) { token ->
            YouTubeDataApi.trending(region, categoryId, token)
        }
    }

    /** Search by the official API (100 units: only used after the extractor failed). Null for music searches. */
    fun search(query: String, type: SearchType, region: String): Paged<SearchResult>? {
        if (type.isMusic) return null
        val kinds = when (type) {
            SearchType.VIDEOS -> listOf(YouTubeDataApi.SearchKind.VIDEO)
            SearchType.CHANNELS -> listOf(YouTubeDataApi.SearchKind.CHANNEL)
            SearchType.PLAYLISTS -> listOf(YouTubeDataApi.SearchKind.PLAYLIST)
            else -> listOf(YouTubeDataApi.SearchKind.VIDEO, YouTubeDataApi.SearchKind.CHANNEL, YouTubeDataApi.SearchKind.PLAYLIST)
        }
        val first = YouTubeDataApi.search(query, kinds, region = region)
        return pagedFrom(first, { hit -> toSearchResult(hit) }) { token ->
            YouTubeDataApi.search(query, kinds, pageToken = token, region = region)
        }
    }

    internal fun toSearchResult(hit: YouTubeDataApi.SearchHit): SearchResult? = when (hit) {
        is YouTubeDataApi.SearchHit.VideoHit -> if (isRegularVideo(hit.video)) SearchResult.Video(toModel(hit.video)) else null
        is YouTubeDataApi.SearchHit.ChannelHit -> SearchResult.Channel(
            ChannelResult(
                name = hit.channel.title,
                url = channelUrl(hit.channel.id),
                avatarUrl = hit.channel.avatarUrl,
                subscribers = compact(hit.channel.subscriberCount),
                description = hit.channel.description
            )
        )
        is YouTubeDataApi.SearchHit.PlaylistHit -> SearchResult.Playlist(toPlaylistResult(hit.playlist))
    }

    private fun toPlaylistResult(p: YouTubeDataApi.Playlist) = PlaylistResult(
        title = p.title,
        url = playlistUrl(p.id),
        thumbnailUrl = p.thumbnailUrl,
        owner = p.channelTitle,
        videoCount = p.itemCount
    )

    // ------------------------------------------------------------------ shorts

    /** A short by length (and the #shorts tag for the longer ones YouTube now allows). */
    internal fun isShortVideo(v: YouTubeDataApi.Video): Boolean =
        !v.isLive && v.durationSeconds in 1..SHORT_MAX_SECONDS ||
            (!v.isLive && v.durationSeconds in 1..180 && "#shorts" in (v.title + " " + v.description).lowercase())

    private fun asShort(v: YouTubeDataApi.Video, avatar: String = ""): MediaItemModel = toModel(v, avatar).copy(isShort = true)

    private fun channelFromUrl(channelUrl: String): YouTubeDataApi.Channel? {
        val id = CHANNEL_ID.find(channelUrl)?.groupValues?.get(1)
        val handle = HANDLE.find(channelUrl)?.groupValues?.get(1)
        return when {
            id != null -> YouTubeDataApi.channelById(id)
            handle != null -> YouTubeDataApi.channelByHandle(handle)
            else -> null
        }
    }

    /** A channel's newest regular videos (2 units), for the personal part of Home. */
    fun channelRecent(channelUrl: String, take: Int = 4): List<MediaItemModel> {
        val channel = channelFromUrl(channelUrl) ?: return emptyList()
        if (channel.uploadsPlaylistId.isEmpty()) return emptyList()
        return YouTubeDataApi.playlistVideos(channel.uploadsPlaylistId, max = 15).items
            .filter { isRegularVideo(it) }.take(take).map { toModel(it, channel.avatarUrl) }
    }

    /** Sorted and length-filtered video search by the official API (used when the extractor's filters fail). */
    fun filteredSearch(query: String, filters: YouTubeWeb.SearchFilters, region: String): Paged<SearchResult> {
        val kinds = listOf(YouTubeDataApi.SearchKind.VIDEO)
        val order = when (filters.sort) {
            YouTubeWeb.SortBy.RELEVANCE -> "relevance"
            YouTubeWeb.SortBy.UPLOAD_DATE -> "date"
            YouTubeWeb.SortBy.VIEW_COUNT -> "viewCount"
            YouTubeWeb.SortBy.RATING -> "rating"
        }
        val duration = when (filters.duration) {
            YouTubeWeb.Duration.SHORT -> "short"
            YouTubeWeb.Duration.MEDIUM -> "medium"
            YouTubeWeb.Duration.LONG -> "long"
            YouTubeWeb.Duration.ANY -> null
        }
        val first = YouTubeDataApi.search(query, kinds, region = region, videoDuration = duration, order = order)
        return pagedFrom(first, { hit -> toSearchResult(hit) }) { token ->
            YouTubeDataApi.search(query, kinds, pageToken = token, region = region, videoDuration = duration, order = order)
        }
    }

    /** The newest Shorts from one channel's uploads (2 units). */
    fun channelShorts(channelUrl: String, take: Int = 1): List<MediaItemModel> {
        val id = CHANNEL_ID.find(channelUrl)?.groupValues?.get(1)
        val handle = HANDLE.find(channelUrl)?.groupValues?.get(1)
        val channel = when {
            id != null -> YouTubeDataApi.channelById(id)
            handle != null -> YouTubeDataApi.channelByHandle(handle)
            else -> null
        } ?: return emptyList()
        if (channel.uploadsPlaylistId.isEmpty()) return emptyList()
        return YouTubeDataApi.playlistVideos(channel.uploadsPlaylistId, max = 30).items
            .filter { isShortVideo(it) }.take(take).map { asShort(it, channel.avatarUrl) }
    }

    /** Shorts found by the official search (100 units): pageable, cached for half an hour. */
    fun shortsSearch(region: String): Paged<MediaItemModel> {
        val kinds = listOf(YouTubeDataApi.SearchKind.VIDEO)
        val first = YouTubeDataApi.search("#shorts", kinds, region = region, max = 50, videoDuration = "short")
        return pagedFrom(first, { hit -> (hit as? YouTubeDataApi.SearchHit.VideoHit)?.video?.takeIf { isShortVideo(it) }?.let { asShort(it) } }) { token ->
            YouTubeDataApi.search("#shorts", kinds, pageToken = token, region = region, max = 50, videoDuration = "short")
        }
    }

    // ------------------------------------------------------------------ video, channel, playlist

    /** The video behind a watch link. */
    fun video(watchUrl: String): YouTubeDataApi.Video {
        val id = VIDEO_ID.find(watchUrl)?.groupValues?.get(1) ?: error("Not a video link")
        return YouTubeDataApi.videos(listOf(id)).firstOrNull() ?: error("Video not found")
    }

    fun details(v: YouTubeDataApi.Video, now: Long = System.currentTimeMillis()): VideoDetails {
        val subscribers = runCatching { YouTubeDataApi.channels(listOf(v.channelId)).firstOrNull()?.subscriberCount }.getOrNull() ?: -1L
        return VideoDetails(
            likes = v.likeCount,
            views = v.viewCount,
            uploadedText = if (v.publishedAtMs > 0) FeedParsing.relativeTime(v.publishedAtMs, now) else "",
            description = v.description,
            subscribers = subscribers,
            tags = v.tags,
            related = emptyList(),
            chapters = chaptersFromDescription(v.description, v.durationSeconds * 1000L)
        )
    }

    /**
     * More videos from the video's channel, for the watch page when the extractor's related list is
     * not available. A channel's uploads playlist id is its channel id with "UC" swapped for "UU",
     * so no extra lookup is needed (2 units).
     */
    fun moreFromChannel(v: YouTubeDataApi.Video, limit: Int = 12, now: Long = System.currentTimeMillis()): List<MediaItemModel> {
        if (v.channelId.length < 3 || !v.channelId.startsWith("UC")) return emptyList()
        val uploads = "UU" + v.channelId.substring(2)
        return YouTubeDataApi.playlistVideos(uploads, max = 20).items
            .filter { it.id != v.id && isRegularVideo(it) }
            .take(limit)
            .map { toModel(it, now = now) }
    }

    fun channelPage(channelUrl: String): ChannelPage {
        val id = CHANNEL_ID.find(channelUrl)?.groupValues?.get(1)
        val handle = HANDLE.find(channelUrl)?.groupValues?.get(1)
        val channel = when {
            id != null -> YouTubeDataApi.channelById(id)
            handle != null -> YouTubeDataApi.channelByHandle(handle)
            else -> null
        } ?: error("Channel not found")
        val uploads = channel.uploadsPlaylistId
        val videos: Paged<MediaItemModel> = if (uploads.isEmpty()) {
            Paged(emptyList(), null)
        } else {
            pagedFrom(YouTubeDataApi.playlistVideos(uploads), { v -> if (isRegularVideo(v)) toModel(v, channel.avatarUrl) else null }) { token ->
                YouTubeDataApi.playlistVideos(uploads, token)
            }
        }
        return ChannelPage(
            name = channel.title,
            url = channelUrl(channel.id),
            avatarUrl = channel.avatarUrl,
            bannerUrl = channel.bannerUrl,
            subscribers = compact(channel.subscriberCount),
            description = channel.description,
            videos = videos,
            loadPlaylists = {
                withContext(Dispatchers.IO) {
                    runCatching {
                        pagedFrom(YouTubeDataApi.channelPlaylists(channel.id), { p -> toPlaylistResult(p) }) { token ->
                            YouTubeDataApi.channelPlaylists(channel.id, token)
                        }
                    }
                }
            }
        )
    }

    fun playlistPage(playlistUrl: String): PlaylistPage {
        val id = LIST_ID.find(playlistUrl)?.groupValues?.get(1) ?: error("Not a playlist link")
        val playlist = YouTubeDataApi.playlist(id) ?: error("Playlist not found")
        val videos = pagedFrom(YouTubeDataApi.playlistVideos(id), { v -> toModel(v) }) { token ->
            YouTubeDataApi.playlistVideos(id, token)
        }
        return PlaylistPage(
            title = playlist.title,
            owner = playlist.channelTitle,
            videoCount = playlist.itemCount,
            thumbnailUrl = playlist.thumbnailUrl,
            videos = videos
        )
    }

    // ------------------------------------------------------------------ comments

    fun comments(videoId: String, newest: Boolean, now: Long = System.currentTimeMillis()): CommentsPage {
        val order = if (newest) YouTubeDataApi.CommentOrder.NEWEST else YouTubeDataApi.CommentOrder.TOP
        val first = YouTubeDataApi.comments(videoId, order)
        val total = runCatching { YouTubeDataApi.videos(listOf(videoId)).firstOrNull()?.commentCount }.getOrNull() ?: -1L
        return CommentsPage(
            totalCount = if (total >= 0) total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else first.items.size,
            paged = pagedFrom(first, { c -> toComment(c, now) }) { token -> YouTubeDataApi.comments(videoId, order, token) }
        )
    }

    internal fun toComment(c: YouTubeDataApi.Comment, now: Long): CommentModel = CommentModel(
        author = c.author,
        avatarUrl = c.authorAvatarUrl,
        text = c.text,
        likes = c.likeCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        date = if (c.publishedAtMs > 0) FeedParsing.relativeTime(c.publishedAtMs, now) else "",
        pinned = false,
        replyCount = c.replyCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        loadReplies = if (c.replyCount > 0) {
            suspend {
                withContext(Dispatchers.IO) {
                    runCatching {
                        YouTubeDataApi.commentReplies(c.id).items.map { toComment(it, now) }
                    }
                }
            }
        } else null,
        id = c.id,
        authorChannelId = c.authorChannelId
    )

    // ------------------------------------------------------------------ paging

    /** Maps an API page and lets the next one be fetched lazily, like the extractor's pages. */
    internal fun <A, B> pagedFrom(first: YouTubeDataApi.Page<A>, map: (A) -> B?, fetch: (String) -> YouTubeDataApi.Page<A>): Paged<B> {
        prefetchAvatars(first.items)
        val loader: (suspend () -> Result<Paged<B>>)? = first.nextPageToken?.let { token ->
            suspend { withContext(Dispatchers.IO) { runCatching { pagedFrom(fetch(token), map, fetch) } } }
        }
        return Paged(first.items.mapNotNull(map), loader)
    }

    private val VIDEO_ID = Regex("[?&]v=([\\w-]{11})")
    private val CHANNEL_ID = Regex("""channel/(UC[\w-]{22})""")
    private val HANDLE = Regex("""youtube\.com/(@[\w.\-]+)""")
    private val LIST_ID = Regex("[?&]list=([\\w-]+)")
}
