package com.nexusstream.app.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.nexusstream.app.model.MediaContentType
import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Watch URL for a live YouTube item (ids are YouTube video ids). */
val MediaItemModel.watchUrl: String get() = "https://www.youtube.com/watch?v=$id"

/** How a subscribed channel notifies you about new uploads (the channel "bell"). */
enum class NotifyLevel { NONE, PERSONALIZED, ALL }

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
        val lastSeenMs: Long = 0L,
        val notifyLevel: NotifyLevel = NotifyLevel.PERSONALIZED,
        /** Publish time of the newest upload already notified about. */
        val lastNotifiedMs: Long = 0L
    )

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _subscriptions = MutableStateFlow<List<ChannelSub>>(emptyList())
    val subscriptions: StateFlow<List<ChannelSub>> = _subscriptions.asStateFlow()

    fun isSubscribed(name: String): Boolean = _subscriptions.value.any { it.name == name }

    fun toggleSubscribe(name: String, avatarUrl: String, channelUrl: String = "") {
        val willSub = !isSubscribed(name)
        _subscriptions.value = if (!willSub) {
            _subscriptions.value.filter { it.name != name }
        } else {
            _subscriptions.value + ChannelSub(name, avatarUrl, channelUrl, lastSeenMs = System.currentTimeMillis())
        }
        persistSubscriptions()
        val channelId = channelUrl.substringAfterLast("/channel/").substringAfterLast("/")
        if (channelId.startsWith("UC")) {
            repoScope.launch {
                InnerTube.subscribeChannel(channelId, unsubscribe = !willSub)
            }
        }
    }

    /** Clears the "new" dot for a channel. */
    fun markSeen(name: String) {
        if (!isSubscribed(name)) return
        _subscriptions.value = _subscriptions.value.map {
            if (it.name == name) it.copy(lastSeenMs = System.currentTimeMillis()) else it
        }
        persistSubscriptions()
    }

    /** Synchronizes channel subscriptions from Google Account InnerTube data. */
    fun syncGoogleSubscriptions(list: List<ChannelSub>) {
        if (list.isEmpty()) return
        val currentMap = _subscriptions.value.associateBy { it.name }.toMutableMap()
        for (sub in list) {
            val existing = currentMap[sub.name]
            if (existing == null) {
                currentMap[sub.name] = sub
            } else if (existing.avatarUrl.isEmpty() && sub.avatarUrl.isNotEmpty()) {
                currentMap[sub.name] = existing.copy(avatarUrl = sub.avatarUrl, channelUrl = sub.channelUrl.ifEmpty { existing.channelUrl })
            }
        }
        _subscriptions.value = currentMap.values.toList()
        persistSubscriptions()
    }

    /** Synchronizes subscriptions derived from feed items. */
    fun syncSubscriptionsFromFeed(items: List<MediaItemModel>) {
        if (items.isEmpty()) return
        val currentMap = _subscriptions.value.associateBy { it.name }.toMutableMap()
        for (item in items) {
            val name = item.artistOrChannel
            if (name.isBlank()) continue
            val existing = currentMap[name]
            if (existing == null) {
                currentMap[name] = ChannelSub(
                    name = name,
                    avatarUrl = item.channelAvatarUrl,
                    channelUrl = item.channelUrl,
                    lastSeenMs = 0L
                )
            } else if (existing.avatarUrl.isEmpty() && item.channelAvatarUrl.isNotEmpty()) {
                currentMap[name] = existing.copy(avatarUrl = item.channelAvatarUrl, channelUrl = item.channelUrl.ifEmpty { existing.channelUrl })
            }
        }
        _subscriptions.value = currentMap.values.toList()
        persistSubscriptions()
    }

    private fun subscriptionsToJson(list: List<ChannelSub>): JSONArray = JSONArray(list.map {
        JSONObject()
            .put("name", it.name).put("avatar", it.avatarUrl)
            .put("url", it.channelUrl).put("seen", it.lastSeenMs)
            .put("level", it.notifyLevel.name).put("notified", it.lastNotifiedMs)
    })

    private fun subscriptionsFromJson(json: String?): List<ChannelSub> = runCatching {
        val arr = JSONArray(json ?: "[]")
        (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            ChannelSub(
                name = it.getString("name"),
                avatarUrl = it.optString("avatar"),
                channelUrl = it.optString("url"),
                lastSeenMs = it.optLong("seen"),
                notifyLevel = runCatching { NotifyLevel.valueOf(it.optString("level")) }
                    .getOrDefault(NotifyLevel.PERSONALIZED),
                lastNotifiedMs = it.optLong("notified")
            )
        }
    }.getOrDefault(emptyList())

    private fun persistSubscriptions() {
        val store = prefs ?: return
        store.edit().putString("subscriptions", subscriptionsToJson(_subscriptions.value).toString()).apply()
    }

    fun setNotifyLevel(name: String, level: NotifyLevel) {
        _subscriptions.value = _subscriptions.value.map { if (it.name == name) it.copy(notifyLevel = level) else it }
        persistSubscriptions()
    }

    /** Remembers that uploads up to [publishedAtMs] were notified, so they are not repeated. */
    fun markNotified(name: String, publishedAtMs: Long) {
        _subscriptions.value = _subscriptions.value.map {
            if (it.name == name && publishedAtMs > it.lastNotifiedMs) it.copy(lastNotifiedMs = publishedAtMs) else it
        }
        persistSubscriptions()
    }

    // ---- notification settings, reminders and inbox ----
    private val _notificationsEnabled = MutableStateFlow(true)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs?.edit()?.putBoolean("notifications_enabled", enabled)?.apply()
    }

    /** "Take a break" reminder after this many minutes of continuous playback; 0 = off. */
    private val _breakMinutes = MutableStateFlow(0)
    val breakMinutes: StateFlow<Int> = _breakMinutes.asStateFlow()

    fun setBreakMinutes(minutes: Int) {
        _breakMinutes.value = minutes
        prefs?.edit()?.putInt("break_minutes", minutes)?.apply()
    }

    /** Bedtime reminder as minutes after midnight; -1 = off. */
    private val _bedtimeMinute = MutableStateFlow(-1)
    val bedtimeMinute: StateFlow<Int> = _bedtimeMinute.asStateFlow()

    fun setBedtimeMinute(minuteOfDay: Int) {
        _bedtimeMinute.value = minuteOfDay
        prefs?.edit()?.putInt("bedtime_minute", minuteOfDay)?.apply()
    }

    private val _inbox = MutableStateFlow<List<MediaItemModel>>(emptyList())
    /** Videos from new-upload notifications, newest first. */
    val inbox: StateFlow<List<MediaItemModel>> = _inbox.asStateFlow()

    private val _inboxSeenAtMs = MutableStateFlow(0L)
    val inboxSeenAtMs: StateFlow<Long> = _inboxSeenAtMs.asStateFlow()

    fun addToInbox(items: List<MediaItemModel>) {
        if (items.isEmpty()) return
        val known = _inbox.value.map { it.id }.toSet()
        _inbox.value = (items.filter { it.id !in known } + _inbox.value)
            .sortedByDescending { it.publishedAtMs }
            .take(50)
        persist("inbox", _inbox.value)
    }

    /** Clears the unread badge (inbox entries published up to now count as seen). */
    fun markInboxSeen() {
        val now = System.currentTimeMillis()
        _inboxSeenAtMs.value = now
        prefs?.edit()?.putLong("inbox_seen", now)?.apply()
    }

    fun clearInbox() {
        _inbox.value = emptyList()
        persist("inbox", _inbox.value)
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

    /** Undoes every "Not interested" / "Don't recommend channel". */
    fun resetHidden() {
        _hidden.value = Hidden()
        persistStrings("hidden_videos", emptyList())
        persistStrings("hidden_channels", emptyList())
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
        val willLike = !isLiked(item.id)
        _liked.value = if (!willLike) _liked.value.filter { it.id != item.id } else listOf(item) + _liked.value
        persist("liked", _liked.value)
        repoScope.launch {
            InnerTube.likeVideo(item.id, remove = !willLike)
        }
        // Liking and disliking are mutually exclusive.
        if (isLiked(item.id) && isDisliked(item.id)) toggleDislike(item)
    }

    // ---- disliked videos (a personal mark; the count shown comes from a separate service) ----
    private val _disliked = MutableStateFlow<Set<String>>(emptySet())
    val disliked: StateFlow<Set<String>> = _disliked.asStateFlow()

    fun isDisliked(id: String): Boolean = id in _disliked.value

    fun toggleDislike(item: MediaItemModel) {
        _disliked.value = if (isDisliked(item.id)) _disliked.value - item.id else _disliked.value + item.id
        persistStrings("disliked", _disliked.value.toList())
        if (isDisliked(item.id) && isLiked(item.id)) toggleLike(item)
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

    /** YouTube's watch history: everything played except YT Music tracks, which stay in "Listen again". */
    fun watchHistory(all: List<MediaItemModel> = _history.value): List<MediaItemModel> =
        all.filter { it.contentType != MediaContentType.MUSIC_TRACK }

    /** Clears YouTube's watch history and leaves YT Music plays alone. */
    fun clearWatchHistory() {
        _history.value = _history.value.filter { it.contentType == MediaContentType.MUSIC_TRACK }
        persist("history", _history.value)
    }

    // ---- imports (Google Takeout) ----

    /** Adds channels that are not subscribed yet; returns how many were new. */
    fun importSubscriptions(subs: List<ChannelSub>): Int {
        val existing = _subscriptions.value.map { it.name }.toSet()
        val now = System.currentTimeMillis()
        val fresh = subs.filter { it.name !in existing }.distinctBy { it.name }
            .map { if (it.lastSeenMs == 0L) it.copy(lastSeenMs = now) else it }
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
        _disliked.value = emptySet()
        _historyPaused.value = false
        _notificationsEnabled.value = true
        _breakMinutes.value = 0
        _bedtimeMinute.value = -1
        _inbox.value = emptyList()
        _inboxSeenAtMs.value = 0L
        _searchHistory.value = emptyList()
        _hidden.value = Hidden()
    }

    /** Item list <-> JSON, shared with other repositories that persist media items. */
    internal fun encodeItems(items: List<MediaItemModel>): String = JSONArray(items.map(::toJson)).toString()
    internal fun decodeItems(json: String?): List<MediaItemModel> = read(json)

    // ---- backup / restore (local file; there is no cloud sync) ----

    /** Everything worth moving to another device, as JSON (downloads are files and are not included). */
    fun exportBackup(): String = JSONObject()
        .put("app", "NexusStream") // backup format id, kept so older backups still restore
        .put("format", 1)
        .put("saved", JSONArray(encodeItems(_saved.value)))
        .put("liked", JSONArray(encodeItems(_liked.value)))
        .put("history", JSONArray(encodeItems(_history.value)))
        .put("disliked", JSONArray(_disliked.value.toList()))
        .put("playlists", JSONArray(_playlists.value.map { playlist ->
            JSONObject()
                .put("id", playlist.id)
                .put("name", playlist.name)
                .put("items", JSONArray(encodeItems(playlist.items)))
        }))
        .put("subscriptions", subscriptionsToJson(_subscriptions.value))
        .put("searchHistory", JSONArray(_searchHistory.value))
        .put("hiddenVideos", JSONArray(_hidden.value.videoIds.toList()))
        .put("hiddenChannels", JSONArray(_hidden.value.channels.toList()))
        .put("settings", AppSettings.exportJson())
        .toString()

    /** Replaces the library with a backup made by [exportBackup]. Returns false if it is not one. */
    fun importBackup(json: String): Boolean {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return false
        if (root.optString("app") != "NexusStream") return false

        _saved.value = read(root.optJSONArray("saved")?.toString())
        persist("saved", _saved.value)
        _liked.value = read(root.optJSONArray("liked")?.toString())
        persist("liked", _liked.value)
        _history.value = read(root.optJSONArray("history")?.toString())
        persist("history", _history.value)
        _disliked.value = readStrings(root.optJSONArray("disliked")?.toString()).toSet()
        persistStrings("disliked", _disliked.value.toList())
        _playlists.value = readPlaylists(root.optJSONArray("playlists")?.toString())
        persistPlaylists()
        _subscriptions.value = subscriptionsFromJson(root.optJSONArray("subscriptions")?.toString())
        persistSubscriptions()
        _searchHistory.value = readStrings(root.optJSONArray("searchHistory")?.toString())
        persistStrings("search_history", _searchHistory.value)
        _hidden.value = Hidden(
            videoIds = readStrings(root.optJSONArray("hiddenVideos")?.toString()).toSet(),
            channels = readStrings(root.optJSONArray("hiddenChannels")?.toString()).toSet()
        )
        persistStrings("hidden_videos", _hidden.value.videoIds.toList())
        persistStrings("hidden_channels", _hidden.value.channels.toList())
        root.optJSONObject("settings")?.let { AppSettings.importJson(it) }
        return true
    }

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
        _disliked.value = readStrings(p.getString("disliked", null)).toSet()
        _playlists.value = readPlaylists(p.getString("playlists", null))
        _historyPaused.value = p.getBoolean("history_paused", false)
        _notificationsEnabled.value = p.getBoolean("notifications_enabled", true)
        _breakMinutes.value = p.getInt("break_minutes", 0)
        _bedtimeMinute.value = p.getInt("bedtime_minute", -1)
        _inbox.value = read(p.getString("inbox", null))
        _inboxSeenAtMs.value = p.getLong("inbox_seen", 0L)
        _subscriptions.value = subscriptionsFromJson(p.getString("subscriptions", null))
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
        put("chUrl", i.channelUrl); put("pub", i.publishedAtMs); put("live", i.isLive)
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
                    publishedAtMs = o.optLong("pub"),
                    isLive = o.optBoolean("live")
                )
            }
        }.getOrDefault(emptyList())
    }
}
