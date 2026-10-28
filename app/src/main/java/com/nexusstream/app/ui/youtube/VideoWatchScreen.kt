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
import com.nexusstream.app.data.DislikeApi
import com.nexusstream.app.data.AppSettings
import com.nexusstream.app.cast.CastEffect
import com.nexusstream.app.cast.CastButton
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.filled.ThumbDown
import com.nexusstream.app.ui.components.OriginalFilterChips
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyListScope
import android.content.res.Configuration
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
import com.nexusstream.app.ui.theme.SystemBarIcons

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
    val dislikedIds by LibraryRepository.disliked.collectAsState()
    val isDisliked = mediaItem.id in dislikedIds
    val showDislikeCounts by AppSettings.showDislikeCounts.collectAsState()
    var dislikeCount by remember(mediaItem.id, showDislikeCounts) { mutableStateOf<Long?>(null) }
    LaunchedEffect(mediaItem.id, showDislikeCounts) {
        // Only asks the Return YouTube Dislike service when the setting is on.
        dislikeCount = if (showDislikeCounts) DislikeApi.fetch(mediaItem.id).getOrNull() else null
    }
    val likedItems by LibraryRepository.liked.collectAsState()
    val isLiked = likedItems.any { it.id == mediaItem.id }
    var showSaveSheet by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isCommentsExpanded by remember { mutableStateOf(false) }

    var isFullscreen by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    val inPip by PipController.isInPip.collectAsState()

    // Leaving the app while a video plays on this page starts picture-in-picture.
    DisposableEffect(playbackState.isPlaying, playbackState.playbackMode) {
        PipController.canAutoEnter = playbackState.isPlaying && playbackState.playbackMode == PlaybackMode.VIDEO
        onDispose { PipController.canAutoEnter = false }
    }

    // Landscape phones go fullscreen automatically; tablets / wide windows get a two-pane layout.
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val wide = configuration.screenWidthDp >= 840
    val showFullscreen = isFullscreen || (landscape && !wide)
    var relatedOnlyChannel by remember(mediaItem.id) { mutableStateOf(false) }

    FullscreenEffect(showFullscreen, lockLandscape = isFullscreen)
    // Cast: while a session is connected the video plays on the receiver.
    CastEffect(item = mediaItem, playbackService = playbackService, audioOnly = false)
    // The status bar sits over the black video area, whatever the theme is.
    SystemBarIcons(lightIcons = true, includeNavigationBar = false)
    BackHandler(enabled = isFullscreen) { isFullscreen = false }
    BackHandler(enabled = !isFullscreen, onBack = onCollapse)

    if (inPip) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            WatchPlayerViewport(
                playbackService = playbackService,
                state = playbackState,
                item = mediaItem,
                fullscreen = false,
                controlsEnabled = false,
                onCollapse = {},
                onToggleFullscreen = {},
                onSwipeUp = {},
                onSwipeDown = {},
                onOpenSettings = {},
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(OriginalColors.YouTubeBackground)
            .navigationBarsPadding()
    ) {
    Column(modifier = Modifier.weight(if (wide) 1.7f else 1f)) {
        // Video viewport on a black status-bar-height backdrop. Swipe down to minimize,
        // swipe up (or the player's fullscreen button) for fullscreen.
        if (!showFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
            ) {
                WatchPlayerViewport(
                    playbackService = playbackService,
                    state = playbackState,
                    item = mediaItem,
                    fullscreen = false,
                    controlsEnabled = true,
                    onCollapse = onCollapse,
                    onToggleFullscreen = { isFullscreen = true },
                    onSwipeUp = { isFullscreen = true },
                    onSwipeDown = onCollapse,
                    onOpenSettings = { showOptions = true },
                    storyboard = details?.storyboard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    topActions = { CastButton() }
                )
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

        val ambientMode by AppSettings.ambientMode.collectAsState()
        val ambientGlowColor = remember(mediaItem.dominantColor) {
            if (mediaItem.dominantColor == Color.Unspecified || mediaItem.dominantColor == Color.Transparent || mediaItem.dominantColor == Color.Black) {
                Color(0xFF282828)
            } else {
                mediaItem.dominantColor
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(
                    if (ambientMode) {
                        Modifier.background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ambientGlowColor.copy(alpha = 0.28f),
                                    OriginalColors.YouTubeBackground
                                ),
                                endY = 400f
                            )
                        )
                    } else Modifier
                ),
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
                        withStyle(SpanStyle(color = OriginalColors.TextPrimary, fontWeight = FontWeight.Medium)) { append("  more") }
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
                        color = OriginalColors.TextPrimary,
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
                            color = OriginalColors.TextPrimary
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
                            .background(if (isSubscribed) OriginalColors.Chip else OriginalColors.TextPrimary)
                            .clickable {
                                LibraryRepository.toggleSubscribe(mediaItem.artistOrChannel, mediaItem.channelAvatarUrl, mediaItem.channelUrl)
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = if (isSubscribed) "Subscribed" else "Subscribe",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSubscribed) OriginalColors.TextPrimary else OriginalColors.OnInverse
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { LibraryRepository.toggleLike(mediaItem) }) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = OriginalColors.TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { LibraryRepository.toggleDislike(mediaItem) }) {
                        Icon(
                            imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                            contentDescription = "Dislike",
                            tint = OriginalColors.TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    dislikeCount?.let { count ->
                        Text(
                            text = YouTubeRepository.formatCount(count),
                            fontSize = 12.sp,
                            color = OriginalColors.TextSecondary
                        )
                    }
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, mediaItem.watchUrl)
                        }
                        context.startActivity(Intent.createChooser(send, "Share"))
                    }) {
                        Icon(Icons.Outlined.Share, "Share", tint = OriginalColors.TextPrimary, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = {
                        if (downloadState !is DownloadState.Downloading) showDownloadSheet = true
                    }) {
                        when {
                            downloadState is DownloadState.Downloading -> CircularProgressIndicator(
                                progress = { downloadState.progress },
                                color = OriginalColors.TextPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                            isDownloaded -> Icon(Icons.Outlined.DownloadDone, "Downloaded", tint = OriginalColors.TextPrimary, modifier = Modifier.size(24.dp))
                            else -> Icon(Icons.Outlined.FileDownload, "Download", tint = OriginalColors.TextPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                    IconButton(onClick = { showSaveSheet = true }) {
                        Icon(
                            imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = if (isSaved) "Saved" else "Save",
                            tint = OriginalColors.TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { showOptions = true }) {
                        Icon(Icons.Rounded.MoreHoriz, "More", tint = OriginalColors.TextPrimary, modifier = Modifier.size(24.dp))
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
                            color = OriginalColors.TextPrimary
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
                                color = OriginalColors.TextPrimary,
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

            // Videos queued with "Play next" / "Add to queue"
            if (playbackState.queue.size > 1) {
                item(key = "queue") {
                    WatchQueueCard(
                        queue = playbackState.queue,
                        currentIndex = playbackState.queueIndex,
                        onSelect = { index -> playbackService.playQueue(playbackState.queue, index, startInVideoMode = true) },
                        onRemove = { index -> playbackService.removeQueueItem(index) }
                    )
                }
            }

            // Up next (on wide screens it lives in the side pane instead)
            if (!wide) {
                relatedSection(
                    channelName = mediaItem.artistOrChannel,
                    related = recommendedVideos,
                    onlyChannel = relatedOnlyChannel,
                    onToggleChannel = { relatedOnlyChannel = it },
                    onSelectVideo = onSelectVideo,
                    onOpenChannel = onOpenChannel
                )
            }
        }
    }
    if (wide) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            relatedSection(
                channelName = mediaItem.artistOrChannel,
                related = recommendedVideos,
                onlyChannel = relatedOnlyChannel,
                onToggleChannel = { relatedOnlyChannel = it },
                onSelectVideo = onSelectVideo,
                onOpenChannel = onOpenChannel
            )
        }
    }
    }

    if (showFullscreen) {
        FullscreenWatchLayout(
            playbackService = playbackService,
            state = playbackState,
            item = mediaItem,
            nextVideo = recommendedVideos.firstOrNull { it.id != mediaItem.id },
            onExit = { isFullscreen = false },
            onOpenSettings = { showOptions = true },
            onOpenComments = {
                isFullscreen = false
                isCommentsExpanded = true
            },
            onSave = { showSaveSheet = true },
            storyboard = details?.storyboard
        )
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
            onDismiss = { showOptions = false },
            showAudioQuality = false,
            showDownload = false
        )
    }

    if (showDownloadSheet) {
        // The Download button opens only the download choices, not the player settings.
        PlayerOptionsSheet(
            item = mediaItem,
            state = playbackState,
            service = null,
            showVideoQuality = false,
            onDismiss = { showDownloadSheet = false }
        )
    }

    if (showChapters) {
        val chapters = details?.chapters.orEmpty()
        ModalBottomSheet(
            onDismissRequest = { showChapters = false },
            containerColor = OriginalColors.Sheet,
            contentColor = OriginalColors.TextPrimary
        ) {
            LazyColumn(modifier = Modifier.fillMaxHeight(0.7f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        text = "Chapters",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OriginalColors.TextPrimary,
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
                        Text(text = chapter.title, fontSize = 14.sp, color = OriginalColors.TextPrimary)
                    }
                }
            }
        }
    }

    if (showComments) {
        ModalBottomSheet(
            onDismissRequest = { showComments = false },
            containerColor = OriginalColors.Sheet,
            contentColor = OriginalColors.TextPrimary
        ) {
            Column(modifier = Modifier.fillMaxHeight(0.8f)) {
                Text(
                    text = "Comments",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = OriginalColors.TextPrimary,
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

/** "Up next": optional "All / From this channel" chips, then the related videos. */
private fun LazyListScope.relatedSection(
    channelName: String,
    related: List<MediaItemModel>,
    onlyChannel: Boolean,
    onToggleChannel: (Boolean) -> Unit,
    onSelectVideo: (MediaItemModel) -> Unit,
    onOpenChannel: (MediaItemModel) -> Unit
) {
    if (related.any { it.artistOrChannel == channelName }) {
        item(key = "related_chips") {
            val channelLabel = "From $channelName"
            OriginalFilterChips(
                labels = listOf("All", channelLabel),
                selected = if (onlyChannel) channelLabel else "All",
                onSelect = { onToggleChannel(it == channelLabel) },
                unselectedColor = OriginalColors.Chip
            )
        }
    }
    val shown = if (onlyChannel) related.filter { it.artistOrChannel == channelName } else related
    items(shown, key = { "rec_${it.id}" }) { rec ->
        YouTubeVideoCard(
            item = rec,
            onClick = { onSelectVideo(rec) },
            inset = true,
            onChannelClick = rec.channelUrl.takeIf { it.isNotBlank() }?.let { { onOpenChannel(rec) } }
        )
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

    // YouTube's own order ("Top") or the loaded comments by like count.
    var mostLiked by remember { mutableStateOf(false) }
    val shown = remember(items, mostLiked) { if (mostLiked) items.sortedByDescending { it.likes } else items }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "sort") {
            OriginalFilterChips(
                labels = listOf("Top", "Most liked"),
                selected = if (mostLiked) "Most liked" else "Top",
                onSelect = { mostLiked = it == "Most liked" },
                unselectedColor = OriginalColors.Chip
            )
        }
        items(shown) { comment -> CommentRow(comment) }
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
    Text(text = annotated, fontSize = 14.sp, color = OriginalColors.TextPrimary, lineHeight = 20.sp)
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
                Text(text = comment.text, fontSize = 14.sp, color = OriginalColors.TextPrimary, lineHeight = 19.sp)
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
 * Hidden system bars while [isFullscreen]; with [lockLandscape] the screen is also forced to
 * landscape (not when fullscreen is only the result of the user rotating the phone).
 * Restored when it ends or the screen leaves.
 */
@Composable
private fun FullscreenEffect(isFullscreen: Boolean, lockLandscape: Boolean) {
    val view = LocalView.current
    DisposableEffect(isFullscreen, lockLandscape) {
        val activity = view.context.findActivity()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (isFullscreen) {
            if (lockLandscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (isFullscreen) {
                if (lockLandscape) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
