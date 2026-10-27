package com.nexusstream.app.ui

import com.nexusstream.app.ui.components.fitVideoFrame
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoFrameTest {
    private val delta = 0.01f

    @Test fun fitWideScreenPillarboxesAndCenters() {
        // 20:9 phone in landscape, 16:9 video: full height, bars left and right.
        val frame = fitVideoFrame(2000f, 900f, 16f / 9f, fill = false)
        assertEquals(900f, frame.height, delta)
        assertEquals(1600f, frame.width, delta)
    }

    @Test fun fillWideScreenCoversWidthAndCropsHeight() {
        val frame = fitVideoFrame(2000f, 900f, 16f / 9f, fill = true)
        assertEquals(2000f, frame.width, delta)
        assertEquals(1125f, frame.height, delta)
    }

    @Test fun sameAspectFillsExactlyEitherWay() {
        for (fill in listOf(true, false)) {
            val frame = fitVideoFrame(1280f, 720f, 16f / 9f, fill)
            assertEquals(1280f, frame.width, delta)
            assertEquals(720f, frame.height, delta)
        }
    }

    @Test fun portraitShortFillsPhoneScreen() {
        // 9:16 short on a 1080x2400 screen: fill crops the sides, no bars.
        val frame = fitVideoFrame(1080f, 2400f, 9f / 16f, fill = true)
        assertEquals(2400f, frame.height, delta)
        assertEquals(1350f, frame.width, delta)
    }

    @Test fun landscapeVideoInPortraitFitLetterboxes() {
        val frame = fitVideoFrame(1080f, 2400f, 16f / 9f, fill = false)
        assertEquals(1080f, frame.width, delta)
        assertEquals(607.5f, frame.height, delta)
    }
}
