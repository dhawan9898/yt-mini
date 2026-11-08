package com.nexusstream.app.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Official YouTube Data API v3 client.
 *
 * This is the app's primary data source: trending, search, video / channel / playlist details
 * and comments come from the documented API, which does not break when YouTube changes its pages.
 * Scraping (NewPipeExtractor, InnerTube) is the guarded fallback for when a call fails or the
 * day's budget is spent. The API does NOT provide stream URLs - playback still resolves through
 * the extractor - nor related videos, storyboards or signed-in feeds.
 *
 * Quota: the default allowance is 10,000 units per day (resets at midnight Pacific time);
 * `search` costs 100, nearly everything else 1. [QuotaTracker] keeps a persisted count, lets
 * expensive calls use only part of the day (so searches cannot starve the cheap calls that
 * every screen needs) and refuses calls once the budget is spent - the caller then falls back
 * to scraping. Responses are cached for the same reason.
 *
 * All functions block; call them from an IO dispatcher. Failures throw [YouTubeApiException].
 */
object YouTubeDataApi {

    // ------------------------------------------------------------------ models

    data class Video(
        val id: String,
        val title: String,
        val channelId: String,
        val channelTitle: String,
        val thumbnailUrl: String,
        val durationSeconds: Long,
        val viewCount: Long,
        val likeCount: Long,
        val commentCount: Long,
        val publishedAtMs: Long,
        val description: String,
        val tags: List<String>,
        val isLive: Boolean,
        val categoryId: String
    )

    data class Channel(
        val id: String,
        val title: String,
        val description: String,
        val avatarUrl: String,
        val bannerUrl: String,
        /** -1 when the channel hides its subscriber count. */
        val subscriberCount: Long,
        val videoCount: Long,
        val uploadsPlaylistId: String
    )

    data class Playlist(
        val id: String,
        val title: String,
        val channelTitle: String,
        val thumbnailUrl: String,
        val itemCount: Long
    )

    data class Comment(
        val id: String,
        val author: String,
        val authorAvatarUrl: String,
        val authorChannelId: String,
        val text: String,
        val likeCount: Long,
        val publishedAtMs: Long,
        val replyCount: Long,
        /** The replies the API returned inline (at most five); more are fetched with [commentReplies]. */
        val replies: List<Comment> = emptyList()
    )

    data class Page<T>(val items: List<T>, val nextPageToken: String?)

    sealed interface SearchHit {
        data class VideoHit(val video: Video) : SearchHit
        data class ChannelHit(val channel: Channel) : SearchHit
        data class PlaylistHit(val playlist: Playlist) : SearchHit
    }

    enum class SearchKind(val apiValue: String) { VIDEO("video"), CHANNEL("channel"), PLAYLIST("playlist") }
    enum class CommentOrder(val apiValue: String) { TOP("relevance"), NEWEST("time") }

    // ------------------------------------------------------------------ configuration

    private const val BASE_URL = "https://www.googleapis.com/youtube/v3/"
    private const val CACHE_TTL_MS = 15 * 60_000L
    private const val SEARCH_CACHE_TTL_MS = 30 * 60_000L
    private const val CACHE_MAX_ENTRIES = 60
    private const val MAX_ATTEMPTS = 3

    @Volatile private var apiKey: String = ""
    @Volatile private var baseUrl: String = BASE_URL
    @Volatile private var androidPackage: String = ""
    @Volatile private var androidCertSha1: String = ""
    @Volatile private var disabledReason: String? = null
    @Volatile private var quota: QuotaTracker = QuotaTracker()
    @Volatile private var sleeper: (Long) -> Unit = { Thread.sleep(it) }

    private var client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private class CacheEntry(val at: Long, val body: JSONObject)
    private val cache = object : LinkedHashMap<String, CacheEntry>(CACHE_MAX_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > CACHE_MAX_ENTRIES
    }

    /**
     * Sets the key and the quota counter. [packageName] / [certSha1] are sent as the
     * `X-Android-Package` / `X-Android-Cert` headers, which is what lets the key be restricted to
     * this app in the Google Cloud console (an unrestricted key in an APK can be copied).
     * An empty [key] leaves the API switched off; everything then keeps using the extractor.
     */
    fun configure(
        key: String,
        quotaTracker: QuotaTracker = QuotaTracker(),
        packageName: String = "",
        certSha1: String = ""
    ) {
        apiKey = key.trim()
        quota = quotaTracker
        androidPackage = packageName
        androidCertSha1 = certSha1
        disabledReason = null
        synchronized(cache) { cache.clear() }
    }

