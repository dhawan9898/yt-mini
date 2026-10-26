package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.MiniPlayerHooks
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalFilterChips
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.PageScaffold
import com.nexusstream.app.ui.components.toFeedState

private val NewDotBlue = Color(0xFF3EA6FF)
private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Subscriptions tab: channel avatars (blue dot = new upload since you last opened the channel),
 * filter chips, and the newest uploads from your subscriptions (public channel RSS feeds).
 * Subscriptions are stored on this device; there is no account sign-in.
 */
@Composable
internal fun SubscriptionsTab(
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit
) {
    val subs by LibraryRepository.subscriptions.collectAsState()
    val hidden by LibraryRepository.hidden.collectAsState()
    var reloadKey by remember { mutableStateOf(0) }
    var forceRefresh by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All") }
    var feed by remember {
        mutableStateOf<FeedState<List<MediaItemModel>>>(
            YouTubeRepository.cachedSubscriptionFeed(subs)?.let { FeedState.Data(it) } ?: FeedState.Loading
        )
    }

    LaunchedEffect(subs.map { it.name to it.channelUrl }, reloadKey) {
        if (subs.isEmpty()) return@LaunchedEffect
        val force = forceRefresh
        forceRefresh = false
        if (force || feed !is FeedState.Data) feed = FeedState.Loading
        feed = YouTubeRepository.subscriptionFeed(subs, refresh = force).toFeedState()
    }

    if (subs.isEmpty()) {
        OriginalEmptyState(
            title = "No subscriptions yet",
            message = "Tap Subscribe on a video or channel and it will appear here."
        )
        return
    }

    val now = System.currentTimeMillis()
    val items = (feed as? FeedState.Data)?.value.orEmpty()
        .filter { it.id !in hidden.videoIds && it.artistOrChannel !in hidden.channels }
    val shown = when (filter) {
        "Today" -> items.filter { it.publishedAtMs > now - DAY_MS }
        "This week" -> items.filter { it.publishedAtMs > now - 7 * DAY_MS }
        else -> items
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            items(subs, key = { it.name }) { sub ->
                val hasNew = items.any {
                    (it.channelUrl == sub.channelUrl && sub.channelUrl.isNotBlank() || it.artistOrChannel == sub.name) &&
                        it.publishedAtMs > sub.lastSeenMs
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(68.dp)
                        .clickable {
                            LibraryRepository.markSeen(sub.name)
                            if (sub.channelUrl.isNotBlank()) nav.openChannel(sub.channelUrl, sub.name, sub.avatarUrl)
                        }
                ) {
                    Box {
                        Avatar(sub.avatarUrl, sub.name, 56.dp)
                        if (hasNew) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(NewDotBlue)
                                    .border(2.dp, OriginalColors.YouTubeBackground, CircleShape)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = sub.name,
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            item(key = "all_button") {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .clickable { nav.openAllSubscriptions() }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("All", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = NewDotBlue)
                }
            }
        }

        OriginalFilterChips(
            labels = listOf("All", "Today", "This week"),
            selected = filter,
            onSelect = { filter = it },
            unselectedColor = OriginalColors.Chip
        )

        when (val state = feed) {
            is FeedState.Loading -> OriginalLoading()
            is FeedState.Error -> OriginalError(
                message = state.message,
                onRetry = {
                    forceRefresh = true
                    reloadKey++
                }
            )
            is FeedState.Data -> if (shown.isEmpty()) {
                OriginalEmptyState("Nothing new", "No uploads match this filter.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shown, key = { "sub_${it.id}" }) { video ->
                        YouTubeVideoCard(
                            item = video,
                            onClick = { onSelectVideo(video, shown) },
                            onChannelClick = video.channelUrl.takeIf { it.isNotBlank() }
                                ?.let { { nav.openChannelOf(video) } }
                        )
                    }
                }
            }
        }
    }
}

/** Every subscribed channel, with a toggle to unsubscribe. */
@Composable
fun AllSubscriptionsScreen(
    nav: YouTubeNav,
    miniPlayer: MiniPlayerHooks
) {
    val subs by LibraryRepository.subscriptions.collectAsState()

    PageScaffold(title = "All subscriptions", onBack = nav.back, miniPlayer = miniPlayer) {
        if (subs.isEmpty()) {
            OriginalEmptyState("No subscriptions", "Channels you subscribe to will be listed here.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(subs, key = { "all_${it.name}" }) { sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibraryRepository.markSeen(sub.name)
                                if (sub.channelUrl.isNotBlank()) nav.openChannel(sub.channelUrl, sub.name, sub.avatarUrl)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(sub.avatarUrl, sub.name, 48.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = sub.name,
                            fontSize = 16.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(OriginalColors.Chip)
                                .clickable { LibraryRepository.toggleSubscribe(sub.name, sub.avatarUrl, sub.channelUrl) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Subscribed", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Avatar(url: String, name: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(OriginalColors.Chip),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(name.take(1).uppercase(), fontSize = (size.value / 2.5f).sp, color = Color.White)
        }
    }
}
