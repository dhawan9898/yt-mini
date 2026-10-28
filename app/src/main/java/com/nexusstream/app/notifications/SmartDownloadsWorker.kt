package com.nexusstream.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nexusstream.app.data.AppSettings
import com.nexusstream.app.data.DownloadRepository
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.YTMusicPages
import com.nexusstream.app.data.YouTubeRepository

/**
 * Smart downloads: once a day on Wi-Fi, keeps the songs YT Music recommends (Home's quick picks
 * and mixes) offline. Songs from the previous run that are no longer recommended are removed,
 * unless they were liked.
 */
class SmartDownloadsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        AppSettings.init(context)
        LibraryRepository.init(context)
        DownloadRepository.init(context)
        val count = AppSettings.smartDownloads.value
        if (count <= 0) return Result.success()

        val home = YouTubeRepository.musicPage(YTMusicPages.HOME, refresh = true).getOrNull() ?: return Result.retry()
        val picks = home.songs.distinctBy { it.id }.take(count)
        if (picks.isEmpty()) return Result.success()

        val keep = picks.map { it.id }.toSet()
        val liked = LibraryRepository.liked.value.map { it.id }.toSet()
        AppSettings.smartDownloadIds.filter { it !in keep && it !in liked }.forEach { DownloadRepository.remove(it) }
        DownloadRepository.startAudioAll(context, picks, preferredKbps = 160)
        AppSettings.smartDownloadIds = keep
        return Result.success()
    }
}