    /** For tests: another server, client and a sleep that does not sleep. */
    internal fun configureForTest(key: String, base: String, quotaTracker: QuotaTracker, http: OkHttpClient? = null) {
        configure(key, quotaTracker)
        baseUrl = base
        sleeper = { }
        if (http != null) client = http
    }

    /** True when a key is set, it has not been rejected, and some of today's quota is left. */
    val isAvailable: Boolean
        get() = apiKey.isNotEmpty() && disabledReason == null && quota.remaining() > 0

    val quotaRemaining: Int get() = quota.remaining()

    /** Whether a call of [units] would be allowed right now (nothing is reserved). */
    fun canAfford(units: Int): Boolean = apiKey.isNotEmpty() && disabledReason == null && quota.canAfford(units)

    /** A search (100 units) plus the details of its videos (1 unit): expensive, so it has its own gate. */
    val canSearch: Boolean get() = canAfford(SEARCH_COST + 1)

    internal const val SEARCH_COST = 100

    // ------------------------------------------------------------------ endpoints

    /** Most popular videos (1 unit). [categoryId] narrows it (10 = Music, 20 = Gaming ...). */
    fun trending(region: String, categoryId: String? = null, pageToken: String? = null, max: Int = 30): Page<Video> {
        val params = linkedMapOf(
            "part" to VIDEO_PARTS, "chart" to "mostPopular", "regionCode" to region.ifBlank { "US" },
            "maxResults" to max.coerceIn(1, 50).toString()
        )
        categoryId?.let { params["videoCategoryId"] = it }
        pageToken?.let { params["pageToken"] = it }
        val json = request("videos", params, cost = 1)
        return Page(parseVideos(json), json.optString("nextPageToken").ifEmpty { null })
    }

    /** Full details of up to 50 videos (1 unit). Unknown or private ids are simply absent. */
    fun videos(ids: List<String>): List<Video> {
        if (ids.isEmpty()) return emptyList()
        val json = request("videos", mapOf("part" to VIDEO_PARTS, "id" to ids.take(50).joinToString(",")), cost = 1)
        return parseVideos(json)
    }

    /**
     * Search (100 units, plus 1 for the video details of video results). Keep this for when the
     * extractor failed.
     */
    fun search(query: String, kinds: List<SearchKind>, pageToken: String? = null, region: String = "", max: Int = 20): Page<SearchHit> {
        require(kinds.isNotEmpty()) { "at least one kind" }
        val params = linkedMapOf(
            "part" to "snippet", "q" to query, "type" to kinds.joinToString(",") { it.apiValue },
            "maxResults" to max.coerceIn(1, 50).toString(), "safeSearch" to "none"
        )
        if (region.isNotBlank()) params["regionCode"] = region
        pageToken?.let { params["pageToken"] = it }
        val json = request("search", params, cost = 100, cacheTtlMs = SEARCH_CACHE_TTL_MS)
        val rows = json.optJSONArray("items") ?: JSONArray()
        val videoIds = (0 until rows.length())
            .mapNotNull { rows.optJSONObject(it)?.optJSONObject("id")?.takeIf { id -> id.optString("kind") == "youtube#video" }?.optString("videoId") }
            .filter { it.isNotEmpty() }
        val details = if (videoIds.isEmpty()) emptyMap() else videos(videoIds).associateBy { it.id }
        return Page(parseSearchHits(json, details), json.optString("nextPageToken").ifEmpty { null })
    }

    /** A channel by `UC…` id (1 unit). */
    fun channelById(id: String): Channel? =
        parseChannels(request("channels", mapOf("part" to CHANNEL_PARTS, "id" to id), cost = 1)).firstOrNull()

    /** A channel by `@handle` (1 unit). */
    fun channelByHandle(handle: String): Channel? =
        parseChannels(request("channels", mapOf("part" to CHANNEL_PARTS, "forHandle" to handle.removePrefix("@")), cost = 1)).firstOrNull()

    /** Channel statistics for a batch of ids (1 unit). */
    fun channels(ids: List<String>): List<Channel> {
        if (ids.isEmpty()) return emptyList()
        return parseChannels(request("channels", mapOf("part" to CHANNEL_PARTS, "id" to ids.take(50).joinToString(",")), cost = 1))
    }

