package com.nexusstream.app.ui.youtube

import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.AddToQueue
import com.nexusstream.app.model.MediaContentType
import com.nexusstream.app.ui.LocalPlaybackService
import com.nexusstream.app.ui.ytmusic.SongMenuSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nexusstream.app.ui.components.OriginalColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.watchUrl
import com.nexusstream.app.media.PlaybackState
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.components.PlayerOptionsSheet

/**
 * The ⋮ menu for a video: Play next in queue, Add to queue, Watch later, Download, Share,
 * Go to channel, Not interested, Don't recommend channel. YT Music tracks get [SongMenuSheet].
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMenuSheet(
    item: MediaItemModel,
    onDismiss: () -> Unit,
    onOpenChannel: (() -> Unit)?,
    /** Extra "remove" action for lists (history, playlists...); null hides it. */
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove"
) {
    // YT Music tracks get the YT Music song menu.
    if (item.contentType == MediaContentType.MUSIC_TRACK) {
        SongMenuSheet(item = item, onDismiss = onDismiss, onRemove = onRemove, removeLabel = removeLabel)
        return
    }
    val context = LocalContext.current
    val service = LocalPlaybackService.current
    val saved by LibraryRepository.saved.collectAsState()
    val isSaved = saved.any { it.id == item.id }
    var showDownload by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }

    if (showPlaylists) {
        AddToPlaylistSheet(item = item, onDismiss = onDismiss)
        return
    }

    if (showDownload) {
        // Reuses the quality sheet with no playback state: only the Download section shows.
        PlayerOptionsSheet(
            item = item,
            state = PlaybackState(),
            service = null,
            showVideoQuality = false,
            onDismiss = onDismiss
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OriginalColors.Sheet,
        contentColor = OriginalColors.TextPrimary
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            MenuRow(Icons.AutoMirrored.Outlined.PlaylistPlay, "Play next in queue") {
                service?.playNext(listOf(item), asVideo = true)
                Toast.makeText(context, "Video will play next", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            MenuRow(Icons.Outlined.AddToQueue, "Add to queue") {
                service?.addToQueue(listOf(item), asVideo = true)
                Toast.makeText(context, "Video added to queue", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            MenuRow(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (isSaved) "Remove from Watch later" else "Save to Watch later"
            ) {
                LibraryRepository.toggleSave(item)
                onDismiss()
            }
            MenuRow(Icons.Outlined.PlaylistAdd, "Save to playlist") { showPlaylists = true }
            MenuRow(Icons.Outlined.FileDownload, "Download") { showDownload = true }
            MenuRow(Icons.Outlined.Share, "Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.watchUrl)
                }
                context.startActivity(Intent.createChooser(send, "Share"))
                onDismiss()
            }
            if (onOpenChannel != null) {
                MenuRow(Icons.Outlined.Person, "Go to channel") {
                    onDismiss()
                    onOpenChannel()
                }
            }
            if (onRemove != null) {
                MenuRow(Icons.Outlined.Delete, removeLabel) {
                    onRemove()
                    onDismiss()
                }
            }
            MenuRow(Icons.Outlined.VisibilityOff, "Not interested") {
                LibraryRepository.hideVideo(item.id)
                onDismiss()
            }
            MenuRow(Icons.Outlined.Block, "Don't recommend channel") {
                LibraryRepository.hideChannel(item.artistOrChannel)
                onDismiss()
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = OriginalColors.TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(20.dp))
        Text(text = label, fontSize = 16.sp, color = OriginalColors.TextPrimary)
    }
}
