package com.nexusstream.app.media

import android.net.Uri
import android.os.Looper
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * What Android Auto (and other media browsers) see: Liked, Downloads, History and playlists to
 * browse, plus voice / text search. Items carry only an id; playing one hands the list it came
 * from to the service's own queue, which resolves the streams.
 */
@UnstableApi
internal class AutoLibrary(
    private val service: MediaPlaybackService,
    private val scope: CoroutineScope
) : MediaLibrarySession.Callback {
    /** Every item served to a browser, by media id, and the list each one came from. */
    private val known = ConcurrentHashMap<String, MediaItemModel>()
    private val parentOf = ConcurrentHashMap<String, List<MediaItemModel>>()
    private val searchResults = ConcurrentHashMap<String, List<MediaItemModel>>()

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "yt-mini"), params))

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val item = known[mediaId]?.let(::playable) ?: folders().firstOrNull { it.mediaId == mediaId }
        return Futures.immediateFuture(item?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(ERROR_BAD_VALUE))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        LibraryRepository.init(service)
        val children: List<MediaItem> = when {
            parentId == ROOT -> folders()
            parentId == PLAYLISTS -> LibraryRepository.playlists.value.map { folder("$PLAYLIST_PREFIX${it.id}", it.name) }
            else -> serve(itemsFor(parentId))
        }
        val from = (page * pageSize).coerceAtMost(children.size)
        val to = (from + pageSize).coerceAtMost(children.size)
        return Futures.immediateFuture(LibraryResult.ofItemList(children.subList(from, to), params))
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        scope.launch {
            val results = YouTubeRepository.searchSongs(query).getOrDefault(emptyList())
            searchResults[query] = results
            session.notifySearchResultChanged(browser, query, results.size, params)
        }
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val all = serve(searchResults[query].orEmpty())
        val from = (page * pageSize).coerceAtMost(all.size)
        val to = (from + pageSize).coerceAtMost(all.size)
        return Futures.immediateFuture(LibraryResult.ofItemList(all.subList(from, to), params))
    }

    /**
     * Voice requests ("play X on yt-mini") arrive as items with only a search query: they become
     * the first search hits. Browsed items pass through; the session then sets them on the
     * wrapped player, which hands them to [play].
     */
    override fun onAddMediaItems(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        val query = mediaItems.firstOrNull()?.requestMetadata?.searchQuery
        if (query == null || mediaItems.first().mediaId.isNotEmpty()) return Futures.immediateFuture(mediaItems)
        val future = SettableFuture.create<MutableList<MediaItem>>()
        scope.launch {
            val results = YouTubeRepository.searchSongs(query).getOrDefault(emptyList()).take(25)
            future.set(serve(results).toMutableList())
        }
        return future
    }

    /** Plays [ids] with the service's queue: the whole list the first one was browsed from. */
    fun play(ids: List<String>, startIndex: Int): Boolean {
        if (ids.isEmpty()) return true
        val models = ids.mapNotNull { known[it] }
        if (models.isEmpty()) return false
        val first = models[startIndex.coerceIn(0, models.size - 1)]
        val list = if (models.size == 1) parentOf[first.id] ?: models else models
        val start = list.indexOfFirst { it.id == first.id }.coerceAtLeast(0)
        android.os.Handler(Looper.getMainLooper()).post { service.playQueue(list, start) }
        return true
    }

    /** The session's player: controller commands that set items we served go to [play]. */
    fun wrap(player: Player): Player = object : ForwardingPlayer(player) {
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
            if (!play(mediaItems.map { it.mediaId }, startIndex)) super.setMediaItems(mediaItems, startIndex, startPositionMs)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
            if (!play(mediaItems.map { it.mediaId }, 0)) super.setMediaItems(mediaItems, resetPosition)
        }

        override fun setMediaItem(mediaItem: MediaItem) {
            if (!play(listOf(mediaItem.mediaId), 0)) super.setMediaItem(mediaItem)
        }

        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
            if (!play(listOf(mediaItem.mediaId), 0)) super.setMediaItem(mediaItem, startPositionMs)
        }

        override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
            if (!play(mediaItems.map { it.mediaId }, 0)) super.addMediaItems(mediaItems)
        }
    }

    private fun itemsFor(parentId: String): List<MediaItemModel> = when {
        parentId == LIKED -> LibraryRepository.liked.value
        parentId == DOWNLOADS -> LibraryRepository.downloads.value
        parentId == HISTORY -> LibraryRepository.history.value.take(100)
        parentId == SAVED -> LibraryRepository.saved.value
        parentId.startsWith(PLAYLIST_PREFIX) -> LibraryRepository.playlists.value
            .firstOrNull { it.id == parentId.removePrefix(PLAYLIST_PREFIX) }?.items.orEmpty()
        else -> emptyList()
    }

    private fun serve(list: List<MediaItemModel>): List<MediaItem> = list.map { model ->
        known[model.id] = model
        parentOf[model.id] = list
        playable(model)
    }

    private fun folders(): List<MediaItem> = listOf(
        folder(LIKED, "Liked"),
        folder(DOWNLOADS, "Downloads"),
        folder(HISTORY, "History"),
        folder(SAVED, "Watch later"),
        folder(PLAYLISTS, "Playlists")
    )

    private fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    private fun playable(model: MediaItemModel): MediaItem = MediaItem.Builder()
        .setMediaId(model.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(model.title)
                .setArtist(model.artistOrChannel)
                .setArtworkUri(model.artworkUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
        )
        .build()

    companion object {
        const val ROOT = "root"
        const val LIKED = "liked"
        const val DOWNLOADS = "downloads"
        const val HISTORY = "history"
        const val SAVED = "saved"
        const val PLAYLISTS = "playlists"
        const val PLAYLIST_PREFIX = "pl:"
        /** Media3's error code for an unknown media id. */
        private const val ERROR_BAD_VALUE = -3
    }
}
