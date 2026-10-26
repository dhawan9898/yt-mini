package com.nexusstream.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagedTest {

    @Test
    fun pageWithoutLoaderHasNoMore() = runBlocking {
        val page = Paged(listOf(1), null)
        assertFalse(page.hasMore)
        assertTrue(page.next().getOrThrow().items.isEmpty())
    }

    @Test
    fun nextLoadsTheFollowingPage() = runBlocking {
        val second = Paged(listOf(3, 4), null)
        val first = Paged(listOf(1, 2)) { Result.success(second) }
        assertTrue(first.hasMore)
        assertEquals(listOf(3, 4), first.next().getOrThrow().items)
        assertFalse(first.next().getOrThrow().hasMore)
    }

    @Test
    fun failuresArePropagatedNotThrown() = runBlocking {
        val page = Paged(listOf(1)) { Result.failure<Paged<Int>>(IllegalStateException("boom")) }
        assertTrue(page.next().isFailure)
    }
}
