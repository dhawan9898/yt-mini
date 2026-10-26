package com.nexusstream.app.data

import com.nexusstream.app.model.MediaItemModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupTest {

    @Before
    fun reset() = LibraryRepository.resetForTest()

    private fun item(id: String) = MediaItemModel(
        id = id, title = "Title $id", artistOrChannel = "Channel",
        audioUrl = "", videoUrl = "", artworkUrl = "", durationMs = 1000L
    )

    @Test
    fun backupRoundTripRestoresTheLibrary() {
        LibraryRepository.toggleSave(item("s1"))
        LibraryRepository.toggleLike(item("l1"))
        LibraryRepository.toggleDislike(item("d1"))
        listOf("h1", "h2").forEach { LibraryRepository.recordPlayed(item(it)) }
        val playlist = LibraryRepository.createPlaylist("Mix")
        listOf("p1", "p2", "p3").forEach { LibraryRepository.togglePlaylistItem(playlist.id, item(it)) }
        LibraryRepository.toggleSubscribe("Chan", "avatar", "https://www.youtube.com/channel/UC1")
        LibraryRepository.setNotifyLevel("Chan", NotifyLevel.ALL)
        LibraryRepository.addSearch("kotlin")
        LibraryRepository.hideVideo("bad")
        LibraryRepository.hideChannel("Spam")
        AppSettings.setDataSaver(true)

        val backup = LibraryRepository.exportBackup()

        LibraryRepository.resetForTest()
        AppSettings.setDataSaver(false)
        assertTrue(LibraryRepository.playlists.value.isEmpty())

        assertTrue(LibraryRepository.importBackup(backup))
        assertEquals(listOf("s1"), LibraryRepository.saved.value.map { it.id })
        assertEquals(listOf("l1"), LibraryRepository.liked.value.map { it.id })
        assertTrue(LibraryRepository.isDisliked("d1"))
        assertEquals(listOf("h2", "h1"), LibraryRepository.history.value.map { it.id })
        val restored = LibraryRepository.playlists.value.single()
        assertEquals("Mix", restored.name)
        assertEquals(listOf("p1", "p2", "p3"), restored.items.map { it.id })
        val sub = LibraryRepository.subscriptions.value.single()
        assertEquals("Chan", sub.name)
        assertEquals(NotifyLevel.ALL, sub.notifyLevel)
        assertEquals("https://www.youtube.com/channel/UC1", sub.channelUrl)
        assertEquals(listOf("kotlin"), LibraryRepository.searchHistory.value)
        assertEquals(setOf("bad"), LibraryRepository.hidden.value.videoIds)
        assertEquals(setOf("Spam"), LibraryRepository.hidden.value.channels)
        assertTrue(AppSettings.dataSaver.value)
    }

    @Test
    fun notABackupIsRejectedAndLeavesDataAlone() {
        LibraryRepository.toggleSave(item("keep"))
        assertFalse(LibraryRepository.importBackup("not json"))
        assertFalse(LibraryRepository.importBackup("""{"app":"Other"}"""))
        assertEquals(listOf("keep"), LibraryRepository.saved.value.map { it.id })
    }

    @Test
    fun likingAndDislikingAreMutuallyExclusive() {
        LibraryRepository.toggleLike(item("a"))
        LibraryRepository.toggleDislike(item("a"))
        assertTrue(LibraryRepository.isDisliked("a"))
        assertFalse(LibraryRepository.isLiked("a"))
        LibraryRepository.toggleLike(item("a"))
        assertTrue(LibraryRepository.isLiked("a"))
        assertFalse(LibraryRepository.isDisliked("a"))
    }
}
