package com.nexusstream.app.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules the app's background work (upload checks, bedtime reminder). */
object BackgroundJobs {
    private const val UPLOAD_WORK = "upload_check"
    private const val UPLOAD_NOW_WORK = "upload_check_now"
    private const val BEDTIME_WORK = "bedtime_reminder"
    private const val SMART_WORK = "smart_downloads"

    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun scheduleUploadChecks(context: Context) {
        val request = PeriodicWorkRequestBuilder<UploadCheckWorker>(30, TimeUnit.MINUTES)
            .setConstraints(needsNetwork)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UPLOAD_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelUploadChecks(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UPLOAD_WORK)
    }

    /** Runs one upload check right away (the "Check now" button). */
    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UploadCheckWorker>().setConstraints(needsNetwork).build()
        WorkManager.getInstance(context).enqueueUniqueWork(UPLOAD_NOW_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    /** Schedules the next bedtime reminder at [minuteOfDay] (minutes after midnight). */
    fun scheduleBedtime(context: Context, minuteOfDay: Int) {
        val delay = Bedtime.delayUntilMs(System.currentTimeMillis(), minuteOfDay)
        val request = OneTimeWorkRequestBuilder<BedtimeWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(BEDTIME_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelBedtime(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(BEDTIME_WORK)
    }

    /** Smart downloads run once a day on an unmetered network with enough battery. */
    fun scheduleSmartDownloads(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<SmartDownloadsWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(SMART_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancelSmartDownloads(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SMART_WORK)
    }
}
