package com.nexusstream.app.ui.ytmusic

import com.nexusstream.app.ui.theme.GlassMotion
import com.nexusstream.app.ui.theme.GlassWeight
import com.nexusstream.app.ui.theme.LocalChromePadding
import com.nexusstream.app.ui.theme.glassBackdrop
import com.nexusstream.app.ui.theme.liquidGlass
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import dev.chrisbanes.haze.HazeState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.key
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.History
import com.nexusstream.app.data.GoogleAccount
import com.nexusstream.app.data.YTMusicPages
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.youtube.PlaylistResultRow
import com.nexusstream.app.ui.youtube.ChannelResultRow
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.data.SearchType
import com.nexusstream.app.data.SearchResult
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
import com.nexusstream.app.ui.components.GlassNavBar
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
    nav: YouTubeNav,
    modifier: Modifier = Modifier,
    /** The Music logo at the top switches back to YouTube. */
    onSwitchApp: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedMood by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var reloadKey by remember { mutableStateOf(0) }
    var libraryTab by remember { mutableStateOf("") }
    val signedIn by GoogleAccount.signedIn.collectAsState()
    var searchFilter by remember { mutableStateOf(YTMusicPages.SearchFilter.SONGS) }
    var musicSearch by remember { mutableStateOf<FeedState<YTMusicPages.Page>>(FeedState.Loading) }
    var searchType by remember { mutableStateOf(SearchType.SONGS) }
    var searchState by remember { mutableStateOf<FeedState<List<SearchResult>>>(FeedState.Loading) }

    // YT Music's own search; the YouTube-backed search stands in if it fails.
    LaunchedEffect(searchQuery, searchFilter, reloadKey) {
        val query = searchQuery.trim()
        if (query.isEmpty()) return@LaunchedEffect
        musicSearch = FeedState.Loading
        delay(450)
        musicSearch = YouTubeRepository.musicSearch(query, searchFilter).toFeedState()
        if (musicSearch is FeedState.Error) {
            searchType = when (searchFilter) {
                YTMusicPages.SearchFilter.ALBUMS -> SearchType.ALBUMS
                YTMusicPages.SearchFilter.ARTISTS, YTMusicPages.SearchFilter.PROFILES -> SearchType.ARTISTS
                YTMusicPages.SearchFilter.COMMUNITY_PLAYLISTS, YTMusicPages.SearchFilter.FEATURED_PLAYLISTS -> SearchType.MUSIC_PLAYLISTS
                else -> SearchType.SONGS
            }
            searchState = YouTubeRepository.search(query, searchType).map { it.items }.toFeedState()
        }
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

    // Albums open their own page; everything else plays directly.
    val playOrOpen: (MediaItemModel, List<MediaItemModel>) -> Unit = { track, queue ->
        if (track.isCollection) {
            nav.openMusicPlaylist(track.sourceUrl, track.title)
        } else {
            onSelectTrack(track, queue)
        }
    }

    val navEntries = remember {
        listOf(
            BottomNavEntry("Home", Icons.Rounded.Home),
            BottomNavEntry("Explore", Icons.Rounded.Explore),
            BottomNavEntry("Library", Icons.Rounded.LibraryMusic)
        )
    }

    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= com.nexusstream.app.ui.components.RAIL_MIN_WIDTH_DP
    // Glass chrome: the header and the bottom bar float over the shelves and blur them.
    val backdrop = remember { HazeState() }
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpace = navBarInset + (if (wide) 0.dp else 76.dp) + (if (currentPlayingItem != null) 72.dp else 0.dp)

    Row(modifier = modifier.fillMaxSize().background(OriginalColors.MusicBackground)) {
    if (wide) com.nexusstream.app.ui.components.SideNavRail(navEntries, selectedTab, onSelect = { selectedTab = it })
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdrop(backdrop)
        ) {
            CompositionLocalProvider(LocalChromePadding provides PaddingValues(top = headerHeight, bottom = bottomSpace)) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isSearchActive && searchQuery.isNotBlank()) {
                Column(modifier = Modifier.fillMaxSize().padding(top = headerHeight)) {
                    OriginalFilterChips(
                        labels = YTMusicPages.SearchFilter.entries.map { it.label },
                        selected = searchFilter.label,
                        onSelect = { label -> searchFilter = YTMusicPages.SearchFilter.entries.first { it.label == label } },
                        unselectedColor = OriginalColors.MusicChip
                    )
                    when (val ytm = musicSearch) {
                        is FeedState.Loading -> OriginalLoading()
                        is FeedState.Data -> LazyColumn(
                            contentPadding = PaddingValues(top = 8.dp, bottom = bottomSpace + 8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            musicSections(
                                sections = ytm.value.sections,
                                keyPrefix = "search",
                                onCard = { card, queue -> openMusicCard(card, queue, nav, onSelectTrack) },
                                onButton = { b -> nav.open(Route.MusicBrowse(b.browseId, b.title, b.params)) }
                            )
                        }
                        is FeedState.Error -> LegacyMusicSearch(searchState, nav, onSelectTrack, onRetry = { reloadKey++ })
                    }
                }
            } else if (selectedTab == 1) {
                MusicPageList(
                    browseId = YTMusicPages.EXPLORE,
                    nav = nav,
                    onPlay = onSelectTrack,
                    reloadKey = reloadKey,
                    leading = {
                        item(key = "explore-buttons") {
                            ExploreButtons(nav, signedIn)
                        }
                    }
                )
            } else if (selectedTab == 2) {
                val tabs = remember(signedIn) {
                    (if (signedIn) YTMusicPages.LibraryTab.entries.map { it.label } + listOf("History", "Recap") else emptyList()) +
                        listOf("Downloads", "On this device")
                }
                val current = libraryTab.takeIf { it in tabs } ?: tabs.first()
                Column(modifier = Modifier.fillMaxSize().padding(top = headerHeight)) {
                    OriginalFilterChips(
                        labels = tabs,
                        selected = current,
                        onSelect = { libraryTab = it },
                        unselectedColor = OriginalColors.MusicChip
                    )
                    val accountTab = YTMusicPages.LibraryTab.entries.firstOrNull { it.label == current }
                    val accountBrowse = accountTab?.browseId ?: when (current) {
                        "History" -> YTMusicPages.HISTORY
                        "Recap" -> YTMusicPages.RECAP
                        else -> null
                    }
                    if (accountBrowse != null) {
                        key(accountBrowse) {
                            // Already below the header: only the bottom chrome to clear.
                            CompositionLocalProvider(LocalChromePadding provides PaddingValues(bottom = bottomSpace)) {
                                MusicPageList(browseId = accountBrowse, nav = nav, onPlay = onSelectTrack, reloadKey = reloadKey)
                            }
                        }
                    } else {
                        val library = remember(current, downloadedItems, likedItems, savedItems) {
                            if (current == "Downloads") downloadedItems.filter { it.contentType == MediaContentType.MUSIC_TRACK }
                            // Only YT Music's own songs: liked music and music downloads, not YouTube's videos.
                            else (LibraryRepository.likedSongs(likedItems) + downloadedItems.filter { it.contentType == MediaContentType.MUSIC_TRACK })
                                .distinctBy { it.id }
                        }
                        if (library.isEmpty()) {
                            OriginalEmptyState(
                                title = if (current == "Downloads") "No downloads" else "Your library",
                                message = if (signedIn) "Songs you like and download will show up here."
                                else "Sign in to see your YouTube Music library. Songs you like and download show up here."
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(top = 8.dp, bottom = bottomSpace + 8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(library, key = { "lib_${it.id}" }) { track ->
                                    YTMusicSearchResultRow(item = track, onClick = { playOrOpen(track, library) })
                                }
                            }
                        }
                    }
                }
            } else {
                // The real YT Music home (Quick picks, mixes, new releases, mood chips); the
                // history-and-search shelves stand in if it can't be loaded.
                MusicPageList(
                    browseId = YTMusicPages.HOME,
                    nav = nav,
                    onPlay = onSelectTrack,
                    showChips = true,
                    reloadKey = reloadKey,
                    leading = {
                        if (!signedIn && listenAgain.items.isNotEmpty()) {
                            item(key = "listen-again") {
                                YTMusicShelfCarousel(shelf = listenAgain, onSelectTrack = { playOrOpen(it, listenAgain.items) })
                                Spacer(Modifier.height(20.dp))
                            }
                        }
                    },
                    errorContent = { _, _ ->
                        LegacyMusicHome(selectedMood, { selectedMood = it }, listenAgain, playOrOpen)
                    }
                )
            }
        }

            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { headerHeight = with(density) { it.height.toDp() } }
                .liquidGlass(RectangleShape, backdrop, GlassWeight.Thick, rim = false)
                .statusBarsPadding()
        ) {
            YTMusicHeader(
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    isSearchActive = !isSearchActive
                    if (!isSearchActive) searchQuery = ""
                },
                onProfile = { nav.open(if (signedIn) Route.Settings else Route.SignIn) },
                onLogo = onSwitchApp
            )
        }

        // Bottom overlay: the mini player and the navigation bar float over the shelves.
        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(
                visible = currentPlayingItem != null,
                enter = slideInVertically(GlassMotion.smooth()) { it / 2 } + fadeIn(GlassMotion.smooth()),
                exit = slideOutVertically(GlassMotion.smooth()) { it / 2 } + fadeOut(GlassMotion.smooth())
            ) {
                val item = currentPlayingItem
                if (item != null) {
                    OriginalMiniPlayer(
                        item = item,
                        isPlaying = isPlaying,
                        onExpand = onExpandPlayer,
                        onTogglePlay = onTogglePlayPause,
                        onClose = onClosePlayer,
                        backdrop = backdrop,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .then(if (wide) Modifier.navigationBarsPadding() else Modifier)
                    )
                }
            }
            if (!wide) GlassNavBar(
                entries = navEntries,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                backdrop = backdrop,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 8.dp)
            )
        }
    }
    }
}

