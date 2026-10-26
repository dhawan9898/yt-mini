package com.nexusstream.app.ui.youtube

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Comment
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nexusstream.app.data.VideoDetails
import com.nexusstream.app.data.CommentsPage
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Bookmark
import kotlin.math.abs
import com.nexusstream.app.ui.components.PlayerOptionsSheet
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.rounded.Settings
import androidx.activity.compose.BackHandler
import android.view.MotionEvent
import android.view.GestureDetector
import android.content.pm.ActivityInfo
import android.content.ContextWrapper
import android.content.Context
import android.app.Activity
import com.nexusstream.app.ui.components.toFeedState
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.data.watchUrl
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.DownloadState
import com.nexusstream.app.data.DownloadRepository
import com.nexusstream.app.data.CommentModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.Bookmark
import android.content.Intent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.components.OriginalColors

/**
 * YouTube Mode Video Watch Page:
 * - Widescreen 16:9 Media3 PlayerView container.
 * - Interactive action bar (Like, Share, Download offline, Save).
 * - Channel subscribe card.
 * - Comments section drawer.
 * - Recommended Up Next video feed.
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoWatchScreen(
    playbackService: MediaPlaybackService,
    onCollapse: () -> Unit,
    onSelectVideo: (MediaItemModel) -> Unit,
    onOpenChannel: (MediaItemModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by playbackService.playbackState.collectAsState()
    val mediaItem = playbackState.currentItem ?: return

    var details by remember(mediaItem.id) { mutableStateOf<VideoDetails?>(null) }
    LaunchedEffect(mediaItem.id) {
        details = YouTubeRepository.details(mediaItem.watchUrl).getOrNull()
    }
    // Real related videos when available, otherwise the last home feed.
    val recommendedVideos = remember(mediaItem.id, details) {
        details?.related?.takeIf { it.isNotEmpty() }
            ?: YouTubeRepository.lastVideoFeed.filter { it.id != mediaItem.id }
    }

    val context = LocalContext.current
    val savedItems by LibraryRepository.saved.collectAsState()
    val offlineItems by LibraryRepository.downloads.collectAsState()
    val downloadStates by DownloadRepository.states.collectAsState()
    val isSaved = savedItems.any { it.id == mediaItem.id }
    val isDownloaded = offlineItems.any { it.id == mediaItem.id }
    val downloadState = downloadStates[mediaItem.id]

    var comments by remember(mediaItem.id) { mutableStateOf<FeedState<CommentsPage>>(FeedState.Loading) }
    var commentsReload by remember { mutableStateOf(0) }
    LaunchedEffect(mediaItem.id, commentsReload) {
        comments = FeedState.Loading
        comments = YouTubeRepository.comments(mediaItem.watchUrl).toFeedState()
    }
    var showComments by remember { mutableStateOf(false) }

    val subscriptions by LibraryRepository.subscriptions.collectAsState()
    val isSubscribed = subscriptions.any { it.name == mediaItem.artistOrChannel }
    var isLiked by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isCommentsExpanded by remember { mutableStateOf(false) }

    var isFullscreen by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }

    FullscreenEffect(isFullscreen)
    BackHandler(enabled = isFullscreen) { isFullscreen = false }
    BackHandler(enabled = !isFullscreen, onBack = onCollapse)

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OriginalColors.YouTubeBackground)
            .navigationBarsPadding()
    ) {
        // Video viewport on a black status-bar-height backdrop. Swipe down to minimize,
        // swipe up (or the player's fullscreen button) for fullscreen.
        if (!isFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
            ) {
                WatchPlayerSurface(
                    playbackService = playbackService,
                    isResolving = playbackState.isResolving,
                    onSwipeUp = { isFullscreen = true },
                    onSwipeDown = onCollapse,
                    onFullscreenClick = { isFullscreen = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Minimize",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = { showOptions = true },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Quality and download",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (playbackState.hasError) {
            Text(
                text = playbackState.errorMessage ?: "Playback error",
                fontSize = 12.sp,
                color = Color(0xFFFF6B6B),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title + stats (tap to expand description)
            item {
                val tag = details?.tags?.firstOrNull()?.takeIf { it.isNotBlank() }
                val statsLine = buildAnnotatedString {
                    val parts = listOfNotNull(
                        mediaItem.artistOrChannel.takeIf { it.isNotBlank() },
                        details?.likes?.takeIf { it >= 0 }?.let { "${YouTubeRepository.formatCount(it)} likes" },
                        (details?.views?.takeIf { it >= 0 }?.let { YouTubeRepository.formatCount(it) }
                            ?: mediaItem.viewsOrPlays.takeIf { it.isNotBlank() })?.let { "$it views" },
                        (details?.uploadedText?.takeIf { it.isNotBlank() } ?: mediaItem.publishDate.takeIf { it.isNotBlank() }),
                        tag?.let { "#${it.replace(" ", "")}" }
                    )
                    append(parts.joinToString("  "))
                    if (!isDescriptionExpanded) {
                        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) { append("  more") }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = mediaItem.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        lineHeight = 24.sp,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statsLine,
                        fontSize = 13.sp,
                        color = OriginalColors.TextSecondary,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val description = details?.description?.takeIf { it.isNotBlank() } ?: mediaItem.description
                    if (isDescriptionExpanded && description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = description,
                            fontSize = 14.sp,
                            color = Color.White,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Channel avatar + Subscribe, with icon-only actions on the right
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(mediaItem.dominantColor)
                            .clickable { onOpenChannel(mediaItem) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (mediaItem.channelAvatarUrl.isNotEmpty()) {
                            AsyncImage(
                                model = mediaItem.channelAvatarUrl,
                                contentDescription = mediaItem.artistOrChannel,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSubscribed) OriginalColors.Chip else Color(0xFFF1F1F1))
                            .clickable {
                                LibraryRepository.toggleSubscribe(mediaItem.artistOrChannel, mediaItem.channelAvatarUrl, mediaItem.channelUrl)
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = if (isSubscribed) "Subscribed" else "Subscribe",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSubscribed) Color.White else Color.Black
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { isLiked = !isLiked }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, mediaItem.watchUrl)
                        }
                        context.startActivity(Intent.createChooser(send, "Share"))
                    }) {
                        Icon(Icons.Outlined.Share, "Share", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = {
                        if (downloadState !is DownloadState.Downloading) showOptions = true
                    }) {
                        when {
                            downloadState is DownloadState.Downloading -> CircularProgressIndicator(
                                progress = { downloadState.progress },
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            isDownloaded -> Icon(Icons.Outlined.DownloadDone, "Downloaded", tint = Color.White, modifier = Modifier.size(24.dp))
                            else -> Icon(Icons.Outlined.FileDownload, "Download", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                    IconButton(onClick = { LibraryRepository.toggleSave(mediaItem) }) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isSaved) "Saved" else "Save",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { showOptions = true }) {
                        Icon(Icons.Rounded.MoreHoriz, "More", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // Comments card with the top comment as a preview
            item {
                val page = (comments as? FeedState.Data)?.value
                val topComment = page?.items?.firstOrNull()
                val total = page?.totalCount?.takeIf { it >= 0 }
                    ?: page?.items?.size?.takeIf { it > 0 }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(OriginalColors.Chip)
                        .clickable { showComments = true }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Comments",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        total?.let {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = YouTubeRepository.formatCount(it.toLong()),
                                fontSize = 14.sp,
                                color = OriginalColors.TextSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    when {
                        topComment != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CommentAvatar(topComment, 28.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = topComment.text,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        comments is FeedState.Loading -> Text("Loading comments...", fontSize = 14.sp, color = OriginalColors.TextSecondary)
                        comments is FeedState.Error -> Text("Comments unavailable", fontSize = 14.sp, color = OriginalColors.TextSecondary)
                        else -> Text("No comments yet", fontSize = 14.sp, color = OriginalColors.TextSecondary)
                    }
                }
            }

            // Up next: same large cards as the home feed
            items(recommendedVideos, key = { "rec_${it.id}" }) { rec ->
                YouTubeVideoCard(
                    item = rec,
                    onClick = { onSelectVideo(rec) },
                    inset = true,
                    onChannelClick = rec.channelUrl.takeIf { it.isNotBlank() }?.let { { onOpenChannel(rec) } }
                )
            }
        }
    }

    if (isFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            WatchPlayerSurface(
                playbackService = playbackService,
                isResolving = playbackState.isResolving,
                onSwipeUp = { },
                onSwipeDown = { isFullscreen = false },
                onFullscreenClick = { isFullscreen = false },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = { showOptions = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Quality and download",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
    }

    if (showOptions) {
        PlayerOptionsSheet(
            item = mediaItem,
            state = playbackState,
            showVideoQuality = true,
            onVideoQuality = playbackService::setVideoQuality,
            onAudioQuality = playbackService::setAudioQuality,
            onDismiss = { showOptions = false }
        )
    }

    if (showComments) {
        ModalBottomSheet(
            onDismissRequest = { showComments = false },
            containerColor = Color(0xFF212121),
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxHeight(0.8f)) {
                Text(
                    text = "Comments",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                when (val state = comments) {
                    is FeedState.Loading -> OriginalLoading()
                    is FeedState.Error -> OriginalError(
                        message = state.message,
                        onRetry = { commentsReload++ }
                    )
                    is FeedState.Data -> if (state.value.items.isEmpty()) {
                        OriginalEmptyState("No comments", "Comments may be turned off for this video.")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(state.value.items) { comment -> CommentRow(comment) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(comment: CommentModel, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(OriginalColors.Divider)
    ) {
        if (comment.avatarUrl.isNotEmpty()) {
            AsyncImage(
                model = comment.avatarUrl,
                contentDescription = comment.author,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CommentRow(comment: CommentModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        CommentAvatar(comment, 36.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = listOfNotNull(
                    if (comment.pinned) "Pinned" else null,
                    comment.author.takeIf { it.isNotBlank() },
                    comment.date.takeIf { it.isNotBlank() }
                ).joinToString(" • "),
                fontSize = 12.sp,
                color = OriginalColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = comment.text, fontSize = 14.sp, color = Color.White, lineHeight = 19.sp)
            if (comment.likes > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${comment.likes} likes", fontSize = 12.sp, color = OriginalColors.TextSecondary)
            }
        }
    }
}

/** PlayerView that reports vertical flings so the page can minimize / go fullscreen. */
@OptIn(UnstableApi::class)
private class SwipePlayerView(context: Context) : PlayerView(context) {
    var onSwipeUp: () -> Unit = {}
    var onSwipeDown: () -> Unit = {}

    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val start = e1 ?: return false
                val dy = e2.y - start.y
                val dx = e2.x - start.x
                val vertical = abs(dy) > 120f && abs(dy) > 2 * abs(dx) && abs(velocityY) > 300f
                if (!vertical) return false
                if (dy < 0) onSwipeUp() else onSwipeDown()
                return true
            }
        }
    )

    // Observe every touch without consuming it, so the controller and seek bar keep working.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun WatchPlayerSurface(
    playbackService: MediaPlaybackService,
    isResolving: Boolean,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                SwipePlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                    player = playbackService.getPlayer()
                }
            },
            update = { view ->
                view.player = playbackService.getPlayer()
                view.onSwipeUp = onSwipeUp
                view.onSwipeDown = onSwipeDown
                view.setFullscreenButtonClickListener { onFullscreenClick() }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (isResolving) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
            )
        }
    }
}

/** Landscape + hidden system bars while [isFullscreen]; restored when it ends or the screen leaves. */
@Composable
private fun FullscreenEffect(isFullscreen: Boolean) {
    val view = LocalView.current
    DisposableEffect(isFullscreen) {
        val activity = view.context.findActivity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (isFullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
