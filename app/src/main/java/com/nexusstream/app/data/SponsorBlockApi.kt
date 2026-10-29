package com.nexusstream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray

data class SponsorSegment(
    val uuid: String,
    val category: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * Client for the crowdsourced SponsorBlock API (https://sponsor.ajay.app).
 * Fetches timestamps for sponsored segments, intros, outros, and self-promotions
 * so they can be automatically skipped during video playback.
 */
object SponsorBlockApi {
    private const val BASE_URL = "https://sponsor.ajay.app/api/skipSegments"
    private val DEFAULT_CATEGORIES = listOf("sponsor", "intro", "outro", "interaction", "selfpromo")

    private val cache = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, List<SponsorSegment>>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SponsorSegment>>?): Boolean = size > 64
        }
    )

    fun cached(videoId: String): List<SponsorSegment>? = cache[videoId]

    suspend fun fetch(videoId: String, categories: List<String> = DEFAULT_CATEGORIES): Result<List<SponsorSegment>> =
        withContext(Dispatchers.IO) {
            cached(videoId)?.let { return@withContext Result.success(it) }
            runCatching {
                val catParam = JSONArray(categories).toString()
                val url = "$BASE_URL?videoID=$videoId&categories=${java.net.URLEncoder.encode(catParam, "UTF-8")}"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", NewPipeDownloader.USER_AGENT)
                    .build()

                NewPipeDownloader.instance.client.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        // 404 simply means no sponsor segments exist for this video.
                        cache[videoId] = emptyList()
                        return@withContext Result.success(emptyList())
                    }
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = response.body?.string().orEmpty()
                    val segments = parse(body)
                    cache[videoId] = segments
                    segments
                }
            }
        }

    internal fun parse(json: String): List<SponsorSegment> = runCatching {
        val arr = JSONArray(json)
        val list = ArrayList<SponsorSegment>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val segmentArr = item.optJSONArray("segment") ?: continue
            if (segmentArr.length() < 2) continue
            val startMs = (segmentArr.getDouble(0) * 1000).toLong()
            val endMs = (segmentArr.getDouble(1) * 1000).toLong()
            if (endMs > startMs) {
                list += SponsorSegment(
                    uuid = item.optString("UUID", "seg_$i"),
                    category = item.optString("category", "sponsor"),
                    startMs = startMs,
                    endMs = endMs
                )
            }
        }
        list.sortedBy { it.startMs }
    }.getOrDefault(emptyList())
}
