package com.nexusstream.app.ui.components

import com.nexusstream.app.ui.theme.glassTile
import com.nexusstream.app.ui.theme.GlassIconButton as IconButton
import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
private val SLEEP_MINUTES = listOf(10, 15, 20, 30, 45, 60)

private fun speedLabel(speed: Float) = if (speed == 1f) "Normal" else "${speed}x"

/** The pages of the player's settings sheet, like YouTube's: a short menu, each row opening its choices. */
private enum class OptionsPage { MAIN, QUALITY, SPEED, CAPTIONS, TRANSLATE, AUDIO_TRACK, AUDIO_QUALITY, SLEEP, MORE }

/**
 * The player's settings, laid out like YouTube's gear menu: Quality, Playback speed, Captions,
 * Audio track, Sleep timer and Additional settings, each opening its own list of choices.
 * With a null [service] (a Download button or a feed menu) it is YouTube's "Download quality"
 * sheet instead. [showAudioQuality] adds an audio-quality page (the music player);
 * [showDownload] is kept for callers that only want the settings.
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
    @Suppress("UNUSED_PARAMETER") showDownload: Boolean = true
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // Fully open at once, so the Download button under the choices is never off screen.
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OriginalColors.Sheet,
        contentColor = OriginalColors.TextPrimary
    ) {
        com.nexusstream.app.ui.theme.GlassWindowBlur()
        if (service == null) {
            DownloadQualityContent(item, onDismiss)
        } else {
            PlayerSettingsContent(state, service, showVideoQuality, showAudioQuality, onDismiss)
        }
    }
}

@Composable
private fun PlayerSettingsContent(
    state: PlaybackState,
    service: MediaPlaybackService,
    showVideoQuality: Boolean,
    showAudioQuality: Boolean,
    onDismiss: () -> Unit
) {
    var page by remember { mutableStateOf(OptionsPage.MAIN) }
    androidx.activity.compose.BackHandler(enabled = page != OptionsPage.MAIN) {
        page = if (page == OptionsPage.TRANSLATE) OptionsPage.CAPTIONS else OptionsPage.MAIN
    }
    val back = { page = if (page == OptionsPage.TRANSLATE) OptionsPage.CAPTIONS else OptionsPage.MAIN }

    AnimatedContent(
        targetState = page,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "optionsPage"
    ) { current ->
        LazyColumn(
            modifier = Modifier.heightIn(max = 560.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            when (current) {
                OptionsPage.MAIN -> {
                    if (showVideoQuality) item {
                        val value = when {
                            state.availableVideoHeights.isEmpty() && state.isResolving -> "Loading..."
                            state.videoQualityAuto -> state.selectedVideoHeight?.let { "Auto (${it}p)" } ?: "Auto"
                            else -> state.selectedVideoHeight?.let { "${it}p" } ?: "Auto"
                        }
                        MenuRow(Icons.Outlined.Tune, "Quality", value, enabled = state.availableVideoHeights.isNotEmpty()) { page = OptionsPage.QUALITY }
                    }
                    item { MenuRow(Icons.Outlined.SlowMotionVideo, "Playback speed", speedLabel(state.playbackSpeed)) { page = OptionsPage.SPEED } }
                    if (state.availableSubtitles.isNotEmpty()) item {
                        val label = state.availableSubtitles.firstOrNull { it.languageTag == state.selectedSubtitleTag }?.label
                        val value = when {
                            label == null -> "Off"
                            state.subtitleTranslation != null -> "$label >> ${translationName(state.subtitleTranslation)}"
                            else -> label
                        }
                        MenuRow(Icons.Outlined.ClosedCaption, "Captions", value) { page = OptionsPage.CAPTIONS }
                    }
                    if (state.availableAudioTracks.size > 1) item {
                        val selected = state.availableAudioTracks.firstOrNull { it.id == state.selectedAudioTrackId }
                            ?: state.availableAudioTracks.firstOrNull { it.isOriginal }
                        MenuRow(Icons.Outlined.Audiotrack, "Audio track", selected?.name?.ifBlank { "Original" } ?: "Original") { page = OptionsPage.AUDIO_TRACK }
                    }
                    if (showAudioQuality && state.availableAudioKbps.isNotEmpty()) item {
                        val value = if (state.audioQualityAuto) "Auto" else state.selectedAudioKbps?.let { "$it kbps" } ?: "Auto"
                        MenuRow(Icons.Outlined.GraphicEq, "Audio quality", value) { page = OptionsPage.AUDIO_QUALITY }
                    }
                    item {
                        MenuRow(Icons.Outlined.Bedtime, "Sleep timer", sleepLabel(state)) { page = OptionsPage.SLEEP }
                    }
                    item { MenuRow(Icons.Outlined.Settings, "Additional settings", null) { page = OptionsPage.MORE } }
                }

                OptionsPage.QUALITY -> {
                    item { PageHeader("Quality for current video", back) }
                    item {
                        ChoiceRow(
                            label = "Auto",
                            detail = "Adjusts to give you the best experience for your conditions",
                            selected = state.videoQualityAuto
                        ) { service.setVideoQuality(null); onDismiss() }
                    }
                    state.availableVideoHeights.forEach { height ->
                        item(key = "v_$height") {
                            ChoiceRow(
                                label = "${height}p",
                                detail = qualityName(height),
                                selected = !state.videoQualityAuto && state.selectedVideoHeight == height
                            ) { service.setVideoQuality(height); onDismiss() }
                        }
                    }
                }

                OptionsPage.SPEED -> {
                    item { PageHeader("Playback speed", back) }
                    SPEEDS.forEach { speed ->
                        item(key = "sp_$speed") {
                            ChoiceRow(speedLabel(speed), selected = state.playbackSpeed == speed) {
                                service.setPlaybackSpeed(speed); onDismiss()
                            }
                        }
                    }
                }

                OptionsPage.CAPTIONS -> {
                    item { PageHeader("Captions", back) }
                    item { ChoiceRow("Off", selected = state.selectedSubtitleTag == null) { service.setSubtitle(null); onDismiss() } }
                    state.availableSubtitles.forEachIndexed { index, sub ->
                        item(key = "s_$index") {
                            ChoiceRow(sub.label, selected = state.selectedSubtitleTag == sub.languageTag && state.subtitleTranslation == null) {
                                service.setSubtitle(sub.languageTag); onDismiss()
                            }
                        }
                    }
                    item {
                        MenuRow(Icons.Outlined.Translate, "Auto-translate", state.subtitleTranslation?.let { translationName(it) } ?: "Off") {
                            page = OptionsPage.TRANSLATE
                        }
                    }
                }

                OptionsPage.TRANSLATE -> {
                    item { PageHeader("Auto-translate", back) }
                    val source = state.selectedSubtitleTag ?: state.availableSubtitles.firstOrNull()?.languageTag.orEmpty()
                    com.nexusstream.app.data.WatchExtras.translationLanguages.forEach { (code, name) ->
                        item(key = "t_$code") {
                            ChoiceRow(name, selected = state.subtitleTranslation == code) {
                                service.setSubtitle(source, translateTo = code); onDismiss()
                            }
                        }
                    }
                }

                OptionsPage.AUDIO_TRACK -> {
                    item { PageHeader("Audio track", back) }
                    state.availableAudioTracks.forEachIndexed { index, track ->
                        item(key = "at_$index") {
                            val label = track.name.ifBlank { "Audio" } +
                                if (track.isOriginal && !track.name.contains("original", ignoreCase = true)) " (original)" else ""
                            val selected = state.selectedAudioTrackId == track.id || (state.selectedAudioTrackId == null && track.isOriginal)
                            // Choosing the original track resets to the default instead of pinning it.
                            ChoiceRow(label, selected = selected) { service.setAudioTrack(if (track.isOriginal) null else track.id); onDismiss() }
                        }
                    }
                }

                OptionsPage.AUDIO_QUALITY -> {
                    item { PageHeader("Audio quality", back) }
                    item { ChoiceRow("Auto", detail = "Best available", selected = state.audioQualityAuto) { service.setAudioQuality(null); onDismiss() } }
                    state.availableAudioKbps.forEach { kbps ->
                        item(key = "a_$kbps") {
                            ChoiceRow("$kbps kbps", selected = !state.audioQualityAuto && state.selectedAudioKbps == kbps) {
                                service.setAudioQuality(kbps); onDismiss()
                            }
                        }
                    }
                }

                OptionsPage.SLEEP -> {
                    item { PageHeader("Sleep timer", back) }
                    item { ChoiceRow("Off", selected = state.sleepTimerEndsAtMs == 0L) { service.setSleepTimer(0); onDismiss() } }
                    SLEEP_MINUTES.forEach { minutes ->
                        item(key = "sl_$minutes") {
                            ChoiceRow("$minutes minutes", selected = false) { service.setSleepTimer(minutes); onDismiss() }
                        }
                    }
                }

                OptionsPage.MORE -> {
                    item { PageHeader("Additional settings", back) }
                    item {
                        ToggleRow(Icons.Outlined.Repeat, "Loop video", state.repeatMode == StreamRepeatMode.ONE) {
                            service.setLoopOne(it)
                        }
                    }
                    item {
                        val ambient by com.nexusstream.app.data.AppSettings.ambientMode.collectAsState()
                        ToggleRow(Icons.Outlined.Tune, "Ambient mode", ambient) { com.nexusstream.app.data.AppSettings.setAmbientMode(it) }
                    }
                    item {
                        val stable by com.nexusstream.app.data.AppSettings.stableVolume.collectAsState()
                        ToggleRow(Icons.Outlined.GraphicEq, "Stable volume", stable) { com.nexusstream.app.data.AppSettings.setStableVolume(it) }
                    }
                }
            }
        }
    }
}

private fun sleepLabel(state: PlaybackState): String =
    if (state.sleepTimerEndsAtMs > 0) {
        val minutesLeft = ((state.sleepTimerEndsAtMs - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(1)
        "$minutesLeft min left"
    } else "Off"

private fun translationName(code: String): String =
    com.nexusstream.app.data.WatchExtras.translationLanguages.firstOrNull { it.first == code }?.second ?: code

/** YouTube's names for the resolutions in its quality and download menus. */
internal fun qualityName(height: Int): String = when {
    height >= 2160 -> "4K"
    height >= 1440 -> "QHD"
    height >= 1080 -> "Full HD"
    height >= 720 -> "High definition"
    height >= 480 -> "Standard definition"
    height >= 360 -> "Medium"
    else -> "Low"
}

