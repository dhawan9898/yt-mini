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
import com.nexusstream.app.ui.theme.DynamicMeshBackground
import com.nexusstream.app.ui.theme.liquidGlassSurface

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

    DynamicMeshBackground(
        dominantColor = mediaItem.dominantColor,
        secondaryColor = mediaItem.secondaryColor,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // TOP BAR: Collapse Chevron & Options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(40.dp)
                        .liquidGlassSurface(
                            shape = CircleShape,
                            backgroundColor = Color.White.copy(alpha = 0.1f),
                            borderWidth = 0.8.dp
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = "YouTube Video Player",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f)
                )

                IconButton(onClick = { /* Options */ }) {
                    Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }

            // 16:9 Video Canvas Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(0.dp),
                        backgroundColor = Color.Black,
                        borderWidth = 0.dp
                    )
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
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Video Metadata Title & Expandable Description Box
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(
                                shape = RoundedCornerShape(20.dp),
                                backgroundColor = Color(0xFF14141B).copy(alpha = 0.65f),
                                borderWidth = 1.dp
                            )
                            .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                            .padding(14.dp)
                    ) {
                        Text(
                            text = mediaItem.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = listOf("${mediaItem.viewsOrPlays} views", mediaItem.publishDate).filter { it.isNotBlank() }.joinToString(" • "),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )

                        if (isDescriptionExpanded && mediaItem.description.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = mediaItem.description,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Action Buttons Row (Like, Share, Download, Save)
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            // Like Glass Pill
                            Row(
                                modifier = Modifier
                                    .liquidGlassSurface(
                                        shape = RoundedCornerShape(20.dp),
                                        backgroundColor = if (isLiked) Color(0xFFFF4757).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                        borderWidth = 0.8.dp
                                    )
                                    .clickable { isLiked = !isLiked }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ThumbUp,
                                    contentDescription = "Like",
                                    tint = if (isLiked) Color(0xFFFF4757) else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mediaItem.likesCount,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        item {
                            // Share Glass Pill
                            Row(
                                modifier = Modifier
                                    .liquidGlassSurface(
                                        shape = RoundedCornerShape(20.dp),
                                        backgroundColor = Color.White.copy(alpha = 0.08f),
                                        borderWidth = 0.8.dp
                                    )
                                    .clickable { /* Share action */ }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Rounded.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Share", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }
                        }

                        item {
                            // Download Offline Glass Pill
                            Row(
                                modifier = Modifier
                                    .liquidGlassSurface(
                                        shape = RoundedCornerShape(20.dp),
                                        backgroundColor = Color.White.copy(alpha = 0.08f),
                                        borderWidth = 0.8.dp
                                    )
                                    .clickable { /* Offline Download */ }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Rounded.Download, contentDescription = "Download", tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Download", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }
                        }

                        item {
                            // Save Playlist Pill
                            Row(
                                modifier = Modifier
                                    .liquidGlassSurface(
                                        shape = RoundedCornerShape(20.dp),
                                        backgroundColor = Color.White.copy(alpha = 0.08f),
                                        borderWidth = 0.8.dp
                                    )
                                    .clickable { /* Save */ }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Rounded.BookmarkBorder, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Save", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }
                        }
                    }
                }

                // Channel Info Bar
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(
                                shape = RoundedCornerShape(22.dp),
                                backgroundColor = Color(0xFF14141B).copy(alpha = 0.65f),
                                borderWidth = 1.dp
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
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

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mediaItem.artistOrChannel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "1.42M subscribers",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        // Subscribe Pill Button
                        Box(
                            modifier = Modifier
                                .liquidGlassSurface(
                                    shape = RoundedCornerShape(18.dp),
                                    backgroundColor = if (isSubscribed) Color.White.copy(alpha = 0.15f) else Color.White,
                                    borderWidth = 1.dp
                                )
                                .clickable { isSubscribed = !isSubscribed }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isSubscribed) "Subscribed" else "Subscribe",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSubscribed) Color.White else Color.Black
                            )
                        }
                    }
                }

                // Comments Section Preview Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(
                                shape = RoundedCornerShape(20.dp),
                                backgroundColor = Color(0xFF14141B).copy(alpha = 0.65f),
                                borderWidth = 1.dp
                            )
                            .clickable { isCommentsExpanded = !isCommentsExpanded }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Comments",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "1.2K",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }

                            Icon(
                                imageVector = Icons.Rounded.Comment,
                                contentDescription = "Comments",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Featured Comment snippet
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(mediaItem.secondaryColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Incredible spatial sound composition! Loving the ad-free premium experience.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Up Next Video Recommendations Title
                item {
                    Text(
                        text = "Up Next",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                // Recommended Videos
                items(recommendedVideos, key = { "rec_${it.id}" }) { rec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .liquidGlassSurface(
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = Color(0xFF14141B).copy(alpha = 0.55f),
                                borderWidth = 0.8.dp
                            )
                            .clickable { onSelectVideo(rec) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(rec.dominantColor)
                        ) {
                            if (rec.artworkUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = rec.artworkUrl,
                                    contentDescription = rec.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rec.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${rec.artistOrChannel} • ${rec.viewsOrPlays}",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}
