package com.nexusstream.app.ui.ytmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.YTMusicApi
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.playlistFallbackFor
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.theme.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource

/**
 * What tapping a YT Music card does: songs play with the rest of their shelf as the queue;
 * artists, albums, playlists and podcasts open their own YT Music page.
 */
internal fun openMusicCard(
    card: YTMusicApi.Card,
    queue: List<MediaItemModel>,
    nav: YouTubeNav,
    onPlay: (MediaItemModel, List<MediaItemModel>) -> Unit
) {
    val track = card.track
    if (card.kind == YTMusicApi.CardKind.SONG && track != null) {
        onPlay(track, queue.ifEmpty { listOf(track) })
        return
    }
    val fallback: Route? = when {
        card.kind == YTMusicApi.CardKind.ARTIST -> Route.Channel(card.url, card.title, card.thumbnailUrl, music = true)
        else -> playlistFallbackFor(card.url, card.title)
    }
    if (card.browseId.isNotEmpty()) {
        nav.open(Route.MusicBrowse(card.browseId, card.title, fallback = fallback))
    } else if (card.kind == YTMusicApi.CardKind.ARTIST) {
        nav.openMusicChannel(card.url, card.title, card.thumbnailUrl)
    } else if (card.url.isNotEmpty()) {
        nav.openMusicPlaylist(card.url, card.title)
    }
}

/**
 * Emits YT Music shelves as lazy items: carousels as one row each, song lists and grids row by
 * row (so a 200-track playlist stays lazy). [numbered] shows track numbers instead of artwork
 * (album pages).
 */
internal fun LazyListScope.musicSections(
    sections: List<YTMusicApi.Section>,
    keyPrefix: String,
    onCard: (YTMusicApi.Card, List<MediaItemModel>) -> Unit,
    onButton: (YTMusicApi.NavButton) -> Unit,
    onMore: ((YTMusicApi.Section) -> Unit)? = null,
    numbered: Boolean = false
) {
    sections.forEachIndexed { index, section ->
        val key = "$keyPrefix-$index"
        val queue = section.cards.mapNotNull { it.track }
        if (section.title.isNotBlank() || section.strapline.isNotBlank()) {
            item(key = "$key-title") {
                ShelfTitle(
                    section = section,
                    onMore = if (onMore != null && section.moreBrowseId.isNotEmpty()) ({ onMore(section) }) else null
                )
            }
        }
        when {
            section.description.isNotEmpty() -> item(key = "$key-desc") { DescriptionBlock(section.description) }
            section.buttons.isNotEmpty() -> itemsIndexed(section.buttons.chunked(2), key = { i, _ -> "$key-b$i" }) { _, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { b -> NavButtonTile(b, Modifier.weight(1f)) { onButton(b) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            section.isSongList && section.rowsPerColumn > 1 -> item(key = "$key-grid") {
                SongColumnsRow(section.cards, section.rowsPerColumn) { onCard(it, queue) }
            }
            section.isSongList -> itemsIndexed(section.cards, key = { i, c -> "$key-r$i-${c.track?.id ?: c.browseId}" }) { i, card ->
                MusicCardRow(card, number = if (numbered) i + 1 else null) { onCard(card, queue) }
            }
            section.isGrid -> itemsIndexed(section.cards.chunked(2), key = { i, _ -> "$key-g$i" }) { _, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { card -> MusicTile(card, Modifier.weight(1f), fill = true) { onCard(card, queue) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            else -> item(key = "$key-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(section.cards) { card -> MusicTile(card, Modifier.width(150.dp)) { onCard(card, queue) } }
                }
            }
        }
        item(key = "$key-gap") { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ShelfTitle(section: YTMusicApi.Section, onMore: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (section.strapline.isNotBlank()) {
                Text(section.strapline.uppercase(), fontSize = 12.sp, color = OriginalColors.TextSecondary, maxLines = 1)
            }
            if (section.title.isNotBlank()) {
                Text(
                    section.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onMore != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(OriginalColors.MusicChip)
                    .clickable(onClick = onMore)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("More", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OriginalColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun DescriptionBlock(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text = text,
        fontSize = 14.sp,
        color = OriginalColors.TextSecondary,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp)
    )
}

/** A Moods & genres / Explore button: dark tile with a coloured stripe on the left. */
@Composable
private fun NavButtonTile(button: YTMusicApi.NavButton, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(OriginalColors.MusicChip)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(if (button.color != 0L) Color(button.color) else OriginalColors.Red)
        )
        Text(
            text = button.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = OriginalColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/** Quick picks: songs stacked [rows] high in columns that scroll sideways. */
@Composable
private fun SongColumnsRow(cards: List<YTMusicApi.Card>, rows: Int, onClick: (YTMusicApi.Card) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 4.dp)) {
        items(cards.chunked(rows)) { column ->
            Column(modifier = Modifier.width(320.dp)) {
                column.forEach { card -> MusicCardRow(card) { onClick(card) } }
            }
        }
    }
}

/** A list row: artwork (round for artists) or track number, title, subtitle and the song menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MusicCardRow(card: YTMusicApi.Card, number: Int? = null, onClick: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val track = card.track
    if (showMenu && track != null) SongMenuSheet(item = track, onDismiss = { showMenu = false })
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = if (track != null) ({ showMenu = true }) else null)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (number != null) {
            Text(
                "$number", fontSize = 15.sp, color = OriginalColors.TextSecondary,
                modifier = Modifier.width(32.dp)
            )
        } else {
            val round = card.kind == YTMusicApi.CardKind.ARTIST
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(if (round) CircleShape else RoundedCornerShape(4.dp))
                    .background(OriginalColors.MusicChip)
            ) {
                if (card.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(card.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                card.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = OriginalColors.TextPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track?.isExplicit == true) {
                    Box(
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(OriginalColors.TextSecondary)
                            .padding(horizontal = 3.dp)
                    ) { Text("E", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = OriginalColors.MusicBackground) }
                }
                Text(
                    card.subtitle, fontSize = 13.sp, color = OriginalColors.TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (track != null) {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Rounded.MoreVert, "More options", tint = OriginalColors.TextPrimary)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** A square (round for artists) tile with two lines of text. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MusicTile(card: YTMusicApi.Card, modifier: Modifier, fill: Boolean = false, onClick: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val track = card.track
    if (showMenu && track != null) SongMenuSheet(item = track, onDismiss = { showMenu = false })
    val round = card.kind == YTMusicApi.CardKind.ARTIST
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressScale(interaction, 0.96f)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick, onLongClick = if (track != null) ({ showMenu = true }) else null),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Box(
            modifier = (if (fill) Modifier.fillMaxWidth() else Modifier.size(150.dp))
                .aspectRatio(1f)
                .clip(if (round) CircleShape else RoundedCornerShape(4.dp))
                .background(OriginalColors.MusicChip)
        ) {
            if (card.thumbnailUrl.isNotEmpty()) {
                AsyncImage(card.thumbnailUrl, card.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            card.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = OriginalColors.TextPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        if (card.subtitle.isNotBlank()) {
            Text(
                card.subtitle, fontSize = 12.sp, color = OriginalColors.TextSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
