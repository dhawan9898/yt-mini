package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.DownloadRepository
import com.nexusstream.app.data.DownloadState
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.PendingDownload
import com.nexusstream.app.data.formatBytes
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.MiniPlayerHooks
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.PageScaffold

// ---------------------------------------------------------------------------------------------
// Watch later / Liked / History
// ---------------------------------------------------------------------------------------------

@Composable
fun WatchLaterScreen(
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    val saved by LibraryRepository.saved.collectAsState()
    PageScaffold(title = "Watch later", onBack = nav.back, miniPlayer = miniPlayer) {
        VideoListBody(
            items = saved,
            nav = nav,
            onSelectVideo = onSelectVideo,
            emptyTitle = "Nothing saved",
            emptyMessage = "Videos you save for later will be listed here.",
            removeLabel = "Remove from Watch later",
            onRemove = { LibraryRepository.toggleSave(it) }
        )
    }
}

@Composable
fun LikedVideosScreen(
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    val liked by LibraryRepository.liked.collectAsState()
    PageScaffold(title = "Liked videos", onBack = nav.back, miniPlayer = miniPlayer) {
        VideoListBody(
            items = liked,
            nav = nav,
            onSelectVideo = onSelectVideo,
            emptyTitle = "No liked videos",
            emptyMessage = "Videos you like will be listed here.",
            removeLabel = "Remove from liked videos",
            onRemove = { LibraryRepository.toggleLike(it) }
        )
    }
}

