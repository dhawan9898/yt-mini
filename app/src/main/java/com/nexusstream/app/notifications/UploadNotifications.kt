package com.nexusstream.app.notifications

import com.nexusstream.app.data.LibraryRepository.ChannelSub
import com.nexusstream.app.data.NotifyLevel
import com.nexusstream.app.model.MediaItemModel

/** New uploads of one channel that should produce notifications. */
data class UploadPlan(val channel: ChannelSub, val videos: List<MediaItemModel>)

/** Decides which new uploads to notify about. Pure logic, no Android. */
object UploadNotifications {
    /** Personalized channels notify at most once per this interval. */
    const val PERSONALIZED_MIN_GAP_MS = 12 * 60 * 60 * 1000L
    /** Never notify about more than this many videos of one channel in one check. */
    const val MAX_PER_CHANNEL = 3

    /**
     * For every subscription (except those set to None): the videos from [feed] published after
     * both the last time the channel was opened and the last notification.
     *  - ALL: up to [MAX_PER_CHANNEL] newest uploads.
     *  - PERSONALIZED: only the newest upload, and not more often than [PERSONALIZED_MIN_GAP_MS].
     * Videos are returned newest first. Channels without anything new are omitted.
     */
    fun select(subs: List<ChannelSub>, feed: List<MediaItemModel>, nowMs: Long): List<UploadPlan> =
        subs.filter { it.notifyLevel != NotifyLevel.NONE }.mapNotNull { sub ->
            val baseline = maxOf(sub.lastSeenMs, sub.lastNotifiedMs)
            val fresh = feed
                .filter { video -> belongsTo(sub, video) && video.publishedAtMs > baseline }
                .sortedByDescending { it.publishedAtMs }
            if (fresh.isEmpty()) return@mapNotNull null

            when (sub.notifyLevel) {
                NotifyLevel.ALL -> UploadPlan(sub, fresh.take(MAX_PER_CHANNEL))
                NotifyLevel.PERSONALIZED ->
                    if (nowMs - sub.lastNotifiedMs >= PERSONALIZED_MIN_GAP_MS) UploadPlan(sub, fresh.take(1)) else null
                NotifyLevel.NONE -> null
            }
        }

    private fun belongsTo(sub: ChannelSub, video: MediaItemModel): Boolean =
        (sub.channelUrl.isNotBlank() && video.channelUrl == sub.channelUrl) || video.artistOrChannel == sub.name
}
