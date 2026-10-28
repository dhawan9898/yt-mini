package com.nexusstream.app.data

import com.nexusstream.app.model.LyricLine
import com.nexusstream.app.model.LyricWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Time-synced lyrics from the community LRCLIB service (lrclib.net), falling back to plain lyrics
 * (LRCLIB's, then YouTube Music's own). Optional and best-effort: only the song title and artist
 * are sent to LRCLIB, and many songs have no lyrics.
 */
object LyricsRepository {
    private val STAMP = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    // Word stamps of "enhanced" LRC: "[00:12.00] <00:12.00> Hello <00:12.40> world".
    private val WORD_STAMP = Regex("""<(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val SPACES = Regex("""\s+""")
    private val NOISE = Regex("""\s*[(\[](official|lyrics?|audio|video|music video|hd|hq|4k|remaster(ed)?|live)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE)

    /** Synced [lines] when available, otherwise [plain] text. Both empty when nothing was found. */
    class Lyrics(val lines: List<LyricLine>, val plain: String = "", val source: String = "") {
        val isEmpty get() = lines.isEmpty() && plain.isBlank()
    }

    /** Parses LRC text ("[01:23.45] words") into lines with start and end times. */
    fun parseLrc(lrc: String): List<LyricLine> {
        val starts = mutableListOf<Pair<Long, String>>()
        for (raw in lrc.lines()) {
            val stamps = STAMP.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val body = raw.substring(stamps.last().range.last + 1)
            if (lineText(body).isEmpty()) continue
            for (m in stamps) starts += millisOf(m) to body
        }
        val sorted = starts.sortedBy { it.first }
        return sorted.mapIndexed { index, (start, body) ->
            val end = if (index < sorted.lastIndex) sorted[index + 1].first else start + 4000L
            LyricLine(
                startTimeMs = start,
                endTimeMs = end,
                text = lineText(body),
                words = parseWords(body, end)
            )
        }
    }

    private fun lineText(body: String) = body.replace(WORD_STAMP, " ").replace(SPACES, " ").trim()

    /** Word timings from an enhanced-LRC line body; empty for plain lines. A word ends at the next stamp. */
    private fun parseWords(body: String, lineEndMs: Long): List<LyricWord> {
        val stamps = WORD_STAMP.findAll(body).toList()
        return stamps.mapIndexedNotNull { i, m ->
            val next = stamps.getOrNull(i + 1)
            val word = body.substring(m.range.last + 1, next?.range?.first ?: body.length).trim()
            if (word.isEmpty()) return@mapIndexedNotNull null
            val start = millisOf(m)
            LyricWord(word = word, startTimeMs = start, endTimeMs = (next?.let(::millisOf) ?: lineEndMs).coerceAtLeast(start))
        }
    }

    private fun millisOf(m: MatchResult): Long {
        val minutes = m.groupValues[1].toLong()
        val seconds = m.groupValues[2].toLong()
        val fraction = m.groupValues[3]
        val millis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.take(3).toLong()
        }
        return (minutes * 60 + seconds) * 1000 + millis
    }

    /** "Song (Official Video)" -> "Song"; "Artist - Topic" -> "Artist". */
    fun cleanTitle(title: String): String = title.replace(NOISE, "").trim()

    fun cleanArtist(artist: String): String = artist.removeSuffix(" - Topic").removeSuffix("VEVO").trim()

    /**
     * Lyrics for a song: YT Music's time-synced lyrics for [videoId], else LRCLIB's synced lyrics for
     * the closest-length match, else plain lyrics from LRCLIB, else YouTube Music's plain lyrics.
     */
    suspend fun fetch(
        title: String,
        artist: String,
        durationMs: Long = 0L,
        videoId: String? = null
    ): Result<Lyrics> = withContext(Dispatchers.IO) {
        runCatching {
            // YT Music's own live lyrics from the label come first, as in the app.
            videoId?.let { YouTubeRepository.musicTimedLyrics(it) }?.let { (lines, source) ->
                return@runCatching Lyrics(lines, source = source.ifBlank { "Source: YouTube Music" })
            }
            val lrclib = runCatching { searchLrclib(title, artist) }
            val results = lrclib.getOrDefault(emptyList()).let { list ->
                if (durationMs <= 0) list
                else list.sortedBy { kotlin.math.abs(it.optDouble("duration", 0.0) * 1000 - durationMs) }
            }
            for (r in results) {
                val lines = parseLrc(r.optString("syncedLyrics"))
                if (lines.isNotEmpty()) return@runCatching Lyrics(lines, source = "Source: LRCLIB")
            }
            results.firstNotNullOfOrNull { r -> r.optString("plainLyrics").takeIf { it.isNotBlank() } }
                ?.let { return@runCatching Lyrics(emptyList(), plain = it.trim(), source = "Source: LRCLIB") }
            videoId?.let { YouTubeRepository.musicLyrics(it) }
                ?.let { return@runCatching Lyrics(emptyList(), plain = it.text, source = it.source) }
            // Nothing anywhere: report a failed LRCLIB request as an error rather than "no lyrics".
            lrclib.getOrThrow()
            Lyrics(emptyList())
        }
    }

    private fun searchLrclib(title: String, artist: String): List<JSONObject> {
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
        return (0 until results.length()).mapNotNull { results.optJSONObject(it) }
    }
}
