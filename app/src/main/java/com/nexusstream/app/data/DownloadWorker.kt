package com.nexusstream.app.data

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.nexusstream.app.notifications.NotificationHelper

/**
 * Runs one download in the background with a progress notification. Created by
 * [DownloadRepository]; cancelling the work (pause / cancel) stops the transfer.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val context = applicationContext
        LibraryRepository.init(context)
        DownloadRepository.init(context)

        val pending = DownloadRepository.pendingFor(id) ?: return Result.success()
        showProgress(pending, 0f)

        val url = DownloadRepository.freshUrl(pending)
        if (url == null) {
            DownloadRepository.fail(id, "Couldn't refresh the download link. Check your connection.")
            return Result.failure()
        }

        val finished = DownloadRepository.runDownload(
            dir = DownloadRepository.downloadsDir(context),
            start = pending,
            url = url,
            onProgress = { progress -> showProgress(pending, progress) }
        )
        return if (finished) Result.success() else Result.failure()
    }

    /** Updates the foreground notification; ignored if the system does not allow it right now. */
    private suspend fun showProgress(pending: PendingDownload, progress: Float) {
        val notificationId = NotificationHelper.DOWNLOAD_NOTIFICATION_ID + (pending.item.id.hashCode() and 0xFFFF)
        val notification = NotificationHelper.downloadNotification(applicationContext, pending.item.title, progress)
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
        try {
            setForeground(info)
        } catch (_: Exception) {
            // Starting a foreground service from the background can be refused (Android 12+).
            // The download itself continues; it just has no progress notification.
        }
    }

    companion object {
        const val KEY_ID = "download_id"
    }
}
