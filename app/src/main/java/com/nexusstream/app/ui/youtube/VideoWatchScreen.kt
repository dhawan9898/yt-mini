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
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableFloatStateOf
import android.view.WindowManager
import android.media.AudioManager
import kotlinx.coroutines.launch
import com.nexusstream.app.ui.components.OnReachEnd
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.data.Paged
import com.nexusstream.app.PipController
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.foundation.lazy.rememberLazyListState
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by playbackService.playbackState.collectAsState()
    val mediaItem = playbackState.currentItem ?: return

    var details by remember(mediaItem.id) { mutableStateOf<VideoDetails?>(null) }
    LaunchedEffect(mediaItem.id) {
        details = YouTubeRepository.details(mediaItem.watchUrl).getOrNull()
    }
    // Related videos are what autoplay continues with when this video ends.
    LaunchedEffect(mediaItem.id, details) {
        playbackService.setAutoplayQueue(details?.related.orEmpty())
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
    val likedItems by LibraryRepository.liked.collectAsState()
    val isLiked = likedItems.any { it.id == mediaItem.id }
    var showSaveSheet by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isCommentsExpanded by remember { mutableStateOf(false) }

    var isFullscreen by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    val inPip by PipController.isInPip.collectAsState()

    // Leaving the app while a video plays on this page starts picture-in-picture.
    DisposableEffect(playbackState.isPlaying, playbackState.playbackMode) {
        PipController.canAutoEnter = playbackState.isPlaying && playbackState.playbackMode == PlaybackMode.VIDEO
        onDispose { PipController.canAutoEnter = false }
    }

    FullscreenEffect(isFullscreen)
    BackHandler(enabled = isFullscreen) { isFullscreen = false }
    BackHandler(enabled = !isFullscreen, onBack = onCollapse)

    if (inPip) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            WatchPlayerSurface(
                playbackService = playbackService,
                isResolving = playbackState.isResolving,
                showController = false,
                onSwipeUp = {},
                onSwipeDown = {},
                onFullscreenClick = {},
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

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
                Row(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { PipController.enter?.invoke() }) {
                        Icon(
                            imageVector = Icons.Outlined.PictureInPictureAlt,
                            contentDescription = "Picture in picture",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { showOptions = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close video",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
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
                        LinkifiedText(
                            text = description,
                            onSeek = { ms -> playbackService.seekTo(ms) }
                        )
                    }
                }
            }

            // Chapters: current chapter, tap for the full list
            details?.chapters?.takeIf { it.isNotEmpty() }?.let { chapters ->
                item(key = "chapters") {
                    val current = chapters.lastOrNull { it.startMs <= playbackState.currentPositionMs } ?: chapters.first()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(OriginalColors.Chip)
                            .clickable { showChapters = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Chapters",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = current.title,
                            fontSize = 14.sp,
                            color = OriginalColors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
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

                    IconButton(onClick = { LibraryRepository.toggleLike(mediaItem) }) {
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
                    IconButton(onClick = { showSaveSheet = true }) {
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
                gesturesEnabled = true,
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

    if (showSaveSheet) {
        AddToPlaylistSheet(item = mediaItem, onDismiss = { showSaveSheet = false })
    }

    if (showOptions) {
        PlayerOptionsSheet(
            item = mediaItem,
            state = playbackState,
            service = playbackService,
            showVideoQuality = true,
            onDismiss = { showOptions = false }
        )
    }

    if (showChapters) {
        val chapters = details?.chapters.orEmpty()
        ModalBottomSheet(
            onDismissRequest = { showChapters = false },
            containerColor = Color(0xFF212121),
            contentColor = Color.White
        ) {
            LazyColumn(modifier = Modifier.fillMaxHeight(0.7f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        text = "Chapters",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(chapters) { chapter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playbackService.seekTo(chapter.startMs)
                                showChapters = false
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = formatVideoDuration(chapter.startMs),
                            fontSize = 14.sp,
                            color = Color(0xFF3EA6FF),
                            modifier = Modifier.width(64.dp)
                        )
                        Text(text = chapter.title, fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }
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
                        CommentsList(state.value)
                    }
                }
            }
        }
    }
}

/** Comments with infinite scroll; each comment can expand its replies. */
@Composable
private fun CommentsList(page: CommentsPage) {
    var items by remember(page) { mutableStateOf(page.items) }
    var cursor by remember(page) { mutableStateOf<Paged<CommentModel>?>(page.paged) }
    var loadingMore by remember(page) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun loadMore() {
        val current = cursor ?: return
        if (!current.hasMore || loadingMore) return
        loadingMore = true
        scope.launch {
            current.next().onSuccess { next ->
                items = items + next.items
                cursor = next
            }
            loadingMore = false
        }
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        items(items) { comment -> CommentRow(comment) }
        if (loadingMore) {
            item(key = "loading_more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
    OnReachEnd(listState) { loadMore() }
}

/** Description text with tappable web links and timestamps (timestamps seek the video). */
@Composable
private fun LinkifiedText(text: String, onSeek: (Long) -> Unit) {
    val currentSeek by rememberUpdatedState(onSeek)
    val annotated = remember(text) {
        buildAnnotatedString {
            var cursor = 0
            val linkStyle = SpanStyle(color = Color(0xFF3EA6FF))
            DescriptionLinks.find(text).forEach { link ->
                if (link.start < cursor) return@forEach
                append(text.substring(cursor, link.start))
                val shown = text.substring(link.start, link.end)
                when (link.kind) {
                    DescriptionLink.Kind.URL ->
                        withLink(LinkAnnotation.Url(link.value)) { withStyle(linkStyle) { append(shown) } }
                    DescriptionLink.Kind.TIMESTAMP ->
                        withLink(LinkAnnotation.Clickable("timestamp") { currentSeek(link.value.toLong()) }) {
                            withStyle(linkStyle) { append(shown) }
                        }
                }
                cursor = link.end
            }
            append(text.substring(cursor))
        }
    }
    Text(text = annotated, fontSize = 14.sp, color = Color.White, lineHeight = 20.sp)
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
private fun CommentRow(comment: CommentModel, isReply: Boolean = false) {
    var replies by remember(comment) { mutableStateOf<List<CommentModel>?>(null) }
    var loadingReplies by remember(comment) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.padding(start = if (isReply) 48.dp else 0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            CommentAvatar(comment, if (isReply) 28.dp else 36.dp)
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
                val loader = comment.loadReplies
                if (!isReply && loader != null && comment.replyCount > 0 && replies == null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (loadingReplies) "Loading replies..." else "${comment.replyCount} replies",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF3EA6FF),
                        modifier = Modifier.clickable(enabled = !loadingReplies) {
                            loadingReplies = true
                            scope.launch {
                                replies = loader().getOrDefault(emptyList())
                                loadingReplies = false
                            }
                        }
                    )
                }
            }
        }
        replies?.forEach { reply -> CommentRow(reply, isReply = true) }
    }
}

/**
 * PlayerView that reports vertical flings (minimize / fullscreen), double-tap to seek 10 s
 * (left / right third of the screen) and hold to play at 2x.
 */
@OptIn(UnstableApi::class)
private class SwipePlayerView(context: Context) : PlayerView(context) {
    var onSwipeUp: () -> Unit = {}
    var onSwipeDown: () -> Unit = {}

    /** Fullscreen: vertical drags on the left half change brightness, on the right half volume. */
    var fullscreenGestures = false
    var onAdjustBrightness: (Float) -> Unit = {}
    var onAdjustVolume: (Float) -> Unit = {}

    private var scrollingVertically: Boolean? = null
    private var boostActive = false
    private var speedBeforeBoost = 1f

    private val detector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (!fullscreenGestures) return false
                val start = e1 ?: return false
                if (scrollingVertically == null) {
                    // Decide once per touch whether this drag is vertical (brightness / volume).
                    val dx = e2.x - start.x
                    val dy = e2.y - start.y
                    if (abs(dx) < 24f && abs(dy) < 24f) return false
                    scrollingVertically = abs(dy) > abs(dx) * 1.5f
                }
                if (scrollingVertically != true) return false
                // distanceY is positive when the finger moves up.
                val fraction = distanceY / height.coerceAtLeast(1)
                if (start.x < width / 2f) onAdjustBrightness(fraction) else onAdjustVolume(fraction)
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                // In fullscreen a vertical drag adjusts brightness / volume instead.
                if (fullscreenGestures) return false
                val start = e1 ?: return false
                val dy = e2.y - start.y
                val dx = e2.x - start.x
                val vertical = abs(dy) > 120f && abs(dy) > 2 * abs(dx) && abs(velocityY) > 300f
                if (!vertical) return false
                if (dy < 0) onSwipeUp() else onSwipeDown()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val p = player ?: return false
                val third = width / 3f
                val delta = when {
                    e.x < third -> -SEEK_STEP_MS
                    e.x > 2 * third -> SEEK_STEP_MS
                    else -> return false
                }
                val duration = p.duration
                val target = p.currentPosition + delta
                p.seekTo(if (duration > 0) target.coerceIn(0L, duration) else target.coerceAtLeast(0L))
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                val p = player ?: return
                speedBeforeBoost = p.playbackParameters.speed
                boostActive = true
                p.setPlaybackSpeed(2f)
            }
        }
    )

    // Observe every touch without consuming it, so the controller and seek bar keep working.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) scrollingVertically = null
        detector.onTouchEvent(ev)
        if (boostActive && (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL)) {
            boostActive = false
            player?.setPlaybackSpeed(speedBeforeBoost)
        }
        return super.dispatchTouchEvent(ev)
    }

    private companion object {
        const val SEEK_STEP_MS = 10_000L
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
    modifier: Modifier = Modifier,
    showController: Boolean = true,
    /** Fullscreen: enables brightness (left half) and volume (right half) swipes. */
    gesturesEnabled: Boolean = false
) {
    val context = LocalContext.current
    var hud by remember { mutableStateOf<String?>(null) }
    var volumeRemainder by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(700)
            hud = null
        }
    }

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
                view.useController = showController
                view.fullscreenGestures = gesturesEnabled
                view.onAdjustBrightness = { fraction ->
                    val window = context.findActivity()?.window
                    if (window != null) {
                        val attributes = window.attributes
                        val current = if (attributes.screenBrightness < 0f) 0.5f else attributes.screenBrightness
                        val next = (current + fraction * 1.2f).coerceIn(0.02f, 1f)
                        attributes.screenBrightness = next
                        window.attributes = attributes
                        hud = "Brightness ${(next * 100).toInt()}%"
                    }
                }
                view.onAdjustVolume = { fraction ->
                    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audio != null) {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        volumeRemainder += fraction * max * 1.5f
                        val steps = volumeRemainder.toInt()
                        if (steps != 0) {
                            volumeRemainder -= steps
                            val level = (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
                        }
                        hud = "Volume ${audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max.coerceAtLeast(1)}%"
                    }
                }
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
        hud?.let { text ->
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
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
                // Give brightness back to the system setting.
                activity?.window?.let { window ->
                    window.attributes = window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
