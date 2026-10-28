package com.nexusstream.app.data

import androidx.compose.ui.graphics.Color
import com.nexusstream.app.model.MediaContentType
import com.nexusstream.app.model.MediaItemModel
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Minimal client for YouTube's web API ("InnerTube"). With a signed-in [GoogleAccount] the requests
 * carry the account's cookies and a SAPISIDHASH header, so the feed is the account's own
 * personalised Home / subscriptions. Signed out it returns the generic feed.
 */
object InnerTube {
    private const val ENDPOINT = "https://www.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val NEXT_ENDPOINT = "https://www.youtube.com/youtubei/v1/next?prettyPrint=false"
    private const val ORIGIN = "https://www.youtube.com"
    private const val CLIENT_VERSION = "2.20250101.00.00"
    private val JSON = "application/json".toMediaType()
    private val DURATION = Regex("""\d{1,2}(:\d{2}){1,2}""")

    /** Feed menu feedback tokens for a video, captured while parsing feeds. */
    class Feedback(val notInterested: String?, val dontRecommendChannel: String?)

    private val feedback = java.util.concurrent.ConcurrentHashMap<String, Feedback>()

    fun feedbackFor(videoId: String): Feedback? = feedback[videoId]

    /**
     * Finds the "Not interested" and "Don't recommend channel" tokens in a video's menu (old
     * menuServiceItemRenderer rows and the newer sheet of listItemViewModels).
     */
    internal fun captureFeedback(videoId: String, node: JSONObject) {
        var notInterested: String? = null
        var channel: String? = null
        fun label(o: JSONObject): String =
            text(o.optJSONObject("text")).ifEmpty { o.optJSONObject("title")?.optString("content").orEmpty() }
        fun token(o: Any?): String? {
            when (o) {
                is JSONObject -> {
                    o.optJSONObject("feedbackEndpoint")?.optString("feedbackToken")?.takeIf { it.isNotEmpty() }?.let { return it }
                    for (k in o.keys()) token(o.opt(k))?.let { return it }
                }
                is JSONArray -> for (i in 0 until o.length()) token(o.opt(i))?.let { return it }
            }
            return null
        }
        fun walk(o: Any?) {
            when (o) {
                is JSONObject -> {
                    val row = o.optJSONObject("menuServiceItemRenderer") ?: o.optJSONObject("listItemViewModel")
                    if (row != null) {
                        val l = label(row).lowercase()
                        val icon = row.optJSONObject("icon")?.optString("iconType").orEmpty()
                        val t = token(row)
                        if (t != null) when {
                            l.startsWith("not interested") || icon == "NOT_INTERESTED" -> notInterested = t
                            l.startsWith("don't recommend") || l.startsWith("don\u2019t recommend") || icon == "REMOVE" -> channel = t
                        }
                    } else o.keys().forEach { walk(o.opt(it)) }
                }
                is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i))
            }
        }
        walk(node)
        if (notInterested != null || channel != null) feedback[videoId] = Feedback(notInterested, channel)
    }

    /** Page of videos plus the token for the next page (null at the end). */
    class Page(val items: List<MediaItemModel>, val continuation: String?)

    fun home(): Page = browse(JSONObject().put("browseId", "FEwhat_to_watch"))

    fun subscriptions(): Page = browse(JSONObject().put("browseId", "FEsubscriptions"))

    /** Fetches the list of channels the signed-in user subscribes to on YouTube. */
    fun channels(): List<LibraryRepository.ChannelSub> = runCatching {
        if (!GoogleAccount.isSignedIn()) return@runCatching emptyList()
        val raw = browseRaw(JSONObject().put("browseId", "FEchannels"))
        val list = ArrayList<LibraryRepository.ChannelSub>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val cr = node.optJSONObject("channelRenderer") ?: node.optJSONObject("gridChannelRenderer")
                    if (cr != null) {
                        val title = text(cr.optJSONObject("title"))
                        val avatar = lastThumbnail(cr.optJSONObject("thumbnail"))
                        val channelId = cr.optString("channelId")
                        val navUrl = cr.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("canonicalBaseUrl").orEmpty()
                        val url = if (navUrl.isNotEmpty()) {
                            if (navUrl.startsWith("/")) "https://www.youtube.com$navUrl" else navUrl
                        } else if (channelId.isNotEmpty()) {
                            "https://www.youtube.com/channel/$channelId"
                        } else ""
                        if (title.isNotEmpty()) {
                            list.add(LibraryRepository.ChannelSub(name = title, avatarUrl = avatar, channelUrl = url, lastSeenMs = 0L))
                        }
                    }
                    node.keys().forEach { walk(node.opt(it)) }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(raw)
        list.distinctBy { it.name }
    }.getOrDefault(emptyList())

    fun history(): Page = browse(JSONObject().put("browseId", "FEhistory"))

    fun more(continuation: String): Page = browse(JSONObject().put("continuation", continuation))

    /** Fetches the official continuous YouTube / YT Music radio queue for a track seed. */
    fun radio(videoId: String): Page {
        val extra = JSONObject().put("videoId", videoId).put("playlistId", "RDAMVM$videoId")
        return parse(postEndpoint(NEXT_ENDPOINT, extra))
    }

    /** Extras from the web player response: loudness (Stable volume) and the watch-history pings. */
    class PlayerExtras(val loudnessDb: Double?, val playbackUrl: String?, val watchtimeUrl: String?)

    fun playerExtras(videoId: String): PlayerExtras? = runCatching {
        val res = web("player", JSONObject().put("videoId", videoId))
        val audio = res.optJSONObject("playerConfig")?.optJSONObject("audioConfig")
        val loudness = when {
            audio == null -> null
            audio.has("loudnessDb") -> audio.optDouble("loudnessDb")
            audio.has("perceptualLoudnessDb") -> audio.optDouble("perceptualLoudnessDb") + 18.0
            else -> null
        }?.takeIf { !it.isNaN() }
        val tracking = res.optJSONObject("playbackTracking")
        PlayerExtras(
            loudnessDb = loudness,
            playbackUrl = tracking?.optJSONObject("videostatsPlaybackUrl")?.optString("baseUrl")?.takeIf { it.isNotEmpty() },
            watchtimeUrl = tracking?.optJSONObject("videostatsWatchtimeUrl")?.optString("baseUrl")?.takeIf { it.isNotEmpty() }
        )
    }.getOrNull()

    /**
     * Reports a play to the account's watch history (what the website does when a video starts),
     * then the watched time when it ends. [cpn] ties the two pings together.
     */
    fun reportPlayback(extras: PlayerExtras, cpn: String) {
        val url = extras.playbackUrl ?: return
        stats(url, mapOf("cpn" to cpn, "fmt" to "251", "rtn" to "0", "rt" to "0"))
    }

    fun reportWatchtime(extras: PlayerExtras, cpn: String, seconds: Double) {
        val url = extras.watchtimeUrl ?: return
        val t = "%.3f".format(java.util.Locale.US, seconds)
        stats(url, mapOf("cpn" to cpn, "st" to "0", "et" to t, "cmt" to t, "final" to "1"))
    }

    private fun stats(baseUrl: String, params: Map<String, String>) {
        if (!GoogleAccount.isSignedIn()) return
        val url = (baseUrl.replace("https://s.youtube.com", "https://www.youtube.com").toHttpUrlOrNull() ?: return).newBuilder()
            .setQueryParameter("ver", "2")
            .setQueryParameter("c", "web")
            .setQueryParameter("cbrver", CLIENT_VERSION)
            .setQueryParameter("cver", CLIENT_VERSION)
            .apply { params.forEach { (k, v) -> setQueryParameter(k, v) } }
            .build()
        val builder = Request.Builder().url(url).get().header("User-Agent", NewPipeDownloader.USER_AGENT)
        GoogleAccount.cookieHeaderFor(ORIGIN)?.let { builder.header("Cookie", it) }
        runCatching { NewPipeDownloader.instance.client.newCall(builder.build()).execute().close() }
    }

    /** A random client playback nonce, as the web player makes one per playback. */
    fun newCpn(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..16).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("")
    }

    private fun browse(extra: JSONObject): Page = parse(browseRaw(extra))

    /** Names of the cookies we hold (never their values), for the self-test report. */
    fun cookieNames(): List<String> =
        GoogleAccount.cookieHeaderFor(ORIGIN)?.split(';')?.map { it.trim().substringBefore('=') }.orEmpty()

    /** What the Home request returned, summarised for the self-test report. */
    fun probeHome(): List<String> {
        val raw = runCatching { browseRaw(JSONObject().put("browseId", "FEwhat_to_watch")) }
            .getOrElse { return listOf("request failed: ${it.message}") }
        val page = parse(raw)
        val counts = sortedMapOf<String, Int>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> node.keys().forEach { key ->
                    if (key.endsWith("Renderer") || key.endsWith("ViewModel")) counts[key] = (counts[key] ?: 0) + 1
                    walk(node.opt(key))
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(raw)
        return listOf(
            "videos parsed: ${page.items.size}, next page: ${page.continuation != null}",
            "logged in per response: ${raw.toString().contains("\"LOGGED_IN\"") || raw.toString().contains("\"loggedIn\":true")}",
            "top renderers: " + counts.entries.sortedByDescending { it.value }.take(14).joinToString { "${it.key}x${it.value}" }
        )
    }

    private fun browseRaw(extra: JSONObject): JSONObject = postEndpoint(ENDPOINT, extra)

    private fun postEndpoint(endpoint: String, extra: JSONObject): JSONObject =
        post(endpoint, extra, ORIGIN, "WEB", "1", CLIENT_VERSION)

    /** POSTs to `youtubei/v1/<path>` as the WEB client (account actions use this). */
    internal fun web(path: String, body: JSONObject): JSONObject =
        postEndpoint("https://www.youtube.com/youtubei/v1/$path?prettyPrint=false", body)

    /**
     * POSTs an InnerTube request as the given web client. Shared with [YTMusicApi], which talks to
     * music.youtube.com as the WEB_REMIX client.
     */
    internal fun post(
        endpoint: String,
        extra: JSONObject,
        origin: String,
        clientName: String,
        clientNameId: String,
        clientVersion: String
    ): JSONObject {
        val body = JSONObject().apply {
            val identity = GoogleAccount.identity.value
            put("context", JSONObject().put("client", JSONObject()
                .put("clientName", clientName)
                .put("clientVersion", clientVersion)
                .put("hl", "en")
                .put("gl", AppSettings.contentCountry.value.ifBlank { "US" }))
                .apply {
                    // A Brand account / other channel picked in the account switcher.
                    if (GoogleAccount.isSignedIn() && identity.pageId.isNotEmpty()) {
                        put("user", JSONObject().put("onBehalfOfUser", identity.pageId))
                    }
                    // Restricted mode.
                    if (AppSettings.restrictedMode.value) {
                        put("user", (optJSONObject("user") ?: JSONObject()).put("enableSafetyMode", true))
                    }
                })
            extra.keys().forEach { put(it, extra.get(it)) }
        }
        val builder = Request.Builder()
            .url(endpoint)
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", NewPipeDownloader.USER_AGENT)
            .header("Origin", origin)
            .header("X-Origin", origin)
            .header("Content-Type", "application/json")
            builder.header("X-Youtube-Client-Name", clientNameId)
            builder.header("X-Youtube-Client-Version", clientVersion)
        GoogleAccount.cookieHeaderFor(origin)?.let { cookies ->
            builder.header("Cookie", cookies)
            sapisidHash(cookies, origin = origin)?.let { builder.header("Authorization", it) }
            builder.header("X-Goog-AuthUser", GoogleAccount.identity.value.authUser.toString())
            builder.header("X-Youtube-Bootstrap-Logged-In", "true")
        }
        NewPipeDownloader.instance.client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "YouTube returned HTTP ${response.code}" }
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    /** `SAPISIDHASH <ts>_<sha1(ts + " " + SAPISID + " " + origin)>`, as the website sends it. */
    internal fun sapisidHash(
        cookieHeader: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
        origin: String = ORIGIN
    ): String? {
        val sapisid = cookieHeader.split(';').map { it.trim() }
            .firstOrNull { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.substringAfter('=') ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest("$nowSeconds $sapisid $origin".toByteArray())
        return "SAPISIDHASH ${nowSeconds}_" + digest.joinToString("") { "%02x".format(it) }
    }

    internal fun parse(root: JSONObject): Page {
        val items = ArrayList<MediaItemModel>()
        var continuation: String? = null
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    node.optJSONObject("videoRenderer")?.let { v -> video(v)?.also { captureFeedback(it.id, v) }?.let(items::add) }
                    node.optJSONObject("gridVideoRenderer")?.let { video(it)?.let(items::add) }
                    node.optJSONObject("compactVideoRenderer")?.let { video(it)?.let(items::add) }
                    node.optJSONObject("lockupViewModel")?.let { l -> lockup(l)?.also { captureFeedback(it.id, l) }?.let(items::add) }
                    node.optJSONObject("playlistPanelVideoRenderer")?.let { playlistPanelVideo(it)?.let(items::add) }
                    node.optJSONObject("continuationItemRenderer")?.let {
                        continuation = it.optJSONObject("continuationEndpoint")
                            ?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { t -> t.isNotEmpty() }
                    }
                    node.keys().forEach { key ->
                        if (key != "videoRenderer" && key != "gridVideoRenderer" && key != "compactVideoRenderer" &&
                            key != "lockupViewModel" && key != "playlistPanelVideoRenderer") walk(node.opt(key))
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        return Page(items.distinctBy { it.id }, continuation)
    }

    internal fun text(node: JSONObject?): String {
        node ?: return ""
        node.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = node.optJSONArray("runs") ?: return ""
        return (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") }
    }

    internal fun parseRelativeTime(text: String): Long {
        if (text.isBlank()) return 0L
        val now = System.currentTimeMillis()
        val clean = text.lowercase().trim()
        val num = Regex("""\d+""").find(clean)?.value?.toLongOrNull() ?: return now
        return when {
            clean.contains("second") -> now - num * 1000L
            clean.contains("minute") -> now - num * 60 * 1000L
            clean.contains("hour") -> now - num * 3600 * 1000L
            clean.contains("day") -> now - num * 86400 * 1000L
            clean.contains("week") -> now - num * 7 * 86400 * 1000L
            clean.contains("month") -> now - num * 30 * 86400 * 1000L
            clean.contains("year") -> now - num * 365 * 86400 * 1000L
            else -> now
        }
    }

    private fun video(v: JSONObject): MediaItemModel? {
        val id = v.optString("videoId").takeIf { it.length == 11 } ?: return null
        val durationText = text(v.optJSONObject("lengthText"))
        val seconds = parseDuration(durationText)
        val live = durationText.isEmpty()
        if (!live && seconds in 1..60) return null // Shorts belong to the Shorts tab
        val owner = v.optJSONObject("ownerText") ?: v.optJSONObject("longBylineText") ?: v.optJSONObject("shortBylineText")
        val runs = owner?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
        val canonical = runs?.optString("canonicalBaseUrl").orEmpty()
        val browseId = runs?.optString("browseId").orEmpty()
        val channelUrl = when {
            canonical.startsWith("/") -> "https://www.youtube.com$canonical"
            canonical.isNotEmpty() -> canonical
            browseId.isNotEmpty() -> "https://www.youtube.com/channel/$browseId"
            else -> ""
        }
        val avatar = lastThumbnail(v.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")?.optJSONObject("thumbnail"))
            .ifEmpty { lastThumbnail(v.optJSONObject("channelThumbnail")) }
            .ifEmpty { lastThumbnail(v.optJSONObject("channelAvatar")) }
        val published = text(v.optJSONObject("publishedTimeText"))
        return build(
            id = id,
            title = text(v.optJSONObject("title")),
            channel = text(owner),
            thumbnail = lastThumbnail(v.optJSONObject("thumbnail")),
            avatar = avatar,
            seconds = seconds,
            views = text(v.optJSONObject("shortViewCountText") ?: v.optJSONObject("viewCountText")),
            published = published,
            channelUrl = channelUrl,
            live = live,
            publishedAtMs = parseRelativeTime(published)
        )
    }

    /** The newer "lockup" layout used by some home feeds. */
    private fun lockup(l: JSONObject): MediaItemModel? {
        if (l.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = l.optString("contentId").takeIf { it.length == 11 } ?: return null
        val meta = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val title = meta?.optJSONObject("title")?.optString("content").orEmpty()
        val rows = meta?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
        val lines = ArrayList<String>()
        if (rows != null) for (i in 0 until rows.length()) {
            val parts = rows.getJSONObject(i).optJSONArray("metadataParts") ?: continue
            for (j in 0 until parts.length()) {
                parts.getJSONObject(j).optJSONObject("text")?.optString("content")?.let(lines::add)
            }
        }
        val thumbs = l.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")
            ?.optJSONObject("image")?.optJSONArray("sources")
        val thumbnail = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
        val durationText = l.toString().let { DURATION.find(it)?.value.orEmpty() }
        val published = lines.getOrNull(2).orEmpty()
        return build(
            id = id, title = title, channel = lines.firstOrNull().orEmpty(), thumbnail = thumbnail,
            avatar = "", seconds = parseDuration(durationText),
            views = lines.getOrNull(1).orEmpty(), published = published,
            channelUrl = "", live = false,
            publishedAtMs = parseRelativeTime(published)
        )
    }

    private fun playlistPanelVideo(p: JSONObject): MediaItemModel? {
        val id = p.optString("videoId").takeIf { it.length == 11 } ?: return null
        val durationText = text(p.optJSONObject("lengthText"))
        val owner = p.optJSONObject("shortBylineText") ?: p.optJSONObject("longBylineText")
        return build(
            id = id,
            title = text(p.optJSONObject("title")),
            channel = text(owner),
            thumbnail = lastThumbnail(p.optJSONObject("thumbnail")),
            avatar = "",
            seconds = parseDuration(durationText),
            views = "",
            published = "",
            channelUrl = "",
            live = durationText.isEmpty(),
            publishedAtMs = 0L
        ).copy(contentType = MediaContentType.MUSIC_TRACK)
    }

    internal fun build(
        id: String, title: String, channel: String, thumbnail: String, avatar: String,
        seconds: Long, views: String, published: String, channelUrl: String, live: Boolean,
        publishedAtMs: Long = 0L
    ) = MediaItemModel(
        id = id,
        title = title,
        artistOrChannel = channel,
        audioUrl = "",
        videoUrl = "",
        artworkUrl = thumbnail.ifEmpty { "https://i.ytimg.com/vi/$id/hqdefault.jpg" },
        durationMs = seconds * 1000L,
        dominantColor = Color(0xFF6C5CE7),
        secondaryColor = Color(0xFFFF7675),
        viewsOrPlays = views,
        contentType = MediaContentType.YOUTUBE_VIDEO,
        channelAvatarUrl = avatar,
        publishDate = published,
        sourceUrl = "https://www.youtube.com/watch?v=$id",
        channelUrl = channelUrl,
        publishedAtMs = publishedAtMs,
        isLive = live
    )

    internal fun lastThumbnail(node: JSONObject?): String {
        val list = node?.optJSONArray("thumbnails") ?: return ""
        return list.optJSONObject(list.length() - 1)?.optString("url").orEmpty()
            .let { if (it.startsWith("//")) "https:$it" else it }
    }

    internal fun parseDuration(text: String): Long {
        val clean = text.trim()
        if (clean.isEmpty()) return 0L
        val match = DURATION.find(clean)?.value ?: return 0L
        return runCatching {
            match.split(':').fold(0L) { acc, part -> acc * 60 + part.trim().toLong() }
        }.getOrDefault(0L)
    }
}
