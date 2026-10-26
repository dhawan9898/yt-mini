package com.nexusstream.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexusstream.app.data.AppSettings
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.NotifyLevel
import com.nexusstream.app.data.YouTubeRepository

/**
 * Periodically checks subscribed channels' public RSS feeds and notifies about new uploads,
 * according to each channel's bell level. New videos also land in the in-app notification inbox.
 */
class UploadCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        LibraryRepository.init(context)
        AppSettings.init(context)
        if (!LibraryRepository.notificationsEnabled.value) return Result.success()

        val subs = LibraryRepository.subscriptions.value.filter { it.notifyLevel != NotifyLevel.NONE }
        if (subs.isEmpty()) return Result.success()

        val feed = YouTubeRepository.subscriptionFeed(subs, refresh = true).getOrNull()
            ?: return Result.retry()

        val plans = UploadNotifications.select(subs, feed, System.currentTimeMillis())
        for (plan in plans) {
            plan.videos.forEach { NotificationHelper.notifyUpload(context, plan.channel.name, it) }
            LibraryRepository.addToInbox(plan.videos)
            LibraryRepository.markNotified(plan.channel.name, plan.videos.maxOf { it.publishedAtMs })
        }
        return Result.success()
    }
}
