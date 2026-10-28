package com.nexusstream.app.data

import com.nexusstream.app.model.MediaItemModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Write-back and read of the signed-in account's own library on YouTube: likes and dislikes,
 * Watch later, Liked videos, the user's playlists, and a channel's bell setting. Every call
 * needs [GoogleAccount.isSignedIn]; failures throw so callers can roll back the local change.
 */
object AccountApi {
    const val WATCH_LATER = "WL"
    const val LIKED = "LL"

    /** One of the user's playlists, as the "Save to playlist" dialog lists them. */
    data class AccountPlaylist(
        val id: String,
        val title: String,
        val containsVideo: Boolean,
        val privacy: String,
        internal val addEndpoint: JSONObject?,
        internal val removeEndpoint: JSONObject?
    )

    /** A playlist row with its YouTube `setVideoId` (needed to remove or move that entry). */
    data class Entry(val item: MediaItemModel, val setVideoId: String)

    private fun requireSignedIn() = check(GoogleAccount.isSignedIn()) { "Not signed in" }

    // ---- ratings ----

    fun rate(videoId: String, rating: Rating) {
        requireSignedIn()
        val path = when (rating) {
            Rating.LIKE -> "like/like"
            Rating.DISLIKE -> "like/dislike"
            Rating.NONE -> "like/removelike"
        }
        InnerTube.web(path, JSONObject().put("target", JSONObject().put("videoId", videoId)))
    }

    enum class Rating { LIKE, DISLIKE, NONE }

    // ---- channels ----

    /** Resolves a channel URL (`/@handle`, `/c/name`, `/user/name`, `/channel/UC…`) to its UC id. */
    fun channelId(channelUrl: String): String? {
        Regex("""/channel/(UC[\w-]{22})""").find(channelUrl)?.let { return it.groupValues[1] }
        if (channelUrl.isBlank()) return null
        val url = if (channelUrl.startsWith("http")) channelUrl else "https://www.youtube.com$channelUrl"
        val res = InnerTube.web("navigation/resolve_url", JSONObject().put("url", url))
        return res.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
            ?.takeIf { it.startsWith("UC") }
    }

    fun subscribe(channelId: String, subscribe: Boolean) {
        requireSignedIn()
        val path = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
        InnerTube.web(path, JSONObject().put("channelIds", JSONArray().put(channelId)))
    }

    /** Sets the bell: all uploads, personalised, or none. */
    fun setNotifications(channelId: String, level: NotifyLevel) {
        requireSignedIn()
        InnerTube.web("notification/modify_channel_preference", JSONObject().put("params", bellParams(channelId, level)))
    }

    /** The `NotificationPreferences` message YouTube.js encodes: PERSONALIZED=1, ALL=2, NONE=3. */
    internal fun bellParams(channelId: String, level: NotifyLevel): String {
        val index = when (level) {
            NotifyLevel.PERSONALIZED -> 1
            NotifyLevel.ALL -> 2
            NotifyLevel.NONE -> 3
        }
        return Proto().string(1, channelId).message(2) { int(1, index) }.int(3, 0).int(4, 4).base64Url()
    }

    // ---- playlists ----

    /**
     * The user's editable playlists (Watch later included), each marked with whether it already
     * holds [videoId]. This is the same list YouTube's "Save to playlist" dialog shows.
     */
    fun playlistsFor(videoId: String): List<AccountPlaylist> {
        requireSignedIn()
        val res = InnerTube.web("playlist/get_add_to_playlist", JSONObject().put("videoIds", JSONArray().put(videoId)))
        return parseAddToPlaylist(res)
    }

    internal fun parseAddToPlaylist(root: JSONObject): List<AccountPlaylist> {
        val out = ArrayList<AccountPlaylist>()
        walk(root) { node ->
            val o = node.optJSONObject("playlistAddToOptionRenderer") ?: return@walk true
            val id = o.optString("playlistId")
            if (id.isNotEmpty()) {
                out += AccountPlaylist(
                    id = id,
                    title = InnerTube.text(o.optJSONObject("title")),
                    containsVideo = o.optString("containsSelectedVideos") == "ALL",
                    privacy = o.optString("privacy"),
                    addEndpoint = o.optJSONObject("addToPlaylistServiceEndpoint")?.optJSONObject("playlistEditEndpoint"),
                    removeEndpoint = o.optJSONObject("removeFromPlaylistServiceEndpoint")?.optJSONObject("playlistEditEndpoint")
                )
            }
            false
        }
        return out.distinctBy { it.id }
    }

