package com.nexusstream.app.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * The extras YouTube draws on and around its player: end screen elements, info cards, the "Most
 * replayed" heat map (also what Jump ahead skips to) and the transcript built from caption tracks.
 */
object WatchExtras {
    /** One end screen tile: a video, playlist, channel, subscribe button or link, placed in the frame. */
    data class EndScreenElement(
        /** VIDEO, PLAYLIST, CHANNEL, WEBSITE (the subscribe tile is a CHANNEL with [isSubscribe]). */
        val style: String,
        val title: String,
        val subtitle: String,
        val imageUrl: String,
        /** Position and size as fractions of the video frame. */
        val left: Float,
        val top: Float,
        val width: Float,
        val aspectRatio: Float,
        val startMs: Long,
        val endMs: Long,
        val videoId: String?,
        val playlistId: String?,
        val browseId: String?,
        val url: String?,
        val isSubscribe: Boolean
    )

    /** An info card ("i" teaser in the corner): a video, playlist or link YouTube shows at [startMs]. */
    data class InfoCard(
        val teaser: String,
        val title: String,
        val imageUrl: String,
        val startMs: Long,
        val videoId: String?,
        val playlistId: String?,
        val url: String?
    )

    /** One slice of the "Most replayed" graph; [intensity] is 0..1. */
    data class HeatPoint(val startMs: Long, val durationMs: Long, val intensity: Float)

    data class TranscriptLine(val startMs: Long, val durationMs: Long, val text: String)

    internal fun parseEndScreen(player: JSONObject): List<EndScreenElement> {
        val elements = player.optJSONObject("endscreen")?.optJSONObject("endscreenRenderer")?.optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<EndScreenElement>()
        for (i in 0 until elements.length()) {
            val e = elements.optJSONObject(i)?.optJSONObject("endscreenElementRenderer") ?: continue
            val endpoint = e.optJSONObject("endpoint") ?: JSONObject()
            val watch = endpoint.optJSONObject("watchEndpoint")
            out += EndScreenElement(
                style = e.optString("style"),
                title = InnerTube.text(e.optJSONObject("title")),
                subtitle = InnerTube.text(e.optJSONObject("metadata")),
                imageUrl = InnerTube.lastThumbnail(e.optJSONObject("image")),
                left = e.optDouble("left", 0.0).toFloat(),
                top = e.optDouble("top", 0.0).toFloat(),
                width = e.optDouble("width", 0.0).toFloat(),
                aspectRatio = e.optDouble("aspectRatio", 1.0).toFloat(),
                startMs = e.optString("startMs").toLongOrNull() ?: 0L,
                endMs = e.optString("endMs").toLongOrNull() ?: Long.MAX_VALUE,
                videoId = watch?.optString("videoId")?.takeIf { it.isNotEmpty() },
                playlistId = watch?.optString("playlistId")?.takeIf { it.isNotEmpty() },
                browseId = endpoint.optJSONObject("browseEndpoint")?.optString("browseId")?.takeIf { it.isNotEmpty() },
                url = endpoint.optJSONObject("urlEndpoint")?.optString("url")?.let(::unwrapRedirect),
                isSubscribe = e.optBoolean("isSubscribe")
            )
        }
        return out
    }

    internal fun parseCards(player: JSONObject): List<InfoCard> {
        val cards = player.optJSONObject("cards")?.optJSONObject("cardCollectionRenderer")?.optJSONArray("cards") ?: return emptyList()
        val out = ArrayList<InfoCard>()
        for (i in 0 until cards.length()) {
            val card = cards.optJSONObject(i)?.optJSONObject("cardRenderer") ?: continue
            val teaser = InnerTube.text(card.optJSONObject("teaser")?.optJSONObject("simpleCardTeaserRenderer")?.optJSONObject("message"))
            val start = card.optJSONArray("cueRanges")?.optJSONObject(0)?.optString("startCardActiveMs")?.toLongOrNull() ?: 0L
            val content = card.optJSONObject("content") ?: continue
            val video = content.optJSONObject("videoInfoCardContentRenderer")
            val playlist = content.optJSONObject("playlistInfoCardContentRenderer")
            val simple = content.optJSONObject("simpleCardContentRenderer")
            out += when {
                video != null -> InfoCard(
                    teaser, InnerTube.text(video.optJSONObject("videoTitle")), InnerTube.lastThumbnail(video.optJSONObject("videoThumbnail")), start,
                    video.optJSONObject("action")?.optJSONObject("watchEndpoint")?.optString("videoId"), null, null
                )
                playlist != null -> InfoCard(
                    teaser, InnerTube.text(playlist.optJSONObject("playlistTitle")), InnerTube.lastThumbnail(playlist.optJSONObject("playlistThumbnail")), start,
                    playlist.optJSONObject("action")?.optJSONObject("watchEndpoint")?.optString("videoId"),
                    playlist.optJSONObject("action")?.optJSONObject("watchEndpoint")?.optString("playlistId"), null
                )
                simple != null -> InfoCard(
                    teaser, InnerTube.text(simple.optJSONObject("title")), InnerTube.lastThumbnail(simple.optJSONObject("image")), start, null, null,
                    simple.optJSONObject("command")?.optJSONObject("urlEndpoint")?.optString("url")?.let(::unwrapRedirect)
                )
                else -> continue
            }
        }
        return out.sortedBy { it.startMs }
    }

    /** What the watch page (`next`) adds to the player: the heat map and where live chat starts. */
    class Next(val heatmap: List<HeatPoint>, val chat: LiveChat.Start?)

