package com.nexusstream.app.data

import android.content.Context
import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Status of an offline media stream download.
 */
sealed class DownloadState {
    object NotDownloaded : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data class Downloaded(val localPath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * Premium Offline Storage & Stream Cache Manager.
 * Enables local audio & video stream caching for offline listening without ads or network access.
 */
class OfflineStorageManager(private val context: Context) {

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val _downloadedItems = MutableStateFlow<List<MediaItemModel>>(emptyList())
    val downloadedItems: StateFlow<List<MediaItemModel>> = _downloadedItems.asStateFlow()

    fun isDownloaded(itemId: String): Boolean {
        return _downloadStates.value[itemId] is DownloadState.Downloaded
    }

    fun getDownloadState(itemId: String): DownloadState {
        return _downloadStates.value[itemId] ?: DownloadState.NotDownloaded
    }

    fun simulateDownload(item: MediaItemModel, onComplete: () -> Unit = {}) {
        val currentMap = _downloadStates.value.toMutableMap()
        currentMap[item.id] = DownloadState.Downloading(0.5f)
        _downloadStates.value = currentMap

        // Mark downloaded
        currentMap[item.id] = DownloadState.Downloaded("local_cache_${item.id}.mp4")
        _downloadStates.value = currentMap

        if (!_downloadedItems.value.any { it.id == item.id }) {
            _downloadedItems.value = _downloadedItems.value + item
        }

        onComplete()
    }

    fun removeDownload(itemId: String) {
        val currentMap = _downloadStates.value.toMutableMap()
        currentMap.remove(itemId)
        _downloadStates.value = currentMap
        _downloadedItems.value = _downloadedItems.value.filter { it.id != itemId }
    }
}