// ------------------------------------------------------------------------------------------ download

/**
 * YouTube's "Download quality" sheet: pick one quality with a radio button, then Download.
 * While a download runs (or is paused / failed) it shows its progress and the matching actions.
 */
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun DownloadQualityContent(item: MediaItemModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val downloadStates by DownloadRepository.states.collectAsState()
    val offline by LibraryRepository.downloads.collectAsState()
    val pending by DownloadRepository.pending.collectAsState()
    val isDownloaded = offline.any { it.id == item.id }
    val downloadState = downloadStates[item.id]

    val options by produceState<FeedState<List<DownloadOption>>>(FeedState.Loading, item.id, isDownloaded) {
        value = if (isDownloaded && item.sourceUrl.isEmpty()) FeedState.Data(emptyList())
        else YouTubeRepository.downloadOptions(item.watchUrl).toFeedState()
    }
    var selected by remember(item.id) { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
        Text(
            text = "Download quality",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = OriginalColors.TextPrimary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
        )
        when {
            isDownloaded -> {
                SheetMessage("This video is downloaded. You can watch it without a connection.")
                SheetButtons(
                    secondary = "Close" to onDismiss,
                    primary = "Delete download" to { DownloadRepository.remove(item.id); onDismiss() }
                )
            }
            downloadState is DownloadState.Downloading || downloadState is DownloadState.Paused -> {
                val progress = (downloadState as? DownloadState.Downloading)?.progress
                    ?: (downloadState as DownloadState.Paused).progress
                val label = pending.firstOrNull { it.item.id == item.id }?.label.orEmpty()
                SheetMessage(
                    (if (downloadState is DownloadState.Paused) "Paused" else "Downloading") +
                        " · ${(progress * 100).toInt()}%" + if (label.isNotEmpty()) " · $label" else ""
                )
                LinearProgressIndicator(
                    progress = { progress },
                    color = OriginalColors.Accent,
                    trackColor = OriginalColors.Chip,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
                )
                SheetButtons(
                    secondary = "Cancel download" to { DownloadRepository.cancel(context, item.id); onDismiss() },
                    primary = if (downloadState is DownloadState.Paused) "Resume" to { DownloadRepository.resume(context, item.id); onDismiss() }
                    else "Pause" to { DownloadRepository.pause(item.id) }
                )
            }
            downloadState is DownloadState.Error && pending.any { it.item.id == item.id } -> {
                SheetMessage("Download failed: ${downloadState.message}")
                SheetButtons(
                    secondary = "Cancel download" to { DownloadRepository.cancel(context, item.id); onDismiss() },
                    primary = "Try again" to { DownloadRepository.resume(context, item.id); onDismiss() }
                )
            }
            else -> when (val o = options) {
                is FeedState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OriginalColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                is FeedState.Error -> {
                    SheetMessage("Couldn't load download options. ${o.message}")
                    SheetButtons(secondary = null, primary = "Close" to onDismiss)
                }
                is FeedState.Data -> {
                    val videos = o.value.filter { it.isVideo }
                    val audios = o.value.filter { !it.isVideo }
                    // Like YouTube, a sensible default is picked: 720p, else the best below it, else the first.
                    val defaultPick = (videos.firstOrNull { it.height in 1..720 } ?: videos.firstOrNull() ?: audios.firstOrNull())?.label
                    val pick = selected ?: defaultPick
                    if (downloadState is DownloadState.Error) SheetMessage("Last download failed: ${downloadState.message}")
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        videos.forEach { option ->
                            item(key = "d_${option.label}") {
                                val fps = Regex("""p(\d{2,3})""").find(option.label)?.groupValues?.get(1)
                                ChoiceRow(
                                    label = "${qualityName(option.height)} (${option.height}p${fps.orEmpty()})",
                                    selected = pick == option.label,
                                    radio = true
                                ) { selected = option.label }
                            }
                        }
                        if (audios.isNotEmpty()) {
                            item { SubHeader("Audio only") }
                            audios.forEach { option ->
                                item(key = "d_${option.label}") {
                                    ChoiceRow(
                                        label = option.label.removeSuffix(" audio").replaceFirstChar { it.uppercase() },
                                        selected = pick == option.label,
                                        radio = true
                                    ) { selected = option.label }
                                }
                            }
                        }
                    }
                    SheetButtons(
                        secondary = "Cancel" to onDismiss,
                        primary = "Download" to {
                            o.value.firstOrNull { it.label == pick }?.let { DownloadRepository.start(context, item, it) }
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetMessage(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = OriginalColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun SheetButtons(secondary: Pair<String, () -> Unit>?, primary: Pair<String, () -> Unit>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        secondary?.let { (label, action) ->
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = OriginalColors.TextPrimary,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = action).padding(horizontal = 16.dp, vertical = 9.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = primary.first,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = OriginalColors.OnInverse,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .glassTile(RoundedCornerShape(18.dp), fill = OriginalColors.TextPrimary)
                .clickable(onClick = primary.second)
                .padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

// ------------------------------------------------------------------------------------------ rows

/** A row of the main menu: icon, name, current value in grey, and an arrow. */
@Composable
private fun MenuRow(icon: ImageVector, label: String, value: String?, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = OriginalColors.TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, fontSize = 15.sp, color = OriginalColors.TextPrimary, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = OriginalColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).widthIn(max = 170.dp)
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = OriginalColors.TextSecondary, modifier = Modifier.size(20.dp))
    }
}

/** Title of a sub-page with a back arrow. */
@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = OriginalColors.TextPrimary)
        }
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OriginalColors.TextPrimary)
    }
}

@Composable
private fun SubHeader(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = OriginalColors.TextSecondary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
    )
}

/** One choice: a check mark (settings) or a radio button (download quality) in front of the label. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, detail: String? = null, radio: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (detail != null) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (radio) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .border(2.dp, if (selected) OriginalColors.Accent else OriginalColors.TextSecondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(OriginalColors.Accent))
                }
            } else if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = OriginalColors.TextPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = OriginalColors.TextPrimary)
            if (detail != null) Text(detail, fontSize = 12.sp, color = OriginalColors.TextSecondary)
        }
    }
}

/** An on / off setting with YouTube's switch. */
@Composable
private fun ToggleRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = OriginalColors.TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, fontSize = 15.sp, color = OriginalColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = OriginalColors.Accent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = OriginalColors.Chip,
                uncheckedBorderColor = OriginalColors.Divider
            )
        )
    }
}