    /** A channel's playlists (1 unit). */
    fun channelPlaylists(channelId: String, pageToken: String? = null, max: Int = 30): Page<Playlist> {
        val params = linkedMapOf("part" to "snippet,contentDetails", "channelId" to channelId, "maxResults" to max.coerceIn(1, 50).toString())
        pageToken?.let { params["pageToken"] = it }
        val json = request("playlists", params, cost = 1)
        return Page(parsePlaylists(json), json.optString("nextPageToken").ifEmpty { null })
    }

    /** One playlist's header (1 unit). */
    fun playlist(id: String): Playlist? =
        parsePlaylists(request("playlists", mapOf("part" to "snippet,contentDetails", "id" to id), cost = 1)).firstOrNull()

    /**
     * The videos of a playlist, or of a channel's uploads playlist (1 unit + 1 for the details).
     * Private / deleted entries are dropped.
     */
    fun playlistVideos(playlistId: String, pageToken: String? = null, max: Int = 30): Page<Video> {
        val params = linkedMapOf("part" to "contentDetails", "playlistId" to playlistId, "maxResults" to max.coerceIn(1, 50).toString())
        pageToken?.let { params["pageToken"] = it }
        val json = request("playlistItems", params, cost = 1)
        val rows = json.optJSONArray("items") ?: JSONArray()
        val ids = (0 until rows.length())
            .mapNotNull { rows.optJSONObject(it)?.optJSONObject("contentDetails")?.optString("videoId") }
            .filter { it.isNotEmpty() }
        val byId = videos(ids).associateBy { it.id }
        // Keep the playlist's own order.
        return Page(ids.mapNotNull { byId[it] }, json.optString("nextPageToken").ifEmpty { null })
    }

    /** Top-level comments with their first replies (1 unit). */
    fun comments(videoId: String, order: CommentOrder = CommentOrder.TOP, pageToken: String? = null, max: Int = 40): Page<Comment> {
        val params = linkedMapOf(
            "part" to "snippet,replies", "videoId" to videoId, "order" to order.apiValue,
            "maxResults" to max.coerceIn(1, 100).toString(), "textFormat" to "plainText"
        )
        pageToken?.let { params["pageToken"] = it }
        val json = request("commentThreads", params, cost = 1)
        return Page(parseCommentThreads(json), json.optString("nextPageToken").ifEmpty { null })
    }

    /** All replies of one comment (1 unit). */
    fun commentReplies(parentId: String, pageToken: String? = null): Page<Comment> {
        val params = linkedMapOf("part" to "snippet", "parentId" to parentId, "maxResults" to "100", "textFormat" to "plainText")
        pageToken?.let { params["pageToken"] = it }
        val json = request("comments", params, cost = 1)
        val rows = json.optJSONArray("items") ?: JSONArray()
        return Page((0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.let { c -> parseComment(c, 0) } }, json.optString("nextPageToken").ifEmpty { null })
    }

    // ------------------------------------------------------------------ transport

    private const val VIDEO_PARTS = "snippet,contentDetails,statistics,liveStreamingDetails"
    private const val CHANNEL_PARTS = "snippet,statistics,contentDetails,brandingSettings"

