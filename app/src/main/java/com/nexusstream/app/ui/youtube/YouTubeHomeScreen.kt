package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.nexusstream.app.data.TakeoutParsing
import com.nexusstream.app.data.DownloadRepository
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.widget.Toast
import android.net.Uri
import android.content.Context
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.Paged
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaContentType
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.BottomNavEntry
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.OnReachEnd
import com.nexusstream.app.ui.components.OriginalBottomNav
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalFilterChips
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.OriginalMiniPlayer
import kotlinx.coroutines.launch

/**
 * YouTube home: Home feed (infinite scroll, pull-to-refresh, personalised with your
 * subscriptions and watch history), Subscriptions and You tabs, and a bottom nav.
 * Shorts and Games are intentionally not shown.
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeHomeScreen(
    currentPlayingItem: MediaItemModel?,
    isPlaying: Boolean,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onClosePlayer: () -> Unit,
    nav: YouTubeNav,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedTab by remember { mutableStateOf(0) }
    var reloadKey by remember { mutableStateOf(0) }
    var forceRefresh by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf(YouTubeRepository.cachedVideoFeed("All")) }
    var feed by remember {
        mutableStateOf<FeedState<List<MediaItemModel>>>(
            cursor?.let { FeedState.Data(it.items) } ?: FeedState.Loading
        )
    }
    val hidden by LibraryRepository.hidden.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Loads page one. Cached feeds (kept until the app process ends) show instantly;
    // Retry and pull-to-refresh set forceRefresh to bypass the cache.
    LaunchedEffect(selectedCategory, reloadKey) {
        val force = forceRefresh
        forceRefresh = false
        val cached = if (force) null else YouTubeRepository.cachedVideoFeed(selectedCategory)
        if (cached != null) {
            cursor = cached
            feed = FeedState.Data(cached.items)
            return@LaunchedEffect
        }
        if (!refreshing) feed = FeedState.Loading
        YouTubeRepository.videoFeedPaged(selectedCategory, personalChannels(), refresh = force)
            .onSuccess {
                cursor = it
                feed = FeedState.Data(it.items)
            }
            .onFailure {
                // Keep showing the old list if a pull-to-refresh fails.
                if (feed !is FeedState.Data) feed = FeedState.Error(it.message ?: "Something went wrong")
            }
        refreshing = false
    }

    fun loadMore() {
        val current = cursor ?: return
        if (!current.hasMore || loadingMore) return
        loadingMore = true
        scope.launch {
            current.next().onSuccess { next ->
                val existing = (feed as? FeedState.Data)?.value.orEmpty()
                feed = FeedState.Data((existing + next.items).distinctBy { it.id })
                cursor = next
            }
            loadingMore = false
        }
    }

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
        YouTubeHeader(onSearch = nav.openSearch)

        if (selectedTab == 0) {
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
            when (selectedTab) {
                1 -> SubscriptionsTab(nav = nav, onSelectVideo = onSelectVideo)
                2 -> YouTab(nav = nav, onSelectVideo = onSelectVideo)
                else -> PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        refreshing = true
                        forceRefresh = true
                        reloadKey++
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when (val state = feed) {
                        is FeedState.Loading -> OriginalLoading()
                        is FeedState.Error -> OriginalError(
                            message = state.message,
                            onRetry = {
                                forceRefresh = true
                                reloadKey++
                            }
                        )
                        is FeedState.Data -> {
                            val visible = remember(state.value, hidden) {
                                state.value.filter { it.id !in hidden.videoIds && it.artistOrChannel !in hidden.channels }
                            }
                            if (visible.isEmpty()) {
                                OriginalEmptyState("Nothing here", "No videos found. Pull down to refresh.")
                            } else {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(visible, key = { "yt_${it.id}" }) { video ->
                                        YouTubeVideoCard(
                                            item = video,
                                            onClick = { onSelectVideo(video, visible) },
                                            onChannelClick = video.channelUrl.takeIf { it.isNotBlank() }
                                                ?.let { { nav.openChannelOf(video) } }
                                        )
                                    }
                                    if (loadingMore) {
                                        item(key = "loading_more") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    strokeWidth = 2.dp,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                OnReachEnd(listState) { loadMore() }
                            }
                        }
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
                onClose = onClosePlayer,
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

/** Subscriptions + the most-watched channels, used to mix personal uploads into the feed. */
private fun personalChannels(): List<String> {
    val subs = LibraryRepository.subscriptions.value.map { it.name }
    val watched = LibraryRepository.history.value
        .filter { it.contentType == MediaContentType.YOUTUBE_VIDEO }
        .groupingBy { it.artistOrChannel }.eachCount()
        .entries.sortedByDescending { it.value }.map { it.key }
    return (subs + watched).filter { it.isNotBlank() }.distinct().take(3)
}

