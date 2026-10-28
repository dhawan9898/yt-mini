package com.nexusstream.app.data

import org.json.JSONObject

/**
 * Where each video was left off, so it resumes there the way YouTube does (and shows the red
 * progress bar). Kept on this device; music and Shorts always start from the beginning.
 */
object WatchPositions {
    private const val KEY = "watch_positions"
    private const val MAX_ENTRIES = 500
    /** Too early to count as started, or close enough to the end to count as finished. */
    private const val MIN_MS = 5_000L
    private const val END_MARGIN_MS = 15_000L

    private var cache: JSONObject? = null

    @Synchronized
    private fun all(): JSONObject =
        cache ?: runCatching { JSONObject(LibraryRepository.loadBlob(KEY) ?: "{}") }.getOrDefault(JSONObject()).also { cache = it }

    /** Saves [positionMs] of [durationMs] for [videoId]; a finished or barely started video is forgotten. */
    @Synchronized
    fun save(videoId: String, positionMs: Long, durationMs: Long) {
        if (videoId.isBlank() || GoogleAccount.incognito.value) return
        val updated = put(all(), videoId, positionMs, durationMs, System.currentTimeMillis())
        cache = updated
        LibraryRepository.saveBlob(KEY, updated.toString())
    }

    /** Where to start [videoId], or 0 when it wasn't left part way. */
    @Synchronized
    fun resumeAt(videoId: String): Long = all().optJSONObject(videoId)?.optLong("p", 0L) ?: 0L

    /** Fraction watched (0..1) for thumbnails' progress bars, or 0 when unknown. */
    @Synchronized
    fun fraction(videoId: String): Float {
        val entry = all().optJSONObject(videoId) ?: return 0f
        val duration = entry.optLong("d", 0L)
        return if (duration > 0) (entry.optLong("p", 0L).toFloat() / duration).coerceIn(0f, 1f) else 0f
    }

    @Synchronized
    fun clear() {
        cache = JSONObject()
        LibraryRepository.saveBlob(KEY, "{}")
    }

    internal fun put(all: JSONObject, videoId: String, positionMs: Long, durationMs: Long, now: Long): JSONObject {
        val finished = durationMs > 0 && positionMs >= durationMs - END_MARGIN_MS
        if (positionMs < MIN_MS || finished) {
            all.remove(videoId)
            return all
        }
        all.put(videoId, JSONObject().put("p", positionMs).put("d", durationMs).put("t", now))
        if (all.length() > MAX_ENTRIES) {
            val oldest = all.keys().asSequence().toList()
                .sortedBy { all.optJSONObject(it)?.optLong("t", 0L) ?: 0L }
                .take(all.length() - MAX_ENTRIES)
            oldest.forEach { all.remove(it) }
        }
        return all
    }
}
