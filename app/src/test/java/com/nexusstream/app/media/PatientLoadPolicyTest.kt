package com.nexusstream.app.media

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

@UnstableApi
class PatientLoadPolicyTest {
    @Test
    fun expiredLinksAreNotRetried() {
        assertEquals(C.TIME_UNSET, PatientLoadPolicy.delayFor(403, 1, retryable = true))
        assertEquals(C.TIME_UNSET, PatientLoadPolicy.delayFor(410, 1, retryable = true))
        assertFalse(PatientLoadPolicy.isExpiredLink(IOException("timeout")))
    }

    @Test
    fun networkHiccupsBackOff() {
        assertEquals(1_000L, PatientLoadPolicy.delayFor(null, 1, retryable = true))
        assertEquals(3_000L, PatientLoadPolicy.delayFor(503, 3, retryable = true))
        assertEquals(5_000L, PatientLoadPolicy.delayFor(null, 9, retryable = true))
        // What ExoPlayer itself will not retry (a parse error) stays unretried.
        assertEquals(C.TIME_UNSET, PatientLoadPolicy.delayFor(null, 1, retryable = false))
    }

    @Test
    fun retriesMoreOftenThanTheDefault() {
        assertEquals(6, PatientLoadPolicy().getMinimumLoadableRetryCount(C.DATA_TYPE_MEDIA))
    }
}
