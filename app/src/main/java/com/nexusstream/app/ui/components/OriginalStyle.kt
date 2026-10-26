package com.nexusstream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.IconButton
import kotlin.math.roundToInt
import kotlin.math.abs
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.player.PlayPauseMorphButton
import com.nexusstream.app.ui.theme.ThemeState

/**
 * Flat, near-black palette matching the official YouTube / YouTube Music apps.
 */
object OriginalColors {
    // Backed by the current theme palette (compose state), so these can be read anywhere.
    val YouTubeBackground: Color get() = ThemeState.palette.background
    val MusicBackground: Color get() = ThemeState.palette.musicBackground
    val Chip: Color get() = ThemeState.palette.chip
    val MusicChip: Color get() = ThemeState.palette.musicChip
    val MiniPlayer: Color get() = ThemeState.palette.surface
    val Sheet: Color get() = ThemeState.palette.surface
    val Divider: Color get() = ThemeState.palette.divider
    val TextPrimary: Color get() = ThemeState.palette.text
    val TextSecondary: Color get() = ThemeState.palette.textSecondary
    /** Text colour for use on a TextPrimary-coloured filled button. */
    val OnInverse: Color get() = ThemeState.palette.onInverse
    val Red = Color(0xFFFF0000)
}

/**
 * Horizontally scrolling filter chips: unselected = dark grey, selected = solid white with black text.
 */
@Composable
fun OriginalFilterChips(
    labels: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    unselectedColor: Color,
    modifier: Modifier = Modifier,
    /** Optional square icon chip shown before the labels (e.g. the Explore compass). */
    leadingIcon: ImageVector? = null,
    onLeadingClick: () -> Unit = {}
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (leadingIcon != null) {
            item {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(unselectedColor)
                        .clickable(onClick = onLeadingClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(leadingIcon, contentDescription = "Explore", tint = OriginalColors.TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
        items(labels) { label ->
            val isSelected = label == selected
            Box(
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) OriginalColors.TextPrimary else unselectedColor)
                    .clickable { onSelect(label) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) OriginalColors.OnInverse else OriginalColors.TextPrimary
                )
            }
        }
    }
}

/**
 * Flat docked mini player with a thin progress hairline.
 * Swipe up to expand, swipe sideways (or tap ✕) to close it and stop playback.
 */
@Composable
fun OriginalMiniPlayer(
    item: MediaItemModel,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val currentOnExpand by rememberUpdatedState(onExpand)
    val currentOnClose by rememberUpdatedState(onClose)
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(dragX.roundToInt(), 0) }
            .clip(RoundedCornerShape(10.dp))
            .background(OriginalColors.MiniPlayer)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val closeThreshold = size.width * 0.35f
                        val expandThreshold = 48.dp.toPx()
                        if (abs(dragX) > closeThreshold && currentOnClose != null) {
                            currentOnClose?.invoke()
                        } else if (dragY < -expandThreshold && abs(dragY) > abs(dragX)) {
                            currentOnExpand()
                        }
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    }
                ) { change, amount ->
                    change.consume()
                    dragX += amount.x
                    dragY += amount.y
                }
            }
            .clickable(onClick = onExpand)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(item.dominantColor)
            ) {
                if (item.artworkUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.artworkUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = OriginalColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artistOrChannel,
                    fontSize = 12.sp,
                    color = OriginalColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            PlayPauseMorphButton(
                isPlaying = isPlaying,
                onClick = onTogglePlay,
                size = 40.dp,
                tint = OriginalColors.TextPrimary,
                glowColor = Color.Transparent,
                flatBackground = OriginalColors.TextPrimary.copy(alpha = 0.10f)
            )
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close player",
                        tint = OriginalColors.TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(OriginalColors.TextPrimary.copy(alpha = 0.15f))
        )
    }
}

data class BottomNavEntry(
    val label: String,
    val icon: ImageVector,
    /** Shown instead of [icon] when this tab is selected (outline -> filled). */
    val selectedIcon: ImageVector = icon
)

/**
 * Flat bottom navigation bar.
 */
@Composable
fun OriginalBottomNav(
    entries: List<BottomNavEntry>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    background: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.forEachIndexed { index, entry ->
            val tint = if (index == selectedIndex) OriginalColors.TextPrimary else OriginalColors.TextSecondary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab
                    ) { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (index == selectedIndex) entry.selectedIcon else entry.icon,
                    contentDescription = entry.label,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.label,
                    fontSize = 10.sp,
                    color = tint,
                    fontWeight = if (index == selectedIndex) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Centered empty-state message for tabs with no content yet.
 */
@Composable
fun OriginalEmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = message, fontSize = 14.sp, color = OriginalColors.TextSecondary)
    }
}

/** Load state for a network-backed list. */
sealed interface FeedState<out T> {
    data object Loading : FeedState<Nothing>
    data class Error(val message: String) : FeedState<Nothing>
    data class Data<T>(val value: T) : FeedState<T>
}

/** Converts a [Result] into a [FeedState]. */
fun <T> Result<T>.toFeedState(): FeedState<T> = fold(
    onSuccess = { FeedState.Data(it) },
    onFailure = { FeedState.Error(it.message ?: "Something went wrong") }
)

@Composable
fun OriginalLoading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = OriginalColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun OriginalError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Couldn't load content", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = message, fontSize = 13.sp, color = OriginalColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(OriginalColors.TextPrimary)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(text = "Retry", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = OriginalColors.OnInverse)
        }
    }
}