/** Explore's top buttons, as in the app: New releases, Charts, Moods & genres, Podcasts. */
@Composable
private fun ExploreButtons(nav: YouTubeNav, signedIn: Boolean) {
    val entries = listOf(
        Triple("New releases", Icons.Rounded.NewReleases, YTMusicPages.NEW_RELEASES),
        Triple("Charts", Icons.AutoMirrored.Rounded.TrendingUp, YTMusicPages.CHARTS),
        Triple("Moods & genres", Icons.Rounded.SentimentSatisfied, YTMusicPages.MOODS),
        Triple("Podcasts", Icons.Rounded.Podcasts, YTMusicPages.PODCASTS)
    ) + if (signedIn) listOf(Triple("Your Recap", Icons.Rounded.History, YTMusicPages.RECAP)) else emptyList()
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entries.forEach { (label, icon, browseId) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(OriginalColors.MusicChip)
                    .clickable { nav.open(Route.MusicBrowse(browseId, label)) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = OriginalColors.TextPrimary)
                Spacer(Modifier.width(16.dp))
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = OriginalColors.TextPrimary)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** The pre-browse home: mood chips over shelves built from searches and listening history. */
@Composable
private fun LegacyMusicHome(
    selectedMood: String,
    onMood: (String) -> Unit,
    listenAgain: MusicShelf,
    playOrOpen: (MediaItemModel, List<MediaItemModel>) -> Unit
) {
    var reload by remember { mutableStateOf(0) }
    var shelves by remember {
        mutableStateOf<FeedState<List<MusicShelf>>>(
            YouTubeRepository.cachedMusicShelves(selectedMood)?.let { FeedState.Data(it) } ?: FeedState.Loading
        )
    }
    LaunchedEffect(selectedMood, reload) {
        shelves = FeedState.Loading
        shelves = YouTubeRepository.musicShelves(selectedMood, refresh = reload > 0).toFeedState()
    }
    val moods = remember { listOf("Energize", "Relax", "Workout", "Focus", "Commute") }
    Column(modifier = Modifier.fillMaxSize()) {
        OriginalFilterChips(
            labels = moods,
            selected = selectedMood,
            onSelect = { onMood(if (selectedMood == it) "" else it) },
            unselectedColor = OriginalColors.MusicChip
        )
        when (val state = shelves) {
            is FeedState.Loading -> OriginalLoading()
            is FeedState.Error -> OriginalError(state.message, onRetry = { reload++ })
            is FeedState.Data -> LazyColumn(
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val rows = if (selectedMood.isBlank() && listenAgain.items.isNotEmpty()) listOf(listenAgain) + state.value else state.value
                items(rows, key = { it.title }) { shelf ->
                    YTMusicShelfCarousel(shelf = shelf, onSelectTrack = { track -> playOrOpen(track, shelf.items) })
                }
            }
        }
    }
}

/** Search results from the YouTube-backed search, used when YT Music's own search fails. */
@Composable
private fun LegacyMusicSearch(
    searchState: FeedState<List<SearchResult>>,
    nav: YouTubeNav,
    onSelectTrack: (MediaItemModel, List<MediaItemModel>) -> Unit,
    onRetry: () -> Unit
) {
    when (val state = searchState) {
        is FeedState.Loading -> OriginalLoading()
        is FeedState.Error -> OriginalError(state.message, onRetry = onRetry)
        is FeedState.Data -> {
            val tracks = remember(state.value) { state.value.filterIsInstance<SearchResult.Video>().map { it.item } }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxSize()) {
                items(state.value) { result ->
                    when (result) {
                        is SearchResult.Video -> YTMusicSearchResultRow(item = result.item, onClick = { onSelectTrack(result.item, tracks) })
                        is SearchResult.Channel -> ChannelResultRow(result.channel) {
                            nav.openMusicChannel(result.channel.url, result.channel.name, result.channel.avatarUrl)
                        }
                        is SearchResult.Playlist -> PlaylistResultRow(result.playlist) {
                            nav.openMusicPlaylist(result.playlist.url, result.playlist.title)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YTMusicHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onProfile: () -> Unit = {},
    onLogo: () -> Unit = {}
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
                    textStyle = TextStyle(color = OriginalColors.TextPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(OriginalColors.TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = onToggleSearch) {
                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", tint = OriginalColors.TextPrimary)
            }
        } else {
            // YT Music logo: red ring with a white play triangle + wordmark.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "Switch to YouTube", onClick = onLogo)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .border(2.dp, OriginalColors.Red, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = OriginalColors.TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Music",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = OriginalColors.TextPrimary,
                    letterSpacing = (-0.5).sp
                )
            }

            Row {
                IconButton(onClick = { /* Cast */ }) {
                    Icon(imageVector = Icons.Rounded.Cast, contentDescription = "Cast", tint = OriginalColors.TextPrimary)
                }
                IconButton(onClick = onToggleSearch) {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search", tint = OriginalColors.TextPrimary)
                }
                IconButton(onClick = onProfile) {
                    Icon(imageVector = Icons.Rounded.AccountCircle, contentDescription = "Profile", tint = OriginalColors.TextPrimary)
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
                color = OriginalColors.TextPrimary
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun YTMusicAlbumCard(
    item: MediaItemModel,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    if (showMenu) SongMenuSheet(item = item, onDismiss = { showMenu = false })
    Column(
        modifier = Modifier
            .width(144.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (item.isCollection) null else ({ showMenu = true })
            )
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
}

@Composable
private fun YTMusicSearchResultRow(
    item: MediaItemModel,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    if (showMenu) SongMenuSheet(item = item, onDismiss = { showMenu = false })
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
                color = OriginalColors.TextPrimary,
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
        if (!item.isCollection) {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = OriginalColors.TextPrimary)
            }
        }
    }
}
