package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.components.OriginalColors

/** "Save to...": Watch later and each of your playlists (tap to add or remove), plus New playlist. */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(item: MediaItemModel, onDismiss: () -> Unit) {
    val saved by LibraryRepository.saved.collectAsState()
    val playlists by LibraryRepository.playlists.collectAsState()
    var showNewDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { LibraryRepository.syncAccount() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OriginalColors.Sheet,
        contentColor = OriginalColors.TextPrimary
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = "Save to...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = OriginalColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn {
                item(key = "watch_later") {
                    SaveRow(
                        icon = Icons.Outlined.WatchLater,
                        label = "Watch later",
                        checked = saved.any { it.id == item.id },
                        onClick = { LibraryRepository.toggleSave(item) }
                    )
                }
                items(playlists, key = { "pl_${it.id}" }) { playlist ->
                    SaveRow(
                        icon = null,
                        label = "${playlist.name} (${playlist.items.size})",
                        checked = playlist.items.any { it.id == item.id },
                        onClick = { LibraryRepository.togglePlaylistItem(playlist.id, item) }
                    )
                }
                item(key = "new_playlist") {
                    SaveRow(
                        icon = Icons.Outlined.Add,
                        label = "New playlist",
                        checked = false,
                        onClick = { showNewDialog = true }
                    )
                }
            }
        }
    }

    if (showNewDialog) {
        TextInputDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                LibraryRepository.createPlaylist(name, listOf(item))
                showNewDialog = false
            },
            onDismiss = { showNewDialog = false }
        )
    }
}

@Composable
private fun SaveRow(icon: ImageVector?, label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = OriginalColors.TextPrimary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(20.dp))
        }
        Text(
            text = label,
            fontSize = 16.sp,
            color = OriginalColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (checked) {
            Icon(Icons.Rounded.Check, contentDescription = "Saved", tint = OriginalColors.TextPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

/** A small dialog with one text field (new playlist, rename). */
@Composable
internal fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OriginalColors.Sheet,
        title = { Text(title, color = OriginalColors.TextPrimary) },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Name", color = OriginalColors.TextSecondary) }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) {
                Text(confirmLabel, color = Color(0xFF3EA6FF))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = OriginalColors.TextSecondary) }
        }
    )
}