@Composable
private fun YouTubeHeader(onSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 12.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            IconButton(onClick = { /* Notifications arrive in a later phase */ }) {
                Icon(Icons.Outlined.Notifications, "Notifications", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, "Search", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/**
 * You tab: recent history, then your library (history, playlists, downloads, Watch later, liked)
 * and an import from a Google Takeout export. Everything is stored on this device.
 */
@Composable
private fun YouTab(nav: YouTubeNav, onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by LibraryRepository.saved.collectAsState()
    val downloads by LibraryRepository.downloads.collectAsState()
    val history by LibraryRepository.history.collectAsState()
    val liked by LibraryRepository.liked.collectAsState()
    val playlists by LibraryRepository.playlists.collectAsState()
    val pendingDownloads by DownloadRepository.pending.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val message = withContext(Dispatchers.IO) { importTakeoutFile(context, uri) }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (history.isNotEmpty()) {
            item(key = "history_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "View all",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF3EA6FF),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { nav.open(Route.History) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            item(key = "history_strip") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(history.take(10), key = { "hs_${it.id}" }) { video ->
                        Column(
                            modifier = Modifier
                                .width(160.dp)
                                .clickable { onSelectVideo(video, history) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OriginalColors.Chip)
                            ) {
                                if (video.artworkUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = video.artworkUrl,
                                        contentDescription = video.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = video.title,
                                fontSize = 13.sp,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = video.artistOrChannel,
                                fontSize = 12.sp,
                                color = OriginalColors.TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            item(key = "history_gap") { Spacer(modifier = Modifier.height(8.dp)) }
        }

        item(key = "row_history") { LibraryRow(Icons.Outlined.History, "History", history.size) { nav.open(Route.History) } }
        item(key = "row_playlists") { LibraryRow(Icons.Outlined.PlaylistPlay, "Playlists", playlists.size) { nav.open(Route.Playlists) } }
        item(key = "row_downloads") {
            LibraryRow(Icons.Outlined.FileDownload, "Downloads", downloads.size + pendingDownloads.size) { nav.open(Route.Downloads) }
        }
        item(key = "row_watch_later") { LibraryRow(Icons.Outlined.WatchLater, "Watch later", saved.size) { nav.open(Route.WatchLater) } }
        item(key = "row_liked") { LibraryRow(Icons.Outlined.ThumbUp, "Liked videos", liked.size) { nav.open(Route.Liked) } }
        item(key = "row_import") {
            LibraryRow(Icons.Outlined.FileUpload, "Import from Google Takeout", null) { picker.launch("*/*") }
        }
    }
}

@Composable
private fun LibraryRow(icon: ImageVector, title: String, count: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(20.dp))
        Text(text = title, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (count != null) {
            Text(text = count.toString(), fontSize = 14.sp, color = OriginalColors.TextSecondary)
        }
    }
}

/**
 * Imports a file from a Google Takeout export: watch-history.json (a JSON list) or
 * subscriptions.csv. Runs off the main thread; returns a message to show the user.
 */
private fun importTakeoutFile(context: Context, uri: Uri): String {
    val text = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull() ?: return "Couldn't read that file."
    val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    return if (trimmed.startsWith("[")) {
        val items = TakeoutParsing.parseWatchHistory(trimmed)
        if (items.isEmpty()) {
            "No watch history found in that file."
        } else {
            "Imported ${LibraryRepository.importHistory(items)} videos into watch history."
        }
    } else {
        val subs = TakeoutParsing.parseSubscriptionsCsv(trimmed)
        if (subs.isEmpty()) {
            "Choose subscriptions.csv or watch-history.json from your Google Takeout export."
        } else {
            "Imported ${LibraryRepository.importSubscriptions(subs)} new subscriptions."
        }
    }
}
