package com.nexusstream.app.ui.ytmusic

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.DownloadRepository
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.YTMusicApi
import com.nexusstream.app.data.YTMusicPages
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.LocalPlaybackService
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.MiniPlayerHooks
import com.nexusstream.app.ui.components.OnReachEnd
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalFilterChips
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.PageScaffold
import com.nexusstream.app.ui.components.toFeedState
import kotlinx.coroutines.launch

/**
 * A YT Music page opened from a card, link or menu: artist, album, playlist, podcast, Explore
 * sub-pages (New releases, Charts, a mood). If YT Music can't load it, [fallbackContent] shows
 * the YouTube-backed page instead.
 */
@Composable
fun MusicBrowseScreen(
    route: Route.MusicBrowse,
    nav: YouTubeNav,
    onPlay: (MediaItemModel, List<MediaItemModel>) -> Unit,
    miniPlayer: MiniPlayerHooks,
    fallbackContent: @Composable (Route) -> Unit
) {
    var failed by remember(route) { mutableStateOf(false) }
    val fallback = route.fallback
    if (failed && fallback != null) {
        fallbackContent(fallback)
        return
    }
    PageScaffold(title = route.title, onBack = nav.back, miniPlayer = miniPlayer, music = true) {
        MusicPageList(
            browseId = route.browseId,
            params = route.params,
            nav = nav,
            onPlay = onPlay,
            showChips = true,
            header = { page, songs ->
                page.header?.let { header ->
                    item(key = "header") { MusicPageHeader(header, route.browseId, songs, nav, onPlay) }
                }
            },
            errorContent = if (fallback != null) { _, _ -> LaunchedEffect(Unit) { failed = true } } else null
        )
    }
}

/**
 * Loads a YT Music browse page and lays out its chips, header and shelves, loading further
 * shelves as the list nears its end. Used by the music Home, Explore and Library tabs too.
 */
