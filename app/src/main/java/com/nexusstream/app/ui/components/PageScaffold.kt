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

/** What a page needs to draw the docked mini-player. */
class MiniPlayerHooks(
    val item: MediaItemModel?,
    val isPlaying: Boolean,
    val onExpand: () -> Unit,
    val onTogglePlay: () -> Unit
)

/**
 * Full-screen page chrome: status-bar inset, back arrow + title bar, content, and the mini-player
 * (when something is playing) above the navigation bar.
 */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    miniPlayer: MiniPlayerHooks,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    /** Replaces the title text (e.g. with a search field). */
    header: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OriginalColors.YouTubeBackground)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            if (header != null) {
                header()
            } else {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            actions()
        }
        Box(modifier = Modifier.weight(1f)) { content() }
        val playing = miniPlayer.item
        if (playing != null) {
            OriginalMiniPlayer(
                item = playing,
                isPlaying = miniPlayer.isPlaying,
                onExpand = miniPlayer.onExpand,
                onTogglePlay = miniPlayer.onTogglePlay,
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .navigationBarsPadding()
            )
        } else {
            Box(modifier = Modifier.navigationBarsPadding())
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
