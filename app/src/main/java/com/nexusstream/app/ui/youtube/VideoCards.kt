package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.theme.bouncyClickable

/**
 * Large feed card: thumbnail, avatar, title, "channel ▷ views age", ⋮ menu.
 * [onChannelClick] (avatar / channel name / menu "Go to channel") is optional.
 */
@Composable
internal fun YouTubeVideoCard(
    item: MediaItemModel,
    onClick: () -> Unit,
    /** Inset, rounded thumbnail (used for "Up next"); the home feed is edge to edge. */
    inset: Boolean = false,
    onChannelClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove",
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable(onClickLabel = "Play ${item.title}", pressedScale = 0.98f, onClick = onClick)
    ) {
        Thumbnail(item, inset)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(item.dominantColor)
                    .then(if (onChannelClick != null) Modifier.clickable(onClick = onChannelClick) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                if (item.channelAvatarUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.channelAvatarUrl,
                        contentDescription = item.artistOrChannel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = item.artistOrChannel.take(1),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = OriginalColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                MetaLine(item)
            }

            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = "Options",
                    tint = OriginalColors.TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    if (showMenu) {
        VideoMenuSheet(
            item = item,
            onDismiss = { showMenu = false },
            onOpenChannel = onChannelClick,
            onRemove = onRemove,
            removeLabel = removeLabel
        )
    }
}

/** Small horizontal row used in playlists and other dense lists. */
@Composable
internal fun CompactVideoRow(
    item: MediaItemModel,
    onClick: () -> Unit,
    onChannelClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove"
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClickLabel = "Play ${item.title}", pressedScale = 0.98f, onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.width(160.dp)) {
            Thumbnail(item, inset = false, rounded = true)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 14.sp,
                color = OriginalColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = listOf(item.artistOrChannel, item.viewsOrPlays.takeIf { it.isNotBlank() }?.let { "$it views" })
                    .filterNotNull().filter { it.isNotBlank() }.joinToString(" • "),
                fontSize = 12.sp,
                color = OriginalColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Rounded.MoreHoriz, "Options", tint = OriginalColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
    }

    if (showMenu) {
        VideoMenuSheet(
            item = item,
            onDismiss = { showMenu = false },
            onOpenChannel = onChannelClick,
            onRemove = onRemove,
            removeLabel = removeLabel
        )
    }
}

@Composable
private fun Thumbnail(item: MediaItemModel, inset: Boolean, rounded: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (inset) Modifier.padding(horizontal = 12.dp) else Modifier)
            .aspectRatio(16f / 9f)
            .then(if (inset || rounded) Modifier.clip(RoundedCornerShape(if (rounded) 8.dp else 12.dp)) else Modifier)
            .background(OriginalColors.Chip)
    ) {
        if (item.artworkUrl.isNotEmpty()) {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Some sources (channel RSS) don't report a duration; hide the badge rather than show 0:00.
        if (item.isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(OriginalColors.Red, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = "LIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else if (item.durationMs > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formatVideoDuration(item.durationMs),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        // Red bar along the bottom for a video left part way, like YouTube.
        val watched = remember(item.id) { com.nexusstream.app.data.WatchPositions.fraction(item.id) }
        if (watched > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.4f))
            ) {
                Box(modifier = Modifier.fillMaxWidth(watched).fillMaxHeight().background(OriginalColors.Red))
            }
        }
    }
}

/** "Channel  ▷ 456K  1d ago", like the original feed. */
@Composable
private fun MetaLine(item: MediaItemModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = item.artistOrChannel,
            fontSize = 13.sp,
            color = OriginalColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (item.viewsOrPlays.isNotBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = OriginalColors.TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Text(text = item.viewsOrPlays, fontSize = 13.sp, color = OriginalColors.TextSecondary, maxLines = 1)
        }
        if (item.publishDate.isNotBlank()) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = item.publishDate, fontSize = 13.sp, color = OriginalColors.TextSecondary, maxLines = 1)
        }
    }
}

internal fun formatVideoDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
