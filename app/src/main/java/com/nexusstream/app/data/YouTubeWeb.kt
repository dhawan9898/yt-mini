package com.nexusstream.app.data

import com.nexusstream.app.model.MediaItemModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parts of the website's own API that NewPipe doesn't cover: filtered search, channel tabs with
 * their sort chips, community posts, the Home feed's topic chips, Explore destinations and the
 * Shorts player's endless sequence.
 */
object YouTubeWeb {
    // ---- search filters (SearchFilter proto, as YouTube.js encodes it) ----

    enum class SortBy(val label: String, val value: Int) {
        RELEVANCE("Relevance", 0), UPLOAD_DATE("Upload date", 2), VIEW_COUNT("View count", 3), RATING("Rating", 1)
    }

    enum class UploadDate(val label: String, val value: Int) {
        ANY("Any time", 0), HOUR("Last hour", 1), TODAY("Today", 2), WEEK("This week", 3), MONTH("This month", 4), YEAR("This year", 5)
    }

    enum class Duration(val label: String, val value: Int) {
        ANY("Any length", 0), SHORT("Under 3 minutes", 4), MEDIUM("3 - 20 minutes", 5), LONG("Over 20 minutes", 2)
    }

    enum class Feature(val label: String, val field: Int) {
        LIVE("Live", 8), K4("4K", 14), HD("HD", 4), SUBTITLES("Subtitles/CC", 5), CREATIVE_COMMONS("Creative Commons", 6),
        VR360("360°", 15), VR180("VR180", 26), THREE_D("3D", 7), HDR("HDR", 25), LOCATION("Location", 23), PURCHASED("Purchased", 9)
    }

    data class SearchFilters(
        val sort: SortBy = SortBy.RELEVANCE,
        val date: UploadDate = UploadDate.ANY,
        val duration: Duration = Duration.ANY,
        val features: Set<Feature> = emptySet()
    ) {
        val isDefault: Boolean get() = sort == SortBy.RELEVANCE && date == UploadDate.ANY && duration == Duration.ANY && features.isEmpty()
        val count: Int get() = listOf(sort != SortBy.RELEVANCE, date != UploadDate.ANY, duration != Duration.ANY).count { it } + features.size
    }

    /** `params` for a search with these filters; [type] 1 = videos only. */
    internal fun searchParams(filters: SearchFilters, type: Int = 0): String = Proto()
        .apply { if (filters.sort.value != 0) int(1, filters.sort.value) }
        .message(2) {
            if (filters.date.value != 0) int(1, filters.date.value)
            if (type != 0) int(2, type)
            if (filters.duration.value != 0) int(3, filters.duration.value)
            filters.features.sortedBy { it.field }.forEach { bool(it.field, true) }
        }
        .base64Url()

    class ResultPage(val results: List<SearchResult>, val continuation: String?)

    fun search(query: String, filters: SearchFilters, videosOnly: Boolean): ResultPage =
        parseResults(InnerTube.web("search", JSONObject().put("query", query).put("params", searchParams(filters, if (videosOnly) 1 else 0))))

    fun searchMore(continuation: String): ResultPage =
        parseResults(InnerTube.web("search", JSONObject().put("continuation", continuation)))

    internal fun parseResults(root: JSONObject): ResultPage {
        val out = ArrayList<SearchResult>()
        var continuation: String? = null
        walk(root) { node ->
            for (key in VIDEO_KEYS) node.optJSONObject(key)?.let { v ->
                InnerTube.parse(JSONObject().put(key, v)).items.firstOrNull()?.let { out += SearchResult.Video(it) }
                return@walk false
            }
            node.optJSONObject("lockupViewModel")?.let { l ->
                playlistFromLockup(l)?.let { out += SearchResult.Playlist(it) }
                    ?: InnerTube.parse(JSONObject().put("lockupViewModel", l)).items.firstOrNull()?.let { out += SearchResult.Video(it) }
                return@walk false
            }
            node.optJSONObject("channelRenderer")?.let { c ->
                val id = c.optString("channelId")
                if (id.isNotEmpty()) out += SearchResult.Channel(
                    ChannelResult(
                        name = InnerTube.text(c.optJSONObject("title")),
                        url = "https://www.youtube.com/channel/$id",
                        avatarUrl = InnerTube.lastThumbnail(c.optJSONObject("thumbnail")),
                        subscribers = InnerTube.text(c.optJSONObject("videoCountText")).ifEmpty { InnerTube.text(c.optJSONObject("subscriberCountText")) },
                        description = InnerTube.text(c.optJSONObject("descriptionSnippet"))
                    )
                )
                return@walk false
            }
            node.optJSONObject("playlistRenderer")?.let { p -> playlistFromRenderer(p)?.let { out += SearchResult.Playlist(it) }; return@walk false }
            node.optJSONObject("continuationItemRenderer")?.let { c -> token(c)?.let { continuation = it }; return@walk false }
            true
        }
        return ResultPage(out.distinctBy { key(it) }, continuation)
    }