    fun addVideo(playlistId: String, videoId: String) = edit(
        playlistId, JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId)
    )

    /** Removes by video id (what the website's "Save to" dialog sends). */
    fun removeVideo(playlistId: String, videoId: String) = edit(
        playlistId, JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId)
    )

    fun removeEntry(playlistId: String, setVideoId: String) = edit(
        playlistId, JSONObject().put("action", "ACTION_REMOVE_VIDEO").put("setVideoId", setVideoId)
    )

    /** Moves an entry to just after [afterSetVideoId] (null = to the top). */
    fun moveEntry(playlistId: String, setVideoId: String, afterSetVideoId: String?) = edit(
        playlistId, JSONObject().put("action", "ACTION_MOVE_VIDEO_AFTER").put("setVideoId", setVideoId).apply {
            if (afterSetVideoId != null) put("movedSetVideoIdPredecessor", afterSetVideoId)
        }
    )

    fun rename(playlistId: String, name: String) = edit(
        playlistId, JSONObject().put("action", "ACTION_SET_PLAYLIST_NAME").put("playlistName", name)
    )

    /** Uses the add/remove request YouTube itself attached to a "Save to playlist" option. */
    fun toggle(option: AccountPlaylist, videoId: String) {
        requireSignedIn()
        val ep = if (option.containsVideo) option.removeEndpoint else option.addEndpoint
        if (ep != null) checkEdit(InnerTube.web("browse/edit_playlist", JSONObject(ep.toString())))
        else if (option.containsVideo) removeVideo(option.id, videoId) else addVideo(option.id, videoId)
    }

    private fun edit(playlistId: String, action: JSONObject) {
        requireSignedIn()
        val body = JSONObject().put("playlistId", playlistId).put("actions", JSONArray().put(action))
        checkEdit(InnerTube.web("browse/edit_playlist", body))
    }

    private fun checkEdit(res: JSONObject) {
        val status = res.optString("status")
        check(status.isEmpty() || status == "STATUS_SUCCEEDED") { "YouTube refused the playlist change ($status)" }
    }

    /** Creates a private playlist and returns its id. */
    fun createPlaylist(title: String, videoIds: List<String> = emptyList(), privacy: String = "PRIVATE"): String {
        requireSignedIn()
        val res = InnerTube.web(
            "playlist/create",
            JSONObject().put("title", title).put("privacyStatus", privacy).put("videoIds", JSONArray(videoIds))
        )
        return res.optString("playlistId").ifEmpty { error("YouTube did not create the playlist") }
    }

    fun deletePlaylist(playlistId: String) {
        requireSignedIn()
        InnerTube.web("playlist/delete", JSONObject().put("playlistId", playlistId))
    }

    /** Every entry of a playlist (Liked videos is "LL", Watch later "WL"), following continuations. */
    fun playlistEntries(playlistId: String, maxPages: Int = 20): List<Entry> {
        requireSignedIn()
        val out = ArrayList<Entry>()
        var res = InnerTube.web("browse", JSONObject().put("browseId", "VL$playlistId"))
        var pages = 0
        while (true) {
            val (entries, next) = parsePlaylistPage(res)
            out += entries
            pages++
            if (next == null || pages >= maxPages) break
            res = InnerTube.web("browse", JSONObject().put("continuation", next))
        }
        return out.distinctBy { it.setVideoId.ifEmpty { it.item.id } }
    }

    internal fun parsePlaylistPage(root: JSONObject): Pair<List<Entry>, String?> {
        val entries = ArrayList<Entry>()
        var next: String? = null
        walk(root) { node ->
            node.optJSONObject("continuationItemRenderer")?.let { c ->
                c.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")
                    ?.optString("token")?.takeIf { it.isNotEmpty() }?.let { next = it }
            }
            val v = node.optJSONObject("playlistVideoRenderer") ?: return@walk true
            playlistVideo(v)?.let { entries += Entry(it, v.optString("setVideoId")) }
            false
        }
        return entries to next
    }

    private fun playlistVideo(v: JSONObject): MediaItemModel? {
        val id = v.optString("videoId").takeIf { it.length == 11 } ?: return null
        if (v.has("unplayableText") && !v.optBoolean("isPlayable", true)) return null
        val byline = v.optJSONObject("shortBylineText")
        val run = byline?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
        val canonical = run?.optString("canonicalBaseUrl").orEmpty()
        val browseId = run?.optString("browseId").orEmpty()
        val channelUrl = when {
            canonical.startsWith("/") -> "https://www.youtube.com$canonical"
            browseId.isNotEmpty() -> "https://www.youtube.com/channel/$browseId"
            else -> ""
        }
        val seconds = v.optString("lengthSeconds").toLongOrNull() ?: InnerTube.parseDuration(InnerTube.text(v.optJSONObject("lengthText")))
        val info = v.optJSONObject("videoInfo")?.let(InnerTube::text).orEmpty()
        val parts = info.split(" • ", "•").map { it.trim() }.filter { it.isNotEmpty() }
        return InnerTube.build(
            id = id,
            title = InnerTube.text(v.optJSONObject("title")),
            channel = InnerTube.text(byline),
            thumbnail = InnerTube.lastThumbnail(v.optJSONObject("thumbnail")),
            avatar = "",
            seconds = seconds,
            views = parts.getOrNull(0).orEmpty(),
            published = parts.getOrNull(1).orEmpty(),
            channelUrl = channelUrl,
            live = seconds == 0L && v.optJSONObject("lengthText") == null,
            publishedAtMs = InnerTube.parseRelativeTime(parts.getOrNull(1).orEmpty())
        )
    }

    /** Depth-first walk; [visit] returns false to skip a node's children. */
    private fun walk(node: Any?, visit: (JSONObject) -> Boolean) {
        when (node) {
            is JSONObject -> if (visit(node)) node.keys().forEach { walk(node.opt(it), visit) }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }
}
