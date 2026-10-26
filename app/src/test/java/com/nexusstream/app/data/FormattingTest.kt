package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun formatsEachMagnitude() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("512 KB", formatBytes(512 * 1024L))
        assertEquals("34.5 MB", formatBytes((34.5 * 1024 * 1024).toLong()))
        assertEquals("1.50 GB", formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun negativeSizesAreBlank() {
        assertEquals("", formatBytes(-1))
    }
}
