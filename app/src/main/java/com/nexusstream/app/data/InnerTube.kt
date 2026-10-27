package com.nexusstream.app.data

import androidx.compose.ui.graphics.Color
import com.nexusstream.app.model.MediaContentType
import com.nexusstream.app.model.MediaItemModel
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

    /** Submits a like or removes a like on YouTube servers if the account is signed in. */
    fun likeVideo(videoId: String, remove: Boolean = false): Boolean = runCatching {
        if (!GoogleAccount.isSignedIn()) return false
        val action = if (remove) "removelike" else "like"
        val endpoint = "https://www.youtube.com/youtubei/v1/like/$action?prettyPrint=false"
        val body = JSONObject().put("target", JSONObject().put("videoId", videoId))
        postEndpoint(endpoint, body)
        true
    }.getOrDefault(false)

    /** Subscribes or unsubscribes from a channel on YouTube servers if the account is signed in. */
    fun subscribeChannel(channelId: String, unsubscribe: Boolean = false): Boolean = runCatching {
        if (!GoogleAccount.isSignedIn() || channelId.isBlank()) return false
        val action = if (unsubscribe) "unsubscribe" else "subscribe"
        val endpoint = "https://www.youtube.com/youtubei/v1/subscription/$action?prettyPrint=false"
        val body = JSONObject().put("channelIds", JSONArray().put(channelId))
        postEndpoint(endpoint, body)
        true
    }.getOrDefault(false)

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

    private fun postEndpoint(endpoint: String, extra: JSONObject): JSONObject {
        val body = JSONObject().apply {
            put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", CLIENT_VERSION)
                .put("hl", "en")
                .put("gl", AppSettings.contentCountry.value.ifBlank { "US" })))
            extra.keys().forEach { put(it, extra.get(it)) }
        }
        val builder = Request.Builder()
            .url(endpoint)
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", NewPipeDownloader.USER_AGENT)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("Content-Type", "application/json")
            builder.header("X-Youtube-Client-Name", "1")
            builder.header("X-Youtube-Client-Version", CLIENT_VERSION)
        GoogleAccount.cookieHeaderFor(ORIGIN)?.let { cookies ->
            builder.header("Cookie", cookies)
            sapisidHash(cookies)?.let { builder.header("Authorization", it) }
            builder.header("X-Goog-AuthUser", "0")
            builder.header("X-Youtube-Bootstrap-Logged-In", "true")
        }
        NewPipeDownloader.instance.client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "YouTube returned HTTP ${response.code}" }
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    /** `SAPISIDHASH <ts>_<sha1(ts + " " + SAPISID + " " + origin)>`, as the website sends it. */
    internal fun sapisidHash(cookieHeader: String, nowSeconds: Long = System.currentTimeMillis() / 1000): String? {
        val sapisid = cookieHeader.split(';').map { it.trim() }
            .firstOrNull { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.substringAfter('=') ?: return null
        val digest = MessageDigest.getInstance("SHA-1").digest("$nowSeconds $sapisid $ORIGIN".toByteArray())
        return "SAPISIDHASH ${nowSeconds}_" + digest.joinToString("") { "%02x".format(it) }
    }

    internal fun parse(root: JSONObject): Page {
        val items = ArrayList<MediaItemModel>()
        var continuation: String? = null
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    node.optJSONObject("videoRenderer")?.let { video(it)?.let(items::add) }
                    node.optJSONObject("gridVideoRenderer")?.let { video(it)?.let(items::add) }
                    node.optJSONObject("compactVideoRenderer")?.let { video(it)?.let(items::add) }
                    node.optJSONObject("lockupViewModel")?.let { lockup(it)?.let(items::add) }
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

    private fun text(node: JSONObject?): String {
        node ?: return ""
        node.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = node.optJSONArray("runs") ?: return ""
        return (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") }
    }

    private fun parseRelativeTime(text: String): Long {
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

    private fun build(
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

    private fun lastThumbnail(node: JSONObject?): String {
        val list = node?.optJSONArray("thumbnails") ?: return ""
        return list.optJSONObject(list.length() - 1)?.optString("url").orEmpty()
            .let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun parseDuration(text: String): Long {
        val clean = text.trim()
        if (clean.isEmpty()) return 0L
        val match = DURATION.find(clean)?.value ?: return 0L
        return runCatching {
            match.split(':').fold(0L) { acc, part -> acc * 60 + part.trim().toLong() }
        }.getOrDefault(0L)
    }
}
