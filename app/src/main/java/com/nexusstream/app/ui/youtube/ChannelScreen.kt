package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.ChannelPage
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.NotifyLevel
import com.nexusstream.app.data.Paged
import com.nexusstream.app.data.PlaylistResult
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.data.YouTubeWeb
import com.nexusstream.app.ui.components.OriginalFilterChips
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.MiniPlayerHooks
import com.nexusstream.app.ui.components.OnReachEnd
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.PageScaffold
import com.nexusstream.app.ui.components.toFeedState
import kotlinx.coroutines.launch

private val CHANNEL_TABS = listOf("Home", "Videos", "Shorts", "Live", "Releases", "Playlists", "Posts", "About")
private const val TAB_VIDEOS = 1
private const val TAB_PLAYLISTS = 5
private const val TAB_ABOUT = 7

/** The website tab behind each channel tab index (Videos and Playlists also have NewPipe lists). */
private fun webTab(index: Int): YouTubeWeb.ChannelTab? = when (index) {
    0 -> YouTubeWeb.ChannelTab.HOME
    2 -> YouTubeWeb.ChannelTab.SHORTS
    3 -> YouTubeWeb.ChannelTab.LIVE
    4 -> YouTubeWeb.ChannelTab.RELEASES
    6 -> YouTubeWeb.ChannelTab.POSTS
    else -> null
}

/** Accumulated pages of one website tab (or of the Videos tab in a non-default sort). */
private data class WebTabState(
    val state: FeedState<Unit> = FeedState.Loading,
    val videos: List<MediaItemModel> = emptyList(),
    val shorts: List<MediaItemModel> = emptyList(),
    val playlists: List<PlaylistResult> = emptyList(),
    val posts: List<YouTubeWeb.Post> = emptyList(),
    val chips: List<Pair<String, String>> = emptyList(),
    val continuation: String? = null
) {
    fun plus(page: YouTubeWeb.TabPage) = copy(
        state = FeedState.Data(Unit),
        videos = (videos + page.videos).distinctBy { it.id },
        shorts = (shorts + page.shorts).distinctBy { it.id },
        playlists = (playlists + page.playlists).distinctBy { it.url },
        posts = (posts + page.posts).distinctBy { it.id },
        chips = chips.ifEmpty { page.chips },
        continuation = page.continuation
    )
}

