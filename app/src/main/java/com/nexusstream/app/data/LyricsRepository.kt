package com.nexusstream.app.data

import com.nexusstream.app.model.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder

/**
 * Time-synced lyrics from the community LRCLIB service (lrclib.net). Optional and best-effort:
 * only the song title and artist are sent, and many songs have no lyrics.
 */
object LyricsRepository {
    private val STAMP = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val NOISE = Regex("""\s*[(\[](official|lyrics?|audio|video|music video|hd|hq|4k|remaster(ed)?|live)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

    /** Parses LRC text ("[01:23.45] words") into lines with start and end times. */
    fun parseLrc(lrc: String): List<LyricLine> {
        val starts = mutableListOf<Pair<Long, String>>()
        for (raw in lrc.lines()) {
            val stamps = STAMP.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = raw.substring(stamps.last().range.last + 1).trim()
            if (text.isEmpty()) continue
            for (m in stamps) {
                val minutes = m.groupValues[1].toLong()
                val seconds = m.groupValues[2].toLong()
                val fraction = m.groupValues[3]
                val millis = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                starts += (minutes * 60 + seconds) * 1000 + millis to text
            }
        }
        val sorted = starts.sortedBy { it.first }
        return sorted.mapIndexed { index, (start, text) ->
            val end = if (index < sorted.lastIndex) sorted[index + 1].first else start + 4000L
            LyricLine(startTimeMs = start, endTimeMs = end, text = text)
        }
    }

    /** "Song (Official Video)" -> "Song"; "Artist - Topic" -> "Artist". */
    fun cleanTitle(title: String): String = title.replace(NOISE, "").trim()

    fun cleanArtist(artist: String): String = artist.removeSuffix(" - Topic").removeSuffix("VEVO").trim()

    suspend fun fetch(title: String, artist: String): Result<List<LyricLine>> = withContext(Dispatchers.IO) {
        runCatching {
            val query = URLEncoder.encode("${cleanTitle(title)} ${cleanArtist(artist)}".trim(), "UTF-8")
            val request = okhttp3.Request.Builder()
                .url("https://lrclib.net/api/search?q=$query")
                .header("User-Agent", NewPipeDownloader.USER_AGENT)
                .build()
            val body = NewPipeDownloader.instance.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body?.string().orEmpty()
            }
            val results = JSONArray(body)
            for (i in 0 until results.length()) {
                val synced = results.getJSONObject(i).optString("syncedLyrics")
                if (synced.isNotBlank()) {
                    val lines = parseLrc(synced)
                    if (lines.isNotEmpty()) return@runCatching lines
                }
            }
            emptyList()
        }
    }
}
