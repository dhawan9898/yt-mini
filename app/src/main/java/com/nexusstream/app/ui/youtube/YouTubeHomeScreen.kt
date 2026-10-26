package com.nexusstream.app.ui.youtube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.AccountCircle
import com.nexusstream.app.data.LibraryRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.ui.components.BottomNavEntry
import com.nexusstream.app.ui.components.OriginalBottomNav
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.toFeedState
import com.nexusstream.app.ui.components.OriginalFilterChips
import com.nexusstream.app.ui.components.OriginalMiniPlayer

/**
 * YouTube Mode Home Screen:
 * - Flat, near-black YouTube styling (logo header, chips, edge-to-edge thumbnails, bottom nav).
 * - NO Shorts shelves and NO Games/Playables sections.
 * - Horizontal topic category chips.
 * - Widescreen video cards with channel avatars and view counts.
 * - Flat docked mini-player above the bottom nav.
 */
@Composable
fun YouTubeHomeScreen(
    currentPlayingItem: MediaItemModel?,
    isPlaying: Boolean,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var reloadKey by remember { mutableStateOf(0) }
    var feed by remember { mutableStateOf<FeedState<List<MediaItemModel>>>(FeedState.Loading) }

    // Live data: refetch on category / search / retry. Search is debounced.
    LaunchedEffect(selectedCategory, searchQuery, reloadKey) {
        val query = searchQuery.trim()
        if (isSearchActive && query.isEmpty()) return@LaunchedEffect
        feed = FeedState.Loading
        if (query.isNotEmpty()) delay(450)
        feed = if (query.isNotEmpty()) {
            YouTubeRepository.searchVideos(query).toFeedState()
        } else {
            YouTubeRepository.videoFeed(selectedCategory).toFeedState()
        }
    }
    val filteredList = (feed as? FeedState.Data)?.value.orEmpty()

    val categories = remember { listOf("All", "Music", "Gaming", "News", "Sports", "Movies", "Technology", "Cooking") }
    val navEntries = remember {
        listOf(
            BottomNavEntry("Home", Icons.Outlined.Home, Icons.Filled.Home),
            BottomNavEntry("Subscriptions", Icons.Outlined.Subscriptions, Icons.Filled.Subscriptions),
            BottomNavEntry("You", Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OriginalColors.YouTubeBackground)
    ) {
        YouTubeHeader(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onToggleSearch = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) searchQuery = ""
            }
        )

        if (!isSearchActive && selectedTab == 0) {
            OriginalFilterChips(
                labels = categories,
                selected = selectedCategory,
                onSelect = { selectedCategory = it },
                unselectedColor = OriginalColors.Chip,
                leadingIcon = Icons.Outlined.Explore,
                onLeadingClick = { selectedCategory = "Trending" }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (selectedTab == 1) {
                SubscriptionsTab(onSelectVideo = onSelectVideo)
            } else if (selectedTab == 2) {
                val savedItems by LibraryRepository.saved.collectAsState()
                val downloadedItems by LibraryRepository.downloads.collectAsState()
                val historyItems by LibraryRepository.history.collectAsState()
                if (savedItems.isEmpty() && downloadedItems.isEmpty() && historyItems.isEmpty()) {
                    OriginalEmptyState(
                        title = "Your library",
                        message = "Watch history, saved videos and downloads will show up here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        listOf(
                            "Downloads" to downloadedItems,
                            "Saved" to savedItems,
                            "History" to historyItems
                        ).filter { it.second.isNotEmpty() }.forEach { (title, list) ->
                            item(key = "you_header_$title") {
                                Text(
                                    text = title,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )
                            }
                            items(list, key = { "you_${title}_${it.id}" }) { video ->
                                YouTubeVideoCard(item = video, onClick = { onSelectVideo(video, list) })
                            }
                        }
                    }
                }
            } else if (feed is FeedState.Loading) {
                OriginalLoading()
            } else if (feed is FeedState.Error) {
                OriginalError(
                    message = (feed as FeedState.Error).message,
                    onRetry = { reloadKey++ }
                )
            } else if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No results found",
                        fontSize = 15.sp,
                        color = OriginalColors.TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { "yt_${it.id}" }) { video ->
                        YouTubeVideoCard(
                            item = video,
                            onClick = { onSelectVideo(video, filteredList) }
                        )
                    }
                }
            }
        }

        if (currentPlayingItem != null) {
            OriginalMiniPlayer(
                item = currentPlayingItem,
                isPlaying = isPlaying,
                onExpand = onExpandPlayer,
                onTogglePlay = onTogglePlayPause,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        OriginalBottomNav(
            entries = navEntries,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            background = OriginalColors.YouTubeBackground,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
private fun YouTubeHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 12.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchActive) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(OriginalColors.Chip)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search YouTube",
                        fontSize = 15.sp,
                        color = OriginalColors.TextSecondary
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = onToggleSearch) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close Search", tint = Color.White)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(OriginalColors.Red),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Premium",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-1.2).sp
                )
            }

            Row {
                IconButton(onClick = { /* Notifications */ }) {
                    Icon(imageVector = Icons.Outlined.Notifications, contentDescription = "Notifications", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = onToggleSearch) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

/**
 * Subscriptions: avatars of channels followed from the watch page, then their recent videos.
 * Subscriptions are stored on this device (no account sign-in).
 */
@Composable
private fun SubscriptionsTab(onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit) {
    val subs by LibraryRepository.subscriptions.collectAsState()
    var videos by remember { mutableStateOf<FeedState<List<MediaItemModel>>>(FeedState.Loading) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(subs, reloadKey) {
        if (subs.isEmpty()) return@LaunchedEffect
        videos = FeedState.Loading
        videos = YouTubeRepository.latestFrom(subs.map { it.name }).toFeedState()
    }

    if (subs.isEmpty()) {
        OriginalEmptyState(
            title = "No subscriptions yet",
            message = "Tap Subscribe on a video's page and the channel will appear here."
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(subs, key = { it.name }) { sub ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(68.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(OriginalColors.Chip)
                    ) {
                        if (sub.avatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = sub.avatarUrl,
                                contentDescription = sub.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
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
        }

        when (val state = videos) {
            is FeedState.Loading -> OriginalLoading()
            is FeedState.Error -> OriginalError(state.message, onRetry = { reloadKey++ })
            is FeedState.Data -> if (state.value.isEmpty()) {
                OriginalEmptyState("Nothing new", "No recent videos found from your subscriptions.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.value, key = { "sub_${it.id}" }) { video ->
                        YouTubeVideoCard(item = video, onClick = { onSelectVideo(video, state.value) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun YouTubeVideoCard(
    item: MediaItemModel,
    onClick: () -> Unit,
    /** Inset, rounded thumbnail (used for "Up next"); the home feed is edge to edge. */
    inset: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Thumbnail Aspect 16:9
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (inset) Modifier.padding(horizontal = 12.dp) else Modifier)
                .aspectRatio(16f / 9f)
                .then(if (inset) Modifier.clip(RoundedCornerShape(12.dp)) else Modifier)
                .background(OriginalColors.Chip)
        ) {
            if (item.artworkUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Duration badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatVideoDuration(item.durationMs),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Details Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(item.dominantColor),
                contentAlignment = Alignment.Center
            ) {
                if (item.channelAvatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.channelAvatarUrl,
                        contentDescription = item.artistOrChannel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = item.artistOrChannel.take(1),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                // "Channel  ▷ 456K  1d ago", like the original feed.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.artistOrChannel,
                        fontSize = 13.sp,
                        color = OriginalColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.viewsOrPlays.isNotBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            tint = OriginalColors.TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = item.viewsOrPlays,
                            fontSize = 13.sp,
                            color = OriginalColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                    if (item.publishDate.isNotBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = item.publishDate,
                            fontSize = 13.sp,
                            color = OriginalColors.TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            IconButton(onClick = { /* Options menu */ }) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun formatVideoDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
