package com.nexusstream.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.media.PlaybackState
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.StreamRepeatMode

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val SLEEP_MINUTES = listOf(15, 30, 45, 60)

private fun speedLabel(speed: Float) = if (speed == 1f) "Normal" else "${speed}x"

/**
 * Everything adjustable for the current item: video and audio quality, audio language, captions,
 * playback speed, sleep timer, loop, autoplay, and download quality.
 * With a null [service] (e.g. opened from a feed menu or a Download button) only the Download
 * section is shown. The YouTube player hides [showAudioQuality] and [showDownload], like the
 * original app's settings sheet (download has its own button there).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsSheet(
    item: MediaItemModel,
    state: PlaybackState,
    service: MediaPlaybackService?,
    showVideoQuality: Boolean,
    onDismiss: () -> Unit,
    showAudioQuality: Boolean = true,
    showDownload: Boolean = true
) {
    val context = LocalContext.current
    val downloadStates by DownloadRepository.states.collectAsState()
    val offline by LibraryRepository.downloads.collectAsState()
    val isDownloaded = offline.any { it.id == item.id }
    val downloadState = downloadStates[item.id]

    // Offline copies have no stream to resolve, so there is nothing to list for them.
    val downloadOptions by produceState<FeedState<List<DownloadOption>>>(FeedState.Loading, item.id, showDownload) {
        value = if (!showDownload || (item.sourceUrl.isEmpty() && isDownloaded)) {
            FeedState.Data(emptyList())
        } else {
            YouTubeRepository.downloadOptions(item.watchUrl).toFeedState()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OriginalColors.Sheet,
        contentColor = OriginalColors.TextPrimary
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight(0.85f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (service != null) {
                if (showVideoQuality && state.availableVideoHeights.isNotEmpty()) {
                    item { SectionTitle("Video quality") }
                    item {
                        OptionRow(
                            label = state.selectedVideoHeight?.takeIf { state.videoQualityAuto }?.let { "Auto (${it}p)" } ?: "Auto",
                            selected = state.videoQualityAuto,
                            onClick = { service.setVideoQuality(null) }
                        )
                    }
                    state.availableVideoHeights.forEach { height ->
                        item(key = "v_$height") {
                            OptionRow(
                                label = "${height}p",
                                selected = !state.videoQualityAuto && state.selectedVideoHeight == height,
                                onClick = { service.setVideoQuality(height) }
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
                                onClick = { service.setAudioTrack(if (track.isOriginal) null else track.id) }
                            )
                        }
                    }
                }

                if (showAudioQuality && state.availableAudioKbps.isNotEmpty()) {
                    item { SectionTitle("Audio quality") }
                    item {
                        OptionRow(
                            label = "Best available",
                            selected = state.audioQualityAuto,
                            onClick = { service.setAudioQuality(null) }
                        )
                    }
                    state.availableAudioKbps.forEach { kbps ->
                        item(key = "a_$kbps") {
                            OptionRow(
                                label = "$kbps kbps",
                                selected = !state.audioQualityAuto && state.selectedAudioKbps == kbps,
                                onClick = { service.setAudioQuality(kbps) }
                            )
                        }
                    }
                } else if (state.isResolving && state.availableVideoHeights.isEmpty()) {
                    item { SectionTitle("Quality") }
                    item { Hint("Loading available qualities...") }
                }

                if (state.availableSubtitles.isNotEmpty()) {
                    item { SectionTitle("Captions") }
                    item {
                        OptionRow(
                            label = "Off",
                            selected = state.selectedSubtitleTag == null,
                            onClick = { service.setSubtitle(null) }
                        )
                    }
                    state.availableSubtitles.forEachIndexed { index, sub ->
                        item(key = "s_$index") {
                            OptionRow(
                                label = sub.label,
                                selected = state.selectedSubtitleTag == sub.languageTag,
                                onClick = { service.setSubtitle(sub.languageTag) }
                            )
                        }
                    }
                }

                item { SectionTitle("Playback speed") }
                SPEEDS.forEach { speed ->
                    item(key = "sp_$speed") {
                        OptionRow(
                            label = speedLabel(speed),
                            selected = state.playbackSpeed == speed,
                            onClick = { service.setPlaybackSpeed(speed) }
                        )
                    }
                }

                item { SectionTitle("Sleep timer") }
                item {
                    OptionRow(
                        label = if (state.sleepTimerEndsAtMs > 0) {
                            val minutesLeft = ((state.sleepTimerEndsAtMs - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
                            "Off (pauses in about $minutesLeft min)"
                        } else {
                            "Off"
                        },
                        selected = state.sleepTimerEndsAtMs == 0L,
                        onClick = { service.setSleepTimer(0) }
                    )
                }
                SLEEP_MINUTES.forEach { minutes ->
                    item(key = "sl_$minutes") {
                        OptionRow(
                            label = "$minutes minutes",
                            selected = false,
                            onClick = {
                                service.setSleepTimer(minutes)
                                onDismiss()
                            }
                        )
                    }
                }

                item { SectionTitle("Playback") }
                item {
                    OptionRow(
                        label = "Loop video",
                        selected = state.repeatMode == StreamRepeatMode.ONE,
                        onClick = { service.setLoopOne(state.repeatMode != StreamRepeatMode.ONE) }
                    )
                }
                item {
                    OptionRow(
                        label = "Autoplay next video",
                        selected = state.autoplayNext,
                        onClick = { service.setAutoplayNext(!state.autoplayNext) }
                    )
                }
            }

            if (showDownload) item { SectionTitle("Download") }
            if (showDownload) when {
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
                downloadState is DownloadState.Downloading -> {
                    item { Hint("Downloading... ${(downloadState.progress * 100).toInt()}%") }
                    item { OptionRow("Pause download", false) { DownloadRepository.pause(item.id) } }
                    item {
                        OptionRow("Cancel download", false) {
                            DownloadRepository.cancel(context, item.id)
                            onDismiss()
                        }
                    }
                }
                downloadState is DownloadState.Paused -> {
                    item {
                        OptionRow("Resume download (${(downloadState.progress * 100).toInt()}%)", false) {
                            DownloadRepository.resume(context, item.id)
                            onDismiss()
                        }
                    }
                    item {
                        OptionRow("Cancel download", false) {
                            DownloadRepository.cancel(context, item.id)
                            onDismiss()
                        }
                    }
                }
                downloadState is DownloadState.Error && DownloadRepository.pending.value.any { it.item.id == item.id } -> {
                    item { Hint("Download failed: ${downloadState.message}") }
                    item {
                        OptionRow("Resume download", false) {
                            DownloadRepository.resume(context, item.id)
                            onDismiss()
                        }
                    }
                    item {
                        OptionRow("Cancel download", false) {
                            DownloadRepository.cancel(context, item.id)
                            onDismiss()
                        }
                    }
                }
                else -> when (val options = downloadOptions) {
                    is FeedState.Loading -> item {
                        CircularProgressIndicator(
                            color = OriginalColors.TextPrimary,
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
            color = OriginalColors.TextPrimary
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = OriginalColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
    }
}
