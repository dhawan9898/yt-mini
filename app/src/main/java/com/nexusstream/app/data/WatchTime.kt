package com.nexusstream.app.data

import org.json.JSONObject
import java.time.LocalDate

/**
 * "Time watched": how long something actually played each day (YouTube's own counter lives in
 * the app and isn't in any API, so this one is counted on this device).
 */
object WatchTime {
    private const val KEY = "watch_time"
    private const val KEEP_DAYS = 60L
    private var playingSince = 0L

    /** Called whenever the player starts or stops actually playing. */
    @Synchronized
    fun playing(isPlaying: Boolean, now: Long = System.currentTimeMillis()) {
        if (isPlaying) {
            if (playingSince == 0L) playingSince = now
            return
        }
        if (playingSince == 0L) return
        val elapsed = (now - playingSince).coerceIn(0L, 6 * 3_600_000L)
        playingSince = 0L
        if (elapsed < 1000 || GoogleAccount.incognito.value) return
        LibraryRepository.saveBlob(KEY, add(LibraryRepository.loadBlob(KEY), LocalDate.now(), elapsed).toString())
    }

    /** Milliseconds watched per day, newest first, for the last [days] days (today included). */
    fun lastDays(days: Int = 7, today: LocalDate = LocalDate.now()): List<Pair<LocalDate, Long>> {
        val totals = runCatching { JSONObject(LibraryRepository.loadBlob(KEY) ?: "{}") }.getOrDefault(JSONObject())
        return (0 until days).map { i ->
            val day = today.minusDays(i.toLong())
            day to totals.optLong(day.toString(), 0L)
        }
    }

    fun clear() = LibraryRepository.saveBlob(KEY, "{}")

    internal fun add(raw: String?, day: LocalDate, elapsedMs: Long): JSONObject {
        val totals = runCatching { JSONObject(raw ?: "{}") }.getOrDefault(JSONObject())
        val key = day.toString()
        totals.put(key, totals.optLong(key, 0L) + elapsedMs)
        val cutoff = day.minusDays(KEEP_DAYS).toString()
        totals.keys().asSequence().toList().filter { it < cutoff }.forEach { totals.remove(it) }
        return totals
    }
}
