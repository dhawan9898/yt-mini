package com.nexusstream.app.ui.components

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendlyErrorTest {
    @Test fun noNetworkReadsAsOffline() {
        assertEquals(FriendlyError.OFFLINE, FriendlyError.message(UnknownHostException("youtube.com")))
        assertEquals(FriendlyError.OFFLINE, FriendlyError.message(RuntimeException("wrapped", UnknownHostException("x"))))
        assertEquals(FriendlyError.OFFLINE, FriendlyError.message(IOException("Unable to resolve host \"x\"")))
    }

    @Test fun timeoutsReadAsSlow() {
        assertEquals(FriendlyError.SLOW, FriendlyError.message(SocketTimeoutException("timeout")))
        assertEquals(FriendlyError.SLOW, FriendlyError.message(RuntimeException("Request timed out")))
    }

    @Test fun videoProblemsAreExplained() {
        assertEquals("This video is private.", FriendlyError.message(RuntimeException("This is a private video")))
        assertEquals("This video isn't available.", FriendlyError.message(RuntimeException("Video unavailable")))
    }

    @Test fun unknownErrorsNeverLeakInternals() {
        val m = FriendlyError.message(IllegalStateException("kotlin.NullPointerException at com.x.Y.z(Y.kt:12)"))
        assertEquals(FriendlyError.GENERIC, m)
        assertFalse(m.contains("Exception"))
    }

    @Test fun actionsKeepTheAppsOwnSentences() {
        assertEquals("Sign in to like comments", FriendlyError.orFallback(IllegalStateException("Sign in to like comments"), "Couldn't like the comment"))
        // A network problem still says so, whatever the action.
        assertEquals(FriendlyError.OFFLINE, FriendlyError.orFallback(UnknownHostException("youtube.com"), "Couldn't post the comment"))
    }

    @Test fun actionsHideInternals() {
        val fallback = "Couldn't post the comment"
        assertEquals(fallback, FriendlyError.orFallback(IllegalStateException("java.lang.IllegalStateException: boom"), fallback))
        assertEquals(fallback, FriendlyError.orFallback(RuntimeException("HTTP 400 from https://youtubei.googleapis.com/x"), fallback))
        assertEquals(fallback, FriendlyError.orFallback(RuntimeException("response code 403"), fallback))
        assertEquals(fallback, FriendlyError.orFallback(RuntimeException(), fallback))
    }

    @Test fun playbackErrorsAreWordsWithAWayForward() {
        assertEquals(FriendlyError.OFFLINE, FriendlyError.playback(null, "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"))
        assertTrue(FriendlyError.playback(null, "ERROR_CODE_IO_BAD_HTTP_STATUS").contains("Retry"))
        assertTrue(FriendlyError.playback(null, "ERROR_CODE_DECODER_INIT_FAILED").contains("format"))
        val unknown = FriendlyError.playback(IllegalStateException("x.y.Z failed"), "ERROR_CODE_UNSPECIFIED")
        assertFalse(unknown.contains("ERROR_CODE"))
        assertTrue(unknown.contains("Retry"))
    }
}
