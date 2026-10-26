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

/**
 * Small on-device library: saved items, play history and finished downloads,
 * persisted in SharedPreferences. Call [init] once at app start.
 */
object LibraryRepository {
    private const val PREFS = "nexus_library"
    private const val MAX_HISTORY = 50

    private var prefs: android.content.SharedPreferences? = null

    private val _saved = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val saved: StateFlow<List<MediaItemModel>> = _saved.asStateFlow()

    private val _history = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val history: StateFlow<List<MediaItemModel>> = _history.asStateFlow()

    private val _downloads = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val downloads: StateFlow<List<MediaItemModel>> = _downloads.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _saved.value = read(p.getString("saved", null))
        _history.value = read(p.getString("history", null))
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
                    secondaryColor = Color(o.optInt("c2"))
                )
            }
        }.getOrDefault(emptyList())
    }
}
