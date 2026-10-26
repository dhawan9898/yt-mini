package com.nexusstream.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.data.DownloadOption
import com.nexusstream.app.data.DownloadRepository
import com.nexusstream.app.data.DownloadState
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.data.watchUrl
import com.nexusstream.app.media.PlaybackState
import com.nexusstream.app.model.MediaItemModel

/**
 * Playback quality (video + audio) and download quality for the current item.
 * Quality rows only appear once the stream has been resolved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsSheet(
    item: MediaItemModel,
    state: PlaybackState,
    showVideoQuality: Boolean,
    onVideoQuality: (Int?) -> Unit,
    onAudioQuality: (Int?) -> Unit,
    onDismiss: () -> Unit,
    onAudioTrack: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val downloadStates by DownloadRepository.states.collectAsState()
    val offline by LibraryRepository.downloads.collectAsState()
    val isDownloaded = offline.any { it.id == item.id }
    val downloadState = downloadStates[item.id]

    // Offline copies have no stream to resolve, so there is nothing to list for them.
    val downloadOptions by produceState<FeedState<List<DownloadOption>>>(FeedState.Loading, item.id) {
        value = if (item.sourceUrl.isEmpty() && isDownloaded) {
            FeedState.Data(emptyList())
        } else {
            YouTubeRepository.downloadOptions(item.watchUrl).toFeedState()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF212121),
        contentColor = Color.White
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(0.85f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (showVideoQuality && state.availableVideoHeights.isNotEmpty()) {
                item { SectionTitle("Video quality") }
                item {
                    OptionRow(
                        label = "Auto (up to ${com.nexusstream.app.data.ResolvedStream.DEFAULT_MAX_HEIGHT}p)",
                        selected = state.videoQualityAuto,
                        onClick = { onVideoQuality(null) }
                    )
                }
                state.availableVideoHeights.forEach { height ->
                    item(key = "v_$height") {
                        OptionRow(
                            label = "${height}p",
                            selected = !state.videoQualityAuto && state.selectedVideoHeight == height,
                            onClick = { onVideoQuality(height) }
                        )
                    }
                }
            }

            // Several tracks = original plus auto-dubbed / other languages. Original is the default.
            if (state.availableAudioTracks.size > 1) {
                item { SectionTitle("Audio language") }
                state.availableAudioTracks.forEachIndexed { index, track ->
                    item(key = "t_$index") {
                        val label = track.name.ifBlank { "Audio" } +
                            if (track.isOriginal && !track.name.contains("original", ignoreCase = true)) " (original)" else ""
                        OptionRow(
                            label = label,
                            selected = state.selectedAudioTrackId == track.id,
                            // Choosing the original track resets to the default instead of pinning it.
                            onClick = { onAudioTrack(if (track.isOriginal) null else track.id) }
                        )
                    }
                }
            }

            if (state.availableAudioKbps.isNotEmpty()) {
                item { SectionTitle("Audio quality") }
                item {
                    OptionRow(
                        label = "Best available",
                        selected = state.audioQualityAuto,
                        onClick = { onAudioQuality(null) }
                    )
                }
                state.availableAudioKbps.forEach { kbps ->
                    item(key = "a_$kbps") {
                        OptionRow(
                            label = "$kbps kbps",
                            selected = !state.audioQualityAuto && state.selectedAudioKbps == kbps,
                            onClick = { onAudioQuality(kbps) }
                        )
                    }
                }
            } else if (state.isResolving) {
                item { SectionTitle("Quality") }
                item { Hint("Loading available qualities...") }
            }

            item { SectionTitle("Download") }
            when {
                isDownloaded -> item {
                    OptionRow(
                        label = "Downloaded - tap to remove",
                        selected = true,
                        onClick = {
                            DownloadRepository.remove(item.id)
                            onDismiss()
                        }
                    )
                }
                downloadState is DownloadState.Downloading -> item {
                    Hint("Downloading... ${(downloadState.progress * 100).toInt()}%")
                }
                else -> when (val options = downloadOptions) {
                    is FeedState.Loading -> item {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(16.dp)
                                .size(24.dp)
                        )
                    }
                    is FeedState.Error -> item { Hint("Couldn't load download options: ${options.message}") }
                    is FeedState.Data -> {
                        if (downloadState is DownloadState.Error) {
                            item { Hint("Last download failed: ${downloadState.message}") }
                        }
                        options.value.forEach { option ->
                            item(key = "d_${option.label}") {
                                OptionRow(
                                    label = "Download ${option.label}",
                                    selected = false,
                                    onClick = {
                                        DownloadRepository.start(context, item, option)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = OriginalColors.TextSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = OriginalColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = Color.White
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}
