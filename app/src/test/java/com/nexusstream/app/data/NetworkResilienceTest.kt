package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkResilienceTest {
    @Test
    fun timeoutsAndDroppedConnectionsAreRetried() {
        assertTrue(YouTubeRepository.isTransientNetworkError(SocketTimeoutException("timeout")))
        assertTrue(YouTubeRepository.isTransientNetworkError(IOException("connection reset")))
        assertTrue("an extractor error that wraps a network error", YouTubeRepository.isTransientNetworkError(RuntimeException("wrapped", IOException("reset"))))
    }

    @Test
    fun offlineCaptchaAndCancellationAreNotRetried() {
        assertFalse(YouTubeRepository.isTransientNetworkError(UnknownHostException("no network")))
        assertFalse(YouTubeRepository.isTransientNetworkError(ReCaptchaException("captcha", "https://youtube.com")))
        assertFalse(YouTubeRepository.isTransientNetworkError(kotlinx.coroutines.CancellationException("cancelled")))
        assertFalse(YouTubeRepository.isTransientNetworkError(IllegalStateException("a plain bug")))
    }

    @Test
    fun certificateFingerprintsAreWrittenTheWayGoogleExpectsThem() {
        assertEquals("00:0F:A0:FF", AppSignature.hex(byteArrayOf(0x00, 0x0F, 0xA0.toByte(), 0xFF.toByte())))
        assertEquals("", AppSignature.hex(byteArrayOf()))
    }
}
