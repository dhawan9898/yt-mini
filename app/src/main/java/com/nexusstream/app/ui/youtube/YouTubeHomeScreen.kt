package com.nexusstream.app.ui.youtube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.MediaRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.ui.player.PlayPauseMorphButton
import com.nexusstream.app.ui.theme.DynamicMeshBackground
import com.nexusstream.app.ui.theme.liquidGlassSurface

/**
 * YouTube Mode Home Screen:
 * - YouTube styled interface with Unlocked Premium badge.
 * - NO Shorts shelves and NO Games/Playables sections.
 * - Horizontal topic category chips.
 * - Widescreen video cards with channel avatars and view counts.
 * - Integrated fluid glass mini-player bar.
 */
@Composable
fun YouTubeHomeScreen(
    currentPlayingItem: MediaItemModel?,
    isPlaying: Boolean,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { MediaRepository() }
    val videoFeed = remember { repository.getFilteredYouTubeFeed() }
    
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredList = remember(selectedCategory, searchQuery) {
        var result = videoFeed
        if (selectedCategory != "All") {
            result = result.filter { it.genre.equals(selectedCategory, ignoreCase = true) }
        }
        if (searchQuery.isNotBlank()) {
            result = repository.search(searchQuery).filter { !it.isShort && !it.isGame }
        }
        result
    }

    val dominant = currentPlayingItem?.dominantColor ?: Color(0xFFE50914)
    val secondary = currentPlayingItem?.secondaryColor ?: Color(0xFF6C5CE7)

    DynamicMeshBackground(
        dominantColor = dominant,
        secondaryColor = secondary,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // YouTube Header
            YouTubeHeader(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                }
            )

            // Category Chips Row
            if (!isSearchActive) {
                YouTubeCategoryChips(
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
            }

            // Video Feed List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No videos match your filter",
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredList, key = { "yt_${it.id}" }) { video ->
                            YouTubeVideoCard(
                                item = video,
                                onClick = { onSelectVideo(video, filteredList) }
                            )
                        }
                    }
                }
            }

            // Bottom Mini Player
            if (currentPlayingItem != null) {
                YouTubeMiniPlayer(
                    item = currentPlayingItem,
                    isPlaying = isPlaying,
                    onExpand = onExpandPlayer,
                    onTogglePlay = onTogglePlayPause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun YouTubeHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchActive) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(22.dp),
                        backgroundColor = Color(0xFF14141B).copy(alpha = 0.75f),
                        borderWidth = 1.dp
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search YouTube...",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onToggleSearch) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close Search", tint = Color.White)
            }
        } else {
            // YouTube Premium Branding
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF0000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "YouTube",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PREMIUM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF4757),
                            modifier = Modifier
                                .background(Color(0xFFFF4757).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Header Actions
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onToggleSearch) {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search", tint = Color.White)
                }
                IconButton(onClick = { /* Notification actions */ }) {
                    Icon(imageVector = Icons.Rounded.Notifications, contentDescription = "Notifications", tint = Color.White)
                }
                IconButton(onClick = { /* Account profile */ }) {
                    Icon(imageVector = Icons.Rounded.AccountCircle, contentDescription = "Account", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun YouTubeCategoryChips(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    val categories = listOf("All", "Synthwave", "Cyberpunk", "Ambient", "Chillout", "Electronic")

    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { cat ->
            val isSelected = cat == selectedCategory
            Box(
                modifier = Modifier
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = if (isSelected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                        borderWidth = if (isSelected) 1.dp else 0.5.dp
                    )
                    .clickable { onSelectCategory(cat) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = cat,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun YouTubeVideoCard(
    item: MediaItemModel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        // Thumbnail Aspect 16:9
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF141419))
        ) {
            if (item.artworkUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Duration badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
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

        // Details Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(item.dominantColor),
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${item.artistOrChannel} • ${item.viewsOrPlays} views • ${item.publishDate}",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = { /* Options menu */ }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun YouTubeMiniPlayer(
    item: MediaItemModel,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .liquidGlassSurface(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0xFF14141B).copy(alpha = 0.82f),
                borderWidth = 1.dp,
                blurRadius = 32.dp
            )
            .clickable(onClick = onExpand)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.artistOrChannel,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            PlayPauseMorphButton(
                isPlaying = isPlaying,
                onClick = onTogglePlay,
                size = 42.dp,
                glowColor = item.dominantColor
            )
        }
    }
}

private fun formatVideoDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%02d:%02d".format(min, sec)
}
