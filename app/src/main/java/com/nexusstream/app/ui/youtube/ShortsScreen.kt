package com.nexusstream.app.ui.youtube

import android.content.Intent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.Paged
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.data.watchUrl
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.toFeedState
import kotlinx.coroutines.launch

/**
 * Shorts: a vertical pager of short videos. Only the page on screen plays (through the shared
 * player, looping); swiping to the next one starts it. Leaving the tab stops playback.
 */
@OptIn(UnstableApi::class)
@Composable
fun ShortsScreen(
    playbackService: MediaPlaybackService?,
    onOpenChannel: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var feed by remember {
        mutableStateOf<FeedState<List<MediaItemModel>>>(
            YouTubeRepository.cachedShorts()?.let { FeedState.Data(it.items) } ?: FeedState.Loading
        )
    }
    var cursor by remember { mutableStateOf(YouTubeRepository.cachedShorts()) }
    var loadingMore by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        if (reloadKey == 0 && feed is FeedState.Data) return@LaunchedEffect
        feed = FeedState.Loading
        YouTubeRepository.shortsFeed(
            seedChannels = LibraryRepository.subscriptions.value.map { it.channelUrl } +
                LibraryRepository.history.value.map { it.channelUrl }.distinct().take(6),
            refresh = reloadKey > 0
        ).fold(
            onSuccess = {
                cursor = it
                feed = FeedState.Data(it.items)
            },
            onFailure = { feed = FeedState.Error(it.message ?: "Something went wrong") }
        )
    }

    // Leaving the Shorts tab stops whatever short is playing.
    DisposableEffect(playbackService) {
        onDispose { playbackService?.closePlayback() }
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

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (val state = feed) {
            is FeedState.Loading -> OriginalLoading()
            is FeedState.Error -> OriginalError(state.message, onRetry = { reloadKey++ })
            is FeedState.Data -> {
                val items = state.value
                if (items.isEmpty()) {
                    OriginalError("No Shorts found right now.", onRetry = { reloadKey++ })
                } else {
                    ShortsPager(items, playbackService, onOpenChannel, onNearEnd = { loadMore() })
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ShortsPager(
    items: List<MediaItemModel>,
    playbackService: MediaPlaybackService?,
    onOpenChannel: (MediaItemModel) -> Unit,
    onNearEnd: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val playback = playbackService?.playbackState?.collectAsState()?.value

    // Play the page that settled on screen, looping.
    LaunchedEffect(pagerState.settledPage, playbackService) {
        val service = playbackService ?: return@LaunchedEffect
        val item = items.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        service.playQueue(listOf(item), 0, startInVideoMode = true)
        service.setLoopOne(true)
        service.setAutoplayQueue(emptyList())
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage >= items.size - 3) onNearEnd()
    }

    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val item = items[page]
        val isActive = page == pagerState.settledPage
        ShortPage(
            item = item,
            isActive = isActive,
            resolving = isActive && playback?.isResolving == true,
            playbackService = playbackService,
            onOpenChannel = onOpenChannel
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ShortPage(
    item: MediaItemModel,
    isActive: Boolean,
    resolving: Boolean,
    playbackService: MediaPlaybackService?,
    onOpenChannel: (MediaItemModel) -> Unit
) {
    val context = LocalContext.current
    val liked by LibraryRepository.liked.collectAsState()
    val saved by LibraryRepository.saved.collectAsState()
    var showSave by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (isActive) playbackService?.togglePlayPause()
            }
    ) {
        // The thumbnail shows until the video has started (and for pages that are not playing).
        AsyncImage(
            model = item.artworkUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
        if (isActive && playbackService != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = playbackService.getPlayer()
                    }
                },
                update = { it.player = playbackService.getPlayer() },
                modifier = Modifier.fillMaxSize()
            )
        }
        if (resolving) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            )
        }

        // Bottom gradient so the text stays readable over bright videos.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 8.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onOpenChannel(item) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(item.dominantColor)
                    ) {
                        if (item.channelAvatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = item.channelAvatarUrl,
                                contentDescription = item.artistOrChannel,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = item.artistOrChannel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val isLiked = liked.any { it.id == item.id }
                IconButton(onClick = { LibraryRepository.toggleLike(item) }) {
                    Icon(
                        imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { showSave = true }) {
                    Icon(
                        imageVector = if (saved.any { it.id == item.id }) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Save",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, item.watchUrl)
                    }
                    context.startActivity(Intent.createChooser(send, "Share"))
                }) {
                    Icon(Icons.Outlined.Share, "Share", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
    }

    if (showSave) AddToPlaylistSheet(item = item, onDismiss = { showSave = false })
}
