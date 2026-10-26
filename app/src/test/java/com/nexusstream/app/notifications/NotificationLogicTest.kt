package com.nexusstream.app.notifications

import com.nexusstream.app.DeepLinks
import com.nexusstream.app.data.LibraryRepository.ChannelSub
import com.nexusstream.app.data.NotifyLevel
import com.nexusstream.app.model.MediaItemModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationLogicTest {

    private fun video(id: String, channel: String, publishedAt: Long) = MediaItemModel(
        id = id, title = "T $id", artistOrChannel = channel,
        audioUrl = "", videoUrl = "", artworkUrl = "", durationMs = 0L,
        publishedAtMs = publishedAt, channelUrl = "https://www.youtube.com/channel/$channel"
    )

    private fun sub(
        name: String,
        level: NotifyLevel = NotifyLevel.ALL,
        seen: Long = 0L,
        notified: Long = 0L
    ) = ChannelSub(name, "", "https://www.youtube.com/channel/$name", seen, level, notified)

    private val now = 100_000_000_000L

    // ---- which uploads to notify about ----

    @Test
    fun allLevelNotifiesNewestFirstUpToTheCap() {
        val feed = (1..5).map { video("v$it", "A", it * 1000L) }
        val plan = UploadNotifications.select(listOf(sub("A")), feed, now).single()
        assertEquals(listOf("v5", "v4", "v3"), plan.videos.map { it.id })
    }

    @Test
    fun nothingNewMeansNoPlan() {
        val feed = listOf(video("v1", "A", 1000L))
        assertTrue(UploadNotifications.select(listOf(sub("A", seen = 2000L)), feed, now).isEmpty())
        assertTrue(UploadNotifications.select(listOf(sub("A", notified = 1000L)), feed, now).isEmpty())
    }

    @Test
    fun levelNoneNeverNotifies() {
        val feed = listOf(video("v1", "A", 5000L))
        assertTrue(UploadNotifications.select(listOf(sub("A", NotifyLevel.NONE)), feed, now).isEmpty())
    }

    @Test
    fun personalizedNotifiesOnlyTheNewestAndRespectsTheGap() {
        val feed = listOf(video("v1", "A", 1000L), video("v2", "A", 2000L))
        val first = UploadNotifications.select(listOf(sub("A", NotifyLevel.PERSONALIZED)), feed, now).single()
        assertEquals(listOf("v2"), first.videos.map { it.id })

        // A video newer than the last notification, but that notification was only a minute ago.
        val recent = listOf(video("v3", "A", now - 30_000L))
        val tooSoon = sub("A", NotifyLevel.PERSONALIZED, notified = now - 60_000L)
        assertTrue(UploadNotifications.select(listOf(tooSoon), recent, now).isEmpty())

        // The same video is allowed once the gap has passed.
        val longAgo = sub("A", NotifyLevel.PERSONALIZED, notified = now - 13 * 60 * 60_000L)
        assertEquals(listOf("v3"), UploadNotifications.select(listOf(longAgo), recent, now).single().videos.map { it.id })
    }

    @Test
    fun videosAreMatchedToTheirOwnChannel() {
        val feed = listOf(video("a1", "A", 5000L), video("b1", "B", 6000L))
        val plans = UploadNotifications.select(listOf(sub("A"), sub("B")), feed, now)
        assertEquals(listOf("a1"), plans.first { it.channel.name == "A" }.videos.map { it.id })
        assertEquals(listOf("b1"), plans.first { it.channel.name == "B" }.videos.map { it.id })
    }

    // ---- links ----

    @Test
    fun extractsVideoIdsFromCommonLinkShapes() {
        val id = "dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=$id",
            "https://youtube.com/watch?feature=share&v=$id&t=10",
            "https://m.youtube.com/watch?v=$id",
            "https://music.youtube.com/watch?v=$id",
            "https://youtu.be/$id?si=abc",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "Check this out https://youtu.be/$id now"
        ).forEach { assertEquals(it, id, DeepLinks.videoIdFrom(it)) }
    }

    @Test
    fun nonVideoLinksGiveNull() {
        assertNull(DeepLinks.videoIdFrom("https://www.youtube.com/@somechannel"))
        assertNull(DeepLinks.videoIdFrom("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(DeepLinks.videoIdFrom("no link here"))
    }

    // ---- bedtime ----

    private val zone = ZoneId.of("UTC")
    private fun at(h: Int, m: Int): Long = ZonedDateTime.of(2026, 10, 26, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun bedtimeLaterToday() {
        assertEquals(60 * 60_000L, Bedtime.delayUntilMs(at(21, 0), 22 * 60, zone))
    }

    @Test
    fun bedtimeAlreadyPassedMeansTomorrow() {
        assertEquals(23 * 60 * 60_000L, Bedtime.delayUntilMs(at(23, 0), 22 * 60, zone))
        // Exactly at the time: next one is tomorrow, not now.
        assertEquals(24 * 60 * 60_000L, Bedtime.delayUntilMs(at(22, 0), 22 * 60, zone))
    }

    @Test
    fun bedtimeLabel() {
        assertEquals("22:00", Bedtime.label(22 * 60))
        assertEquals("00:30", Bedtime.label(30))
    }
}
