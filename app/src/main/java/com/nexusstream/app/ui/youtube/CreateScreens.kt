package com.nexusstream.app.ui.youtube

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.data.GoogleAccount
import com.nexusstream.app.data.VideoUpload
import com.nexusstream.app.data.WatchTime
import com.nexusstream.app.data.YouTubeWeb
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.MiniPlayerHooks
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.PageScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale

/** Create > Upload a video: pick a file, give it a title and visibility, and send it to your channel. */
@Composable
fun UploadScreen(nav: YouTubeNav, miniPlayer: MiniPlayerHooks) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(VideoUpload.Privacy.PRIVATE) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var doneId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val uploading = progress >= 0f && doneId == null && error == null

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            file = uri
            fileName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0).orEmpty() else ""
            }.orEmpty()
            if (title.isBlank()) title = fileName.substringBeforeLast('.')
            doneId = null
            error = null
            progress = -1f
        }
    }

    PageScaffold(title = "Upload video", onBack = nav.back, miniPlayer = miniPlayer) {
        if (!GoogleAccount.isSignedIn()) {
            OriginalEmptyState("Sign in to upload", "Uploads go to the channel of the Google account you sign in with.")
            return@PageScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(OriginalColors.Chip)
                    .clickable(enabled = !uploading) { picker.launch("video/*") }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (file == null) "Choose a video" else fileName.ifBlank { "Video selected" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = OriginalColors.TextPrimary
                )
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(100) },
                label = { Text("Title") },
                singleLine = true,
                enabled = !uploading,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(5000) },
                label = { Text("Description") },
                minLines = 3,
                enabled = !uploading,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Visibility", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoUpload.Privacy.entries.forEach { p ->
                    val selected = p == privacy
                    Text(
                        p.label,
                        fontSize = 14.sp,
                        color = if (selected) Color.Black else OriginalColors.TextPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color.White else OriginalColors.Chip)
                            .clickable(enabled = !uploading) { privacy = p }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            if (progress >= 0f && doneId == null && error == null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (progress >= 1f) "Processing..." else "Uploading ${(progress * 100).toInt()}%",
                    fontSize = 13.sp,
                    color = OriginalColors.TextSecondary
                )
            }
            error?.let { Text(it, fontSize = 13.sp, color = Color(0xFFFF6B6B)) }
            doneId?.let { id ->
                Text("Uploaded. YouTube is processing it now.", fontSize = 14.sp, color = OriginalColors.TextPrimary)
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com/video/$id/edit")))
                }) { Text("Open in YouTube Studio") }
            }
            TextButton(
                enabled = file != null && !uploading && doneId == null,
                onClick = {
                    val uri = file ?: return@TextButton
                    progress = 0f
                    error = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                VideoUpload.upload(context, uri, title, description, privacy) { p -> progress = p }
                            }
                        }.onSuccess {
                            doneId = it
                            Toast.makeText(context, "Video uploaded", Toast.LENGTH_SHORT).show()
                        }.onFailure { error = it.message ?: "The upload failed" }
                    }
                }
            ) { Text("Upload", fontSize = 16.sp) }
        }
    }
}

/** An account page from YouTube itself, such as Your clips or Your movies. */
@Composable
fun AccountFeedScreen(
    route: Route.AccountFeed,
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    var items by remember(route.browseId) { mutableStateOf<List<MediaItemModel>?>(null) }
    var error by remember(route.browseId) { mutableStateOf<String?>(null) }
    LaunchedEffect(route.browseId) {
        withContext(Dispatchers.IO) { runCatching { YouTubeWeb.explore(route.browseId).items } }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: "Couldn't load this page" }
    }
    PageScaffold(title = route.title, onBack = nav.back, miniPlayer = miniPlayer) {
        val list = items
        when {
            !GoogleAccount.isSignedIn() -> OriginalEmptyState("Sign in", "This page comes from your Google account.")
            error != null -> OriginalEmptyState("Couldn't load ${route.title}", error.orEmpty())
            list == null -> OriginalEmptyState("Loading...", "")
            list.isEmpty() -> OriginalEmptyState("Nothing here yet", route.emptyMessage)
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
                items(list, key = { "af_${it.id}" }) { video ->
                    CompactVideoRow(
                        item = video,
                        onClick = { onSelectVideo(video, list) },
                        onChannelClick = video.channelUrl.takeIf { it.isNotBlank() }?.let { { nav.openChannelOf(video) } }
                    )
                }
            }
        }
    }
}

/** Time watched: a daily average and a bar for each of the last seven days. */
@Composable
fun TimeWatchedScreen(nav: YouTubeNav, miniPlayer: MiniPlayerHooks) {
    var days by remember { mutableStateOf(WatchTime.lastDays()) }
    PageScaffold(
        title = "Time watched",
        onBack = nav.back,
        miniPlayer = miniPlayer,
        actions = {
            TextButton(onClick = { WatchTime.clear(); days = WatchTime.lastDays() }) { Text("Clear", color = OriginalColors.TextPrimary) }
        }
    ) {
        val total = days.sumOf { it.second }
        val max = days.maxOf { it.second }.coerceAtLeast(1L)
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Daily average", fontSize = 14.sp, color = OriginalColors.TextSecondary)
            Text(formatWatchTime(total / days.size), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary)
            Text("Last 7 days: ${formatWatchTime(total)}", fontSize = 13.sp, color = OriginalColors.TextSecondary)
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                days.reversed().forEach { (day, ms) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Box(Modifier.weight(1f).width(24.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((ms.toFloat() / max).coerceAtLeast(0.02f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (day == days.first().first) Color(0xFF3EA6FF) else OriginalColors.TextSecondary)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 11.sp, color = OriginalColors.TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Counted on this device from what actually played in yt-mini (videos and music), not paused time.",
                fontSize = 12.sp,
                color = OriginalColors.TextSecondary
            )
        }
    }
}

internal fun formatWatchTime(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "0 min"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} hr ${minutes % 60} min"
    }
}
