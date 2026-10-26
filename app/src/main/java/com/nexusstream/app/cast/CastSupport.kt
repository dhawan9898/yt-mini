package com.nexusstream.app.cast

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.data.watchUrl
import com.nexusstream.app.media.MediaPlaybackService
import com.nexusstream.app.model.MediaItemModel

/** Cast needs Google Play services; on devices without it everything here quietly does nothing. */
object CastSupport {
    fun context(context: Context): CastContext? =
        runCatching { CastContext.getSharedInstance(context.applicationContext) }.getOrNull()
}

/** The standard Cast button; hidden when Cast is not available on this device. */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val appContext = LocalContext.current.applicationContext
    val available = remember { CastSupport.context(appContext) != null }
    if (!available) return
    AndroidView(
        factory = { ctx ->
            // MediaRouteButton insists on an AppCompat theme; the app itself does not use one.
            val themed = ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat)
            MediaRouteButton(themed).also { button ->
                runCatching { CastButtonFactory.setUpMediaRouteButton(ctx.applicationContext, button) }
            }
        },
        modifier = modifier.size(48.dp)
    )
}

/**
 * While a Cast session is connected, sends [item] to the receiver (and again whenever the video
 * changes) and pauses local playback. Uses a single combined video + audio stream (up to 720p).
 * Ending the session leaves local playback paused where it was.
 */
@Composable
fun CastEffect(item: MediaItemModel, playbackService: MediaPlaybackService, audioOnly: Boolean) {
    val appContext = LocalContext.current.applicationContext
    var session by remember { mutableStateOf<CastSession?>(null) }

    DisposableEffect(Unit) {
        val castContext = CastSupport.context(appContext)
        if (castContext == null) {
            onDispose { }
        } else {
            val manager = castContext.sessionManager
            session = manager.currentCastSession?.takeIf { it.isConnected }
            val listener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(s: CastSession, sessionId: String) { session = s }
                override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) { session = s }
                override fun onSessionEnded(s: CastSession, error: Int) { session = null }
                override fun onSessionStarting(s: CastSession) {}
                override fun onSessionStartFailed(s: CastSession, error: Int) {}
                override fun onSessionEnding(s: CastSession) {}
                override fun onSessionResuming(s: CastSession, sessionId: String) {}
                override fun onSessionResumeFailed(s: CastSession, error: Int) {}
                override fun onSessionSuspended(s: CastSession, reason: Int) {}
            }
            manager.addSessionManagerListener(listener, CastSession::class.java)
            onDispose { manager.removeSessionManagerListener(listener, CastSession::class.java) }
        }
    }

    LaunchedEffect(session, item.id) {
        val active = session ?: return@LaunchedEffect
        val client = active.remoteMediaClient ?: return@LaunchedEffect
        val target = YouTubeRepository.castTarget(item.watchUrl, audioOnly).getOrNull() ?: return@LaunchedEffect

        val player = playbackService.getPlayer()
        val positionMs = player.currentPosition
        player.pause()

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, item.title)
            putString(MediaMetadata.KEY_SUBTITLE, item.artistOrChannel)
        }
        val info = MediaInfo.Builder(target.url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(target.mimeType)
            .setMetadata(metadata)
            .build()
        client.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(info)
                .setAutoplay(true)
                .setCurrentTime(positionMs)
                .build()
        )
    }
}
