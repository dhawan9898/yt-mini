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
                    item { WatchActionChip(Icons.Rounded.Share, "Share") { } }
                    item { WatchActionChip(Icons.Rounded.Download, "Download") { } }
                    item { WatchActionChip(Icons.Rounded.BookmarkBorder, "Save") { } }
                }
            }

            // Comments card
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OriginalColors.Chip)
                        .clickable { isCommentsExpanded = !isCommentsExpanded }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Comments",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.Comment,
                        contentDescription = null,
                        tint = OriginalColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Up next: same large cards as the home feed
            items(recommendedVideos, key = { "rec_${it.id}" }) { rec ->
                YouTubeVideoCard(item = rec, onClick = { onSelectVideo(rec) })
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
