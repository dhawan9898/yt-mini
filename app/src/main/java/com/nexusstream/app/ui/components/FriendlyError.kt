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

    const val OFFLINE = "You're offline. Check your connection and try again."
    const val SLOW = "That took too long. Check your connection and try again."
    const val GENERIC = "Something went wrong. Please try again."
}
