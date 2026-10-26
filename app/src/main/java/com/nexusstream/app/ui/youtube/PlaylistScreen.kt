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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.Paged
import com.nexusstream.app.data.PlaylistPage
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
import kotlinx.coroutines.launch

/** A playlist: header with Play all / Shuffle, then its videos (infinite scroll). */
@Composable
fun PlaylistScreen(
    route: Route.Playlist,
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    var page by remember(route.url) { mutableStateOf<FeedState<PlaylistPage>>(FeedState.Loading) }
    var videos by remember(route.url) { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var cursor by remember(route.url) { mutableStateOf<Paged<MediaItemModel>?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(route.url, reloadKey) {
        page = FeedState.Loading
        YouTubeRepository.playlistPage(route.url, route.music).fold(
            onSuccess = {
                videos = it.videos.items
                cursor = it.videos
                page = FeedState.Data(it)
            },
            onFailure = { page = FeedState.Error(it.message ?: "Something went wrong") }
        )
    }

    fun loadMore() {
        val current = cursor ?: return
        if (!current.hasMore || loadingMore) return
        loadingMore = true
        scope.launch {
            current.next().onSuccess { next ->
                videos = (videos + next.items).distinctBy { it.id }
                cursor = next
            }
            loadingMore = false
        }
    }

    PageScaffold(title = route.title, onBack = nav.back, miniPlayer = miniPlayer, music = route.music) {
        when (val state = page) {
            is FeedState.Loading -> OriginalLoading()
            is FeedState.Error -> OriginalError(state.message, onRetry = { reloadKey++ })
            is FeedState.Data -> {
                if (videos.isEmpty()) {
                    OriginalEmptyState("Empty playlist", "There are no playable videos in this playlist.")
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "header") {
                            PlaylistHeader(
                                page = state.value,
                                onPlayAll = { onSelectVideo(videos.first(), videos) },
                                onShuffle = {
                                    val shuffled = videos.shuffled()
                                    onSelectVideo(shuffled.first(), shuffled)
                                }
                            )
                        }
                        items(videos, key = { "pv_${it.id}" }) { video ->
                            CompactVideoRow(
                                item = video,
                                onClick = { onSelectVideo(video, videos) },
                                onChannelClick = video.channelUrl.takeIf { it.isNotBlank() }
                                    ?.let { { nav.openChannelOf(video) } }
                            )
                        }
                    }
                    OnReachEnd(listState) { loadMore() }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(page: PlaylistPage, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(OriginalColors.Chip)
        ) {
            if (page.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = page.thumbnailUrl,
                    contentDescription = page.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Text(page.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary)
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = listOf(page.owner, if (page.videoCount > 0) "${page.videoCount} videos" else "")
                .filter { it.isNotBlank() }.joinToString(" • "),
            fontSize = 13.sp,
            color = OriginalColors.TextSecondary
        )
        Spacer(modifier = Modifier.size(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Play all", Icons.Rounded.PlayArrow, filled = true, onClick = onPlayAll, modifier = Modifier.weight(1f))
            PillButton("Shuffle", Icons.Rounded.Shuffle, filled = false, onClick = onShuffle, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun PillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (filled) OriginalColors.TextPrimary else OriginalColors.Chip)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (filled) OriginalColors.OnInverse else OriginalColors.TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (filled) OriginalColors.OnInverse else OriginalColors.TextPrimary)
    }
}
