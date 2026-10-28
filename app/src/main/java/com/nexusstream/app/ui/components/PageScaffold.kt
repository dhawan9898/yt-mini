package com.nexusstream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.model.MediaItemModel
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState

/** What a page needs to draw the docked mini-player. */
class MiniPlayerHooks(
    val item: MediaItemModel?,
    val isPlaying: Boolean,
    val onExpand: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onClose: (() -> Unit)? = null
)

/**
 * Full-screen page chrome: a glass top bar (back arrow + title) under the status bar, the content,
 * and the glass mini-player (when something is playing) above the navigation bar.
 *
 * With [scrollsUnderChrome] the content fills the whole page and the bars float over it, blurring
 * it as it scrolls underneath; the content must then add [LocalChromePadding] to its list padding.
 * Without it the content is laid out between the bars.
 */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    miniPlayer: MiniPlayerHooks,
    modifier: Modifier = Modifier,
    /** YT Music pages use the music background. */
    music: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    /** Replaces the title text (e.g. with a search field). */
    header: (@Composable RowScope.() -> Unit)? = null,
    scrollsUnderChrome: Boolean = false,
    content: @Composable () -> Unit
) {
    val playing = miniPlayer.item
    // Keeps the last item on screen while the mini player slides away after closing.
    val shownItem = remember { mutableStateOf<MediaItemModel?>(null) }.apply { if (playing != null) value = playing }.value
    val background = if (music) OriginalColors.MusicBackground else OriginalColors.YouTubeBackground

    val topBar = @Composable { backdrop: HazeState?, barModifier: Modifier ->
        Row(
            modifier = barModifier
                .fillMaxWidth()
                .liquidGlass(RectangleShape, backdrop, GlassWeight.Thick, rim = false)
                .statusBarsPadding()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = OriginalColors.TextPrimary)
            }
            if (header != null) {
                header()
            } else {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OriginalColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            actions()
        }
    }
    val bottomBar = @Composable { backdrop: HazeState?, barModifier: Modifier ->
        Box(modifier = barModifier.fillMaxWidth().navigationBarsPadding()) {
            AnimatedVisibility(
                visible = playing != null,
                enter = slideInVertically(GlassMotion.smooth()) { it } + fadeIn(),
                exit = slideOutVertically(GlassMotion.smooth()) { it } + fadeOut()
            ) {
                val item = shownItem
                if (item != null) {
                    OriginalMiniPlayer(
                        item = item,
                        isPlaying = miniPlayer.isPlaying,
                        onExpand = miniPlayer.onExpand,
                        onTogglePlay = miniPlayer.onTogglePlay,
                        onClose = miniPlayer.onClose,
                        backdrop = backdrop,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (!scrollsUnderChrome) {
        Column(modifier = modifier.fillMaxSize().background(background)) {
            topBar(null, Modifier)
            Box(modifier = Modifier.weight(1f)) { content() }
            bottomBar(null, Modifier)
        }
        return
    }

    val backdrop = remember { HazeState() }
    val density = LocalDensity.current
    var topHeight by remember { mutableStateOf(0.dp) }
    var bottomHeight by remember { mutableStateOf(0.dp) }
    Box(modifier = modifier.fillMaxSize().background(background)) {
        Box(modifier = Modifier.fillMaxSize().glassBackdrop(backdrop)) {
            CompositionLocalProvider(LocalChromePadding provides PaddingValues(top = topHeight, bottom = bottomHeight)) {
                content()
            }
        }
        topBar(backdrop, Modifier.align(Alignment.TopCenter).onSizeChanged { topHeight = with(density) { it.height.toDp() } })
        bottomBar(backdrop, Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomHeight = with(density) { it.height.toDp() } })
    }
}

/** Same as the list version, for a [LazyVerticalGrid]. */
@Composable
fun OnReachEnd(gridState: LazyGridState, buffer: Int = 4, onReachEnd: () -> Unit) {
    val callback by rememberUpdatedState(onReachEnd)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }.collect { (total, last) ->
            if (total > 0 && last >= total - 1 - buffer) callback()
        }
    }
}

/**
 * Calls [onReachEnd] whenever the list is scrolled within [buffer] items of its end.
 * The callback must guard against repeated calls (e.g. with a "loading" flag).
 */
@Composable
fun OnReachEnd(listState: LazyListState, buffer: Int = 4, onReachEnd: () -> Unit) {
    val callback by rememberUpdatedState(onReachEnd)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }.collect { (total, last) ->
            if (total > 0 && last >= total - 1 - buffer) callback()
        }
    }
}
