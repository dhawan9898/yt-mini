package com.nexusstream.app.ui.player

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.YTMusicApi
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.theme.DarkPalette

/**
 * The RELATED tab, built from YouTube Music's own related shelves: "You might also like" songs in
 * four-row columns, playlist / album carousels, "Similar artists" and "About the artist".
 */
@Composable
fun RelatedPanel(
    sections: List<YTMusicApi.Section>,
    onPlay: (tracks: List<MediaItemModel>, index: Int) -> Unit,
    onOpenPlaylist: (url: String, title: String) -> Unit,
    onOpenArtist: (url: String, name: String, avatarUrl: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(sections) { section ->
            Column {
                if (section.title.isNotBlank()) {
                    Text(
                        text = section.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                when {
                    section.description.isNotBlank() -> AboutArtist(section.description)
                    section.isSongList -> SongColumns(section.cards, onPlay)
                    else -> CardRow(section.cards, onPlay, onOpenPlaylist, onOpenArtist)
                }
            }
        }
    }
}

/** Songs as horizontally scrolling columns of four rows (the YT Music "quick picks" layout). */
@Composable
private fun SongColumns(cards: List<YTMusicApi.Card>, onPlay: (List<MediaItemModel>, Int) -> Unit) {
    val tracks = cards.mapNotNull { it.track }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(cards.chunked(4)) { column ->
            Column(modifier = Modifier.width(290.dp)) {
                column.forEach { card ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val index = tracks.indexOfFirst { it.id == card.track?.id }
                                if (index >= 0) onPlay(tracks, index)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Thumbnail(card.thumbnailUrl, card.title, Modifier.size(48.dp), RoundedCornerShape(4.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                card.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                card.subtitle,
                                fontSize = 12.sp,
                                color = DarkPalette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Square tiles for albums, playlists and videos; round ones for artists. */
@Composable
private fun CardRow(
    cards: List<YTMusicApi.Card>,
    onPlay: (List<MediaItemModel>, Int) -> Unit,
    onOpenPlaylist: (String, String) -> Unit,
    onOpenArtist: (String, String, String) -> Unit
) {
    val tracks = cards.mapNotNull { it.track }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(cards) { card ->
            val isArtist = card.kind == YTMusicApi.CardKind.ARTIST
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable {
                        when (card.kind) {
                            YTMusicApi.CardKind.SONG -> {
                                val index = tracks.indexOfFirst { it.id == card.track?.id }
                                if (index >= 0) onPlay(tracks, index)
                            }
                            YTMusicApi.CardKind.ARTIST -> onOpenArtist(card.url, card.title, card.thumbnailUrl)
                            YTMusicApi.CardKind.ALBUM, YTMusicApi.CardKind.PLAYLIST -> onOpenPlaylist(card.url, card.title)
                        }
                    },
                horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start
            ) {
                Thumbnail(
                    card.thumbnailUrl,
                    card.title,
                    Modifier.size(140.dp),
                    if (isArtist) CircleShape else RoundedCornerShape(6.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    card.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = if (isArtist) TextAlign.Center else TextAlign.Start
                )
                if (card.subtitle.isNotBlank()) {
                    Text(
                        card.subtitle,
                        fontSize = 12.sp,
                        color = DarkPalette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (isArtist) TextAlign.Center else TextAlign.Start
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutArtist(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }
    Text(
        text = description,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Color.White.copy(alpha = 0.85f),
        maxLines = if (expanded) Int.MAX_VALUE else 5,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(14.dp)
    )
}

@Composable
private fun Thumbnail(url: String, description: String, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF222222))
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
