package com.nexusstream.app.ui.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
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

enum class FeedTab {
    MUSIC_DISCOVER,
    VIDEO_FEED
}

/**
 * Unified Feed System:
 * - Fluid dual-tab navigation ("Music Discover" and "Video Feed")
 * - 3D physical depth tilt illusion (rotationX, rotationY) on scroll velocity
 * - Ambient backdrop glow echoing each card's dominant color palette
 * - Search bar with instant real-time filtering
 * - Bottom floating Liquid Glass Mini-Player
 */
@Composable
fun FeedScreen(
    currentPlayingItem: MediaItemModel?,
    isPlaying: Boolean,
    onItemClick: (MediaItemModel, List<MediaItemModel>, PlaybackMode) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(FeedTab.MUSIC_DISCOVER) }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val repository = remember { MediaRepository() }
    val allItems = remember { repository.getAllItems() }
    val videoItems = remember { repository.getVideoFeed() }
    val searchResults = remember(searchQuery) { repository.search(searchQuery) }

    val sampleDominant = currentPlayingItem?.dominantColor ?: Color(0xFF6C5CE7)
    val sampleSecondary = currentPlayingItem?.secondaryColor ?: Color(0xFFFF7675)

    DynamicMeshBackground(
        dominantColor = sampleDominant,
        secondaryColor = sampleSecondary,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header: Branding & Search Toggle
            FeedHeader(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                }
            )

            if (!isSearchActive) {
                // Dynamic Liquid Glass Tab Bar
                FeedTabBar(
                    activeTab = activeTab,
                    onTabSelect = { activeTab = it }
                )
            }

            // Dynamic Content Switcher with Liquid Morph transition
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isSearchActive && searchQuery.isNotBlank()) {
                    SearchResultsList(
                        results = searchResults,
                        onSelectTrack = { item -> onItemClick(item, searchResults, PlaybackMode.AUDIO_ONLY) }
                    )
                } else {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            (scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            ) + fadeIn(tween(350))) togetherWith (scaleOut(
                                targetScale = 1.05f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeOut(tween(200)))
                        },
                        label = "FeedTabContent"
                    ) { tab ->
                        when (tab) {
                            FeedTab.MUSIC_DISCOVER -> MusicDiscoverList(
                                tracks = allItems,
                                onSelectTrack = { item -> onItemClick(item, allItems, PlaybackMode.AUDIO_ONLY) }
                            )
                            FeedTab.VIDEO_FEED -> VideoFeedList(
                                videos = videoItems,
                                onSelectVideo = { item -> onItemClick(item, videoItems, PlaybackMode.VIDEO) }
                            )
                        }
                    }
                }
            }

            // Bottom Floating Mini-Player bar
            if (currentPlayingItem != null) {
                FloatingMiniPlayer(
                    item = currentPlayingItem,
                    isPlaying = isPlaying,
                    onExpand = onExpandPlayer,
                    onTogglePlay = onTogglePlayPause,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun FeedHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchActive) {
            // Interactive Search Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .liquidGlassSurface(
                        shape = RoundedCornerShape(23.dp),
                        backgroundColor = Color(0xFF14141B).copy(alpha = 0.75f),
                        borderWidth = 1.dp
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search tracks, artists, genres...",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(44.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        backgroundColor = Color.White.copy(alpha = 0.08f),
                        borderWidth = 0.8.dp
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close Search",
                    tint = Color.White
                )
            }
        } else {
            Column {
                Text(
                    text = "NexusStream",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Ad-free • Infinite Glass Flow",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(44.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        backgroundColor = Color.White.copy(alpha = 0.08f),
                        borderWidth = 0.8.dp
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Liquid glass pill tab bar.
 */
@Composable
private fun FeedTabBar(
    activeTab: FeedTab,
    onTabSelect: (FeedTab) -> Unit
) {
    val isVideo = activeTab == FeedTab.VIDEO_FEED
    val tabOffset by animateFloatAsState(
        targetValue = if (isVideo) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "FeedTabOffset"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(280.dp)
                .height(44.dp)
                .liquidGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = Color.Black.copy(alpha = 0.40f),
                    borderWidth = 0.8.dp,
                    blurRadius = 16.dp
                )
                .padding(3.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val tabWidth = maxWidth / 2

                // Sliding fluid pill background
                Box(
                    modifier = Modifier
                        .width(tabWidth)
                        .fillMaxSize()
                        .offset(x = tabWidth * tabOffset)
                        .liquidGlassSurface(
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = Color.White.copy(alpha = 0.20f),
                            borderWidth = 1.dp
                        )
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelect(FeedTab.MUSIC_DISCOVER) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = if (!isVideo) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Music",
                                fontSize = 13.sp,
                                fontWeight = if (!isVideo) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isVideo) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelect(FeedTab.VIDEO_FEED) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = null,
                                tint = if (isVideo) Color.White else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Videos",
                                fontSize = 13.sp,
                                fontWeight = if (isVideo) FontWeight.Bold else FontWeight.Medium,
                                color = if (isVideo) Color.White else Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Music discover section with 3D tilt perspective scrolling.
 */
@Composable
private fun MusicDiscoverList(
    tracks: List<MediaItemModel>,
    onSelectTrack: (MediaItemModel) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(tracks, key = { it.id }) { item ->
            TiltParallaxGlassCard(
                item = item,
                listState = listState,
                onClick = { onSelectTrack(item) }
            )
        }
    }
}

/**
 * Video feed section with widescreen ambient aspect cards.
 */
@Composable
private fun VideoFeedList(
    videos: List<MediaItemModel>,
    onSelectVideo: (MediaItemModel) -> Unit
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(videos, key = { "vid_${it.id}" }) { item ->
            TiltParallaxVideoCard(
                item = item,
                listState = listState,
                onClick = { onSelectVideo(item) }
            )
        }
    }
}

/**
 * Real-time Search Results List.
 */
@Composable
private fun SearchResultsList(
    results: List<MediaItemModel>,
    onSelectTrack: (MediaItemModel) -> Unit
) {
    if (results.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No tracks or videos match your search",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(results, key = { "search_${it.id}" }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            backgroundColor = Color(0xFF14141B).copy(alpha = 0.65f),
                            borderWidth = 0.8.dp
                        )
                        .clickable { onSelectTrack(item) }
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
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.artistOrChannel} • ${item.genre}",
                            fontSize = 13.sp,
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
        }
    }
}

