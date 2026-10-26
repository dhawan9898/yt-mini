package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.ChannelPage
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.NotifyLevel
import com.nexusstream.app.data.Paged
import com.nexusstream.app.data.PlaylistResult
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.MiniPlayerHooks
import com.nexusstream.app.ui.components.OnReachEnd
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.PageScaffold
import com.nexusstream.app.ui.components.toFeedState
import kotlinx.coroutines.launch

private val CHANNEL_TABS = listOf("Videos", "Playlists", "About")

/** A channel: banner, avatar, Subscribe, and Videos / Playlists / About tabs with infinite scroll. */
@Composable
fun ChannelScreen(
    route: Route.Channel,
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    var page by remember(route.url) { mutableStateOf<FeedState<ChannelPage>>(FeedState.Loading) }
    var videos by remember(route.url) { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var videoCursor by remember(route.url) { mutableStateOf<Paged<MediaItemModel>?>(null) }
    var playlists by remember(route.url) { mutableStateOf<FeedState<List<PlaylistResult>>?>(null) }
    var playlistCursor by remember(route.url) { mutableStateOf<Paged<PlaylistResult>?>(null) }
    var tab by remember(route.url) { mutableStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var playlistReload by remember { mutableStateOf(0) }

    val subs by LibraryRepository.subscriptions.collectAsState()
    val hidden by LibraryRepository.hidden.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(route.url, reloadKey) {
        page = FeedState.Loading
        YouTubeRepository.channelPage(route.url, route.music).fold(
            onSuccess = {
                videos = it.videos.items
                videoCursor = it.videos
                page = FeedState.Data(it)
                LibraryRepository.markSeen(it.name)
            },
            onFailure = { page = FeedState.Error(it.message ?: "Something went wrong") }
        )
    }

    // Playlists load the first time their tab is opened.
    LaunchedEffect(tab, page, playlistReload) {
        val loaded = (page as? FeedState.Data)?.value ?: return@LaunchedEffect
        if (tab != 1 || playlists != null) return@LaunchedEffect
        playlists = FeedState.Loading
        loaded.loadPlaylists().fold(
            onSuccess = {
                playlistCursor = it
                playlists = FeedState.Data(it.items)
            },
            onFailure = { playlists = FeedState.Error(it.message ?: "Something went wrong") }
        )
    }

    fun loadMore() {
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            when (tab) {
                0 -> videoCursor?.takeIf { it.hasMore }?.next()?.onSuccess { next ->
                    videos = (videos + next.items).distinctBy { it.id }
                    videoCursor = next
                }
                1 -> playlistCursor?.takeIf { it.hasMore }?.next()?.onSuccess { next ->
                    playlists = FeedState.Data(((playlists as? FeedState.Data)?.value.orEmpty()) + next.items)
                    playlistCursor = next
                }
            }
            loadingMore = false
        }
    }

    val title = (page as? FeedState.Data)?.value?.name ?: route.name
    PageScaffold(title = title, onBack = nav.back, miniPlayer = miniPlayer, music = route.music) {
        when (val state = page) {
            is FeedState.Loading -> OriginalLoading()
            is FeedState.Error -> OriginalError(state.message, onRetry = { reloadKey++ })
            is FeedState.Data -> {
                val channel = state.value
                val subscribed = subs.any { it.name == channel.name }
                val visibleVideos = remember(videos, hidden) { videos.filter { it.id !in hidden.videoIds } }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "header") {
                        ChannelHeader(
                            channel = channel,
                            subscribed = subscribed,
                            notifyLevel = subs.firstOrNull { it.name == channel.name }?.notifyLevel,
                            onNotifyLevel = { LibraryRepository.setNotifyLevel(channel.name, it) },
                            onToggleSubscribe = {
                                LibraryRepository.toggleSubscribe(channel.name, channel.avatarUrl, channel.url)
                            }
                        )
                    }
                    item(key = "tabs") {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            CHANNEL_TABS.forEachIndexed { index, label ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { tab = index },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (tab == index) OriginalColors.TextPrimary else OriginalColors.TextSecondary,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .background(if (tab == index) OriginalColors.TextPrimary else OriginalColors.Divider)
                                    )
                                }
                            }
                        }
                    }

                    when (tab) {
                        0 -> if (visibleVideos.isEmpty()) {
                            item(key = "no_videos") { OriginalEmptyState("No videos", "This channel has no videos yet.", Modifier.height(240.dp)) }
                        } else {
                            items(visibleVideos, key = { "cv_${it.id}" }) { video ->
                                YouTubeVideoCard(item = video, onClick = { onSelectVideo(video, visibleVideos) })
                            }
                        }
                        1 -> when (val lists = playlists) {
                            null, is FeedState.Loading -> item(key = "pl_loading") { Box(Modifier.height(160.dp)) { OriginalLoading() } }
                            is FeedState.Error -> item(key = "pl_error") { Box(Modifier.height(240.dp)) { OriginalError(lists.message, onRetry = { playlists = null; playlistReload++ }) } }
                            is FeedState.Data -> if (lists.value.isEmpty()) {
                                item(key = "no_playlists") { OriginalEmptyState("No playlists", "This channel has no public playlists.", Modifier.height(240.dp)) }
                            } else {
                                items(lists.value, key = { "pl_${it.url}" }) { playlist ->
                                    PlaylistResultRow(playlist) { nav.openPlaylist(playlist.url, playlist.title) }
                                }
                            }
                        }
                        else -> item(key = "about") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("Description", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = channel.description.ifBlank { "No description." },
                                    fontSize = 14.sp,
                                    color = OriginalColors.TextPrimary,
                                    lineHeight = 20.sp
                                )
                                if (channel.subscribers.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("${channel.subscribers} subscribers", fontSize = 14.sp, color = OriginalColors.TextSecondary)
                                }
                            }
                        }
                    }

                    if (loadingMore) {
                        item(key = "loading_more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = OriginalColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
                OnReachEnd(listState) { loadMore() }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    channel: ChannelPage,
    subscribed: Boolean,
    notifyLevel: NotifyLevel?,
    onNotifyLevel: (NotifyLevel) -> Unit,
    onToggleSubscribe: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (channel.bannerUrl.isNotBlank()) {
            AsyncImage(
                model = channel.bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 5f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(channel.avatarUrl, channel.name, 72.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = OriginalColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.subscribers.isNotBlank()) {
                    Text("${channel.subscribers} subscribers", fontSize = 13.sp, color = OriginalColors.TextSecondary)
                }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (subscribed) OriginalColors.Chip else OriginalColors.TextPrimary)
                    .clickable(onClick = onToggleSubscribe)
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            ) {
                Text(
                    text = if (subscribed) "Subscribed" else "Subscribe",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (subscribed) OriginalColors.TextPrimary else OriginalColors.OnInverse
                )
            }
            if (subscribed && notifyLevel != null) {
                Spacer(modifier = Modifier.width(4.dp))
                NotifyLevelButton(level = notifyLevel, onSelect = onNotifyLevel)
            }
        }
    }
}
