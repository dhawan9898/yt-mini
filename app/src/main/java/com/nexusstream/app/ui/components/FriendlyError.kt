package com.nexusstream.app.ui.components

import com.nexusstream.app.data.YouTubeApiException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** What a person should read when a load fails: plain words, never an exception class or a stack. */
object FriendlyError {

    fun message(error: Throwable): String {
        var e: Throwable? = error
        var depth = 0
        while (e != null && depth++ < 6) {
            when (e) {
                is UnknownHostException -> return OFFLINE
                is SocketTimeoutException -> return SLOW
                is YouTubeApiException -> return when {
                    e.isQuota -> "YouTube is busy right now. Showing it again in a little while should work."
                    e.isKeyProblem -> "YouTube couldn't be reached from this app. Please try again later."
                    else -> GENERIC
                }
            }
            e = e.cause
        }
        val text = error.message.orEmpty().lowercase()
        return when {
            "unable to resolve host" in text || "failed to connect" in text || "network is unreachable" in text -> OFFLINE
            "timeout" in text || "timed out" in text -> SLOW
            "429" in text || "too many requests" in text -> "YouTube is busy right now. Try again in a minute."
            "sign in to confirm" in text || "confirm you're not a bot" in text || "confirm you’re not a bot" in text ->
                "YouTube is asking for a check. Open it again in a minute."
            "private" in text && "video" in text -> "This video is private."
            "unavailable" in text || "not available" in text || "removed" in text -> "This video isn't available."
            "age" in text && "restrict" in text -> "This video is age-restricted. Sign in to watch it."
            error is IOException -> OFFLINE
            else -> GENERIC
        }
    }

    /**
     * For actions with their own wording ("Couldn't post the comment"): a network or YouTube problem
     * is described as such, a sentence the app wrote itself is kept, and anything else (exception
     * text, URLs, codes) becomes [fallback].
     */
    fun orFallback(error: Throwable, fallback: String): String {
        val mapped = message(error)
        if (mapped != GENERIC) return mapped
        val text = error.message?.trim().orEmpty()
        return if (readable(text)) text else fallback
    }

    /** Playback failures: what happened, in words, and that trying again may help. */
    fun playback(error: Throwable?, code: String? = null): String {
        val c = code.orEmpty()
        return when {
            "NETWORK" in c || "TIMEOUT" in c -> OFFLINE
            "BAD_HTTP_STATUS" in c || "FILE_NOT_FOUND" in c -> "This video's stream stopped working. Tap Retry to load it again."
            "DECOD" in c || "FORMAT" in c || "AUDIO_TRACK" in c -> "This device couldn't play this video's format. Try another quality."
            "DRM" in c -> "This video is protected and can't be played here."
            error != null -> message(error).let { if (it == GENERIC) "This video couldn't be played. Tap Retry to try again." else it }
            else -> "This video couldn't be played. Tap Retry to try again."
        }
    }

    private fun readable(text: String): Boolean =
        text.isNotEmpty() && text.length <= 160 && !text.contains("://") && !text.contains("Exception") &&
            !text.contains("java.") && !text.contains("kotlin.") && !Regex("""\b[1-5]\d\d\b""").containsMatchIn(text) &&
            text.first().isUpperCase()

    const val OFFLINE = "You're offline. Check your connection and try again."
    const val SLOW = "That took too long. Check your connection and try again."
    const val GENERIC = "Something went wrong. Please try again."
}
