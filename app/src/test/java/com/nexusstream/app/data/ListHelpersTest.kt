package com.nexusstream.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ListHelpersTest {
    @Test
    fun mixInSpreadsExtrasAndKeepsAllItems() {
        val mixed = YouTubeRepository.mixIn(listOf(1, 2, 3, 4), listOf(90, 91), every = 2)
        assertEquals(listOf(1, 2, 90, 3, 4, 91), mixed)
    }

    @Test
    fun mixInAppendsExtrasThatDidNotFit() {
        assertEquals(listOf(1, 90, 91), YouTubeRepository.mixIn(listOf(1), listOf(90, 91), every = 5))
    }

    @Test
    fun mixInNeverDividesByZero() {
        // A non-positive interval behaves like "after every item" instead of crashing.
        assertEquals(listOf(1, 90, 2, 91), YouTubeRepository.mixIn(listOf(1, 2), listOf(90, 91), every = 0))
        assertEquals(listOf(1, 90, 2, 91), YouTubeRepository.mixIn(listOf(1, 2), listOf(90, 91), every = -3))
    }

    @Test
    fun interleaveTakesOneFromEachListInTurn() {
        assertEquals(listOf(1, 10, 100, 2, 20, 3), YouTubeRepository.interleave(listOf(listOf(1, 2, 3), listOf(10, 20), listOf(100))))
    }

    @Test
    fun interleaveHandlesEmptyInputs() {
        assertEquals(emptyList<Int>(), YouTubeRepository.interleave(emptyList<List<Int>>()))
        assertEquals(listOf(1, 2), YouTubeRepository.interleave(listOf(emptyList(), listOf(1, 2), emptyList())))
    }
}
