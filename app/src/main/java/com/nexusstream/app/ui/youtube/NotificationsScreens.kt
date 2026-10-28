package com.nexusstream.app.ui.youtube

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.nexusstream.app.LaunchRequests
import com.nexusstream.app.VideoLaunch
import com.nexusstream.app.data.AccountApi
import com.nexusstream.app.data.GoogleAccount
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.OnReachEnd
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.toFeedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.NotifyLevel
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.notifications.BackgroundJobs
import com.nexusstream.app.notifications.Bedtime
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.MiniPlayerHooks
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.OriginalFilterChips
import com.nexusstream.app.ui.components.PageScaffold

private val NewDotBlue = Color(0xFF3EA6FF)

/** Inbox of new-upload notifications. Opening it clears the bell badge. */
@Composable
fun NotificationsScreen(
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    val inbox by LibraryRepository.inbox.collectAsState()
    // Rows newer than this were unread when the page opened.
    val seenAtOnEntry = remember { LibraryRepository.inboxSeenAtMs.value }
    LaunchedEffect(Unit) { LibraryRepository.markInboxSeen() }

    PageScaffold(
        title = "Notifications",
        onBack = nav.back,
        miniPlayer = miniPlayer,
        actions = {
            IconButton(onClick = { LibraryRepository.clearInbox() }, enabled = inbox.isNotEmpty()) {
                Icon(Icons.Outlined.DeleteSweep, "Clear notifications", tint = OriginalColors.TextPrimary)
            }
            IconButton(onClick = { nav.open(Route.NotificationSettings) }) {
                Icon(Icons.Outlined.Settings, "Notification settings", tint = OriginalColors.TextPrimary)
            }
        }
    ) {
        val signedIn by GoogleAccount.signedIn.collectAsState()
        var tab by remember { mutableStateOf("YouTube") }
        Column(modifier = Modifier.fillMaxSize()) {
        if (signedIn) {
            OriginalFilterChips(
                labels = listOf("YouTube", "Upload alerts"),
                selected = tab,
                onSelect = { tab = it },
                unselectedColor = OriginalColors.Chip
            )
        }
        if (signedIn && tab == "YouTube") {
            AccountNotificationsList(onSelectVideo)
        } else if (inbox.isEmpty()) {
            OriginalEmptyState(
                title = "No notifications",
                message = "New uploads from channels you subscribe to will appear here."
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(inbox, key = { "inbox_${it.id}" }) { video ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (video.publishedAtMs > seenAtOnEntry) NewDotBlue else Color.Transparent)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            CompactVideoRow(
                                item = video,
                                onClick = { onSelectVideo(video, listOf(video)) },
                                onChannelClick = video.channelUrl.takeIf { it.isNotBlank() }?.let { { nav.openChannelOf(video) } }
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/** YouTube's own bell notifications for the signed-in account (uploads, replies, mentions). */
@Composable
private fun AccountNotificationsList(onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit) {
    var state by remember { mutableStateOf<FeedState<AccountApi.NotificationsPage>>(FeedState.Loading) }
    var reload by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<AccountApi.AccountNotification>>(emptyList()) }
    var next by remember { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(reload) {
        state = FeedState.Loading
        state = withContext(Dispatchers.IO) { runCatching { AccountApi.notifications() } }.toFeedState()
        (state as? FeedState.Data)?.value?.let { items = it.items; next = it.continuation }
    }
    when (val s = state) {
        is FeedState.Loading -> OriginalLoading()
        is FeedState.Error -> OriginalError(s.message, onRetry = { reload++ })
        is FeedState.Data -> if (items.isEmpty()) {
            OriginalEmptyState("No notifications", "Your YouTube notifications will appear here.")
        } else {
            OnReachEnd(listState) {
                val token = next ?: return@OnReachEnd
                if (loadingMore) return@OnReachEnd
                loadingMore = true
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { AccountApi.notifications(token) } }
                        .onSuccess { page -> items = items + page.items; next = page.continuation?.takeIf { it != token } }
                        .onFailure { next = null }
                    loadingMore = false
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(items, key = { "yt_${it.id}_${it.videoId}" }) { n ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = n.videoId.isNotEmpty()) {
                                val item = LaunchRequests.minimalItem(VideoLaunch(n.videoId)).copy(
                                    title = n.message, artworkUrl = n.thumbnailUrl.ifEmpty { "https://i.ytimg.com/vi/${n.videoId}/hqdefault.jpg" }
                                )
                                onSelectVideo(item, listOf(item))
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier.padding(top = 18.dp).size(6.dp).clip(CircleShape)
                                .background(if (!n.read) NewDotBlue else Color.Transparent)
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(44.dp).clip(CircleShape).background(OriginalColors.Divider)) {
                            if (n.avatarUrl.isNotEmpty()) AsyncImage(n.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(n.message, fontSize = 14.sp, color = OriginalColors.TextPrimary, maxLines = 3)
                            Text(n.time, fontSize = 12.sp, color = OriginalColors.TextSecondary)
                        }
                        if (n.thumbnailUrl.isNotEmpty()) {
                            Spacer(Modifier.width(10.dp))
                            AsyncImage(
                                n.thumbnailUrl, null, contentScale = ContentScale.Crop,
                                modifier = Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

private val BREAK_OPTIONS = listOf("Off" to 0, "15 min" to 15, "30 min" to 30, "45 min" to 45, "60 min" to 60, "90 min" to 90)
private val BEDTIME_OPTIONS = listOf("Off" to -1) + listOf(21 * 60, 22 * 60, 23 * 60, 0).map { Bedtime.label(it) to it }

private fun levelLabel(level: NotifyLevel) = when (level) {
    NotifyLevel.NONE -> "None"
    NotifyLevel.PERSONALIZED -> "Personalized"
    NotifyLevel.ALL -> "All"
}

/** Upload notifications on/off, per-channel bell level, take-a-break and bedtime reminders. */
@Composable
fun NotificationSettingsScreen(nav: YouTubeNav, miniPlayer: MiniPlayerHooks) {
    val context = LocalContext.current
    val enabled by LibraryRepository.notificationsEnabled.collectAsState()
    val subs by LibraryRepository.subscriptions.collectAsState()
    val breakMinutes by LibraryRepository.breakMinutes.collectAsState()
    val bedtime by LibraryRepository.bedtimeMinute.collectAsState()

    PageScaffold(title = "Notification settings", onBack = nav.back, miniPlayer = miniPlayer) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "enabled") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Upload notifications", fontSize = 16.sp, color = OriginalColors.TextPrimary)
                        Text(
                            "Checked about every 30 minutes while the app has network access.",
                            fontSize = 12.sp,
                            color = OriginalColors.TextSecondary
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { LibraryRepository.setNotificationsEnabled(it) })
                }
            }
            item(key = "check_now") {
                Text(
                    text = "Check for new uploads now",
                    fontSize = 15.sp,
                    color = NewDotBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            BackgroundJobs.checkNow(context)
                            Toast.makeText(context, "Checking for new uploads...", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            item(key = "h_channels") { Header("Channels") }
            if (subs.isEmpty()) {
                item(key = "no_subs") {
                    Text(
                        "Subscribe to channels to choose how they notify you.",
                        fontSize = 13.sp,
                        color = OriginalColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            } else {
                items(subs, key = { "cs_${it.name}" }) { sub ->
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = sub.name,
                            fontSize = 15.sp,
                            color = OriginalColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        OriginalFilterChips(
                            labels = NotifyLevel.entries.map { levelLabel(it) },
                            selected = levelLabel(sub.notifyLevel),
                            onSelect = { label ->
                                LibraryRepository.setNotifyLevel(sub.name, NotifyLevel.entries.first { levelLabel(it) == label })
                            },
                            unselectedColor = OriginalColors.Chip
                        )
                    }
                }
            }

            item(key = "h_break") { Header("Take a break reminder") }
            item(key = "break") {
                Text(
                    "Remind me after this long of continuous playback.",
                    fontSize = 12.sp,
                    color = OriginalColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                OriginalFilterChips(
                    labels = BREAK_OPTIONS.map { it.first },
                    selected = BREAK_OPTIONS.firstOrNull { it.second == breakMinutes }?.first ?: "Off",
                    onSelect = { label -> LibraryRepository.setBreakMinutes(BREAK_OPTIONS.first { it.first == label }.second) },
                    unselectedColor = OriginalColors.Chip
                )
            }

            item(key = "h_bedtime") { Header("Bedtime reminder") }
            item(key = "bedtime") {
                Text(
                    "A notification at the chosen time every day.",
                    fontSize = 12.sp,
                    color = OriginalColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                OriginalFilterChips(
                    labels = BEDTIME_OPTIONS.map { it.first },
                    selected = BEDTIME_OPTIONS.firstOrNull { it.second == bedtime }?.first ?: "Off",
                    onSelect = { label -> LibraryRepository.setBedtimeMinute(BEDTIME_OPTIONS.first { it.first == label }.second) },
                    unselectedColor = OriginalColors.Chip
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = OriginalColors.TextSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

/** The channel "bell": shows the current level and lets you pick All / Personalized / None. */
@Composable
internal fun NotifyLevelButton(level: NotifyLevel, onSelect: (NotifyLevel) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                imageVector = when (level) {
                    NotifyLevel.ALL -> Icons.Outlined.NotificationsActive
                    NotifyLevel.PERSONALIZED -> Icons.Outlined.Notifications
                    NotifyLevel.NONE -> Icons.Outlined.NotificationsOff
                },
                contentDescription = "Notifications: ${levelLabel(level)}",
                tint = OriginalColors.TextPrimary
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            NotifyLevel.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = levelLabel(option),
                            fontWeight = if (option == level) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelect(option)
                        open = false
                    }
                )
            }
        }
    }
}
