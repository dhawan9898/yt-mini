package com.nexusstream.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexusstream.app.data.AppSettings
import com.nexusstream.app.data.LibraryRepository

/** Posts the bedtime reminder, then schedules tomorrow's. */
class BedtimeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        LibraryRepository.init(context)
        AppSettings.init(context)
        val minute = LibraryRepository.bedtimeMinute.value
        if (minute < 0) return Result.success()

        NotificationHelper.notifyBedtime(context)
        BackgroundJobs.scheduleBedtime(context, minute)
        return Result.success()
    }
}