    private fun key(r: SearchResult) = when (r) {
        is SearchResult.Video -> "v" + r.item.id
        is SearchResult.Channel -> "c" + r.channel.url
        is SearchResult.Playlist -> "p" + r.playlist.url
    }

    // ---- channel tabs ----

    enum class ChannelTab(val label: String, val params: String) {
        HOME("Home", "EghmZWF0dXJlZPIGBAoCMgA%3D"),
        VIDEOS("Videos", "EgZ2aWRlb3PyBgQKAjoA"),
        SHORTS("Shorts", "EgZzaG9ydHPyBgUKA5oBAA%3D%3D"),
        LIVE("Live", "EgdzdHJlYW1z8gYECgJ6AA%3D%3D"),
        RELEASES("Releases", "EghyZWxlYXNlc_IGBQoDsgEA"),
        PLAYLISTS("Playlists", "EglwbGF5bGlzdHPyBgQKAkIA"),
        POSTS("Posts", "EgVwb3N0c_IGBAoCSgA%3D")
    }

    /** A community post. */
    data class Post(
        val id: String,
        val author: String,
        val authorAvatar: String,
        val text: String,
        val published: String,
        val likes: String,
        val images: List<String>,
        val video: MediaItemModel?,
        val pollChoices: List<String>,
        val comments: String
    )

    /** One page of a channel tab: whichever kinds of items the tab holds, plus its sort chips. */
    class TabPage(
        val videos: List<MediaItemModel>,
        val shorts: List<MediaItemModel>,
        val playlists: List<PlaylistResult>,
        val posts: List<Post>,
        /** Sort / filter chips ("Latest", "Popular", "Oldest") with their continuation tokens. */
        val chips: List<Pair<String, String>>,
        val continuation: String?
    ) {
        val isEmpty: Boolean get() = videos.isEmpty() && shorts.isEmpty() && playlists.isEmpty() && posts.isEmpty()
    }

    fun channelTab(channelId: String, tab: ChannelTab): TabPage =
        parseTab(InnerTube.web("browse", JSONObject().put("browseId", channelId).put("params", java.net.URLDecoder.decode(tab.params, "UTF-8"))))

    fun channelTabMore(continuation: String): TabPage =
        parseTab(InnerTube.web("browse", JSONObject().put("continuation", continuation)))

    internal fun parseTab(root: JSONObject): TabPage {
        val videos = ArrayList<MediaItemModel>()
        val shorts = ArrayList<MediaItemModel>()
        val playlists = ArrayList<PlaylistResult>()
        val posts = ArrayList<Post>()
        val chips = ArrayList<Pair<String, String>>()
        var continuation: String? = null
        walk(root) { node ->
            for (key in VIDEO_KEYS) node.optJSONObject(key)?.let { v ->
                InnerTube.parse(JSONObject().put(key, v)).items.firstOrNull()?.let(videos::add)
                    ?: shortFrom(key, v)?.let(shorts::add)
                return@walk false
            }
            node.optJSONObject("lockupViewModel")?.let { l ->
                playlistFromLockup(l)?.let(playlists::add)
                    ?: InnerTube.parse(JSONObject().put("lockupViewModel", l)).items.firstOrNull()?.let(videos::add)
                return@walk false
            }
            node.optJSONObject("reelItemRenderer")?.let { r -> reelItem(r)?.let(shorts::add); return@walk false }
            node.optJSONObject("shortsLockupViewModel")?.let { s -> shortsLockup(s)?.let(shorts::add); return@walk false }
            (node.optJSONObject("gridPlaylistRenderer") ?: node.optJSONObject("playlistRenderer"))?.let { p ->
                playlistFromRenderer(p)?.let(playlists::add)
                return@walk false
            }
            node.optJSONObject("backstagePostRenderer")?.let { p -> post(p)?.let(posts::add); return@walk false }
            node.optJSONObject("chipCloudChipRenderer")?.let { c ->
                val label = InnerTube.text(c.optJSONObject("text"))
                token(c)?.let { if (label.isNotEmpty()) chips += label to it }
                return@walk false
            }
            node.optJSONObject("chipViewModel")?.let { c ->
                val label = c.optString("text")
                token(c)?.let { if (label.isNotEmpty()) chips += label to it }
                return@walk false
            }
            node.optJSONObject("continuationItemRenderer")?.let { c -> token(c)?.let { continuation = it }; return@walk false }
            true
        }
        return TabPage(
            videos.distinctBy { it.id }, shorts.distinctBy { it.id }, playlists.distinctBy { it.url },
            posts.distinctBy { it.id }, chips.distinctBy { it.first }, continuation
        )
    }