/** Watch history: search, remove single entries, pause recording, clear everything. */
@Composable
fun HistoryScreen(
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    val history by LibraryRepository.history.collectAsState()
    val paused by LibraryRepository.historyPaused.collectAsState()
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val shown = remember(history, query) {
        val q = query.trim()
        if (q.isEmpty()) history
        else history.filter { it.title.contains(q, ignoreCase = true) || it.artistOrChannel.contains(q, ignoreCase = true) }
    }

    PageScaffold(
        title = "Watch history",
        onBack = nav.back,
        miniPlayer = miniPlayer,
        actions = {
            IconButton(onClick = { LibraryRepository.setHistoryPaused(!paused) }) {
                Icon(
                    imageVector = if (paused) Icons.Outlined.PlayCircle else Icons.Outlined.PauseCircle,
                    contentDescription = if (paused) "Turn on watch history" else "Pause watch history",
                    tint = Color.White
                )
            }
            IconButton(onClick = { confirmClear = true }, enabled = history.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteSweep, "Clear watch history", tint = Color.White)
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(OriginalColors.Chip)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text("Search watch history", fontSize = 15.sp, color = OriginalColors.TextSecondary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (paused) {
                Text(
                    text = "Watch history is paused. New videos won't be added.",
                    fontSize = 13.sp,
                    color = OriginalColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            VideoListBody(
                items = shown,
                nav = nav,
                onSelectVideo = onSelectVideo,
                emptyTitle = if (query.isBlank()) "No watch history" else "No matches",
                emptyMessage = if (query.isBlank()) "Videos you watch will show up here." else "Try a different search.",
                removeLabel = "Remove from watch history",
                onRemove = { LibraryRepository.removeFromHistory(it.id) }
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear watch history?",
            message = "This removes all ${history.size} videos from your history on this device.",
            confirmLabel = "Clear",
            onConfirm = {
                LibraryRepository.clearHistory()
                confirmClear = false
            },
            onDismiss = { confirmClear = false }
        )
    }
}

/** Header (Play all / Shuffle) plus a compact row per video. */
@Composable
private fun VideoListBody(
    items: List<MediaItemModel>,
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    emptyTitle: String,
    emptyMessage: String,
    removeLabel: String,
    onRemove: (MediaItemModel) -> Unit
) {
    if (items.isEmpty()) {
        OriginalEmptyState(emptyTitle, emptyMessage)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = 12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "play_buttons") {
            PlayButtons(
                onPlayAll = { onSelectVideo(items.first(), items) },
                onShuffle = {
                    val shuffled = items.shuffled()
                    onSelectVideo(shuffled.first(), shuffled)
                }
            )
        }
        items(items, key = { "vl_${it.id}" }) { video ->
            CompactVideoRow(
                item = video,
                onClick = { onSelectVideo(video, items) },
                onChannelClick = video.channelUrl.takeIf { it.isNotBlank() }?.let { { nav.openChannelOf(video) } },
                onRemove = { onRemove(video) },
                removeLabel = removeLabel
            )
        }
    }
}

@Composable
private fun PlayButtons(onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PillButton("Play all", Icons.Rounded.PlayArrow, filled = true, onClick = onPlayAll, modifier = Modifier.weight(1f))
        PillButton("Shuffle", Icons.Rounded.Shuffle, filled = false, onClick = onShuffle, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------------------------
// Your playlists
// ---------------------------------------------------------------------------------------------

@Composable
fun PlaylistsScreen(nav: YouTubeNav, miniPlayer: MiniPlayerHooks) {
    val playlists by LibraryRepository.playlists.collectAsState()
    var showNew by remember { mutableStateOf(false) }

    PageScaffold(
        title = "Playlists",
        onBack = nav.back,
        miniPlayer = miniPlayer,
        actions = {
            IconButton(onClick = { showNew = true }) {
                Icon(Icons.Outlined.Add, "New playlist", tint = Color.White)
            }
        }
    ) {
        if (playlists.isEmpty()) {
            OriginalEmptyState("No playlists", "Tap + to create one, or use Save on any video.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { nav.open(Route.LocalPlaylist(playlist.id)) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OriginalColors.Chip),
                            contentAlignment = Alignment.Center
                        ) {
                            val cover = playlist.items.firstOrNull()?.artworkUrl.orEmpty()
                            if (cover.isNotEmpty()) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = playlist.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Outlined.PlaylistPlay, null, tint = OriginalColors.TextSecondary, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(playlist.name, fontSize = 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.items.size} videos", fontSize = 13.sp, color = OriginalColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        TextInputDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onConfirm = {
                LibraryRepository.createPlaylist(it)
                showNew = false
            },
            onDismiss = { showNew = false }
        )
    }
}

/** One of your playlists: play, shuffle, reorder (up / down), remove, rename, delete. */
@Composable
fun LocalPlaylistScreen(
    route: Route.LocalPlaylist,
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    val playlists by LibraryRepository.playlists.collectAsState()
    val playlist = playlists.firstOrNull { it.id == route.id }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    // The playlist was deleted: leave the page.
    LaunchedEffect(playlist == null) { if (playlist == null) nav.back() }
    if (playlist == null) return

    PageScaffold(
        title = playlist.name,
        onBack = nav.back,
        miniPlayer = miniPlayer,
        actions = {
            IconButton(onClick = { showRename = true }) { Icon(Icons.Outlined.Edit, "Rename", tint = Color.White) }
            IconButton(onClick = { showDelete = true }) { Icon(Icons.Outlined.Delete, "Delete playlist", tint = Color.White) }
        }
    ) {
        if (playlist.items.isEmpty()) {
            OriginalEmptyState("Empty playlist", "Add videos with Save on any video's page or menu.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "play_buttons") {
                    PlayButtons(
                        onPlayAll = { onSelectVideo(playlist.items.first(), playlist.items) },
                        onShuffle = {
                            val shuffled = playlist.items.shuffled()
                            onSelectVideo(shuffled.first(), shuffled)
                        }
                    )
                }
                itemsIndexed(playlist.items, key = { _, video -> "lp_${video.id}" }) { index, video ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { LibraryRepository.movePlaylistItem(playlist.id, index, index - 1) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp, "Move up",
                                    tint = if (index > 0) Color.White else OriginalColors.Divider
                                )
                            }
                            IconButton(
                                onClick = { LibraryRepository.movePlaylistItem(playlist.id, index, index + 1) },
                                enabled = index < playlist.items.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowDown, "Move down",
                                    tint = if (index < playlist.items.lastIndex) Color.White else OriginalColors.Divider
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CompactVideoRow(
                                item = video,
                                onClick = { onSelectVideo(video, playlist.items) },
                                onChannelClick = video.channelUrl.takeIf { it.isNotBlank() }?.let { { nav.openChannelOf(video) } },
                                onRemove = { LibraryRepository.removeFromPlaylist(playlist.id, video.id) },
                                removeLabel = "Remove from playlist"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        TextInputDialog(
            title = "Rename playlist",
            initial = playlist.name,
            confirmLabel = "Save",
            onConfirm = {
                LibraryRepository.renamePlaylist(playlist.id, it)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "Delete playlist?",
            message = "\"${playlist.name}\" will be removed. The videos themselves are not deleted.",
            confirmLabel = "Delete",
            onConfirm = {
                showDelete = false
                LibraryRepository.deletePlaylist(playlist.id)
            },
            onDismiss = { showDelete = false }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Downloads
// ---------------------------------------------------------------------------------------------

/** Downloads manager: progress with pause / resume / cancel, finished files, storage used. */
@Composable
fun DownloadsScreen(
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    val context = LocalContext.current
    val downloads by LibraryRepository.downloads.collectAsState()
    val pending by DownloadRepository.pending.collectAsState()
    val states by DownloadRepository.states.collectAsState()
    val usedBytes = remember(downloads, pending) { DownloadRepository.totalBytesOnDisk(context) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    PageScaffold(
        title = "Downloads",
        onBack = nav.back,
        miniPlayer = miniPlayer,
        actions = {
            IconButton(onClick = { confirmDeleteAll = true }, enabled = downloads.isNotEmpty() || pending.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteSweep, "Delete all downloads", tint = Color.White)
            }
        }
    ) {
        if (downloads.isEmpty() && pending.isEmpty()) {
            OriginalEmptyState("No downloads", "Download a video from its menu to watch it offline.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 12.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "storage") {
                    Text(
                        text = "${formatBytes(usedBytes)} used on this device",
                        fontSize = 13.sp,
                        color = OriginalColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                if (pending.isNotEmpty()) {
                    item(key = "h_progress") { SectionHeader("In progress") }
                    items(pending, key = { "pd_${it.item.id}" }) { download ->
                        PendingRow(download, states[download.item.id], context)
                    }
                }
                if (downloads.isNotEmpty()) {
                    item(key = "h_done") { SectionHeader("Downloaded") }
                    items(downloads, key = { "dl_${it.id}" }) { video ->
                        CompactVideoRow(
                            item = video,
                            onClick = { onSelectVideo(video, downloads) },
                            onRemove = { DownloadRepository.remove(video.id) },
                            removeLabel = "Delete download"
                        )
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        ConfirmDialog(
            title = "Delete all downloads?",
            message = "Finished and unfinished downloads will be removed from this device.",
            confirmLabel = "Delete all",
            onConfirm = {
                pending.forEach { DownloadRepository.cancel(context, it.item.id) }
                downloads.forEach { DownloadRepository.remove(it.id) }
                confirmDeleteAll = false
            },
            onDismiss = { confirmDeleteAll = false }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun PendingRow(download: PendingDownload, state: DownloadState?, context: android.content.Context) {
    val running = state is DownloadState.Downloading
    val progress = when (state) {
        is DownloadState.Downloading -> state.progress
        is DownloadState.Paused -> state.progress
        else -> 0f
    }
    val status = when (state) {
        is DownloadState.Downloading -> "Downloading • ${(progress * 100).toInt()}%"
        is DownloadState.Paused -> "Paused • ${(progress * 100).toInt()}%"
        is DownloadState.Error -> "Failed: ${state.message}"
        else -> "Waiting"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(OriginalColors.Chip)
        ) {
            if (download.item.artworkUrl.isNotEmpty()) {
                AsyncImage(
                    model = download.item.artworkUrl,
                    contentDescription = download.item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(download.item.title, fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = Color.White,
                trackColor = OriginalColors.Chip,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${download.label} • $status",
                fontSize = 12.sp,
                color = OriginalColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = {
            if (running) DownloadRepository.pause(download.item.id) else DownloadRepository.resume(context, download.item.id)
        }) {
            Icon(
                imageVector = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (running) "Pause" else "Resume",
                tint = Color.White
            )
        }
        IconButton(onClick = { DownloadRepository.cancel(context, download.item.id) }) {
            Icon(Icons.Rounded.Close, "Cancel download", tint = Color.White)
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF212121),
        title = { Text(title, color = Color.White) },
        text = { Text(message, color = OriginalColors.TextSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = Color(0xFF3EA6FF)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = OriginalColors.TextSecondary) } }
    )
}