    private fun request(path: String, params: Map<String, String>, cost: Int, cacheTtlMs: Long = CACHE_TTL_MS): JSONObject {
        if (apiKey.isEmpty()) throw YouTubeApiException("disabled", 0, "YouTube Data API key is not configured")
        disabledReason?.let { throw YouTubeApiException(it, 0, "YouTube Data API is switched off: $it") }

        val cacheKey = path + "?" + params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        synchronized(cache) {
            cache[cacheKey]?.takeIf { System.currentTimeMillis() - it.at < cacheTtlMs }?.let { return it.body }
        }
        if (!quota.tryCharge(cost)) {
            throw YouTubeApiException("quotaExceeded", 403, "Daily YouTube Data API budget is used up")
        }

        val url = buildUrl(path, params)
        var lastError: YouTubeApiException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                client.newCall(requestFor(url)).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val json = JSONObject(text)
                        synchronized(cache) { cache[cacheKey] = CacheEntry(System.currentTimeMillis(), json) }
                        return json
                    }
                    val error = classify(response.code, text)
                    when {
                        error.isQuota -> quota.markExhausted()
                        error.isKeyProblem -> disabledReason = error.reason
                    }
                    if (!error.isTransient) throw error
                    lastError = error
                }
            } catch (e: YouTubeApiException) {
                if (!e.isTransient) throw e
                lastError = e
            } catch (e: IOException) {
                lastError = YouTubeApiException("network", 0, e.message ?: "network error")
            } catch (e: org.json.JSONException) {
                lastError = YouTubeApiException("badResponse", 200, "Unreadable response: ${e.message}")
            }
            if (attempt < MAX_ATTEMPTS) sleeper(backoffMs(attempt))
        }
        throw lastError ?: YouTubeApiException("unknown", 0, "Request failed")
    }

    private fun buildUrl(path: String, params: Map<String, String>): HttpUrl {
        val builder = baseUrl.toHttpUrl().newBuilder().addPathSegments(path)
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        builder.addQueryParameter("key", apiKey)
        return builder.build()
    }

    private fun requestFor(url: HttpUrl): Request {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        // A key restricted to this Android app is only honoured with these two headers.
        if (androidPackage.isNotEmpty() && androidCertSha1.isNotEmpty()) {
            builder.header("X-Android-Package", androidPackage).header("X-Android-Cert", androidCertSha1)
        }
        return builder.build()
    }

    /** 400, 800 ms plus a little jitter. */
    internal fun backoffMs(attempt: Int): Long = (400L shl (attempt - 1)) + (System.nanoTime() % 120L)

    internal fun classify(code: Int, body: String): YouTubeApiException {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val reason = error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason").orEmpty()
            .ifEmpty { error?.optString("status").orEmpty() }
            .ifEmpty { "http$code" }
        val message = error?.optString("message").orEmpty().ifEmpty { "HTTP $code" }
        return YouTubeApiException(reason, code, message)
    }

    // ------------------------------------------------------------------ parsing (pure, unit-tested)

    internal fun parseVideos(json: JSONObject): List<Video> {
        val rows = json.optJSONArray("items") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i -> rows.optJSONObject(i)?.let { parseVideo(it) } }
    }

    internal fun parseVideo(item: JSONObject): Video? {
        val id = item.optString("id").ifEmpty { return null }
        val snippet = item.optJSONObject("snippet") ?: return null
        val stats = item.optJSONObject("statistics")
        val live = item.optJSONObject("liveStreamingDetails")
        val broadcast = snippet.optString("liveBroadcastContent")
        val tags = snippet.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        return Video(
            id = id,
            title = snippet.optString("title"),
            channelId = snippet.optString("channelId"),
            channelTitle = snippet.optString("channelTitle"),
            thumbnailUrl = bestThumbnail(snippet.optJSONObject("thumbnails")),
            durationSeconds = parseIsoDuration(item.optJSONObject("contentDetails")?.optString("duration").orEmpty()),
            viewCount = stats?.optString("viewCount")?.toLongOrNull() ?: -1L,
            likeCount = stats?.optString("likeCount")?.toLongOrNull() ?: -1L,
            commentCount = stats?.optString("commentCount")?.toLongOrNull() ?: -1L,
            publishedAtMs = parseIsoTime(snippet.optString("publishedAt")),
            description = snippet.optString("description"),
            tags = tags,
            isLive = broadcast == "live" || (live != null && live.has("actualStartTime") && !live.has("actualEndTime")),
            categoryId = snippet.optString("categoryId")
        )
    }

    internal fun parseChannels(json: JSONObject): List<Channel> {
        val rows = json.optJSONArray("items") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val item = rows.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id").ifEmpty { return@mapNotNull null }
            val snippet = item.optJSONObject("snippet")
            val stats = item.optJSONObject("statistics")
            val hidden = stats?.optBoolean("hiddenSubscriberCount") == true
            Channel(
                id = id,
                title = snippet?.optString("title").orEmpty(),
                description = snippet?.optString("description").orEmpty(),
                avatarUrl = bestThumbnail(snippet?.optJSONObject("thumbnails")),
                bannerUrl = item.optJSONObject("brandingSettings")?.optJSONObject("image")?.optString("bannerExternalUrl").orEmpty(),
                subscriberCount = if (hidden) -1L else stats?.optString("subscriberCount")?.toLongOrNull() ?: -1L,
                videoCount = stats?.optString("videoCount")?.toLongOrNull() ?: -1L,
                uploadsPlaylistId = item.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")?.optString("uploads").orEmpty()
            )
        }
    }

    internal fun parsePlaylists(json: JSONObject): List<Playlist> {
        val rows = json.optJSONArray("items") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val item = rows.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id").ifEmpty { return@mapNotNull null }
            val snippet = item.optJSONObject("snippet")
            Playlist(
                id = id,
                title = snippet?.optString("title").orEmpty(),
                channelTitle = snippet?.optString("channelTitle").orEmpty(),
                thumbnailUrl = bestThumbnail(snippet?.optJSONObject("thumbnails")),
                itemCount = item.optJSONObject("contentDetails")?.optLong("itemCount", -1L) ?: -1L
            )
        }
    }

    /** Search rows in order; videos take their full details from [videoDetails] (a row without them is dropped). */
    internal fun parseSearchHits(json: JSONObject, videoDetails: Map<String, Video>): List<SearchHit> {
        val rows = json.optJSONArray("items") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val item = rows.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optJSONObject("id") ?: return@mapNotNull null
            val snippet = item.optJSONObject("snippet")
            when (id.optString("kind")) {
                "youtube#video" -> videoDetails[id.optString("videoId")]?.let { SearchHit.VideoHit(it) }
                "youtube#channel" -> SearchHit.ChannelHit(
                    Channel(
                        id = id.optString("channelId"),
                        title = snippet?.optString("channelTitle").orEmpty().ifEmpty { snippet?.optString("title").orEmpty() },
                        description = snippet?.optString("description").orEmpty(),
                        avatarUrl = bestThumbnail(snippet?.optJSONObject("thumbnails")),
                        bannerUrl = "", subscriberCount = -1L, videoCount = -1L, uploadsPlaylistId = ""
                    )
                )
                "youtube#playlist" -> SearchHit.PlaylistHit(
                    Playlist(
                        id = id.optString("playlistId"),
                        title = snippet?.optString("title").orEmpty(),
                        channelTitle = snippet?.optString("channelTitle").orEmpty(),
                        thumbnailUrl = bestThumbnail(snippet?.optJSONObject("thumbnails")),
                        itemCount = -1L
                    )
                )
                else -> null
            }
        }
    }

    internal fun parseCommentThreads(json: JSONObject): List<Comment> {
        val rows = json.optJSONArray("items") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { i ->
            val thread = rows.optJSONObject(i) ?: return@mapNotNull null
            val top = thread.optJSONObject("snippet")?.optJSONObject("topLevelComment") ?: return@mapNotNull null
            val replyCount = thread.optJSONObject("snippet")?.optLong("totalReplyCount", 0L) ?: 0L
            val inline = thread.optJSONObject("replies")?.optJSONArray("comments")
                ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { c -> parseComment(c, 0L) } } }
                .orEmpty()
            parseComment(top, replyCount)?.copy(replies = inline)
        }
    }

    internal fun parseComment(item: JSONObject, replyCount: Long): Comment? {
        val id = item.optString("id").ifEmpty { return null }
        val s = item.optJSONObject("snippet") ?: return null
        return Comment(
            id = id,
            author = s.optString("authorDisplayName"),
            authorAvatarUrl = s.optString("authorProfileImageUrl"),
            authorChannelId = s.optJSONObject("authorChannelId")?.optString("value").orEmpty(),
            text = s.optString("textDisplay").ifEmpty { s.optString("textOriginal") },
            likeCount = s.optLong("likeCount", 0L),
            publishedAtMs = parseIsoTime(s.optString("publishedAt")),
            replyCount = replyCount
        )
    }

    /** The largest thumbnail offered (the API lists default, medium, high, standard, maxres). */
    internal fun bestThumbnail(thumbnails: JSONObject?): String {
        if (thumbnails == null) return ""
        for (name in listOf("maxres", "standard", "high", "medium", "default")) {
            thumbnails.optJSONObject(name)?.optString("url")?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }

    /** ISO-8601 duration as the API writes it ("PT1H2M3S", "P1DT2H", "PT45S"); live streams give "P0D" (0). */
    internal fun parseIsoDuration(iso: String): Long {
        val m = Regex("""^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""").matchEntire(iso.trim()) ?: return 0L
        val (d, h, min, s) = m.destructured
        return (d.toLongOrNull() ?: 0L) * 86_400L + (h.toLongOrNull() ?: 0L) * 3_600L + (min.toLongOrNull() ?: 0L) * 60L + (s.toLongOrNull() ?: 0L)
    }

    /** RFC 3339 time as the API writes it ("2024-05-01T12:30:45Z", with optional fraction); 0 when unreadable. */
    internal fun parseIsoTime(text: String): Long {
        if (text.isBlank()) return 0L
        val trimmed = text.substringBefore('.').removeSuffix("Z")
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(trimmed)?.time ?: 0L
        }.getOrDefault(0L)
    }
}