/** A channel: banner, avatar, Subscribe, and YouTube's channel tabs with infinite scroll. */
@Composable
fun ChannelScreen(
    route: Route.Channel,
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks
) {
    var page by remember(route.url) { mutableStateOf<FeedState<ChannelPage>>(FeedState.Loading) }
    var videos by remember(route.url) { mutableStateOf<List<MediaItemModel>>(emptyList()) }
    var videoCursor by remember(route.url) { mutableStateOf<Paged<MediaItemModel>?>(null) }
    var playlists by remember(route.url) { mutableStateOf<FeedState<List<PlaylistResult>>?>(null) }
    var playlistCursor by remember(route.url) { mutableStateOf<Paged<PlaylistResult>?>(null) }
    var tab by remember(route.url) { mutableStateOf(TAB_VIDEOS) }
    var webTabs by remember(route.url) { mutableStateOf<Map<Int, WebTabState>>(emptyMap()) }
    var webReload by remember { mutableStateOf(0) }
    // Videos sort: "Latest" is NewPipe's list; Popular / Oldest come from the website's sort chips.
    var videoSort by remember(route.url) { mutableStateOf("Latest") }
    var sortedVideos by remember(route.url) { mutableStateOf<WebTabState?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var playlistReload by remember { mutableStateOf(0) }

    val subs by LibraryRepository.subscriptions.collectAsState()
    val hidden by LibraryRepository.hidden.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(route.url, reloadKey) {
        page = FeedState.Loading
        YouTubeRepository.channelPage(route.url, route.music).fold(
            onSuccess = {
                videos = it.videos.items
                videoCursor = it.videos
                page = FeedState.Data(it)
                LibraryRepository.markSeen(it.name)
            },
            onFailure = { page = FeedState.Error(it.message ?: "Something went wrong") }
        )
    }

    // Website tabs load the first time they are opened.
    LaunchedEffect(tab, page, webReload) {
        val loaded = (page as? FeedState.Data)?.value ?: return@LaunchedEffect
        val web = webTab(tab) ?: return@LaunchedEffect
        if (webTabs[tab]?.state is FeedState.Data) return@LaunchedEffect
        webTabs = webTabs + (tab to WebTabState())
        YouTubeRepository.channelTab(loaded.url, web).fold(
            onSuccess = { webTabs = webTabs + (tab to WebTabState().plus(it)) },
            onFailure = { webTabs = webTabs + (tab to WebTabState(state = FeedState.Error(it.message ?: "Something went wrong"))) }
        )
    }
    LaunchedEffect(videoSort, page) {
        val loaded = (page as? FeedState.Data)?.value ?: return@LaunchedEffect
        if (videoSort == "Latest") { sortedVideos = null; return@LaunchedEffect }
        sortedVideos = WebTabState()
        val chips = YouTubeRepository.channelTab(loaded.url, YouTubeWeb.ChannelTab.VIDEOS).getOrNull()?.chips.orEmpty()
        val token = chips.firstOrNull { it.first.equals(videoSort, ignoreCase = true) }?.second
            ?: chips.getOrNull(if (videoSort == "Popular") 1 else 2)?.second
        if (token == null) {
            sortedVideos = WebTabState(state = FeedState.Error("This channel can't be sorted"))
            return@LaunchedEffect
        }
        YouTubeRepository.channelTab(loaded.url, YouTubeWeb.ChannelTab.VIDEOS, token).fold(
            onSuccess = { sortedVideos = WebTabState().plus(it) },
            onFailure = { sortedVideos = WebTabState(state = FeedState.Error(it.message ?: "Something went wrong")) }
        )
    }

    // Playlists load the first time their tab is opened.
    LaunchedEffect(tab, page, playlistReload) {
        val loaded = (page as? FeedState.Data)?.value ?: return@LaunchedEffect
        if (tab != TAB_PLAYLISTS || playlists != null) return@LaunchedEffect
        playlists = FeedState.Loading
        loaded.loadPlaylists().fold(
            onSuccess = {
                playlistCursor = it
                playlists = FeedState.Data(it.items)
            },
            onFailure = { playlists = FeedState.Error(it.message ?: "Something went wrong") }
        )
    }

    fun loadMore() {
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            val loaded = (page as? FeedState.Data)?.value
            when {
                tab == TAB_VIDEOS && sortedVideos != null -> sortedVideos?.continuation?.let { token ->
                    YouTubeRepository.channelTab(loaded?.url.orEmpty(), YouTubeWeb.ChannelTab.VIDEOS, token).onSuccess { sortedVideos = sortedVideos?.plus(it) }
                }
                tab == TAB_VIDEOS -> videoCursor?.takeIf { it.hasMore }?.next()?.onSuccess { next ->
                    videos = (videos + next.items).distinctBy { it.id }
                    videoCursor = next
                }
                tab == TAB_PLAYLISTS -> playlistCursor?.takeIf { it.hasMore }?.next()?.onSuccess { next ->
                    playlists = FeedState.Data(((playlists as? FeedState.Data)?.value.orEmpty()) + next.items)
                    playlistCursor = next
                }
                webTab(tab) != null -> webTabs[tab]?.continuation?.let { token ->
                    val web = webTab(tab)!!
                    YouTubeRepository.channelTab(loaded?.url.orEmpty(), web, token).onSuccess { more ->
                        webTabs[tab]?.let { webTabs = webTabs + (tab to it.plus(more)) }
                    }
                }
            }
            loadingMore = false
        }
    }

    val title = (page as? FeedState.Data)?.value?.name ?: route.name
    PageScaffold(title = title, onBack = nav.back, miniPlayer = miniPlayer, music = route.music) {
        when (val state = page) {
            is FeedState.Loading -> OriginalLoading()
            is FeedState.Error -> OriginalError(state.message, onRetry = { reloadKey++ })
            is FeedState.Data -> {
                val channel = state.value
                val subscribed = subs.any { it.name == channel.name }
                val visibleVideos = remember(videos, hidden) { videos.filter { it.id !in hidden.videoIds } }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "header") {
                        ChannelHeader(
                            channel = channel,
                            subscribed = subscribed,
                            notifyLevel = subs.firstOrNull { it.name == channel.name }?.notifyLevel,
                            onNotifyLevel = { LibraryRepository.setNotifyLevel(channel.name, it) },
                            onToggleSubscribe = {
                                LibraryRepository.toggleSubscribe(channel.name, channel.avatarUrl, channel.url)
                            }
                        )
                    }
                    item(key = "tabs") {
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                            CHANNEL_TABS.forEachIndexed { index, label ->
                                Column(
                                    modifier = Modifier
                                        .clickable { tab = index }
                                        .padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (tab == index) OriginalColors.TextPrimary else OriginalColors.TextSecondary,
                                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(64.dp)
                                            .height(2.dp)
                                            .background(if (tab == index) OriginalColors.TextPrimary else OriginalColors.Divider)
                                    )
                                }
                            }
                        }
                    }

                    when (tab) {
                        TAB_VIDEOS -> {
                            item(key = "video_sort") {
                                OriginalFilterChips(
                                    labels = listOf("Latest", "Popular", "Oldest"),
                                    selected = videoSort,
                                    onSelect = { videoSort = it },
                                    unselectedColor = OriginalColors.Chip
                                )
                            }
                            val sorted = sortedVideos
                            if (sorted != null) {
                                webTabItems(sorted, channel, nav, onSelectVideo, retry = { videoSort = "Latest" })
                            } else if (visibleVideos.isEmpty()) {
                            item(key = "no_videos") { OriginalEmptyState("No videos", "This channel has no videos yet.", Modifier.height(240.dp)) }
                        } else {
                            items(visibleVideos, key = { "cv_${it.id}" }) { video ->
                                YouTubeVideoCard(item = video, onClick = { onSelectVideo(video, visibleVideos) })
                            }
                            }
                        }
                        TAB_PLAYLISTS -> when (val lists = playlists) {
                            null, is FeedState.Loading -> item(key = "pl_loading") { Box(Modifier.height(160.dp)) { OriginalLoading() } }
                            is FeedState.Error -> item(key = "pl_error") { Box(Modifier.height(240.dp)) { OriginalError(lists.message, onRetry = { playlists = null; playlistReload++ }) } }
                            is FeedState.Data -> if (lists.value.isEmpty()) {
                                item(key = "no_playlists") { OriginalEmptyState("No playlists", "This channel has no public playlists.", Modifier.height(240.dp)) }
                            } else {
                                items(lists.value, key = { "pl_${it.url}" }) { playlist ->
                                    PlaylistResultRow(playlist) { nav.openPlaylist(playlist.url, playlist.title) }
                                }
                            }
                        }
                        TAB_ABOUT -> item(key = "about") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("Description", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = channel.description.ifBlank { "No description." },
                                    fontSize = 14.sp,
                                    color = OriginalColors.TextPrimary,
                                    lineHeight = 20.sp
                                )
                                if (channel.subscribers.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("${channel.subscribers} subscribers", fontSize = 14.sp, color = OriginalColors.TextSecondary)
                                }
                            }
                        }
                    }

                    if (webTab(tab) != null) {
                        val state = webTabs[tab] ?: WebTabState()
                        webTabItems(state, channel, nav, onSelectVideo, retry = { webTabs = webTabs - tab; webReload++ })
                    }

                    if (loadingMore) {
                        item(key = "loading_more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = OriginalColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
                OnReachEnd(listState) { loadMore() }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    channel: ChannelPage,
    subscribed: Boolean,
    notifyLevel: NotifyLevel?,
    onNotifyLevel: (NotifyLevel) -> Unit,
    onToggleSubscribe: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (channel.bannerUrl.isNotBlank()) {
            AsyncImage(
                model = channel.bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 5f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(channel.avatarUrl, channel.name, 72.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = OriginalColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.subscribers.isNotBlank()) {
                    Text("${channel.subscribers} subscribers", fontSize = 13.sp, color = OriginalColors.TextSecondary)
                }
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (subscribed) OriginalColors.Chip else OriginalColors.TextPrimary)
                    .clickable(onClick = onToggleSubscribe)
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            ) {
                Text(
                    text = if (subscribed) "Subscribed" else "Subscribe",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (subscribed) OriginalColors.TextPrimary else OriginalColors.OnInverse
                )
            }
            if (subscribed && notifyLevel != null) {
                Spacer(modifier = Modifier.width(4.dp))
                NotifyLevelButton(level = notifyLevel, onSelect = onNotifyLevel)
            }
        }
    }
}

