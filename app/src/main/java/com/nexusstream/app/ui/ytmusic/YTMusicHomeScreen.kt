package com.nexusstream.app.ui.ytmusic

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
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
import com.nexusstream.app.model.MediaCategory
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.player.PlayPauseMorphButton
import com.nexusstream.app.ui.theme.DynamicMeshBackground
import com.nexusstream.app.ui.theme.liquidGlassSurface

/**
 * YouTube Music Mode Home Screen:
 * - YT Music Discover Feed with Liquid Glassmorphism.
 * - Horizontal music carousels ("Quick Picks", "Top Charts", "Ambient Horizon").
 * - Filter pills ("Relax", "Energize", "Focus", "Commute").
 * - Docked mini-player with vector morphing play/pause.
 */
@Composable
fun YTMusicHomeScreen(
    currentPlayingItem: MediaItemModel?,
    isPlaying: Boolean,
    onSelectTrack: (MediaItemModel, List<MediaItemModel>) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { MediaRepository() }
    val categories = remember { repository.getYTMusicCategories() }
    val allItems = remember { repository.getAllItems() }
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedMood by remember { mutableStateOf("All") }

    val dominant = currentPlayingItem?.dominantColor ?: Color(0xFF6C5CE7)
    val secondary = currentPlayingItem?.secondaryColor ?: Color(0xFFFF7675)

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
            // Header
            YTMusicHeader(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                }
            )

            // Mood Pills
            if (!isSearchActive) {
                YTMusicMoodPills(
                    selectedMood = selectedMood,
                    onMoodSelected = { selectedMood = it }
                )
            }

            // Main Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isSearchActive && searchQuery.isNotBlank()) {
                    val searchResults = remember(searchQuery) { repository.search(searchQuery) }
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(searchResults, key = { "ytm_search_${it.id}" }) { track ->
                            YTMusicSearchResultRow(
                                item = track,
                                onClick = { onSelectTrack(track, searchResults) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(categories, key = { it.id }) { cat ->
                            YTMusicCategoryCarousel(
                                category = cat,
                                onSelectTrack = { track -> onSelectTrack(track, cat.items) }
                            )
                        }
                    }
                }
            }

            // Mini Player Bar
            if (currentPlayingItem != null) {
                YTMusicMiniPlayer(
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
private fun YTMusicHeader(
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
                        text = "Search songs, albums, artists...",
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
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }
        } else {
            // YT Music Premium Logo Branding
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF0000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Music",
                            fontSize = 22.sp,
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

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onToggleSearch) {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search", tint = Color.White)
                }
                IconButton(onClick = { /* Profile */ }) {
                    Icon(imageVector = Icons.Rounded.AccountCircle, contentDescription = "Profile", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun YTMusicMoodPills(
    selectedMood: String,
    onMoodSelected: (String) -> Unit
) {
    val moods = listOf("All", "Energize", "Focus", "Relax", "Workout", "Commute")

    LazyRow(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(moods) { mood ->
            val isSelected = mood == selectedMood
            Box(
                modifier = Modifier
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = if (isSelected) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                        borderWidth = if (isSelected) 1.dp else 0.5.dp
                    )
                    .clickable { onMoodSelected(mood) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = mood,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun YTMusicCategoryCarousel(
    category: MediaCategory,
    onSelectTrack: (MediaItemModel) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Category Title & Subtitle
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
            Text(
                text = category.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = category.subtitle,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Carousel
        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(category.items, key = { "carousel_${category.id}_${it.id}" }) { item ->
                YTMusicAlbumCard(
                    item = item,
                    onClick = { onSelectTrack(item) }
                )
            }
        }
    }
}

@Composable
private fun YTMusicAlbumCard(
    item: MediaItemModel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(item.dominantColor)
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (item.artworkUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Quick Play overlay button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        backgroundColor = Color.Black.copy(alpha = 0.6f),
                        borderWidth = 1.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
}

@Composable
private fun YTMusicSearchResultRow(
    item: MediaItemModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = Color(0xFF14141B).copy(alpha = 0.65f),
                borderWidth = 0.8.dp
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(item.dominantColor),
            contentAlignment = Alignment.Center
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
                text = "${item.artistOrChannel} • ${item.genre}",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = "Play",
            tint = item.dominantColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun YTMusicMiniPlayer(
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
                shape = RoundedCornerShape(22.dp),
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
