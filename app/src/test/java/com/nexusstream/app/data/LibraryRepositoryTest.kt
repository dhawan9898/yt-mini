package com.nexusstream.app.data

import com.nexusstream.app.model.MediaItemModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryRepositoryTest {

    @Before
    fun reset() = LibraryRepository.resetForTest()

    private fun item(id: String) = MediaItemModel(
        id = id, title = "Title $id", artistOrChannel = "Channel",
        audioUrl = "", videoUrl = "", artworkUrl = "", durationMs = 1000L
    )

    private fun ids(playlistId: String) =
        LibraryRepository.playlists.value.first { it.id == playlistId }.items.map { it.id }

    // ---- playlists ----

    @Test
    fun createPlaylistTrimsNameAndFallsBackToDefault() {
        assertEquals("Mix", LibraryRepository.createPlaylist("  Mix  ").name)
        assertEquals("New playlist", LibraryRepository.createPlaylist("   ").name)
        assertEquals(2, LibraryRepository.playlists.value.size)
    }

    @Test
    fun togglePlaylistItemAddsThenRemoves() {
        val id = LibraryRepository.createPlaylist("P").id
        LibraryRepository.togglePlaylistItem(id, item("a"))
        LibraryRepository.togglePlaylistItem(id, item("b"))
        assertEquals(listOf("a", "b"), ids(id))
        assertTrue(LibraryRepository.isInPlaylist(id, "a"))

        LibraryRepository.togglePlaylistItem(id, item("a"))
        assertEquals(listOf("b"), ids(id))
        assertFalse(LibraryRepository.isInPlaylist(id, "a"))
    }

    @Test
    fun movePlaylistItemReordersAndIgnoresBadIndexes() {
        val id = LibraryRepository.createPlaylist("P").id
        listOf("a", "b", "c").forEach { LibraryRepository.togglePlaylistItem(id, item(it)) }

        LibraryRepository.movePlaylistItem(id, 0, 2)
        assertEquals(listOf("b", "c", "a"), ids(id))

        LibraryRepository.movePlaylistItem(id, 2, 0)
        assertEquals(listOf("a", "b", "c"), ids(id))

        LibraryRepository.movePlaylistItem(id, 7, 0)
        assertEquals(listOf("a", "b", "c"), ids(id))

        LibraryRepository.movePlaylistItem(id, 0, 99)
        assertEquals(listOf("b", "c", "a"), ids(id))
    }

    @Test
    fun renameIgnoresBlankAndDeleteRemoves() {
        val id = LibraryRepository.createPlaylist("Old").id
        LibraryRepository.renamePlaylist(id, "   ")
        assertEquals("Old", LibraryRepository.playlists.value.single().name)
        LibraryRepository.renamePlaylist(id, "New")
        assertEquals("New", LibraryRepository.playlists.value.single().name)
        LibraryRepository.deletePlaylist(id)
        assertTrue(LibraryRepository.playlists.value.isEmpty())
    }

    @Test
    fun removeFromPlaylistDropsOnlyThatItem() {
        val id = LibraryRepository.createPlaylist("P").id
        listOf("a", "b").forEach { LibraryRepository.togglePlaylistItem(id, item(it)) }
        LibraryRepository.removeFromPlaylist(id, "a")
        assertEquals(listOf("b"), ids(id))
    }

    // ---- liked / watch later ----

    @Test
    fun toggleLikeAndSave() {
        LibraryRepository.toggleLike(item("a"))
        assertTrue(LibraryRepository.isLiked("a"))
        LibraryRepository.toggleLike(item("a"))
        assertFalse(LibraryRepository.isLiked("a"))

        LibraryRepository.toggleSave(item("b"))
        assertTrue(LibraryRepository.isSaved("b"))
    }

    // ---- history ----

    @Test
    fun historyIsNewestFirstAndDeduplicated() {
        LibraryRepository.recordPlayed(item("a"))
        LibraryRepository.recordPlayed(item("b"))
        LibraryRepository.recordPlayed(item("a"))
        assertEquals(listOf("a", "b"), LibraryRepository.history.value.map { it.id })
    }

    @Test
    fun pausedHistoryRecordsNothing() {
        LibraryRepository.setHistoryPaused(true)
        LibraryRepository.recordPlayed(item("a"))
        assertTrue(LibraryRepository.history.value.isEmpty())
        LibraryRepository.setHistoryPaused(false)
        LibraryRepository.recordPlayed(item("a"))
        assertEquals(1, LibraryRepository.history.value.size)
    }

    @Test
    fun removeAndClearHistory() {
        listOf("a", "b", "c").forEach { LibraryRepository.recordPlayed(item(it)) }
        LibraryRepository.removeFromHistory("b")
        assertEquals(listOf("c", "a"), LibraryRepository.history.value.map { it.id })
        LibraryRepository.clearHistory()
        assertTrue(LibraryRepository.history.value.isEmpty())
    }

    // ---- imports ----

    @Test
    fun importHistorySkipsExistingEntries() {
        LibraryRepository.recordPlayed(item("a"))
        val added = LibraryRepository.importHistory(listOf(item("a"), item("b"), item("b"), item("c")))
        assertEquals(2, added)
        assertEquals(listOf("a", "b", "c"), LibraryRepository.history.value.map { it.id })
    }

    @Test
    fun importSubscriptionsOnlyAddsNewChannels() {
        LibraryRepository.toggleSubscribe("One", "")
        val added = LibraryRepository.importSubscriptions(
            listOf(
                LibraryRepository.ChannelSub("One", ""),
                LibraryRepository.ChannelSub("Two", ""),
                LibraryRepository.ChannelSub("Two", "")
            )
        )
        assertEquals(1, added)
        assertEquals(listOf("One", "Two"), LibraryRepository.subscriptions.value.map { it.name })
    }
}
