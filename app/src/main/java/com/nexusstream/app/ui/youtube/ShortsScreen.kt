package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.flow.first
import androidx.compose.ui.unit.Dp
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
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
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
import com.nexusstream.app.ui.components.VideoSurface
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.toFeedState
import kotlinx.coroutines.launch

/**
 * Shorts: a vertical pager of short videos. Only the page on screen plays (through the shared
 * player, looping); swiping to the next one starts it. Leaving the tab stops playback, and leaving
 * the app pauses it (Shorts never play in the background); coming back resumes it.
 */
/**
 * Where you are in Shorts for this run of the app: the feed loaded so far, the short on screen
 * and how far into it. Going to another tab or page and back continues from here; it starts
 * fresh only when the app is closed and opened again (or on pull to retry).
 */
internal object ShortsSession {
    var items: List<MediaItemModel>? = null
    var cursor: Paged<MediaItemModel>? = null
    var page = 0
    var videoId: String? = null
    var positionMs = 0L

    fun clear() {
        items = null
        cursor = null
        page = 0
        videoId = null
        positionMs = 0L
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ShortsScreen(
    playbackService: MediaPlaybackService?,
    onOpenChannel: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier,
    /** Space the floating navigation bar takes at the bottom; the video runs underneath it. */
    bottomInset: Dp = 0.dp
) {
    var feed by remember {
        mutableStateOf<FeedState<List<MediaItemModel>>>(
            (ShortsSession.items ?: YouTubeRepository.cachedShorts()?.items)?.let { FeedState.Data(it) } ?: FeedState.Loading
        )
    }
    var cursor by remember { mutableStateOf(ShortsSession.cursor ?: YouTubeRepository.cachedShorts()) }
    var loadingMore by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        if (reloadKey == 0 && feed is FeedState.Data) return@LaunchedEffect
        feed = FeedState.Loading
        YouTubeRepository.shortsFeed(
            seedChannels = LibraryRepository.subscriptions.value.map { it.channelUrl } +
                LibraryRepository.watchHistory().map { it.channelUrl }.distinct().take(6),
            refresh = reloadKey > 0
        ).fold(
            onSuccess = {
                cursor = it
                feed = FeedState.Data(it.items)
                ShortsSession.clear()
                ShortsSession.items = it.items
                ShortsSession.cursor = it
            },
            onFailure = { feed = FeedState.Error(it.message ?: "Something went wrong") }
        )
    }

    // Leaving the Shorts tab stops whatever short is playing, remembering how far into it you were.
    DisposableEffect(playbackService) {
        onDispose {
            val state = playbackService?.playbackState?.value
            if (state?.currentItem?.id != null && state.currentItem.id == ShortsSession.videoId) {
                ShortsSession.positionMs = playbackService.getPlayer().currentPosition
            }
            playbackService?.closePlayback()
        }
    }

    // Leaving the app (home, recents, screen off) pauses the short; returning resumes it if it
    // was playing. Unlike regular videos, Shorts have no background play or picture-in-picture.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playbackService) {
        val player = playbackService?.getPlayer()
        var inBackground = false
        var resumeOnReturn = false
        // A short that finishes loading while the app is away must not start playing there.
        val holdInBackground = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (inBackground && playWhenReady) {
                    resumeOnReturn = true
                    player?.pause()
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            player ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    inBackground = true
                    resumeOnReturn = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_START -> {
                    inBackground = false
                    if (resumeOnReturn) player.play()
                    resumeOnReturn = false
                }
                else -> Unit
            }
        }
        player?.addListener(holdInBackground)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.removeListener(holdInBackground)
        }
    }

    fun loadMore() {
        val current = cursor ?: return
        if (!current.hasMore || loadingMore) return
        loadingMore = true
        scope.launch {
            current.next().onSuccess { next ->
                val existing = (feed as? FeedState.Data)?.value.orEmpty()
                val all = (existing + next.items).distinctBy { it.id }
                feed = FeedState.Data(all)
                cursor = next
                ShortsSession.items = all
                ShortsSession.cursor = next
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
                    ShortsPager(items, playbackService, onOpenChannel, bottomInset, onNearEnd = { loadMore() })
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
    bottomInset: Dp,
    onNearEnd: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = ShortsSession.page.coerceIn(0, (items.size - 1).coerceAtLeast(0)), pageCount = { items.size })
    val playback = playbackService?.playbackState?.collectAsState()?.value

    // Play the page that settled on screen, looping. Coming back to the short you left continues
    // it where it was.
    LaunchedEffect(pagerState.settledPage, playbackService) {
        val service = playbackService ?: return@LaunchedEffect
        val item = items.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        val resumeAt = if (item.id == ShortsSession.videoId) ShortsSession.positionMs else 0L
        ShortsSession.page = pagerState.settledPage
        ShortsSession.videoId = item.id
        ShortsSession.positionMs = 0L
        service.playQueue(listOf(item), 0, startInVideoMode = true)
        service.setLoopOne(true)
        service.setAutoplayQueue(emptyList())
        if (resumeAt > 1000) {
            service.playbackState.first { it.currentItem?.id == item.id && !it.isResolving && it.durationMs > 0 }
            service.getPlayer().seekTo(resumeAt)
        }
    }
    // Load the next few shorts ahead (streams, titles and covers), so swiping doesn't wait.
    val context = LocalContext.current
    LaunchedEffect(pagerState.settledPage, items.size) {
        val ahead = items.drop(pagerState.settledPage + 1).take(PRELOAD_AHEAD)
        ahead.forEachIndexed { index, next ->
            val url = next.sourceUrl.ifBlank { next.watchUrl }
            YouTubeRepository.prefetchStream(url)
            coil.Coil.imageLoader(context).enqueue(coil.request.ImageRequest.Builder(context).data(next.artworkUrl).build())
            // The very next ones also get their first seconds of video downloaded.
            if (index < PRECACHE_AHEAD) playbackService?.precacheShort(url)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage >= items.size - 3) onNearEnd()
    }

    // The next page is composed ahead, so its cover is already on screen while swiping.
    VerticalPager(state = pagerState, beyondViewportPageCount = 1, modifier = Modifier.fillMaxSize()) { page ->
        val item = items[page]
        val isActive = page == pagerState.settledPage
        ShortPage(
            item0 = item,
            isActive = isActive,
            resolving = isActive && playback?.isResolving == true,
            // Only real playback counts (not buffering), so the thumbnail stays until the first frame.
            playing = isActive && playback?.currentItem?.id == item.id && playback.isPlaying && !playback.isBuffering,
            buffering = isActive && playback?.currentItem?.id == item.id && playback.isBuffering,
            error = if (isActive && playback?.hasError == true) playback.errorMessage ?: "Couldn't play this short" else null,
            onRetry = {
                playbackService?.playQueue(listOf(item), 0, startInVideoMode = true)
                playbackService?.setLoopOne(true)
            },
            playbackService = playbackService,
            onOpenChannel = onOpenChannel,
            bottomInset = bottomInset
        )
    }
}

/** How many shorts after the one on screen are loaded ahead. */
private const val PRELOAD_AHEAD = 4
/** How many of those also get their first seconds of video downloaded. */
private const val PRECACHE_AHEAD = 2

@OptIn(UnstableApi::class)
@Composable
private fun ShortPage(
    item0: MediaItemModel,
    isActive: Boolean,
    resolving: Boolean,
    playing: Boolean,
    buffering: Boolean,
    error: String?,
    onRetry: () -> Unit,
    playbackService: MediaPlaybackService?,
    onOpenChannel: (MediaItemModel) -> Unit,
    bottomInset: Dp
) {
    val context = LocalContext.current
    // Shorts from the Shorts player's sequence arrive with only an id; their overlay fills in the rest.
    var item by remember(item0.id) { mutableStateOf(item0) }
    LaunchedEffect(item0.id) {
        if (item0.title.isBlank()) YouTubeRepository.shortInfo(item0.id).onSuccess { info ->
            item = item0.copy(title = info.title, artistOrChannel = info.artistOrChannel, channelAvatarUrl = info.channelAvatarUrl, channelUrl = info.channelUrl)
        }
    }
    val liked by LibraryRepository.liked.collectAsState()
    val disliked by LibraryRepository.disliked.collectAsState()
    val saved by LibraryRepository.saved.collectAsState()
    var showSave by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    // Once this short has really played, the thumbnail cover is removed for good (pausing keeps the frame).
    var started by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(playing) { if (playing) started = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (isActive) playbackService?.togglePlayPause()
            }
    ) {
        if (isActive && playbackService != null) {
            // The whole picture is always shown, never cropped: vertical shorts fill the width,
            // and the odd square or landscape clip gets black bars like on YouTube.
            VideoSurface(
                player = playbackService.getPlayer(),
                fill = false,
                placeholderAspect = 9f / 16f,
                modifier = Modifier.fillMaxSize()
            )
        }
        // The thumbnail covers the picture until this short has started (and on pages that are not
        // playing), so the previous short's last frame is never shown.
        if (!started) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (error != null) {
            // A short that can't play says why (YouTube blocking the network, for example)
            // instead of spinning forever.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp)
            ) {
                Text(error, color = Color.White, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Retry",
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        } else if (resolving || buffering || (isActive && !started)) {
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
                .height(220.dp + bottomInset)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, bottom = 16.dp + bottomInset),
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
                IconButton(onClick = { LibraryRepository.toggleDislike(item) }) {
                    Icon(
                        imageVector = if (item.id in disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Dislike",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { showComments = true }) {
                    Icon(Icons.Outlined.Comment, "Comments", tint = Color.White, modifier = Modifier.size(28.dp))
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
    if (showComments) ShortComments(item, onDismiss = { showComments = false })
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ShortComments(item: MediaItemModel, onDismiss: () -> Unit) {
    var sort by remember { mutableStateOf(com.nexusstream.app.data.CommentsApi.Sort.TOP) }
    var page by remember(item.id) { mutableStateOf<FeedState<com.nexusstream.app.data.CommentsPage>>(FeedState.Loading) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(item.id, sort, reload) {
        page = FeedState.Loading
        page = YouTubeRepository.comments(item.watchUrl, sort).toFeedState()
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = com.nexusstream.app.ui.components.OriginalColors.Sheet,
        contentColor = com.nexusstream.app.ui.components.OriginalColors.TextPrimary
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.75f)) {
            Text("Comments", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            when (val state = page) {
                is FeedState.Loading -> OriginalLoading()
                is FeedState.Error -> OriginalError(state.message, onRetry = { reload++ })
                is FeedState.Data -> CommentsList(state.value, sort, onSort = { sort = it })
            }
        }
    }
}
