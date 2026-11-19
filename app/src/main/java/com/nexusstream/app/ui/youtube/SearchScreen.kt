package com.nexusstream.app.ui.youtube

import com.nexusstream.app.ui.theme.glassTile
import com.nexusstream.app.ui.theme.GlassButton as Button
import com.nexusstream.app.ui.theme.GlassTextButton as TextButton
import com.nexusstream.app.ui.theme.GlassIconButton as IconButton
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.isImeVisible
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
import com.nexusstream.app.data.YouTubeWeb
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Mic
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


/**
 * YouTube search: recent searches, live suggestions while typing, and typed results
 * (videos, channels, playlists) with type and length filters and infinite scroll.
 */
@kotlin.OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf<String?>(null) }
    var type by remember { mutableStateOf(SearchType.ALL) }
    var filters by remember { mutableStateOf(YouTubeWeb.SearchFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var results by remember { mutableStateOf<FeedState<List<SearchResult>>>(FeedState.Loading) }
    var cursor by remember { mutableStateOf<Paged<SearchResult>?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    val history by LibraryRepository.searchHistory.collectAsState()
    val hidden by LibraryRepository.hidden.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyGridState()
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    // The keyboard opens with the page, but only while nothing has been searched yet.
    LaunchedEffect(Unit) { if (submitted == null) focus.requestFocus() }

    fun dismissKeyboard() {
        // Clearing focus (not only hiding the keyboard) keeps it from popping up again when the
        // app comes back to the foreground.
        focusManager.clearFocus()
        keyboard?.hide()
    }

    // With the keyboard up, Back (button or gesture) closes the keyboard first.
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    androidx.activity.compose.BackHandler(enabled = imeVisible) { dismissKeyboard() }
    // Editing the query of a search already made: Back returns to its results.
    var lastSubmitted by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(submitted) { submitted?.let { lastSubmitted = it } }
    androidx.activity.compose.BackHandler(enabled = !imeVisible && submitted == null && lastSubmitted != null) {
        query = lastSubmitted.orEmpty()
        submitted = lastSubmitted
    }

    fun submit(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        query = trimmed
        submitted = trimmed
        LibraryRepository.addSearch(trimmed)
        dismissKeyboard()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
            query = spoken
            submit(spoken)
        }
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
    LaunchedEffect(submitted, type, reloadKey, filters) {
        val q = submitted ?: return@LaunchedEffect
        results = FeedState.Loading
        cursor = null
        // YouTube's own filters when any are set (videos), otherwise the regular typed search.
        val useFilters = !filters.isDefault && (type == SearchType.ALL || type == SearchType.VIDEOS)
        (if (useFilters) YouTubeRepository.filteredSearch(q, type, filters) else YouTubeRepository.search(q, type)).fold(
            onSuccess = {
                cursor = it
                results = FeedState.Data(it.items)
            },
            onFailure = { results = FeedState.Error(com.nexusstream.app.ui.components.FriendlyError.message(it)) }
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
        header = {
            SearchField(
                query = query,
                focus = focus,
                onChange = { query = it; submitted = null },
                onSearch = { submit(query) },
                // Tapping the query above the results goes back to editing it, as on YouTube.
                onFocused = { if (submitted != null) submitted = null },
                onClear = {
                    query = ""
                    submitted = null
                    focus.requestFocus()
                }
            )
        },
        actions = {
            if (submitted != null && (type == SearchType.ALL || type == SearchType.VIDEOS)) {
                IconButton(onClick = { showFilters = true }) {
                    Icon(
                        Icons.Outlined.Tune,
                        if (filters.isDefault) "Search filters" else "Search filters (${filters.count})",
                        tint = if (filters.isDefault) OriginalColors.TextPrimary else OriginalColors.Accent
                    )
                }
            }
            // Voice search: the system speech recognizer fills in and submits the query.
            IconButton(onClick = {
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search YouTube")
                runCatching { voiceLauncher.launch(intent) }.onFailure {
                    android.widget.Toast.makeText(context, "No voice recognition on this device", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) {
                Box(
                    modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).background(OriginalColors.Chip),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Mic, "Search with your voice", tint = OriginalColors.TextPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    ) {
        val active = submitted
        if (active == null) {
            // Not searched yet: suggestions while typing, otherwise recent searches.
            // Distinct: list keys must be unique, and suggestions can repeat a history entry.
            val rows = (if (query.isBlank()) history else suggestions).distinct()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { "row_$it" }) { text ->
                    val fromHistory = query.isBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { submit(text) },
                                // Long-press a recent search to remove it, as on YouTube.
                                onLongClick = if (fromHistory) ({ confirmRemove = text }) else null
                            )
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
                            color = OriginalColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        // Fills the box with this text to keep typing, on recent searches and suggestions alike.
                        IconButton(onClick = {
                            query = "$text "
                            submitted = null
                            focus.requestFocus()
                        }) {
                            Icon(Icons.Outlined.NorthWest, "Edit \"$text\"", tint = OriginalColors.TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                if (query.isBlank() && history.isNotEmpty()) {
                    item(key = "clear_history") {
                        Text(
                            text = "Clear search history",
                            fontSize = 14.sp,
                            color = OriginalColors.Accent,
                            modifier = Modifier
                                .clickable { LibraryRepository.clearSearchHistory() }
                                .padding(16.dp)
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // One row of chips, like YouTube's results: the result type, plus quick
                // "Recently uploaded" and "Live"; everything else is in the filters sheet (top bar).
                val recent = YouTubeWeb.SearchFilters(sort = YouTubeWeb.SortBy.UPLOAD_DATE)
                val live = YouTubeWeb.SearchFilters(features = setOf(YouTubeWeb.Feature.LIVE))
                val chipLabels = SearchType.video.map { it.label }.let { listOf(it.first()) + listOf("Recently uploaded", "Live") + it.drop(1) }
                val selectedChip = when {
                    type != SearchType.ALL -> type.label
                    filters == recent -> "Recently uploaded"
                    filters == live -> "Live"
                    else -> "All"
                }
                OriginalFilterChips(
                    labels = chipLabels,
                    selected = selectedChip,
                    onSelect = { label ->
                        when (label) {
                            "Recently uploaded" -> { type = SearchType.ALL; filters = recent }
                            "Live" -> { type = SearchType.ALL; filters = live }
                            else -> { type = SearchType.video.first { it.label == label }; filters = YouTubeWeb.SearchFilters() }
                        }
                    },
                    unselectedColor = OriginalColors.Chip
                )
                when (val state = results) {
                    is FeedState.Loading -> OriginalLoading()
                    is FeedState.Error -> OriginalError(state.message, onRetry = { reloadKey++ })
                    is FeedState.Data -> {
                        val visible = remember(state.value, hidden) {
                            // Unique keys only: later pages can repeat a result, and a repeated
                            // key crashes the list.
                            state.value.distinctBy(::searchResultKey).filter { result ->
                                when (result) {
                                    is SearchResult.Video ->
                                        result.item.id !in hidden.videoIds &&
                                            result.item.artistOrChannel !in hidden.channels
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
                            LazyVerticalGrid(
                                state = listState,
                                columns = GridCells.Adaptive(minSize = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    visible,
                                    key = ::searchResultKey,
                                    // Channels and playlists use the whole row; videos share it.
                                    span = { result ->
                                        if (result is SearchResult.Video) GridItemSpan(1) else GridItemSpan(maxLineSpan)
                                    }
                                ) { result ->
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
    if (showFilters) {
        SearchFiltersSheet(filters, onApply = { filters = it; showFilters = false }, onDismiss = { showFilters = false })
    }
    confirmRemove?.let { text ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(text, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text("Remove from search history?") },
            confirmButton = {
                TextButton(onClick = { LibraryRepository.removeSearch(text); confirmRemove = null }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
            containerColor = OriginalColors.Sheet
        )
    }
}

private fun searchResultKey(result: SearchResult): String = when (result) {
    is SearchResult.Video -> "v_${result.item.id}"
    is SearchResult.Channel -> "c_${result.channel.url}"
    is SearchResult.Playlist -> "p_${result.playlist.url}"
}

@Composable
private fun RowScope.SearchField(
    query: String,
    focus: FocusRequester,
    onChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFocused: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .weight(1f)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .glassTile(RoundedCornerShape(20.dp))
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (query.isEmpty()) {
            Text("Search YouTube", fontSize = 15.sp, color = OriginalColors.TextSecondary)
        }
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = OriginalColors.TextPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(OriginalColors.TextPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged { if (it.isFocused) onFocused() }
        )
    }
    // Clear, inside the box as on YouTube.
    if (query.isNotEmpty()) {
        IconButton(onClick = onClear) {
            Icon(Icons.Rounded.Close, "Clear", tint = OriginalColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
    } else {
        Spacer(Modifier.width(16.dp))
    }
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
            Text(channel.name, fontSize = 16.sp, color = OriginalColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text(playlist.title, fontSize = 14.sp, color = OriginalColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (playlist.owner.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(playlist.owner, fontSize = 12.sp, color = OriginalColors.TextSecondary, maxLines = 1)
            }
        }
    }
}

/** YouTube's search filters: sort order, upload date, length and features. */
@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SearchFiltersSheet(initial: YouTubeWeb.SearchFilters, onApply: (YouTubeWeb.SearchFilters) -> Unit, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(initial) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = OriginalColors.Sheet, contentColor = OriginalColors.TextPrimary) {
        com.nexusstream.app.ui.theme.GlassWindowBlur()
        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { Text("Search filters", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp)) }
            item { FilterLabel("Sort by") }
            item {
                OriginalFilterChips(
                    labels = YouTubeWeb.SortBy.entries.map { it.label },
                    selected = f.sort.label,
                    onSelect = { l -> f = f.copy(sort = YouTubeWeb.SortBy.entries.first { it.label == l }) },
                    unselectedColor = OriginalColors.Chip
                )
            }
            item { FilterLabel("Upload date") }
            item {
                OriginalFilterChips(
                    labels = YouTubeWeb.UploadDate.entries.map { it.label },
                    selected = f.date.label,
                    onSelect = { l -> f = f.copy(date = YouTubeWeb.UploadDate.entries.first { it.label == l }) },
                    unselectedColor = OriginalColors.Chip
                )
            }
            item { FilterLabel("Length") }
            item {
                OriginalFilterChips(
                    labels = YouTubeWeb.Duration.entries.map { it.label },
                    selected = f.duration.label,
                    onSelect = { l -> f = f.copy(duration = YouTubeWeb.Duration.entries.first { it.label == l }) },
                    unselectedColor = OriginalColors.Chip
                )
            }
            item { FilterLabel("Features") }
            items(YouTubeWeb.Feature.entries.toList(), key = { "f_${it.name}" }) { feature ->
                val on = feature in f.features
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { f = f.copy(features = if (on) f.features - feature else f.features + feature) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(feature.label, fontSize = 15.sp, modifier = Modifier.weight(1f), color = OriginalColors.TextPrimary)
                    androidx.compose.material3.Checkbox(checked = on, onCheckedChange = null)
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { f = YouTubeWeb.SearchFilters() }) { Text("Reset") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onApply(f) }) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = OriginalColors.TextSecondary, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}
