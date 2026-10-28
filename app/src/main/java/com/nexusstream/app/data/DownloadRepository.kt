package com.nexusstream.app.data

import android.content.Context
import android.net.Uri
import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/** Status of an offline download. */
sealed class DownloadState {
    object NotDownloaded : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    /** Stopped by the user or by an app restart; resumes from the partial file. */
    data class Paused(val progress: Float) : DownloadState()
    data class Downloaded(val localPath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/** A download that has not finished: running, paused, or failed (all resumable). */
data class PendingDownload(
    val item: MediaItemModel,
    /** Quality label such as "720p video"; used to find the same file again when resuming. */
    val label: String,
    val extension: String,
    val isVideo: Boolean,
    /** Video-only + audio-only streams downloaded separately and merged at the end. */
    val merged: Boolean = false,
    val bytesDone: Long = 0L,
    val totalBytes: Long = 0L
)

/**
 * Downloads streams into app-private storage and registers them in [LibraryRepository].
 *
 * Unfinished downloads keep a ".part" file and are remembered across app restarts; they can be
 * paused, resumed (HTTP range request with a freshly resolved link) or cancelled. Finished items
 * are stored with a file:// URL so they play offline through the normal player path.
 * The transfer itself runs in a WorkManager worker ([DownloadWorker]) with a foreground progress
 * notification, so downloads continue when the app is closed.
 */
object DownloadRepository {
    private var downloadsDir: File? = null
    private var appContext: Context? = null
    private var initialized = false

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingDownload>>(emptyList())
    val pending: StateFlow<List<PendingDownload>> = _pending.asStateFlow()

    /** Loads unfinished downloads (shown as paused). Call once after [LibraryRepository.init]. */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        val dir = dir(context)
        val items = LibraryRepository.decodeItems(LibraryRepository.loadBlob("pending_items")).associateBy { it.id }
        val loaded = runCatching {
            val arr = JSONArray(LibraryRepository.loadBlob("pending_meta") ?: "[]")
            (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { o ->
                val item = items[o.getString("id")] ?: return@mapNotNull null
                val pending = PendingDownload(
                    item = item,
                    label = o.optString("label"),
                    extension = o.optString("ext", "mp4"),
                    isVideo = o.optBoolean("video"),
                    merged = o.optBoolean("merged"),
                    totalBytes = o.optLong("total")
                )
                pending.copy(bytesDone = bytesOnDisk(dir, pending))
            }
        }.getOrDefault(emptyList())
        _pending.value = loaded
        _states.value = _states.value + loaded.associate { it.item.id to DownloadState.Paused(progressOf(it)) }
    }

    fun start(context: Context, item: MediaItemModel, option: DownloadOption) {
        appContext = context.applicationContext
        val dir = dir(context)
        val pending = PendingDownload(item, option.label, option.extension, option.isVideo, merged = option.audioUrl != null)
        partFiles(dir, pending).forEach { it.delete() } // a new download never continues an old partial file
        upsertPending(pending)
        setState(item.id, DownloadState.Downloading(0f))
        enqueue(context, item.id)
    }

    /**
     * Downloads every song of an album or playlist as audio (best bitrate), skipping songs that are
     * already downloaded or downloading. Returns how many downloads were started.
     */
    suspend fun startAudioAll(context: Context, items: List<MediaItemModel>, preferredKbps: Int = Int.MAX_VALUE): Int {
        val have = LibraryRepository.downloads.value.map { it.id }.toSet() + _pending.value.map { it.item.id }
        var started = 0
        for (item in items.distinctBy { it.id }) {
            if (item.id in have) continue
            val audio = YouTubeRepository.downloadOptions(item.watchUrl).getOrNull()?.filter { !it.isVideo } ?: continue
            val option = audio.firstOrNull { (it.label.substringBefore(' ').toIntOrNull() ?: 0) <= preferredKbps } ?: audio.lastOrNull() ?: continue
            start(context, item, option)
            started++
        }
        return started
    }

    /** Stops a running download but keeps what has been downloaded so far. */
    fun pause(id: String) {
        val pending = _pending.value.firstOrNull { it.item.id == id } ?: return
        appContext?.let { WorkManager.getInstance(it).cancelUniqueWork(workName(id)) }
        val dir = downloadsDir
        val done = dir?.let { bytesOnDisk(it, pending) } ?: pending.bytesDone
        upsertPending(pending.copy(bytesDone = done))
        setState(id, DownloadState.Paused(progressOf(pending.copy(bytesDone = done))))
    }

    /** Continues a paused or failed download from the partial file. */
    fun resume(context: Context, id: String) {
        val pending = _pending.value.firstOrNull { it.item.id == id } ?: return
        appContext = context.applicationContext
        setState(id, DownloadState.Downloading(progressOf(pending)))
        enqueue(context, id)
    }

    /** Stops the download and deletes the partial file. */
    fun cancel(context: Context, id: String) {
        val pending = _pending.value.firstOrNull { it.item.id == id }
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(id))
        if (pending != null) partFiles(dir(context), pending).forEach { it.delete() }
        removePending(id)
        _states.value = _states.value - id
    }

    /** Deletes a finished download (file and library entry). */
    fun remove(id: String) {
        LibraryRepository.removeDownload(id)
        _states.value = _states.value - id
    }

    /** Bytes used on disk by finished downloads and partial files. */
    fun totalBytesOnDisk(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    // ---- internals ----

    internal fun pendingFor(id: String): PendingDownload? = _pending.value.firstOrNull { it.item.id == id }

    internal fun downloadsDir(context: Context): File = dir(context)

    internal fun fail(id: String, message: String) = setState(id, DownloadState.Error(message))

    /**
     * Stream links expire, so look the same file up again to get fresh links. A fallback must keep
     * the same container and kind (single file or merged) so partial files stay usable.
     */
    internal suspend fun freshOption(pending: PendingDownload): DownloadOption? =
        YouTubeRepository.downloadOptions(pending.item.watchUrl).getOrNull()?.let { options ->
            options.firstOrNull { it.label == pending.label && it.extension == pending.extension }
                ?: options.firstOrNull {
                    it.isVideo == pending.isVideo && it.extension == pending.extension &&
                        (it.audioUrl != null) == pending.merged
                }
        }

    private fun workName(id: String) = "download_$id"

    private fun enqueue(context: Context, id: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_ID to id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (AppSettings.wifiOnlyDownloads.value) NetworkType.UNMETERED else NetworkType.CONNECTED
                    )
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Transfers [option] into the partial file(s), continuing them if they exist, and finishes the
     * download. Merged options download the video and audio streams one after the other and then
     * mux them into one file. Returns true when the download completed. Cancellation
     * (pause / cancel) is rethrown; other failures keep the partial files and mark the download as
     * failed so it can be resumed.
     */
    internal suspend fun runDownload(
        dir: File,
        start: PendingDownload,
        option: DownloadOption,
        onProgress: suspend (Float) -> Unit = {}
    ): Boolean {
        val id = start.item.id
        var lastReport = 0L
        val report: suspend (Float) -> Unit = { progress ->
            if (currentCoroutineContext().isActive) {
                setState(id, DownloadState.Downloading(progress))
                val now = System.currentTimeMillis()
                if (now - lastReport > 1000) {
                    lastReport = now
                    onProgress(progress)
                }
            }
        }
        try {
            val audioUrl = option.audioUrl
            if (!start.merged || audioUrl == null) {
                val part = partFile(dir, start)
                val total = transfer(part, option.url) { done, size ->
                    if (size > 0) report(done.toFloat() / size)
                }
                upsertPending(start.copy(bytesDone = part.length(), totalBytes = total))
                finish(dir, _pending.value.firstOrNull { it.item.id == id } ?: start, part)
                return true
            }

            val videoPart = videoPartFile(dir, start)
            val audioPart = audioPartFile(dir, start)
            // Progress covers both transfers; the clen URL parameter gives sizes up front.
            val videoHint = contentLengthHint(option.url)
            val audioHint = contentLengthHint(audioUrl)
            fun overall(videoDone: Long, videoTotal: Long, audioDone: Long, audioTotal: Long): Float {
                val fraction = if (videoTotal > 0 && audioTotal > 0) {
                    (videoDone + audioDone).toFloat() / (videoTotal + audioTotal)
                } else {
                    val v = if (videoTotal > 0) videoDone.toFloat() / videoTotal else 0f
                    val a = if (audioTotal > 0) audioDone.toFloat() / audioTotal else 0f
                    0.9f * v + 0.1f * a
                }
                return fraction.coerceIn(0f, 1f) * 0.99f // the last percent is the merge
            }

            val videoTotal = transfer(videoPart, option.url) { done, total ->
                report(overall(done, total, 0L, audioHint))
            }
            upsertPending(start.copy(bytesDone = bytesOnDisk(dir, start), totalBytes = videoTotal + audioHint))
            val audioTotal = transfer(audioPart, audioUrl) { done, total ->
                report(overall(videoTotal, videoTotal, done, total))
            }
            upsertPending(start.copy(bytesDone = bytesOnDisk(dir, start), totalBytes = videoTotal + audioTotal))
            report(0.99f)

            val muxed = File(dir, "${start.item.id}.${start.extension}.mux")
            StreamMuxer.merge(videoPart, audioPart, muxed, webm = start.extension == "webm")
            finish(dir, _pending.value.firstOrNull { it.item.id == id } ?: start, muxed)
            videoPart.delete()
            audioPart.delete()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the partial files and pending entry so the user can resume.
            upsertPending(start.copy(bytesDone = bytesOnDisk(dir, start)))
            setState(id, DownloadState.Error(e.message ?: "Download failed"))
            return false
        }
    }

    /**
     * Appends [url] to [part] in [CHUNK_BYTES] range requests until the file is complete and
     * returns its total size (0 if the server never said). YouTube throttles long single
     * requests on its adaptive (video-only / audio-only) streams, so chunking keeps them fast.
     */
    private suspend fun transfer(part: File, url: String, onBytes: suspend (done: Long, total: Long) -> Unit): Long {
        var total = contentLengthHint(url)
        while (true) {
            currentCoroutineContext().ensureActive()
            val existing = if (part.exists()) part.length() else 0L
            if (total > 0 && existing >= total) return total
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", NewPipeDownloader.USER_AGENT)
                .header("Range", "bytes=$existing-${existing + CHUNK_BYTES - 1}")
                .build()
            val call = NewPipeDownloader.instance.client.newCall(request)
            try {
                call.execute().use { response ->
                    // 416: the partial file already is the whole file.
                    if (response.code == 416) return if (total > 0) total else existing
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val resumed = response.code == 206
                    val body = response.body ?: error("Empty response")
                    if (resumed) {
                        response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()?.let { total = it }
                    } else {
                        // The server ignored the range and sent the whole file.
                        total = body.contentLength().coerceAtLeast(0L)
                    }
                    var written = if (resumed) existing else 0L
                    var chunkBytes = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(part, resumed).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                chunkBytes += read
                                onBytes(written, total)
                            }
                        }
                    }
                    if (!resumed) return written
                    // Unknown total: a short chunk means the end of the file.
                    if (total <= 0 && chunkBytes < CHUNK_BYTES) return written
                    if (chunkBytes == 0L) return written
                }
            } finally {
                call.cancel()
            }
        }
    }

    /** YouTube stream links carry the file size as the clen parameter. */
    private fun contentLengthHint(url: String): Long =
        Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    private const val CHUNK_BYTES = 10L * 1024 * 1024

    private fun finish(dir: File, pending: PendingDownload, completed: File) {
        val finalFile = File(dir, "${pending.item.id}.${pending.extension}")
        finalFile.delete()
        check(completed.renameTo(finalFile)) { "Couldn't save the downloaded file" }
        val uri = Uri.fromFile(finalFile).toString()
        LibraryRepository.addDownload(pending.item.copy(audioUrl = uri, videoUrl = uri, sourceUrl = ""))
        removePending(pending.item.id)
        setState(pending.item.id, DownloadState.Downloaded(finalFile.absolutePath))
    }

    private fun dir(context: Context): File {
        downloadsDir?.let { return it }
        val base = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
        return File(base, "downloads").apply { mkdirs() }.also { downloadsDir = it }
    }

    private fun partFile(dir: File, pending: PendingDownload) =
        File(dir, "${pending.item.id}.${pending.extension}.part")

    private fun videoPartFile(dir: File, pending: PendingDownload) =
        File(dir, "${pending.item.id}.${pending.extension}.video.part")

    private fun audioPartFile(dir: File, pending: PendingDownload) =
        File(dir, "${pending.item.id}.${pending.extension}.audio.part")

    private fun partFiles(dir: File, pending: PendingDownload) = listOf(
        partFile(dir, pending),
        videoPartFile(dir, pending),
        audioPartFile(dir, pending),
        File(dir, "${pending.item.id}.${pending.extension}.mux")
    )

    private fun bytesOnDisk(dir: File, pending: PendingDownload): Long =
        partFiles(dir, pending).sumOf { if (it.exists()) it.length() else 0L }

    private fun progressOf(p: PendingDownload): Float =
        if (p.totalBytes > 0) (p.bytesDone.toFloat() / p.totalBytes).coerceIn(0f, 1f) else 0f

    private fun upsertPending(pending: PendingDownload) {
        _pending.value = _pending.value.filter { it.item.id != pending.item.id } + pending
        persistPending()
    }

    private fun removePending(id: String) {
        _pending.value = _pending.value.filter { it.item.id != id }
        persistPending()
    }

    private fun persistPending() {
        val list = _pending.value
        LibraryRepository.saveBlob("pending_items", LibraryRepository.encodeItems(list.map { it.item }))
        LibraryRepository.saveBlob(
            "pending_meta",
            JSONArray(list.map {
                JSONObject().put("id", it.item.id).put("label", it.label).put("ext", it.extension)
                    .put("video", it.isVideo).put("merged", it.merged).put("total", it.totalBytes)
            }).toString()
        )
    }

    private fun setState(id: String, state: DownloadState) {
        _states.value = _states.value + (id to state)
    }
}
