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
    private const val ORIGIN = "https://www.youtube.com"
    private const val CLIENT_VERSION = "2.20250101.00.00"
    private val JSON = "application/json".toMediaType()
    private val DURATION = Regex("""\d{1,2}(:\d{2}){1,2}""")

    /** Page of videos plus the token for the next page (null at the end). */
    class Page(val items: List<MediaItemModel>, val continuation: String?)

    fun home(): Page = browse(JSONObject().put("browseId", "FEwhat_to_watch"))

    fun subscriptions(): Page = browse(JSONObject().put("browseId", "FEsubscriptions"))

    fun history(): Page = browse(JSONObject().put("browseId", "FEhistory"))

    fun more(continuation: String): Page = browse(JSONObject().put("continuation", continuation))

    private fun browse(extra: JSONObject): Page {
        val body = JSONObject().apply {
            put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", CLIENT_VERSION)
                .put("hl", "en")
                .put("gl", AppSettings.contentCountry.value.ifBlank { "US" })))
            extra.keys().forEach { put(it, extra.get(it)) }
        }
        val builder = Request.Builder()
            .url(ENDPOINT)
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", NewPipeDownloader.USER_AGENT)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("Content-Type", "application/json")
        GoogleAccount.cookieHeaderFor(ORIGIN)?.let { cookies ->
            builder.header("Cookie", cookies)
            sapisidHash(cookies)?.let { builder.header("Authorization", it) }
            builder.header("X-Goog-AuthUser", "0")
            builder.header("X-Youtube-Bootstrap-Logged-In", "true")
        }
        NewPipeDownloader.instance.client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "YouTube returned HTTP ${response.code}" }
            return parse(JSONObject(response.body?.string().orEmpty()))
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
                    node.optJSONObject("lockupViewModel")?.let { lockup(it)?.let(items::add) }
                    node.optJSONObject("continuationItemRenderer")?.let {
                        continuation = it.optJSONObject("continuationEndpoint")
                            ?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { t -> t.isNotEmpty() }
                    }
                    node.keys().forEach { key ->
                        if (key != "videoRenderer" && key != "lockupViewModel") walk(node.opt(key))
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

    private fun video(v: JSONObject): MediaItemModel? {
        val id = v.optString("videoId").takeIf { it.length == 11 } ?: return null
        val durationText = text(v.optJSONObject("lengthText"))
        val seconds = parseDuration(durationText)
        val live = durationText.isEmpty()
        if (!live && seconds in 1..60) return null // Shorts belong to the Shorts tab
        val owner = v.optJSONObject("ownerText") ?: v.optJSONObject("longBylineText")
        val channelUrl = owner?.optJSONArray("runs")?.optJSONObject(0)
            ?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
            ?.optString("canonicalBaseUrl").orEmpty().let { if (it.startsWith("/")) "https://www.youtube.com$it" else it }
        return build(
            id = id,
            title = text(v.optJSONObject("title")),
            channel = text(owner),
            thumbnail = lastThumbnail(v.optJSONObject("thumbnail")),
            avatar = lastThumbnail(v.optJSONObject("channelThumbnailSupportedRenderers")
                ?.optJSONObject("channelThumbnailWithLinkRenderer")?.optJSONObject("thumbnail")),
            seconds = seconds,
            views = text(v.optJSONObject("shortViewCountText") ?: v.optJSONObject("viewCountText")),
            published = text(v.optJSONObject("publishedTimeText")),
            channelUrl = channelUrl,
            live = live
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
        return build(
            id = id, title = title, channel = lines.firstOrNull().orEmpty(), thumbnail = thumbnail,
            avatar = "", seconds = parseDuration(durationText),
            views = lines.getOrNull(1).orEmpty(), published = lines.getOrNull(2).orEmpty(),
            channelUrl = "", live = false
        )
    }

    private fun build(
        id: String, title: String, channel: String, thumbnail: String, avatar: String,
        seconds: Long, views: String, published: String, channelUrl: String, live: Boolean
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
        isLive = live
    )

    private fun lastThumbnail(node: JSONObject?): String {
        val list = node?.optJSONArray("thumbnails") ?: return ""
        return list.optJSONObject(list.length() - 1)?.optString("url").orEmpty()
            .let { if (it.startsWith("//")) "https:$it" else it }
    }

    private fun parseDuration(text: String): Long {
        if (text.isEmpty() || !DURATION.matches(text)) return 0
        return text.split(':').fold(0L) { acc, part -> acc * 60 + part.toLong() }
    }
}
