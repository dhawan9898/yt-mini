package com.nexusstream.app.media

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * How the player retries a piece of a stream that failed to load.
 *
 * - A network hiccup (timeout, dropped connection, 5xx) is retried more times than ExoPlayer's
 *   default, with a growing pause, so a few seconds of bad signal do not end playback.
 * - "403 Forbidden" / "410 Gone" mean the stream link has expired: retrying the same link cannot
 *   work, so it fails at once and the service fetches fresh links (see onPlayerError).
 */
@UnstableApi
class PatientLoadPolicy : DefaultLoadErrorHandlingPolicy(MIN_RETRIES) {
    override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val code = (info.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        // Checked first: the default policy also retries some HTTP errors.
        if (code in EXPIRED_CODES) return C.TIME_UNSET
        return delayFor(code, info.errorCount, retryable = super.getRetryDelayMsFor(info) != C.TIME_UNSET)
    }

    companion object {
        private const val MIN_RETRIES = 6
        private const val MAX_DELAY_MS = 5_000L
        private val EXPIRED_CODES = setOf(403, 410)

        /** The pause before retry [errorCount], or [C.TIME_UNSET] for none. Pure, for tests. */
        internal fun delayFor(responseCode: Int?, errorCount: Int, retryable: Boolean): Long = when {
            responseCode in EXPIRED_CODES -> C.TIME_UNSET
            !retryable -> C.TIME_UNSET
            else -> minOf(errorCount * 1_000L, MAX_DELAY_MS)
        }

        /** Whether an error is the link having expired (fresh links fix it). */
        fun isExpiredLink(error: Throwable?): Boolean {
            var e = error
            while (e != null) {
                if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode in EXPIRED_CODES) return true
                e = e.cause
            }
            return false
        }
    }
}
