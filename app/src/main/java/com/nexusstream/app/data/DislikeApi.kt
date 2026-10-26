package com.nexusstream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Dislike counts from the community "Return YouTube Dislike" service (YouTube no longer shows
 * them). Optional: only used when the setting is on, and only the video id is sent.
 */
object DislikeApi {
    private const val ENDPOINT = "https://returnyoutubedislikeapi.com/votes?videoId="

    /** Reads `dislikes` from the service's JSON answer; null if missing or malformed. */
    fun parse(json: String): Long? = runCatching {
        val obj = JSONObject(json)
        if (obj.has("dislikes")) obj.getLong("dislikes") else null
    }.getOrNull()

    suspend fun fetch(videoId: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val request = okhttp3.Request.Builder()
                .url(ENDPOINT + videoId)
                .header("User-Agent", NewPipeDownloader.USER_AGENT)
                .build()
            NewPipeDownloader.instance.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                parse(response.body?.string().orEmpty()) ?: error("No dislike count")
            }
        }
    }
}