@Composable
internal fun MusicPageList(
    browseId: String,
    params: String = "",
    nav: YouTubeNav,
    onPlay: (MediaItemModel, List<MediaItemModel>) -> Unit,
    modifier: Modifier = Modifier,
    showChips: Boolean = false,
    reloadKey: Int = 0,
    leading: LazyListScope.() -> Unit = {},
    header: (LazyListScope.(YTMusicPages.Page, List<MediaItemModel>) -> Unit)? = null,
    errorContent: (@Composable (message: String, retry: () -> Unit) -> Unit)? = null,
    transform: (List<YTMusicApi.Section>) -> List<YTMusicApi.Section> = { it }
) {
    var chip by remember(browseId, params) { mutableStateOf<YTMusicPages.Chip?>(null) }
    val targetId = chip?.browseId?.ifEmpty { browseId } ?: browseId
    val targetParams = chip?.params ?: params
    var retry by remember { mutableStateOf(0) }
    var state by remember(targetId, targetParams) {
        mutableStateOf<FeedState<YTMusicPages.Page>>(
            YouTubeRepository.cachedMusicPage(targetId, targetParams)?.let { FeedState.Data(it) } ?: FeedState.Loading
        )
    }
    var baseChips by remember(browseId, params) { mutableStateOf<List<YTMusicPages.Chip>>(emptyList()) }
    val extra = remember(targetId, targetParams) { mutableStateListOf<YTMusicApi.Section>() }
    var continuation by remember(targetId, targetParams) { mutableStateOf<String?>(null) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(targetId, targetParams, retry, reloadKey) {
        val refresh = retry > 0 || reloadKey > 0
        if (refresh || state !is FeedState.Data) state = FeedState.Loading
        val result = YouTubeRepository.musicPage(targetId, targetParams, refresh = refresh)
        result.onSuccess { page ->
            extra.clear()
            continuation = page.continuation
            if (chip == null || baseChips.isEmpty()) baseChips = page.chips
        }
        state = result.toFeedState()
    }

    when (val s = state) {
        is FeedState.Loading -> OriginalLoading(modifier)
        is FeedState.Error -> errorContent?.invoke(s.message) { retry++ } ?: OriginalError(s.message, onRetry = { retry++ }, modifier = modifier)
        is FeedState.Data -> {
            val listState = rememberLazyListState()
            val page = s.value
            val sections = transform(page.sections + extra)
            val songs = remember(sections) {
                sections.filter { it.isSongList }.flatMap { sec -> sec.cards.mapNotNull { it.track } }.distinctBy { it.id }
            }
            OnReachEnd(listState) {
                val token = continuation ?: return@OnReachEnd
                if (loadingMore) return@OnReachEnd
                loadingMore = true
                scope.launch {
                    YouTubeRepository.musicPageMore(token).onSuccess { more ->
                        extra += more.sections
                        continuation = more.continuation?.takeIf { it != token }
                    }.onFailure { continuation = null }
                    loadingMore = false
                }
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = modifier.fillMaxSize()
            ) {
                leading()
                val chips = baseChips
                if (showChips && chips.isNotEmpty()) {
                    item(key = "chips") {
                        OriginalFilterChips(
                            labels = chips.map { it.title },
                            selected = chip?.title.orEmpty(),
                            onSelect = { label -> chip = if (chip?.title == label) null else chips.firstOrNull { it.title == label } },
                            unselectedColor = OriginalColors.MusicChip
                        )
                    }
                }
                header?.invoke(this, page, songs)
                musicSections(
                    sections = sections,
                    keyPrefix = "s",
                    onCard = { card, queue -> openMusicCard(card, queue, nav, onPlay) },
                    onButton = { b -> nav.open(Route.MusicBrowse(b.browseId, b.title, b.params)) },
                    onMore = { sec -> nav.open(Route.MusicBrowse(sec.moreBrowseId, sec.title, sec.moreParams)) },
                    numbered = targetId.startsWith("MPRE")
                )
                if (continuation != null) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = OriginalColors.TextSecondary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Artist header (wide picture, subscribers, Shuffle / Radio / Subscribe) or album / playlist header. */
@Composable
private fun MusicPageHeader(
    header: YTMusicPages.Header,
    browseId: String,
    songs: List<MediaItemModel>,
    nav: YouTubeNav,
    onPlay: (MediaItemModel, List<MediaItemModel>) -> Unit
) {
    val context = LocalContext.current
    val service = LocalPlaybackService.current
    val scope = rememberCoroutineScope()

    // Plays [songs], or the header's playlist when the page lists none of its tracks.
    fun play(playlistId: String, shuffle: Boolean) {
        val list = if (playlistId.isEmpty() || (songs.isNotEmpty() && !header.isArtist)) songs else emptyList()
        if (list.isNotEmpty()) {
            val queue = if (shuffle) list.shuffled() else list
            onPlay(queue.first(), queue)
            return
        }
        if (playlistId.isEmpty()) {
            Toast.makeText(context, "Nothing to play", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            YouTubeRepository.musicPlaylistTracks(playlistId)
                .onSuccess { tracks ->
                    val queue = if (shuffle) tracks.shuffled() else tracks
                    val first = queue.firstOrNull()
                    if (first == null) Toast.makeText(context, "Nothing to play", Toast.LENGTH_SHORT).show()
                    else onPlay(first, queue)
                }
                .onFailure { Toast.makeText(context, it.message ?: "Couldn't play this", Toast.LENGTH_SHORT).show() }
        }
    }

    if (header.isArtist) {
        val subscriptions by LibraryRepository.subscriptions.collectAsState()
        val subscribed = subscriptions.any { it.name == header.title }
        val channelId = header.channelId.ifEmpty { browseId.takeIf { it.startsWith("UC") }.orEmpty() }
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.25f)) {
                if (header.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(header.thumbnailUrl, header.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.45f to Color.Transparent, 1f to OriginalColors.MusicBackground)
                    )
                )
                Text(
                    header.title,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = OriginalColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (header.subscriberText.isNotBlank()) {
                Text(
                    header.subscriberText, fontSize = 13.sp, color = OriginalColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PillButton(Icons.Rounded.Shuffle, "Shuffle", filled = true) { play(header.shufflePlaylistId, shuffle = true) }
                PillButton(Icons.Outlined.Radio, "Radio") { play(header.radioPlaylistId, shuffle = false) }
                Spacer(Modifier.weight(1f))
                if (channelId.isNotEmpty()) {
                    PillButton(null, if (subscribed) "Subscribed" else "Subscribe", filled = !subscribed) {
                        LibraryRepository.toggleSubscribe(header.title, header.thumbnailUrl, YTMusicApi.artistUrl(channelId))
                    }
                }
            }
            if (header.description.isNotBlank()) {
                var expanded by remember { mutableStateOf(false) }
                Text(
                    header.description,
                    fontSize = 14.sp,
                    color = OriginalColors.TextSecondary,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.padding(top = 8.dp).size(232.dp).clip(RoundedCornerShape(6.dp)).background(OriginalColors.MusicChip)
            ) {
                if (header.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(header.thumbnailUrl, header.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                header.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            if (header.artistName.isNotBlank()) {
                Text(
                    header.artistName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = OriginalColors.TextPrimary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(enabled = header.artistId.isNotEmpty()) {
                            nav.open(Route.MusicBrowse(header.artistId, header.artistName))
                        }
                )
            }
            listOf(header.subtitle, header.secondSubtitle).filter { it.isNotBlank() }.forEach {
                Text(it, fontSize = 13.sp, color = OriginalColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
            if (header.description.isNotBlank()) {
                var expanded by remember { mutableStateOf(false) }
                Text(
                    header.description,
                    fontSize = 13.sp,
                    color = OriginalColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp).clickable { expanded = !expanded }
                )
            }
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundIcon(Icons.Outlined.FileDownload, "Download all") {
                    scope.launch {
                        val tracks = songs.ifEmpty {
                            header.playlistId.takeIf { it.isNotEmpty() }
                                ?.let { YouTubeRepository.musicPlaylistTracks(it).getOrNull() }.orEmpty()
                        }
                        val started = DownloadRepository.startAudioAll(context, tracks)
                        val message = when {
                            tracks.isEmpty() -> "Nothing to download"
                            started == 0 -> "Already downloaded"
                            else -> "Downloading $started songs"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(OriginalColors.TextPrimary)
                        .clickable { play(header.playlistId, shuffle = false) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, "Play", tint = OriginalColors.MusicBackground, modifier = Modifier.size(36.dp))
                }
                RoundIcon(Icons.Rounded.Shuffle, "Shuffle") { play(header.playlistId, shuffle = true) }
                RoundIcon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to queue") {
                    if (songs.isNotEmpty() && service != null) {
                        service.addToQueue(songs)
                        Toast.makeText(context, "Added ${songs.size} songs to the queue", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

@Composable
private fun PillButton(icon: ImageVector?, label: String, filled: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(OriginalColors.TextPrimary) else Modifier.border(1.dp, OriginalColors.Divider, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (filled) OriginalColors.MusicBackground else OriginalColors.TextPrimary
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).clip(CircleShape).background(OriginalColors.MusicChip)
    ) {
        Icon(icon, label, tint = OriginalColors.TextPrimary)
    }
}
