package com.nexusstream.app.ui.youtube

import android.content.Intent
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
import androidx.compose.material.icons.outlined.FileDownload
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
 * The ⋮ menu for a video: Watch later, Download, Share, Go to channel, Not interested,
 * Don't recommend channel.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoMenuSheet(
    item: MediaItemModel,
    onDismiss: () -> Unit,
    onOpenChannel: (() -> Unit)?
) {
    val context = LocalContext.current
    val saved by LibraryRepository.saved.collectAsState()
    val isSaved = saved.any { it.id == item.id }
    var showDownload by remember { mutableStateOf(false) }

    if (showDownload) {
        // Reuses the quality sheet with no playback state: only the Download section shows.
        PlayerOptionsSheet(
            item = item,
            state = PlaybackState(),
            showVideoQuality = false,
            onVideoQuality = {},
            onAudioQuality = {},
            onDismiss = onDismiss
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF212121),
        contentColor = Color.White
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            MenuRow(
                icon = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                label = if (isSaved) "Remove from Watch later" else "Save to Watch later"
            ) {
                LibraryRepository.toggleSave(item)
                onDismiss()
            }
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
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(20.dp))
        Text(text = label, fontSize = 16.sp, color = Color.White)
    }
}
