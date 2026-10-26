package com.nexusstream.app.data

import android.content.Context
import android.net.Uri
import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Downloads streams into app-private storage and registers them in [LibraryRepository].
 * Finished items are stored with a file:// URL so they play offline through the normal player path.
 */
object DownloadRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    fun start(context: Context, item: MediaItemModel, option: DownloadOption) {
        val current = _states.value[item.id]
        if (current is DownloadState.Downloading) return
        val dir = File(context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.filesDir, "downloads")
            .apply { mkdirs() }
        setState(item.id, DownloadState.Downloading(0f))

        scope.launch {
            val result = runCatching {
                val file = File(dir, "${item.id}.${option.extension}")
                val request = okhttp3.Request.Builder()
                    .url(option.url)
                    .header("User-Agent", NewPipeDownloader.USER_AGENT)
                    .build()
                NewPipeDownloader.instance.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Empty response")
                    val total = body.contentLength()
                    var written = 0L
                    body.byteStream().use { input ->
                        file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) setState(item.id, DownloadState.Downloading(written.toFloat() / total))
                            }
                        }
                    }
                }
                file
            }
            result.onSuccess { file ->
                val uri = Uri.fromFile(file).toString()
                LibraryRepository.addDownload(item.copy(audioUrl = uri, videoUrl = uri, sourceUrl = ""))
                setState(item.id, DownloadState.Downloaded(file.absolutePath))
            }.onFailure {
                setState(item.id, DownloadState.Error(it.message ?: "Download failed"))
            }
        }
    }

    fun remove(id: String) {
        LibraryRepository.removeDownload(id)
        _states.value = _states.value - id
    }

    private fun setState(id: String, state: DownloadState) {
        _states.value = _states.value + (id to state)
    }
}
