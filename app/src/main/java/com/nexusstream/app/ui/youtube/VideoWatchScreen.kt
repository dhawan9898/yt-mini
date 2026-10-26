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
    modifier: Modifier = Modifier
) {
    val playbackState by playbackService.playbackState.collectAsState()
    val mediaItem = playbackState.currentItem ?: return

    val recommendedVideos = remember(mediaItem) {
        YouTubeRepository.lastVideoFeed.filter { it.id != mediaItem.id }
    }

    val context = LocalContext.current
    val savedItems by LibraryRepository.saved.collectAsState()
    val offlineItems by LibraryRepository.downloads.collectAsState()
    val downloadStates by DownloadRepository.states.collectAsState()
    val isSaved = savedItems.any { it.id == mediaItem.id }
    val isDownloaded = offlineItems.any { it.id == mediaItem.id }
    val downloadState = downloadStates[mediaItem.id]

    var comments by remember(mediaItem.id) { mutableStateOf<FeedState<List<CommentModel>>>(FeedState.Loading) }
    var commentsReload by remember { mutableStateOf(0) }
    LaunchedEffect(mediaItem.id, commentsReload) {
        comments = FeedState.Loading
        comments = YouTubeRepository.comments(mediaItem.watchUrl).toFeedState()
    }
    var showComments by remember { mutableStateOf(false) }

    var isSubscribed by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var isCommentsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OriginalColors.YouTubeBackground)
            .navigationBarsPadding()
    ) {
        // Video viewport on a black status-bar-height backdrop, with collapse chevron overlaid.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = true
                            player = playbackService.getPlayer()
                        }
                    },
                    update = { playerView ->
                        playerView.player = playbackService.getPlayer()
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (playbackState.isResolving) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                    )
                }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = mediaItem.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        lineHeight = 21.sp,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = (listOf(
                            mediaItem.viewsOrPlays.takeIf { it.isNotBlank() }?.let { "$it views" },
                            mediaItem.publishDate.takeIf { it.isNotBlank() }
                        ).filterNotNull() + if (isDescriptionExpanded) emptyList() else listOf("...more"))
                            .joinToString("  "),
                        fontSize = 12.sp,
                        color = OriginalColors.TextSecondary
                    )
                    if (isDescriptionExpanded && mediaItem.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = mediaItem.description,
                            fontSize = 13.sp,
                            color = Color.White,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Channel row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(mediaItem.dominantColor),
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
                    Text(
                        text = mediaItem.artistOrChannel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSubscribed) OriginalColors.Chip else Color.White)
                            .clickable { isSubscribed = !isSubscribed }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isSubscribed) "Subscribed" else "Subscribe",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSubscribed) Color.White else Color.Black
                        )
                    }
                }
            }

            // Action chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        WatchActionChip(
                            icon = Icons.Rounded.ThumbUp,
                            label = if (isLiked) "Liked" else "Like",
                            highlighted = isLiked,
                            onClick = { isLiked = !isLiked }
                        )
                    }
                    item {
                        WatchActionChip(Icons.Rounded.Share, "Share") {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, mediaItem.watchUrl)
                            }
                            context.startActivity(Intent.createChooser(send, "Share"))
                        }
                    }
                    item {
                        WatchActionChip(
                            icon = Icons.Rounded.Download,
                            label = when {
                                isDownloaded -> "Downloaded"
                                downloadState is DownloadState.Downloading ->
                                    "${(downloadState.progress * 100).toInt()}%"
                                downloadState is DownloadState.Error -> "Retry"
                                else -> "Download"
                            },
                            highlighted = isDownloaded
                        ) {
                            when {
                                isDownloaded -> DownloadRepository.remove(mediaItem.id)
                                downloadState is DownloadState.Downloading -> Unit
                                else -> DownloadRepository.start(context, mediaItem)
                            }
                        }
                    }
                    item {
                        WatchActionChip(
                            icon = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            label = if (isSaved) "Saved" else "Save",
                            highlighted = isSaved
                        ) { LibraryRepository.toggleSave(mediaItem) }
                    }
                }
            }

            // Comments card with the top comment as a preview
            item {
                val topComment = (comments as? FeedState.Data)?.value?.firstOrNull()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OriginalColors.Chip)
                        .clickable { showComments = true }
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Comments",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        (comments as? FeedState.Data)?.value?.size?.takeIf { it > 0 }?.let {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "$it+", fontSize = 12.sp, color = OriginalColors.TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        topComment != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CommentAvatar(topComment, 24.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = topComment.text,
                                fontSize = 13.sp,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        comments is FeedState.Loading -> Text("Loading comments...", fontSize = 13.sp, color = OriginalColors.TextSecondary)
                        comments is FeedState.Error -> Text("Comments unavailable", fontSize = 13.sp, color = OriginalColors.TextSecondary)
                        else -> Text("No comments yet", fontSize = 13.sp, color = OriginalColors.TextSecondary)
                    }
                }
            }

            // Up next: same large cards as the home feed
            items(recommendedVideos, key = { "rec_${it.id}" }) { rec ->
                YouTubeVideoCard(item = rec, onClick = { onSelectVideo(rec) })
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
                    is FeedState.Data -> if (state.value.isEmpty()) {
                        OriginalEmptyState("No comments", "Comments may be turned off for this video.")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            items(state.value) { comment -> CommentRow(comment) }
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

@Composable
private fun WatchActionChip(
    icon: ImageVector,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(OriginalColors.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (highlighted) Color(0xFF3EA6FF) else Color.White,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}