    fun watchNext(videoId: String): Next = runCatching {
        val root = InnerTube.web("next", JSONObject().put("videoId", videoId))
        Next(parseHeatmap(root), runCatching { LiveChat.parseStart(root) }.getOrNull())
    }.getOrDefault(Next(emptyList(), null))

    /** The "Most replayed" graph from the watch page (`next`), empty when the video has none. */
    fun heatmap(videoId: String): List<HeatPoint> = runCatching {
        parseHeatmap(InnerTube.web("next", JSONObject().put("videoId", videoId)))
    }.getOrDefault(emptyList())

    internal fun parseHeatmap(root: JSONObject): List<HeatPoint> {
        val out = ArrayList<HeatPoint>()
        fun add(m: JSONObject) {
            val start = m.optString("startMillis").toLongOrNull() ?: m.optString("timeRangeStartMillis").toLongOrNull() ?: return
            val duration = m.optString("durationMillis").toLongOrNull() ?: m.optString("markerDurationMillis").toLongOrNull() ?: return
            out += HeatPoint(start, duration, m.optDouble("intensityScoreNormalized", 0.0).toFloat().coerceIn(0f, 1f))
        }
        walk(root) { node ->
            node.optJSONObject("heatMarkerRenderer")?.let { add(it); return@walk false }
            val entity = node.optJSONObject("macroMarkersListEntity")
            if (entity != null) {
                val list = entity.optJSONObject("markersList")
                if (list?.optString("markerType") == "MARKER_TYPE_HEATMAP") {
                    val markers = list.optJSONArray("markers") ?: JSONArray()
                    for (i in 0 until markers.length()) markers.optJSONObject(i)?.let(::add)
                }
                return@walk false
            }
            true
        }
        return out.distinctBy { it.startMs }.sortedBy { it.startMs }
    }

    /**
     * Jump ahead: the start of the next replay peak after [positionMs] (at least 10 s on), the way
     * YouTube skips to where most people continue watching. Null when there is no clear peak ahead.
     */
    fun jumpAheadTarget(heat: List<HeatPoint>, positionMs: Long): Long? {
        if (heat.size < 3) return null
        for (i in 1 until heat.size - 1) {
            val p = heat[i]
            if (p.startMs < positionMs + 10_000) continue
            val peak = p.intensity >= heat[i - 1].intensity && p.intensity >= heat[i + 1].intensity && p.intensity >= 0.5f
            if (peak) return p.startMs
        }
        return null
    }

    /** The transcript of a caption track: its lines with start times (YouTube's json3 caption format). */
    fun transcript(track: SubtitleOption): List<TranscriptLine> {
        val url = track.url.toHttpUrlOrNull()?.newBuilder()?.setQueryParameter("fmt", "json3")?.build() ?: error("Bad caption URL")
        val request = Request.Builder().url(url).header("User-Agent", NewPipeDownloader.USER_AGENT).build()
        val body = NewPipeDownloader.instance.client.newCall(request).execute().use { res ->
            check(res.isSuccessful) { "Couldn't load the transcript (${res.code})" }
            res.body?.string().orEmpty()
        }
        return parseTranscript(JSONObject(body))
    }

    internal fun parseTranscript(root: JSONObject): List<TranscriptLine> {
        val events = root.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<TranscriptLine>()
        for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            val segs = e.optJSONArray("segs") ?: continue
            val text = buildString { for (j in 0 until segs.length()) append(segs.optJSONObject(j)?.optString("utf8").orEmpty()) }
                .replace('\n', ' ').trim()
            if (text.isEmpty()) continue
            out += TranscriptLine(e.optLong("tStartMs"), e.optLong("dDurationMs"), text)
        }
        return out
    }

    /** Caption URL translated by YouTube into [language] (auto-translate). */
    fun translatedUrl(url: String, language: String): String =
        url.toHttpUrlOrNull()?.newBuilder()?.setQueryParameter("tlang", language)?.build()?.toString() ?: url

    /** Languages offered for auto-translated captions, as YouTube lists the most common ones. */
    val translationLanguages: List<Pair<String, String>> = listOf(
        "en" to "English", "es" to "Spanish", "fr" to "French", "de" to "German", "it" to "Italian",
        "pt" to "Portuguese", "ru" to "Russian", "ja" to "Japanese", "ko" to "Korean", "zh-Hans" to "Chinese (Simplified)",
        "zh-Hant" to "Chinese (Traditional)", "ar" to "Arabic", "hi" to "Hindi", "bn" to "Bengali", "te" to "Telugu",
        "ta" to "Tamil", "kn" to "Kannada", "ml" to "Malayalam", "mr" to "Marathi", "ur" to "Urdu", "id" to "Indonesian",
        "tr" to "Turkish", "vi" to "Vietnamese", "th" to "Thai", "nl" to "Dutch", "pl" to "Polish", "uk" to "Ukrainian"
    )

    /** YouTube wraps outside links in `/redirect?q=...`; the real address is in `q`. */
    private fun unwrapRedirect(url: String): String {
        val parsed = (if (url.startsWith("/")) "https://www.youtube.com$url" else url).toHttpUrlOrNull() ?: return url
        return if (parsed.encodedPath == "/redirect") parsed.queryParameter("q") ?: url else parsed.toString()
    }

    private fun walk(node: Any?, visit: (JSONObject) -> Boolean) {
        when (node) {
            is JSONObject -> if (visit(node)) for (k in node.keys()) walk(node.opt(k), visit)
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }
}
