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
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.model.MediaContentType
import com.nexusstream.app.data.LibraryRepository
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.delay
import com.nexusstream.app.data.MusicShelf
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.components.BottomNavEntry
import com.nexusstream.app.ui.components.OriginalBottomNav
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalFilterChips
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.toFeedState
import com.nexusstream.app.ui.components.OriginalMiniPlayer

/**
 * YouTube Music Mode Home Screen:
 * - YT Music Discover Feed with Liquid Glassmorphism.
 * - Horizontal music carousels ("Quick Picks", "Top Charts", "Ambient Horizon").
 * - Flat near-black styling with mood chips, square album cards and a bottom nav.
 * - Docked mini-player with vector morphing play/pause.
 */
@Composable
fun YTMusicHomeScreen(
    currentPlayingItem: MediaItemModel?,
    isPlaying: Boolean,
    onSelectTrack: (MediaItemModel, List<MediaItemModel>) -> Unit,
    onExpandPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onClosePlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedMood by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var reloadKey by remember { mutableStateOf(0) }
    var shelves by remember {
        mutableStateOf<FeedState<List<MusicShelf>>>(
            YouTubeRepository.cachedMusicShelves("")?.let { FeedState.Data(it) } ?: FeedState.Loading
        )
    }
    var searchState by remember { mutableStateOf<FeedState<List<MediaItemModel>>>(FeedState.Loading) }

    // Live shelves, refetched when the mood changes or on retry.
    LaunchedEffect(selectedMood, reloadKey) {
        val cached = if (reloadKey == 0) YouTubeRepository.cachedMusicShelves(selectedMood) else null
        if (cached != null) {
            shelves = FeedState.Data(cached)
        } else {
            shelves = FeedState.Loading
            shelves = YouTubeRepository.musicShelves(selectedMood, refresh = reloadKey > 0).toFeedState()
        }
    }
    // Live song search, debounced.
    LaunchedEffect(searchQuery, reloadKey) {
        val query = searchQuery.trim()
        if (query.isEmpty()) return@LaunchedEffect
        searchState = FeedState.Loading
        delay(450)
        searchState = YouTubeRepository.searchSongs(query).toFeedState()
    }
    val history by LibraryRepository.history.collectAsState()
    val savedItems by LibraryRepository.saved.collectAsState()
    val likedItems by LibraryRepository.liked.collectAsState()
    val downloadedItems by LibraryRepository.downloads.collectAsState()
    val listenAgain = remember(history) {
        MusicShelf(
            "Listen again",
            "Pick up where you left off",
            history.filter { it.contentType == MediaContentType.MUSIC_TRACK && !it.isCollection }.take(12)
        )
    }

    // Albums open by loading their tracks; everything else plays directly.
    val scope = rememberCoroutineScope()
    val playOrOpen: (MediaItemModel, List<MediaItemModel>) -> Unit = { track, queue ->
        if (track.isCollection) {
            scope.launch {
                YouTubeRepository.playlistTracks(track.sourceUrl).onSuccess { tracks ->
                    onSelectTrack(tracks.first(), tracks)
                }
            }
        } else {
            onSelectTrack(track, queue)
        }
    }

    val moods = remember { listOf("Energize", "Relax", "Workout", "Focus", "Commute") }
    val navEntries = remember {
        listOf(
            BottomNavEntry("Home", Icons.Rounded.Home),
            BottomNavEntry("Explore", Icons.Rounded.Explore),
            BottomNavEntry("Library", Icons.Rounded.LibraryMusic)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OriginalColors.MusicBackground)
    ) {
        YTMusicHeader(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onToggleSearch = {
                isSearchActive = !isSearchActive
                if (!isSearchActive) searchQuery = ""
            }
        )

        if (!isSearchActive && selectedTab == 0) {
            OriginalFilterChips(
                labels = moods,
                selected = selectedMood,
                // Tapping the active mood again clears it, like the original.
                onSelect = { selectedMood = if (selectedMood == it) "" else it },
                unselectedColor = OriginalColors.MusicChip
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (selectedTab == 1) {
                // Explore: tap a genre to search it.
                val genres = remember { listOf("Pop", "Hip-Hop", "Rock", "Electronic", "Indie", "Jazz", "Classical", "Bollywood", "K-Pop", "Lo-fi") }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = "Moods & genres",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    items(genres, key = { "genre_$it" }) { genre ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OriginalColors.MusicChip)
                                .clickable {
                                    selectedTab = 0
                                    isSearchActive = true
                                    searchQuery = genre
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(text = genre, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        }
                    }
                }
            } else if (selectedTab == 2) {
                val library = remember(downloadedItems, likedItems, savedItems) {
                    (downloadedItems + likedItems + savedItems).distinctBy { it.id }
                }
                if (library.isEmpty()) {
                    OriginalEmptyState(
                        title = "Your library",
                        message = "Songs you like and download will show up here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(library, key = { "lib_${it.id}" }) { track ->
                            YTMusicSearchResultRow(item = track, onClick = { playOrOpen(track, library) })
                        }
                    }
                }
            } else if (isSearchActive && searchQuery.isNotBlank()) {
                when (val state = searchState) {
                    is FeedState.Loading -> OriginalLoading()
                    is FeedState.Error -> OriginalError(state.message, onRetry = { reloadKey++ })
                    is FeedState.Data -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.value, key = { "ytm_search_${it.id}" }) { track ->
                            YTMusicSearchResultRow(
                                item = track,
                                onClick = { playOrOpen(track, state.value) }
                            )
                        }
                    }
                }
            } else {
                when (val state = shelves) {
                    is FeedState.Loading -> OriginalLoading()
                    is FeedState.Error -> OriginalError(state.message, onRetry = { reloadKey++ })
                    is FeedState.Data -> LazyColumn(
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val rows = if (selectedMood.isBlank() && listenAgain.items.isNotEmpty()) {
                            listOf(listenAgain) + state.value
                        } else {
                            state.value
                        }
                        items(rows, key = { it.title }) { shelf ->
                            YTMusicShelfCarousel(
                                shelf = shelf,
                                onSelectTrack = { track -> playOrOpen(track, shelf.items) }
                            )
                        }
                    }
                }
            }
        }

        if (currentPlayingItem != null) {
            OriginalMiniPlayer(
                item = currentPlayingItem,
                isPlaying = isPlaying,
                onExpand = onExpandPlayer,
                onTogglePlay = onTogglePlayPause,
                onClose = onClosePlayer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        OriginalBottomNav(
            entries = navEntries,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            background = Color(0xFF212121),
            modifier = Modifier.navigationBarsPadding()
        )
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
            .height(52.dp)
            .padding(start = 12.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchActive) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OriginalColors.MusicChip)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (searchQuery.isEmpty()) {
                    Text(
                        text = "Search songs, albums, artists",
                        fontSize = 15.sp,
                        color = OriginalColors.TextSecondary
                    )
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = onToggleSearch) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }
        } else {
            // YT Music logo: red ring with a white play triangle + wordmark.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .border(2.dp, OriginalColors.Red, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Music",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
            }

            Row {
                IconButton(onClick = { /* Cast */ }) {
                    Icon(imageVector = Icons.Rounded.Cast, contentDescription = "Cast", tint = Color.White)
                }
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
private fun YTMusicShelfCarousel(
    shelf: MusicShelf,
    onSelectTrack: (MediaItemModel) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Category Title & Subtitle
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                text = shelf.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = shelf.subtitle,
                fontSize = 12.sp,
                color = OriginalColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Carousel
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shelf.items, key = { "carousel_${shelf.title}_${it.id}" }) { item ->
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
            .width(144.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(RoundedCornerShape(4.dp))
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
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
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
}

@Composable
private fun YTMusicSearchResultRow(
    item: MediaItemModel,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
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
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Song • ${item.artistOrChannel}",
                fontSize = 12.sp,
                color = OriginalColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
