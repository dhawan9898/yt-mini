package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.ChannelResult
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.Paged
import com.nexusstream.app.data.PlaylistResult
import com.nexusstream.app.data.SearchResult
import com.nexusstream.app.data.SearchType
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.MiniPlayerHooks
import com.nexusstream.app.ui.components.OnReachEnd
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalFilterChips
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.PageScaffold
import com.nexusstream.app.ui.components.toFeedState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DURATION_FILTERS = listOf("Any length", "Under 4 min", "4-20 min", "Over 20 min")

private fun matchesDuration(filter: String, durationMs: Long): Boolean {
    val minutes = durationMs / 60_000.0
    return when (filter) {
        "Under 4 min" -> minutes < 4
        "4-20 min" -> minutes in 4.0..20.0
        "Over 20 min" -> minutes > 20
        else -> true
    }
}

/**
 * YouTube search: recent searches, live suggestions while typing, and typed results
 * (videos, channels, playlists) with type and length filters and infinite scroll.
 */
@Composable
fun SearchScreen(
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf(SearchType.ALL) }
    var duration by remember { mutableStateOf(DURATION_FILTERS.first()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var results by remember { mutableStateOf<FeedState<List<SearchResult>>>(FeedState.Loading) }
    var cursor by remember { mutableStateOf<Paged<SearchResult>?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    val history by LibraryRepository.searchHistory.collectAsState()
    val hidden by LibraryRepository.hidden.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        submitted = trimmed
        LibraryRepository.addSearch(trimmed)
        keyboard?.hide()
    }

    // Suggestions while typing (debounced); cleared once a search is submitted.
    LaunchedEffect(query, submitted) {
        if (submitted != null || query.isBlank()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(200)
        suggestions = YouTubeRepository.suggestions(query.trim()).getOrDefault(emptyList())
    }

    // Results for the submitted query and type.
    LaunchedEffect(submitted, type, reloadKey) {
        val q = submitted ?: return@LaunchedEffect
        results = FeedState.Loading
        cursor = null
        YouTubeRepository.search(q, type).fold(
            onSuccess = {
                cursor = it
                results = FeedState.Data(it.items)
            },
            onFailure = { results = FeedState.Error(it.message ?: "Something went wrong") }
        )
    }

    fun loadMore() {
        val current = cursor ?: return
        if (!current.hasMore || loadingMore) return
        loadingMore = true
        scope.launch {
            current.next().onSuccess { next ->
                val existing = (results as? FeedState.Data)?.value.orEmpty()
                results = FeedState.Data(existing + next.items)
                cursor = next
            }
            loadingMore = false
        }
    }

    PageScaffold(
        title = "Search",
        onBack = nav.back,
        miniPlayer = miniPlayer,
        header = { SearchField(query, focus, { query = it; submitted = null }, { submit(query) }) },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    query = ""
                    submitted = null
                    focus.requestFocus()
                }) {
                    Icon(Icons.Rounded.Close, "Clear", tint = Color.White)
                }
            }
        }
    ) {
        val active = submitted
        if (active == null) {
            // Not searched yet: suggestions while typing, otherwise recent searches.
            val rows = if (query.isBlank()) history else suggestions
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { "row_$it" }) { text ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { submit(text) }
                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (query.isBlank()) Icons.Outlined.History else Icons.Outlined.Search,
                            contentDescription = null,
                            tint = OriginalColors.TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = text,
                            fontSize = 16.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (query.isBlank()) {
                            IconButton(onClick = { LibraryRepository.removeSearch(text) }) {
                                Icon(Icons.Rounded.Close, "Remove", tint = OriginalColors.TextSecondary, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            IconButton(onClick = { query = text }) {
                                Icon(Icons.Outlined.NorthWest, "Use suggestion", tint = OriginalColors.TextSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                if (query.isBlank() && history.isNotEmpty()) {
                    item(key = "clear_history") {
                        Text(
                            text = "Clear search history",
                            fontSize = 14.sp,
                            color = Color(0xFF3EA6FF),
                            modifier = Modifier
                                .clickable { LibraryRepository.clearSearchHistory() }
                                .padding(16.dp)
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                OriginalFilterChips(
                    labels = SearchType.entries.map { it.label },
                    selected = type.label,
                    onSelect = { label -> type = SearchType.entries.first { it.label == label } },
                    unselectedColor = OriginalColors.Chip
                )
                if (type == SearchType.ALL || type == SearchType.VIDEOS) {
                    OriginalFilterChips(
                        labels = DURATION_FILTERS,
                        selected = duration,
                        onSelect = { duration = it },
                        unselectedColor = OriginalColors.Chip
                    )
                }
                when (val state = results) {
                    is FeedState.Loading -> OriginalLoading()
                    is FeedState.Error -> OriginalError(state.message, onRetry = { reloadKey++ })
                    is FeedState.Data -> {
                        val visible = remember(state.value, hidden, duration) {
                            state.value.filter { result ->
                                when (result) {
                                    is SearchResult.Video ->
                                        result.item.id !in hidden.videoIds &&
                                            result.item.artistOrChannel !in hidden.channels &&
                                            matchesDuration(duration, result.item.durationMs)
                                    else -> true
                                }
                            }
                        }
                        val videos = remember(visible) {
                            visible.filterIsInstance<SearchResult.Video>().map { it.item }
                        }
                        if (visible.isEmpty()) {
                            OriginalEmptyState("No results", "Try different keywords or filters.")
                        } else {
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(visible, key = { result ->
                                    when (result) {
                                        is SearchResult.Video -> "v_${result.item.id}"
                                        is SearchResult.Channel -> "c_${result.channel.url}"
                                        is SearchResult.Playlist -> "p_${result.playlist.url}"
                                    }
                                }) { result ->
                                    when (result) {
                                        is SearchResult.Video -> YouTubeVideoCard(
                                            item = result.item,
                                            onClick = { onSelectVideo(result.item, videos) },
                                            onChannelClick = result.item.channelUrl.takeIf { it.isNotBlank() }
                                                ?.let { { nav.openChannelOf(result.item) } }
                                        )
                                        is SearchResult.Channel -> ChannelResultRow(result.channel) {
                                            nav.openChannel(result.channel.url, result.channel.name, result.channel.avatarUrl)
                                        }
                                        is SearchResult.Playlist -> PlaylistResultRow(result.playlist) {
                                            nav.openPlaylist(result.playlist.url, result.playlist.title)
                                        }
                                    }
                                }
                            }
                            OnReachEnd(listState) { loadMore() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.SearchField(
    query: String,
    focus: FocusRequester,
    onChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(OriginalColors.Chip)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (query.isEmpty()) {
            Text("Search YouTube", fontSize = 15.sp, color = OriginalColors.TextSecondary)
        }
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
        )
    }
}

@Composable
internal fun ChannelResultRow(channel: ChannelResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(channel.avatarUrl, channel.name, 72.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(channel.name, fontSize = 16.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (channel.subscribers.isNotBlank()) {
                Text("${channel.subscribers} subscribers", fontSize = 13.sp, color = OriginalColors.TextSecondary)
            }
            if (channel.description.isNotBlank()) {
                Text(
                    channel.description,
                    fontSize = 13.sp,
                    color = OriginalColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun PlaylistResultRow(playlist: PlaylistResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(OriginalColors.Chip)
        ) {
            if (playlist.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = playlist.thumbnailUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (playlist.videoCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("${playlist.videoCount} videos", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(playlist.title, fontSize = 14.sp, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (playlist.owner.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(playlist.owner, fontSize = 12.sp, color = OriginalColors.TextSecondary, maxLines = 1)
            }
        }
    }
}
