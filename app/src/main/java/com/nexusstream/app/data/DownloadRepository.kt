package com.nexusstream.app.data

import android.content.Context
import android.net.Uri
import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

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
    val bytesDone: Long = 0L,
    val totalBytes: Long = 0L
)

/**
 * Downloads streams into app-private storage and registers them in [LibraryRepository].
 *
 * Unfinished downloads keep a ".part" file and are remembered across app restarts; they can be
 * paused, resumed (HTTP range request with a freshly resolved link) or cancelled. Finished items
 * are stored with a file:// URL so they play offline through the normal player path.
 * Downloads run while the app process is alive (there is no background service yet).
 */
object DownloadRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private var downloadsDir: File? = null

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val _pending = MutableStateFlow<List<PendingDownload>>(emptyList())
    val pending: StateFlow<List<PendingDownload>> = _pending.asStateFlow()

    /** Loads unfinished downloads (shown as paused). Call once after [LibraryRepository.init]. */
    fun init(context: Context) {
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
                    totalBytes = o.optLong("total")
                )
                pending.copy(bytesDone = partFile(dir, pending).let { if (it.exists()) it.length() else 0L })
            }
        }.getOrDefault(emptyList())
        _pending.value = loaded
        _states.value = _states.value + loaded.associate { it.item.id to DownloadState.Paused(progressOf(it)) }
    }

    fun start(context: Context, item: MediaItemModel, option: DownloadOption) {
        if (jobs[item.id]?.isActive == true) return
        val dir = dir(context)
        val pending = PendingDownload(item, option.label, option.extension, option.isVideo)
        partFile(dir, pending).delete() // a new download never continues an old partial file
        upsertPending(pending)
        setState(item.id, DownloadState.Downloading(0f))
        jobs[item.id] = scope.launch { runDownload(dir, pending, option.url) }
    }

    /** Stops a running download but keeps what has been downloaded so far. */
    fun pause(id: String) {
        val pending = _pending.value.firstOrNull { it.item.id == id } ?: return
        jobs.remove(id)?.cancel()
        val dir = downloadsDir
        val done = dir?.let { partFile(it, pending).let { f -> if (f.exists()) f.length() else 0L } } ?: pending.bytesDone
        upsertPending(pending.copy(bytesDone = done))
        setState(id, DownloadState.Paused(progressOf(pending.copy(bytesDone = done))))
    }

    /** Continues a paused or failed download from the partial file. */
    fun resume(context: Context, id: String) {
        val pending = _pending.value.firstOrNull { it.item.id == id } ?: return
        if (jobs[id]?.isActive == true) return
        val dir = dir(context)
        setState(id, DownloadState.Downloading(progressOf(pending)))
        jobs[id] = scope.launch {
            // Stream links expire, so look the same file up again to get a fresh link.
            val url = YouTubeRepository.downloadOptions(pending.item.watchUrl).getOrNull()?.let { options ->
                (options.firstOrNull { it.label == pending.label }
                    ?: options.firstOrNull { it.isVideo == pending.isVideo })?.url
            }
            if (url == null) {
                setState(id, DownloadState.Error("Couldn't refresh the download link. Check your connection."))
                return@launch
            }
            runDownload(dir, pending, url)
        }
    }

    /** Stops the download and deletes the partial file. */
    fun cancel(context: Context, id: String) {
        val pending = _pending.value.firstOrNull { it.item.id == id }
        jobs.remove(id)?.cancel()
        if (pending != null) partFile(dir(context), pending).delete()
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

    private suspend fun runDownload(dir: File, start: PendingDownload, url: String) {
        val id = start.item.id
        val part = partFile(dir, start)
        try {
            val existing = if (part.exists()) part.length() else 0L
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", NewPipeDownloader.USER_AGENT)
                .apply { if (existing > 0) header("Range", "bytes=$existing-") }
                .build()
            val call = NewPipeDownloader.instance.client.newCall(request)
            try {
                call.execute().use { response ->
                    // 416: the partial file already is the whole file.
                    if (response.code != 416) {
                        check(response.isSuccessful) { "HTTP ${response.code}" }
                        val resumed = response.code == 206
                        val body = response.body ?: error("Empty response")
                        val offset = if (resumed) existing else 0L
                        val remaining = body.contentLength()
                        val total = if (remaining > 0) remaining + offset else 0L
                        var written = offset
                        body.byteStream().use { input ->
                            FileOutputStream(part, resumed).use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    written += read
                                    if (total > 0 && currentCoroutineContext().isActive) {
                                        setState(id, DownloadState.Downloading(written.toFloat() / total))
                                    }
                                }
                            }
                        }
                        upsertPending(start.copy(bytesDone = written, totalBytes = total))
                    }
                }
            } finally {
                call.cancel()
            }
            finish(dir, _pending.value.firstOrNull { it.item.id == id } ?: start, part)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the partial file and pending entry so the user can resume.
            upsertPending(start.copy(bytesDone = if (part.exists()) part.length() else 0L))
            setState(id, DownloadState.Error(e.message ?: "Download failed"))
        }
    }

    private fun finish(dir: File, pending: PendingDownload, part: File) {
        val finalFile = File(dir, "${pending.item.id}.${pending.extension}")
        finalFile.delete()
        check(part.renameTo(finalFile)) { "Couldn't save the downloaded file" }
        val uri = Uri.fromFile(finalFile).toString()
        LibraryRepository.addDownload(pending.item.copy(audioUrl = uri, videoUrl = uri, sourceUrl = ""))
        removePending(pending.item.id)
        jobs.remove(pending.item.id)
        setState(pending.item.id, DownloadState.Downloaded(finalFile.absolutePath))
    }

    private fun dir(context: Context): File {
        downloadsDir?.let { return it }
        val base = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir
        return File(base, "downloads").apply { mkdirs() }.also { downloadsDir = it }
    }

    private fun partFile(dir: File, pending: PendingDownload) =
        File(dir, "${pending.item.id}.${pending.extension}.part")

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
                    .put("video", it.isVideo).put("total", it.totalBytes)
            }).toString()
        )
    }

    private fun setState(id: String, state: DownloadState) {
        _states.value = _states.value + (id to state)
    }
}