    private fun post(p: JSONObject): Post? {
        val id = p.optString("postId").takeIf { it.isNotEmpty() } ?: return null
        val attachment = p.optJSONObject("backstageAttachment")
        val images = ArrayList<String>()
        attachment?.optJSONObject("backstageImageRenderer")?.let { images += InnerTube.lastThumbnail(it.optJSONObject("image")) }
        attachment?.optJSONObject("postMultiImageRenderer")?.optJSONArray("images")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.optJSONObject("backstageImageRenderer")?.let { images += InnerTube.lastThumbnail(it.optJSONObject("image")) }
        }
        val video = attachment?.optJSONObject("videoRenderer")?.let { InnerTube.parse(JSONObject().put("videoRenderer", it)).items.firstOrNull() }
        val poll = ArrayList<String>()
        attachment?.optJSONObject("pollRenderer")?.optJSONArray("choices")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { poll += InnerTube.text(it.optJSONObject("text")) }
        }
        val comments = p.optJSONObject("actionButtons")?.optJSONObject("commentActionButtonsRenderer")
            ?.optJSONObject("replyButton")?.optJSONObject("buttonRenderer")?.let { InnerTube.text(it.optJSONObject("text")) }.orEmpty()
        return Post(
            id = id,
            author = InnerTube.text(p.optJSONObject("authorText")),
            authorAvatar = InnerTube.lastThumbnail(p.optJSONObject("authorThumbnail")),
            text = InnerTube.text(p.optJSONObject("contentText")),
            published = InnerTube.text(p.optJSONObject("publishedTimeText")),
            likes = InnerTube.text(p.optJSONObject("voteCount")),
            images = images.filter { it.isNotEmpty() },
            video = video,
            pollChoices = poll,
            comments = comments
        )
    }

    // ---- Home topic chips and Explore ----

    class Feed(val items: List<MediaItemModel>, val continuation: String?, val chips: List<Pair<String, String>>)

    /** The signed-in Home feed with its topic chips ("All", "Music", "Mixes", ...). */
    fun home(): Feed = feed(InnerTube.web("browse", JSONObject().put("browseId", "FEwhat_to_watch")))

    /** The feed behind a Home chip, or the next page of any feed. */
    fun continuation(token: String): Feed = feed(InnerTube.web("browse", JSONObject().put("continuation", token)))

    /** An Explore destination (Trending, Music, Gaming, ...): YouTube's own page for it. */
    fun explore(browseId: String): Feed = feed(InnerTube.web("browse", JSONObject().put("browseId", browseId)))

    /** Explore destinations and their pages (YouTube's topic channels). */
    val exploreDestinations: List<Pair<String, String>> = listOf(
        "Trending" to "FEtrending",
        "Music" to "UC-9-kyTW8ZkZNDHQJ6FgpwQ",
        "Gaming" to "UCOpNcN46UbXVtpKMrmU4Abg",
        "News" to "UCYfdidRxbB8Qhf0Nx7ioOYw",
        "Sports" to "UCEgdi0XIXXZ-qJOFPf4JSKw",
        "Live" to "UC4R8DWoMoI7CAwX8_LjQHig",
        "Learning" to "UCtFRv9O2AHqOZjjynzrv-xg",
        "Fashion & Beauty" to "UCrpQ4p1Ql_hG8rKXIKM1MOQ",
        "Podcasts" to "FEpodcasts_destination"
    )

    internal fun feed(root: JSONObject): Feed {
        val page = InnerTube.parse(root)
        val chips = ArrayList<Pair<String, String>>()
        walk(root) { node ->
            node.optJSONObject("chipCloudChipRenderer")?.let { c ->
                val label = InnerTube.text(c.optJSONObject("text"))
                val t = token(c)
                if (label.isNotEmpty()) chips += label to (t ?: "")
                return@walk false
            }
            true
        }
        return Feed(page.items, page.continuation, chips.distinctBy { it.first })
    }

    // ---- Shorts ----

    /** The Shorts player's own "what's next" sequence after [seedId] (or from a continuation). */
    fun reelSequence(seedId: String?, continuation: String?): Pair<List<String>, String?> {
        val params = continuation ?: Proto().string(1, seedId.orEmpty()).message(5) { int(3, 5) }.int(10, 25).base64Url()
        return parseReelSequence(InnerTube.web("reel/reel_watch_sequence", JSONObject().put("sequenceParams", params)))
    }

    internal fun parseReelSequence(root: JSONObject): Pair<List<String>, String?> {
        val ids = ArrayList<String>()
        root.optJSONArray("entries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val reel = e.optJSONObject("command")?.optJSONObject("reelWatchEndpoint") ?: e.optJSONObject("reelWatchEndpoint")
                reel?.optString("videoId")?.takeIf { it.length == 11 }?.let(ids::add)
            }
        }
        val next = root.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }
        return ids.distinct() to next
    }

    /** Title and channel of a short, from the Shorts player overlay. */
    fun reelInfo(videoId: String): MediaItemModel? = runCatching {
        parseReelInfo(
            videoId,
            InnerTube.web(
                "reel/reel_item_watch",
                JSONObject().put("playerRequest", JSONObject().put("videoId", videoId)).put("params", "CAUwAg%3D%3D").put("disablePlayerResponse", true)
            )
        )
    }.getOrNull()

    internal fun parseReelInfo(videoId: String, root: JSONObject): MediaItemModel? {
        var title = ""
        var channel = ""
        var avatar = ""
        var browseId = ""
        walk(root) { node ->
            node.optJSONObject("reelPlayerHeaderRenderer")?.let { h ->
                title = title.ifEmpty { InnerTube.text(h.optJSONObject("reelTitleText")) }
                channel = channel.ifEmpty { InnerTube.text(h.optJSONObject("channelTitleText")) }
                avatar = avatar.ifEmpty { InnerTube.lastThumbnail(h.optJSONObject("channelThumbnail")) }
                browseId = browseId.ifEmpty { h.optJSONObject("channelNavigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty() }
                return@walk false
            }
            node.optJSONObject("shortsVideoTitleViewModel")?.let { t ->
                title = title.ifEmpty { t.optJSONObject("text")?.optString("content").orEmpty() }
                return@walk false
            }
            node.optJSONObject("reelChannelBarViewModel")?.let { c ->
                channel = channel.ifEmpty { c.optJSONObject("channelName")?.optString("content").orEmpty() }
                avatar = avatar.ifEmpty {
                    c.optJSONObject("avatar")?.optJSONObject("avatarViewModel")?.optJSONObject("image")?.optJSONArray("sources")
                        ?.let { it.optJSONObject(it.length() - 1)?.optString("url") }.orEmpty()
                }
                browseId = browseId.ifEmpty { findString(c, "browseId").orEmpty() }
                return@walk false
            }
            true
        }
        if (title.isEmpty() && channel.isEmpty()) return null
        return InnerTube.build(
            id = videoId, title = title, channel = channel, thumbnail = "https://i.ytimg.com/vi/$videoId/oar2.jpg", avatar = avatar,
            seconds = 0, views = "", published = "", channelUrl = if (browseId.startsWith("UC")) "https://www.youtube.com/channel/$browseId" else "",
            live = false
        ).copy(isShort = true)
    }

    private fun shortFrom(key: String, v: JSONObject): MediaItemModel? {
        // Short uploads come back as video renderers the main parser leaves out (60 s or less).
        val id = v.optString("videoId").takeIf { it.length == 11 } ?: return null
        if (key != "videoRenderer" && key != "gridVideoRenderer") return null
        return InnerTube.build(
            id = id, title = InnerTube.text(v.optJSONObject("title")), channel = "", thumbnail = InnerTube.lastThumbnail(v.optJSONObject("thumbnail")),
            avatar = "", seconds = InnerTube.parseDuration(InnerTube.text(v.optJSONObject("lengthText"))),
            views = InnerTube.text(v.optJSONObject("shortViewCountText") ?: v.optJSONObject("viewCountText")), published = "", channelUrl = "", live = false
        ).copy(isShort = true)
    }

    private fun reelItem(r: JSONObject): MediaItemModel? {
        val id = r.optString("videoId").takeIf { it.length == 11 } ?: return null
        return InnerTube.build(
            id = id, title = InnerTube.text(r.optJSONObject("headline")), channel = "", thumbnail = InnerTube.lastThumbnail(r.optJSONObject("thumbnail")),
            avatar = "", seconds = 0, views = InnerTube.text(r.optJSONObject("viewCountText")), published = "", channelUrl = "", live = false
        ).copy(isShort = true)
    }

    private fun shortsLockup(s: JSONObject): MediaItemModel? {
        val id = findString(s.optJSONObject("onTap"), "videoId")?.takeIf { it.length == 11 }
            ?: s.optString("entityId").substringAfterLast('-').takeIf { it.length == 11 } ?: return null
        val overlay = s.optJSONObject("overlayMetadata")
        val thumbs = s.optJSONObject("thumbnail")?.optJSONArray("sources")
        return InnerTube.build(
            id = id, title = overlay?.optJSONObject("primaryText")?.optString("content").orEmpty(), channel = "",
            thumbnail = thumbs?.optJSONObject(0)?.optString("url").orEmpty(), avatar = "", seconds = 0,
            views = overlay?.optJSONObject("secondaryText")?.optString("content").orEmpty(), published = "", channelUrl = "", live = false
        ).copy(isShort = true)
    }

    private fun playlistFromRenderer(p: JSONObject): PlaylistResult? {
        val id = p.optString("playlistId").takeIf { it.isNotEmpty() } ?: return null
        val thumb = InnerTube.lastThumbnail(p.optJSONObject("thumbnail"))
            .ifEmpty { InnerTube.lastThumbnail(p.optJSONArray("thumbnails")?.optJSONObject(0)) }
        val count = InnerTube.text(p.optJSONObject("videoCountText")).ifEmpty { p.optString("videoCount") }
        return PlaylistResult(
            title = InnerTube.text(p.optJSONObject("title")),
            url = "https://www.youtube.com/playlist?list=$id",
            thumbnailUrl = thumb,
            owner = InnerTube.text(p.optJSONObject("shortBylineText") ?: p.optJSONObject("longBylineText")),
            videoCount = Regex("""\d[\d,]*""").find(count)?.value?.replace(",", "")?.toLongOrNull() ?: -1L
        )
    }

    private fun playlistFromLockup(l: JSONObject): PlaylistResult? {
        val type = l.optString("contentType")
        if (type != "LOCKUP_CONTENT_TYPE_PLAYLIST" && type != "LOCKUP_CONTENT_TYPE_ALBUM" && type != "LOCKUP_CONTENT_TYPE_PODCAST") return null
        val id = l.optString("contentId").takeIf { it.isNotEmpty() } ?: return null
        val meta = l.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        val sources = findArray(l.optJSONObject("contentImage"), "sources")
        val badge = findString(l.optJSONObject("contentImage"), "text").orEmpty()
        val owner = findString(meta?.optJSONObject("metadata"), "content").orEmpty()
        return PlaylistResult(
            title = meta?.optJSONObject("title")?.optString("content").orEmpty(),
            url = "https://www.youtube.com/playlist?list=$id",
            thumbnailUrl = sources?.optJSONObject(sources.length() - 1)?.optString("url").orEmpty(),
            owner = owner,
            videoCount = Regex("""\d[\d,]*""").find(badge)?.value?.replace(",", "")?.toLongOrNull() ?: -1L
        )
    }

    private val VIDEO_KEYS = listOf("videoRenderer", "gridVideoRenderer", "compactVideoRenderer")

    /** The continuation token inside a renderer or chip (any command shape). */
    private fun token(node: JSONObject): String? = findObject(node, "continuationCommand")?.optString("token")?.takeIf { it.isNotEmpty() }

    private fun walk(node: Any?, visit: (JSONObject) -> Boolean) {
        when (node) {
            is JSONObject -> if (visit(node)) for (k in node.keys()) walk(node.opt(k), visit)
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }

    private fun findObject(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                for (k in node.keys()) findObject(node.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findObject(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun findArray(node: Any?, key: String): JSONArray? {
        when (node) {
            is JSONObject -> {
                node.optJSONArray(key)?.let { return it }
                for (k in node.keys()) findArray(node.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findArray(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun findString(node: Any?, key: String): String? {
        when (node) {
            is JSONObject -> {
                (node.opt(key) as? String)?.takeIf { it.isNotEmpty() }?.let { return it }
                for (k in node.keys()) findString(node.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findString(node.opt(i), key)?.let { return it }
        }
        return null
    }
}