/**
 * 3D Physical Depth Tilt Card with Coil AsyncImage and ambient backdrop glow.
 */
@Composable
private fun TiltParallaxGlassCard(
    item: MediaItemModel,
    listState: LazyListState,
    onClick: () -> Unit
) {
    val scrollOffset by remember {
        derivedStateOf {
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.id }
            if (itemInfo != null) {
                val viewportHeight = listState.layoutInfo.viewportEndOffset.toFloat()
                val centerOffset = (itemInfo.offset + itemInfo.size / 2) - (viewportHeight / 2)
                (centerOffset / viewportHeight).coerceIn(-1f, 1f)
            } else {
                0f
            }
        }
    }

    val tiltX = scrollOffset * 8.5f
    val tiltY = scrollOffset * 2.2f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationX = tiltX
                rotationY = tiltY
                cameraDistance = 14f * density
            }
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = item.dominantColor.copy(alpha = 0.5f),
                spotColor = item.dominantColor.copy(alpha = 0.5f)
            )
            .liquidGlassSurface(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xFF14141A).copy(alpha = 0.62f),
                borderWidth = 1.dp,
                blurRadius = 24.dp
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork container with Coil Image
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(item.dominantColor, item.secondaryColor)
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
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
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${item.artistOrChannel} • ${item.viewsOrPlays}",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        backgroundColor = Color.White.copy(alpha = 0.12f),
                        borderWidth = 0.8.dp
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
    }
}

/**
 * 3D Widescreen Video Card with ambient aura and Coil video thumbnail.
 */
@Composable
private fun TiltParallaxVideoCard(
    item: MediaItemModel,
    listState: LazyListState,
    onClick: () -> Unit
) {
    val scrollOffset by remember {
        derivedStateOf {
            val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "vid_${item.id}" }
            if (itemInfo != null) {
                val viewportHeight = listState.layoutInfo.viewportEndOffset.toFloat()
                val centerOffset = (itemInfo.offset + itemInfo.size / 2) - (viewportHeight / 2)
                (centerOffset / viewportHeight).coerceIn(-1f, 1f)
            } else {
                0f
            }
        }
    }

    val tiltX = scrollOffset * 6.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationX = tiltX
                cameraDistance = 14f * density
            }
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = item.dominantColor.copy(alpha = 0.4f),
                spotColor = item.dominantColor.copy(alpha = 0.4f)
            )
            .liquidGlassSurface(
                shape = RoundedCornerShape(26.dp),
                backgroundColor = Color(0xFF131318).copy(alpha = 0.68f),
                borderWidth = 1.dp
            )
            .clickable(onClick = onClick)
    ) {
        // Video Preview Thumb with 16:9 ratio and Coil Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(item.dominantColor, item.secondaryColor, Color(0xFF09090D))
                    )
                ),
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

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .liquidGlassSurface(
                        shape = CircleShape,
                        backgroundColor = Color.Black.copy(alpha = 0.55f),
                        borderWidth = 1.2.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Play Video",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Title and channel info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${item.artistOrChannel} • ${item.viewsOrPlays} views",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Docked Floating Mini-Player bar with liquid glass styling and Coil artwork thumbnail.
 */
@Composable
private fun FloatingMiniPlayer(
    item: MediaItemModel,
    isPlaying: Boolean,
    onExpand: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(68.dp)
            .liquidGlassSurface(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color(0xFF14141B).copy(alpha = 0.75f),
                borderWidth = 1.dp,
                borderBrush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.45f), item.dominantColor.copy(alpha = 0.35f))
                ),
                blurRadius = 32.dp
            )
            .clickable(onClick = onExpand)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork Pill
            Box(
                modifier = Modifier
                    .size(44.dp)
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
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
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

            // Morphing play/pause button
            PlayPauseMorphButton(
                isPlaying = isPlaying,
                onClick = onTogglePlay,
                size = 46.dp,
                glowColor = item.dominantColor
            )
        }
    }
}
