package com.nexusstream.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexusstream.app.MainActivity
import com.nexusstream.app.model.MediaItemModel

/** Notification channels and builders for upload alerts, reminders and download progress. */
object NotificationHelper {
    const val CHANNEL_UPLOADS = "new_uploads"
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_DOWNLOADS = "downloads"

    const val EXTRA_VIDEO_ID = "extra_video_id"
    const val EXTRA_VIDEO_TITLE = "extra_video_title"
    const val EXTRA_VIDEO_CHANNEL = "extra_video_channel"

    private const val BEDTIME_NOTIFICATION_ID = 2001
    const val DOWNLOAD_NOTIFICATION_ID = 3001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_UPLOADS, "New uploads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New videos from channels you subscribe to"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Bedtime reminder"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DOWNLOADS, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Download progress"
            }
        )
    }

    /** Posts a "new upload" notification; tapping it opens the video. */
    fun notifyUpload(context: Context, channelName: String, video: MediaItemModel) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_VIDEO_ID, video.id)
            putExtra(EXTRA_VIDEO_TITLE, video.title)
            putExtra(EXTRA_VIDEO_CHANNEL, channelName)
        }
        val pending = PendingIntent.getActivity(
            context,
            video.id.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPLOADS)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(channelName)
            .setContentText(video.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(video.title))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()
        post(context, video.id.hashCode(), notification)
    }

    fun notifyBedtime(context: Context) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context, BEDTIME_NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time for bed")
            .setContentText("It's your bedtime. Consider wrapping up for tonight.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        post(context, BEDTIME_NOTIFICATION_ID, notification)
    }

    /** Ongoing notification with a progress bar for a running download. */
    fun downloadNotification(context: Context, title: String, progress: Float): Notification {
        ensureChannels(context)
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(title)
            .setProgress(100, percent, progress <= 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun post(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check and the call; nothing to do.
        }
    }
}
