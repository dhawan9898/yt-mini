package com.nexusstream.app.ui.ytmusic

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.YTMusicApi
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.data.watchUrl
import com.nexusstream.app.media.PlaybackState
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.LocalNav
import com.nexusstream.app.ui.LocalPlaybackService
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.PlayerOptionsSheet
import com.nexusstream.app.ui.youtube.AddToPlaylistSheet

/**
 * The YT Music song menu (⋮ on a song): Start radio, Play next, Add to queue, like / dislike,
 * Save to playlist, Download, Go to album, Go to artist, View song credits and Share.
 * [onQuality] adds an "Audio quality" row (used by the now-playing screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuSheet(
    item: MediaItemModel,
    onDismiss: () -> Unit,
    onQuality: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeLabel: String = "Remove"
) {
    val context = LocalContext.current
    val service = LocalPlaybackService.current
    val nav = LocalNav.current
    val liked by LibraryRepository.liked.collectAsState()
    val disliked by LibraryRepository.disliked.collectAsState()
    val isLiked = liked.any { it.id == item.id }
    val isDisliked = item.id in disliked
    var sub by remember { mutableStateOf(SongSubSheet.NONE) }

    // Album, artists and the credits page come from YT Music's own data for the song.
    var info by remember(item.id) { mutableStateOf<YTMusicApi.TrackInfo?>(null) }
    LaunchedEffect(item.id) {
        info = YouTubeRepository.musicTrackInfo(item).getOrNull()
    }
    val albumId = item.albumId.ifEmpty { info?.albumId.orEmpty() }
    val albumName = item.albumName.ifEmpty { info?.albumName.orEmpty() }
    val artist = info?.artists?.firstOrNull { it.id.isNotEmpty() }
    val artistUrl = item.channelUrl.ifEmpty { artist?.id?.let { "https://www.youtube.com/channel/$it" }.orEmpty() }

    when (sub) {
        SongSubSheet.PLAYLISTS -> {
            AddToPlaylistSheet(item = item, onDismiss = onDismiss)
            return
        }
        SongSubSheet.DOWNLOAD -> {
            PlayerOptionsSheet(item = item, state = PlaybackState(), service = null, showVideoQuality = false, onDismiss = onDismiss)
            return
        }
        SongSubSheet.CREDITS -> {
            CreditsSheet(item = item, creditsBrowseId = info?.creditsBrowseId, onDismiss = onDismiss)
            return
        }
        SongSubSheet.NONE -> Unit
    }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OriginalColors.Sheet,
        contentColor = OriginalColors.TextPrimary
    ) {
        com.nexusstream.app.ui.theme.GlassWindowBlur()
        Column(modifier = Modifier.navigationBarsPadding().verticalScroll(rememberScrollState())) {
            SongHeader(item)
            MenuRow(Icons.Outlined.Radio, "Start radio") {
                service?.playQueue(listOf(item), 0, startInVideoMode = false)
                onDismiss()
            }
            MenuRow(Icons.AutoMirrored.Outlined.PlaylistPlay, "Play next") {
                service?.playNext(listOf(item))
                toast("Song will play next")
                onDismiss()
            }
            MenuRow(Icons.Outlined.QueueMusic, "Add to queue") {
                service?.addToQueue(listOf(item))
                toast("Song added to queue")
                onDismiss()
            }
            MenuRow(
                if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                if (isLiked) "Remove from liked songs" else "Add to liked songs"
            ) {
                LibraryRepository.toggleLike(item)
                onDismiss()
            }
            MenuRow(if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, if (isDisliked) "Remove dislike" else "Dislike") {
                LibraryRepository.toggleDislike(item)
                onDismiss()
            }
            MenuRow(Icons.Outlined.PlaylistAdd, "Save to playlist") { sub = SongSubSheet.PLAYLISTS }
            MenuRow(Icons.Outlined.FileDownload, "Download") { sub = SongSubSheet.DOWNLOAD }
            if (onQuality != null) {
                MenuRow(Icons.Outlined.Tune, "Audio quality") {
                    onDismiss()
                    onQuality()
                }
            }
            if (albumId.isNotEmpty() && nav != null) {
                MenuRow(Icons.Outlined.Album, "Go to album") {
                    nav.open(Route.MusicBrowse(albumId, albumName.ifEmpty { "Album" }))
                    onDismiss()
                }
            }
            if (artistUrl.isNotEmpty() && nav != null) {
                MenuRow(Icons.Outlined.Person, "Go to artist") {
                    nav.openMusicChannel(artistUrl, artist?.name ?: item.artistOrChannel, "")
                    onDismiss()
                }
            }
            if (info?.creditsBrowseId != null) {
                MenuRow(Icons.Outlined.Info, "View song credits") { sub = SongSubSheet.CREDITS }
            }
            if (onRemove != null) {
                MenuRow(Icons.Outlined.QueueMusic, removeLabel) {
                    onRemove()
                    onDismiss()
                }
            }
            MenuRow(Icons.Outlined.Share, "Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.watchUrl.replace("www.youtube.com", "music.youtube.com"))
                }
                context.startActivity(Intent.createChooser(send, "Share"))
                onDismiss()
            }
        }
    }
}

private enum class SongSubSheet { NONE, PLAYLISTS, DOWNLOAD, CREDITS }

@Composable
private fun SongHeader(item: MediaItemModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(item.dominantColor)
        ) {
            if (item.artworkUrl.isNotEmpty()) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(item.artistOrChannel, item.albumName).filter { it.isNotBlank() }.joinToString(" • "),
                fontSize = 13.sp,
                color = OriginalColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

/** "View song credits": Performed by, Written by, Produced by... */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditsSheet(item: MediaItemModel, creditsBrowseId: String?, onDismiss: () -> Unit) {
    var sections by remember { mutableStateOf<Result<List<YTMusicApi.CreditSection>>?>(null) }
    LaunchedEffect(creditsBrowseId) {
        sections = creditsBrowseId?.let { YouTubeRepository.songCredits(it) }
            ?: Result.failure(IllegalStateException("No credits for this song"))
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OriginalColors.Sheet,
        contentColor = OriginalColors.TextPrimary
    ) {
        com.nexusstream.app.ui.theme.GlassWindowBlur()
        Column(modifier = Modifier.fillMaxHeight(0.7f).navigationBarsPadding()) {
            Text(
                text = "Song credits",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            SongHeader(item)
            val result = sections
            when {
                result == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OriginalColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
                result.isFailure -> Text(
                    text = result.exceptionOrNull()?.message ?: "Couldn't load credits",
                    color = OriginalColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(20.dp)
                )
                else -> LazyColumn {
                    items(result.getOrDefault(emptyList())) { section ->
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(section.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(section.names.joinToString("\n"), fontSize = 14.sp, color = OriginalColors.TextSecondary, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = OriginalColors.TextPrimary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(20.dp))
        Text(text = label, fontSize = 16.sp, color = OriginalColors.TextPrimary)
    }
}