/** The items of a website channel tab: videos, a Shorts grid, playlists / releases or posts. */
private fun androidx.compose.foundation.lazy.LazyListScope.webTabItems(
    state: WebTabState,
    channel: ChannelPage,
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit,
    retry: () -> Unit
) {
    when (val s = state.state) {
        is FeedState.Loading -> item(key = "web_loading") { Box(Modifier.height(160.dp)) { OriginalLoading() } }
        is FeedState.Error -> item(key = "web_error") { Box(Modifier.height(240.dp)) { OriginalError(s.message, onRetry = retry) } }
        is FeedState.Data -> {
            val videos = state.videos.map { it.copy(artistOrChannel = it.artistOrChannel.ifBlank { channel.name }, channelAvatarUrl = it.channelAvatarUrl.ifBlank { channel.avatarUrl }, channelUrl = it.channelUrl.ifBlank { channel.url }) }
            val shorts = state.shorts.map { it.copy(artistOrChannel = channel.name, channelAvatarUrl = channel.avatarUrl, channelUrl = channel.url) }
            if (videos.isEmpty() && shorts.isEmpty() && state.playlists.isEmpty() && state.posts.isEmpty()) {
                item(key = "web_empty") { OriginalEmptyState("Nothing here", "This channel has nothing in this tab yet.", Modifier.height(240.dp)) }
            }
            if (shorts.isNotEmpty()) {
                items(shorts.chunked(3), key = { "sh_${it.first().id}" }) { row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        row.forEach { short ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(2.dp)
                                    .clickable { onSelectVideo(short, shorts) }
                            ) {
                                AsyncImage(
                                    model = short.artworkUrl,
                                    contentDescription = short.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(8.dp))
                                )
                                Text(short.title, fontSize = 12.sp, color = OriginalColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (short.viewsOrPlays.isNotBlank()) Text(short.viewsOrPlays, fontSize = 11.sp, color = OriginalColors.TextSecondary)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            items(videos, key = { "wv_${it.id}" }) { video ->
                YouTubeVideoCard(item = video, onClick = { onSelectVideo(video, videos) })
            }
            items(state.playlists, key = { "wp_${it.url}" }) { playlist ->
                PlaylistResultRow(playlist) { nav.openPlaylist(playlist.url, playlist.title) }
            }
            items(state.posts, key = { "post_${it.id}" }) { post ->
                PostCard(post, onOpenVideo = { onSelectVideo(it, listOf(it)) })
            }
        }
    }
}

/** A community post: author, text, images or an attached video / poll, likes and comments. */
@Composable
private fun PostCard(post: YouTubeWeb.Post, onOpenVideo: (MediaItemModel) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(OriginalColors.Chip)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(post.authorAvatar, post.author, 32.dp)
            Spacer(Modifier.width(8.dp))
            Text(post.author, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OriginalColors.TextPrimary)
            Spacer(Modifier.width(6.dp))
            Text(post.published, fontSize = 12.sp, color = OriginalColors.TextSecondary)
        }
        if (post.text.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(post.text, fontSize = 14.sp, color = OriginalColors.TextPrimary, lineHeight = 20.sp)
        }
        post.images.forEach { url ->
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            )
        }
        post.video?.let { video ->
            Spacer(Modifier.height(8.dp))
            YouTubeVideoCard(item = video, onClick = { onOpenVideo(video) })
        }
        post.pollChoices.forEach { choice ->
            Spacer(Modifier.height(6.dp))
            Text(
                choice,
                fontSize = 14.sp,
                color = OriginalColors.TextPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(OriginalColors.YouTubeBackground)
                    .padding(10.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            if (post.likes.isNotBlank()) Text("${post.likes} likes", fontSize = 12.sp, color = OriginalColors.TextSecondary)
            if (post.comments.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text("${post.comments} comments", fontSize = 12.sp, color = OriginalColors.TextSecondary)
            }
        }
    }
}