/** An error answered by the API (or the lack of one: reason "network", status 0). */
class YouTubeApiException(val reason: String, val httpCode: Int, message: String) : IOException(message) {
    /** The day's quota is spent: nothing more until it resets. */
    val isQuota: Boolean get() = reason == "quotaExceeded" || reason == "dailyLimitExceeded"

    /** The key is wrong, restricted away from this app, or the API is not enabled for it. */
    val isKeyProblem: Boolean
        get() = reason in KEY_REASONS || (httpCode == 400 && reason == "badRequest" && message?.contains("API key", ignoreCase = true) == true)

    /** Worth trying again shortly: server trouble, a burst limit, or the network. */
    val isTransient: Boolean
        get() = reason == "network" || reason == "badResponse" || httpCode in 500..599 || reason == "rateLimitExceeded" ||
            reason == "userRateLimitExceeded" || reason == "backendError"

    private companion object {
        val KEY_REASONS = setOf("keyInvalid", "API_KEY_INVALID", "ipRefererBlocked", "accessNotConfigured", "keyExpired", "API_KEY_SERVICE_BLOCKED", "androidClientBlocked")
    }
}

/** Where the day's quota count is kept (the app backs it with its preferences). */
interface QuotaStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class MemoryQuotaStore : QuotaStore {
    private val map = HashMap<String, String>()
    @Synchronized override fun get(key: String): String? = map[key]
    @Synchronized override fun put(key: String, value: String) { map[key] = value }
}

