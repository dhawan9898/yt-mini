package com.nexusstream.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.nexusstream.app.model.MediaContentType
import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Watch URL for a live YouTube item (ids are YouTube video ids). */
val MediaItemModel.watchUrl: String get() = "https://www.youtube.com/watch?v=$id"

/** A user-created playlist, stored on this device. Items keep the order they were added in. */
data class Playlist(val id: String, val name: String, val items: List<MediaItemModel>)

/**
 * Small on-device library: saved items, play history and finished downloads,
 * persisted in SharedPreferences. Call [init] once at app start.
 */
object LibraryRepository {
    private const val PREFS = "nexus_library"
    private const val MAX_HISTORY = 300

    private var prefs: android.content.SharedPreferences? = null

    private val _saved = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val saved: StateFlow<List<MediaItemModel>> = _saved.asStateFlow()

    private val _history = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val history: StateFlow<List<MediaItemModel>> = _history.asStateFlow()

    private val _downloads = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val downloads: StateFlow<List<MediaItemModel>> = _downloads.asStateFlow()

    /** A followed channel (stored locally; there is no account sign-in). */
    data class ChannelSub(
        val name: String,
        val avatarUrl: String,
        val channelUrl: String = "",
        /** When the user last opened this channel; newer uploads show a "new" dot. */
        val lastSeenMs: Long = 0L
    )

    private val _subscriptions = MutableStateFlow<List<ChannelSub>>(emptyList())
    val subscriptions: StateFlow<List<ChannelSub>> = _subscriptions.asStateFlow()

    fun isSubscribed(name: String): Boolean = _subscriptions.value.any { it.name == name }

    fun toggleSubscribe(name: String, avatarUrl: String, channelUrl: String = "") {
        _subscriptions.value = if (isSubscribed(name)) {
            _subscriptions.value.filter { it.name != name }
        } else {
            _subscriptions.value + ChannelSub(name, avatarUrl, channelUrl, lastSeenMs = System.currentTimeMillis())
        }
        persistSubscriptions()
    }

    /** Clears the "new" dot for a channel. */
    fun markSeen(name: String) {
        if (!isSubscribed(name)) return
        _subscriptions.value = _subscriptions.value.map {
            if (it.name == name) it.copy(lastSeenMs = System.currentTimeMillis()) else it
        }
        persistSubscriptions()
    }

    private fun persistSubscriptions() {
        val store = prefs ?: return
        val json = JSONArray(_subscriptions.value.map {
            JSONObject()
                .put("name", it.name).put("avatar", it.avatarUrl)
                .put("url", it.channelUrl).put("seen", it.lastSeenMs)
        })
        store.edit().putString("subscriptions", json.toString()).apply()
    }

