package com.nexusstream.app.ui.components

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