/**
 * Counts the Data API units spent today. The allowance resets at midnight Pacific time, so the
 * counter is keyed by the Pacific date. [dailyLimit] is kept a little under the 10,000 default so
 * the app never takes the last units that other tools on the same key may need.
 */
class QuotaTracker(
    private val store: QuotaStore = MemoryQuotaStore(),
    val dailyLimit: Int = DEFAULT_DAILY_LIMIT,
    /**
     * Calls costing at least [EXPENSIVE_UNITS] (search) only run while the day's total stays under
     * this, which keeps the rest of the day for the 1-unit calls every screen depends on.
     */
    val expensiveLimit: Int = DEFAULT_EXPENSIVE_LIMIT,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    @Synchronized
    fun remaining(): Int = (dailyLimit - usedToday()).coerceAtLeast(0)

    /** True when [units] would fit under today's limits. Reserves nothing. */
    @Synchronized
    fun canAfford(units: Int): Boolean {
        val used = usedToday()
        val limit = if (units >= EXPENSIVE_UNITS) minOf(expensiveLimit, dailyLimit) else dailyLimit
        return used + units <= limit
    }

    /** Reserves [units] and returns true, or returns false (and reserves nothing) when they do not fit. */
    @Synchronized
    fun tryCharge(units: Int): Boolean {
        if (!canAfford(units)) return false
        save(usedToday() + units)
        return true
    }

    /** The API said the quota is gone (maybe used elsewhere): treat the day as spent. */
    @Synchronized
    fun markExhausted() = save(dailyLimit)

    @Synchronized
    fun used(): Int = usedToday()

    private fun usedToday(): Int {
        val day = dayKey()
        if (store.get(KEY_DAY) != day) {
            store.put(KEY_DAY, day)
            store.put(KEY_USED, "0")
            return 0
        }
        return store.get(KEY_USED)?.toIntOrNull() ?: 0
    }

    private fun save(used: Int) {
        store.put(KEY_DAY, dayKey())
        store.put(KEY_USED, used.toString())
    }

    internal fun dayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("America/Los_Angeles") }.format(now())

    companion object {
        const val DEFAULT_DAILY_LIMIT = 9_000
        /** About 60 searches a day; the other 3,000 units are for everything else. */
        const val DEFAULT_EXPENSIVE_LIMIT = 6_000
        const val EXPENSIVE_UNITS = 50
        private const val KEY_DAY = "yt_quota_day"
        private const val KEY_USED = "yt_quota_used"
    }
}