    // ---- search history ----
    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _searchHistory.value = (listOf(q) + _searchHistory.value.filter { !it.equals(q, ignoreCase = true) }).take(15)
        persistStrings("search_history", _searchHistory.value)
    }

    fun removeSearch(query: String) {
        _searchHistory.value = _searchHistory.value.filter { it != query }
        persistStrings("search_history", _searchHistory.value)
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        persistStrings("search_history", emptyList())
    }

    // ---- "Not interested" / "Don't recommend channel" ----
    data class Hidden(val videoIds: Set<String> = emptySet(), val channels: Set<String> = emptySet())

    private val _hidden = MutableStateFlow(Hidden())
    val hidden: StateFlow<Hidden> = _hidden.asStateFlow()

    fun hideVideo(id: String) {
        _hidden.value = _hidden.value.copy(videoIds = _hidden.value.videoIds + id)
        persistStrings("hidden_videos", _hidden.value.videoIds.toList())
    }

    fun hideChannel(name: String) {
        _hidden.value = _hidden.value.copy(channels = _hidden.value.channels + name)
        persistStrings("hidden_channels", _hidden.value.channels.toList())
    }

    private fun persistStrings(key: String, values: List<String>) {
        prefs?.edit()?.putString(key, JSONArray(values).toString())?.apply()
    }

    private fun readStrings(json: String?): List<String> = runCatching {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    // ---- playlists ----
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(java.util.UUID.randomUUID().toString(), name.trim().ifEmpty { "New playlist" }, emptyList())
        _playlists.value = _playlists.value + playlist
        persistPlaylists()
        return playlist
    }

    fun renamePlaylist(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        updatePlaylist(id) { it.copy(name = trimmed) }
    }

    fun deletePlaylist(id: String) {
        _playlists.value = _playlists.value.filter { it.id != id }
        persistPlaylists()
    }

    fun isInPlaylist(playlistId: String, itemId: String): Boolean =
        _playlists.value.firstOrNull { it.id == playlistId }?.items?.any { it.id == itemId } == true

    /** Adds the item to the end of the playlist, or removes it if it is already there. */
    fun togglePlaylistItem(playlistId: String, item: MediaItemModel) {
        updatePlaylist(playlistId) { playlist ->
            if (playlist.items.any { it.id == item.id }) {
                playlist.copy(items = playlist.items.filter { it.id != item.id })
            } else {
                playlist.copy(items = playlist.items + item)
            }
        }
    }

    fun removeFromPlaylist(playlistId: String, itemId: String) {
        updatePlaylist(playlistId) { it.copy(items = it.items.filter { item -> item.id != itemId }) }
    }

    /** Moves the item at [from] to position [to] (both clamped to the list). */
    fun movePlaylistItem(playlistId: String, from: Int, to: Int) {
        updatePlaylist(playlistId) { playlist ->
            if (from !in playlist.items.indices) return@updatePlaylist playlist
            val items = playlist.items.toMutableList()
            val moved = items.removeAt(from)
            items.add(to.coerceIn(0, items.size), moved)
            playlist.copy(items = items)
        }
    }

    private fun updatePlaylist(id: String, change: (Playlist) -> Playlist) {
        _playlists.value = _playlists.value.map { if (it.id == id) change(it) else it }
        persistPlaylists()
    }

    private fun persistPlaylists() {
        val store = prefs ?: return
        val json = JSONArray(_playlists.value.map { playlist ->
            JSONObject()
                .put("id", playlist.id)
                .put("name", playlist.name)
                .put("items", JSONArray(playlist.items.map(::toJson)))
        })
        store.edit().putString("playlists", json.toString()).apply()
    }

    private fun readPlaylists(json: String?): List<Playlist> = runCatching {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            Playlist(o.getString("id"), o.getString("name"), read(o.getJSONArray("items").toString()))
        }
    }.getOrDefault(emptyList())

    // ---- liked videos ----
    private val _liked = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val liked: StateFlow<List<MediaItemModel>> = _liked.asStateFlow()

    fun isLiked(id: String): Boolean = _liked.value.any { it.id == id }

    fun toggleLike(item: MediaItemModel) {
        _liked.value = if (isLiked(item.id)) _liked.value.filter { it.id != item.id } else listOf(item) + _liked.value
        persist("liked", _liked.value)
    }

    // ---- history management ----
    private val _historyPaused = MutableStateFlow(false)
    val historyPaused: StateFlow<Boolean> = _historyPaused.asStateFlow()

    fun setHistoryPaused(paused: Boolean) {
        _historyPaused.value = paused
        prefs?.edit()?.putBoolean("history_paused", paused)?.apply()
    }

    fun removeFromHistory(id: String) {
        _history.value = _history.value.filter { it.id != id }
        persist("history", _history.value)
    }

    fun clearHistory() {
        _history.value = emptyList()
        persist("history", _history.value)
    }

    // ---- imports (Google Takeout) ----

    /** Adds channels that are not subscribed yet; returns how many were new. */
    fun importSubscriptions(subs: List<ChannelSub>): Int {
        val existing = _subscriptions.value.map { it.name }.toSet()
        val fresh = subs.filter { it.name !in existing }.distinctBy { it.name }
        if (fresh.isEmpty()) return 0
        _subscriptions.value = _subscriptions.value + fresh
        persistSubscriptions()
        return fresh.size
    }

    /** Adds imported watch history behind the existing entries; returns how many were new. */
    fun importHistory(items: List<MediaItemModel>): Int {
        val existing = _history.value.map { it.id }.toSet()
        val fresh = items.filter { it.id !in existing }.distinctBy { it.id }
        if (fresh.isEmpty()) return 0
        _history.value = (_history.value + fresh).take(MAX_HISTORY)
        persist("history", _history.value)
        return fresh.size
    }

    /** Resets all in-memory state; used by unit tests (nothing is persisted without [init]). */
    internal fun resetForTest() {
        prefs = null
        _saved.value = emptyList()
        _history.value = emptyList()
        _downloads.value = emptyList()
        _subscriptions.value = emptyList()
        _playlists.value = emptyList()
        _liked.value = emptyList()
        _historyPaused.value = false
        _searchHistory.value = emptyList()
        _hidden.value = Hidden()
    }

    /** Item list <-> JSON, shared with other repositories that persist media items. */
    internal fun encodeItems(items: List<MediaItemModel>): String = JSONArray(items.map(::toJson)).toString()
    internal fun decodeItems(json: String?): List<MediaItemModel> = read(json)

    // ---- small key/value store for other repositories (e.g. unfinished downloads) ----
    fun loadBlob(key: String): String? = prefs?.getString("blob_$key", null)

    fun saveBlob(key: String, value: String) {
        prefs?.edit()?.putString("blob_$key", value)?.apply()
    }

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _saved.value = read(p.getString("saved", null))
        _history.value = read(p.getString("history", null))
        _liked.value = read(p.getString("liked", null))
        _playlists.value = readPlaylists(p.getString("playlists", null))
        _historyPaused.value = p.getBoolean("history_paused", false)
        _subscriptions.value = runCatching {
            val arr = JSONArray(p.getString("subscriptions", "[]"))
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .map {
                    ChannelSub(
                        it.getString("name"), it.optString("avatar"),
                        it.optString("url"), it.optLong("seen")
                    )
                }
        }.getOrDefault(emptyList())
        _searchHistory.value = readStrings(p.getString("search_history", null))
        _hidden.value = Hidden(
            videoIds = readStrings(p.getString("hidden_videos", null)).toSet(),
            channels = readStrings(p.getString("hidden_channels", null)).toSet()
        )
        // Drop downloads whose files were deleted.
        _downloads.value = read(p.getString("downloads", null)).filter { item ->
            item.audioUrl.removePrefix("file://").let { path -> java.io.File(path).exists() }
        }
    }

    fun isSaved(id: String): Boolean = _saved.value.any { it.id == id }

    fun toggleSave(item: MediaItemModel) {
        _saved.value = if (isSaved(item.id)) _saved.value.filter { it.id != item.id } else listOf(item) + _saved.value
        persist("saved", _saved.value)
    }

    fun recordPlayed(item: MediaItemModel) {
        if (_historyPaused.value) return
        _history.value = (listOf(item) + _history.value.filter { it.id != item.id }).take(MAX_HISTORY)
        persist("history", _history.value)
    }

    fun addDownload(localItem: MediaItemModel) {
        _downloads.value = listOf(localItem) + _downloads.value.filter { it.id != localItem.id }
        persist("downloads", _downloads.value)
    }

    fun removeDownload(id: String) {
        _downloads.value.firstOrNull { it.id == id }?.let {
            runCatching { java.io.File(it.audioUrl.removePrefix("file://")).delete() }
        }
        _downloads.value = _downloads.value.filter { it.id != id }
        persist("downloads", _downloads.value)
    }

    // ---- serialization ----

    private fun persist(key: String, items: List<MediaItemModel>) {
        prefs?.edit()?.putString(key, JSONArray(items.map(::toJson)).toString())?.apply()
    }

    private fun toJson(i: MediaItemModel) = JSONObject().apply {
        put("id", i.id); put("title", i.title); put("artist", i.artistOrChannel)
        put("audioUrl", i.audioUrl); put("videoUrl", i.videoUrl)
        put("art", i.artworkUrl); put("avatar", i.channelAvatarUrl)
        put("duration", i.durationMs); put("views", i.viewsOrPlays); put("date", i.publishDate)
        put("source", i.sourceUrl); put("music", i.contentType == MediaContentType.MUSIC_TRACK)
        put("c1", i.dominantColor.toArgb()); put("c2", i.secondaryColor.toArgb())
        put("chUrl", i.channelUrl); put("pub", i.publishedAtMs)
    }

    private fun read(json: String?): List<MediaItemModel> {
        if (json.isNullOrEmpty()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
                MediaItemModel(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    artistOrChannel = o.optString("artist"),
                    audioUrl = o.optString("audioUrl"),
                    videoUrl = o.optString("videoUrl"),
                    artworkUrl = o.optString("art"),
                    channelAvatarUrl = o.optString("avatar"),
                    durationMs = o.optLong("duration"),
                    viewsOrPlays = o.optString("views"),
                    publishDate = o.optString("date"),
                    sourceUrl = o.optString("source"),
                    contentType = if (o.optBoolean("music")) MediaContentType.MUSIC_TRACK else MediaContentType.YOUTUBE_VIDEO,
                    dominantColor = Color(o.optInt("c1")),
                    secondaryColor = Color(o.optInt("c2")),
                    channelUrl = o.optString("chUrl"),
                    publishedAtMs = o.optLong("pub")
                )
            }
        }.getOrDefault(emptyList())
    }
}
