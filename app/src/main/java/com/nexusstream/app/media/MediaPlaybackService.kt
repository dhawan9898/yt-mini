package com.nexusstream.app.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.model.StreamRepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State representing continuous playback and queue status for Jetpack Compose UI.
 */
data class PlaybackState(
    val currentItem: MediaItemModel? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackMode: PlaybackMode = PlaybackMode.AUDIO_ONLY,
    val isBuffering: Boolean = false,
    val queue: List<MediaItemModel> = emptyList(),
    val queueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: StreamRepeatMode = StreamRepeatMode.ALL
)

/**
 * AndroidX MediaSessionService enabling foreground audio streaming,
 * ad-blocked media pipelines, queue navigation, and seamless instant audio <-> video toggling.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService(), Player.Listener {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var adBlockPipeline: AdBlockMediaPipeline

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentMode: PlaybackMode = PlaybackMode.AUDIO_ONLY
    private var isReceiverRegistered = false

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
            }
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "nexus_stream_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.nexusstream.action.PLAY"
        const val ACTION_PAUSE = "com.nexusstream.action.PAUSE"
        const val ACTION_NEXT = "com.nexusstream.action.NEXT"
        const val ACTION_PREV = "com.nexusstream.action.PREV"
        const val ACTION_STOP = "com.nexusstream.action.STOP"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        initAdBlockPipeline()
        initExoPlayer()
        initMediaSession()
        createNotificationChannel()
        registerBecomingNoisyReceiver()
        startPositionTrackingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> if (!exoPlayer.isPlaying) exoPlayer.play()
            ACTION_PAUSE -> if (exoPlayer.isPlaying) exoPlayer.pause()
            ACTION_NEXT -> skipToNext()
            ACTION_PREV -> skipToPrevious()
            ACTION_STOP -> {
                exoPlayer.pause()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun initAdBlockPipeline() {
        adBlockPipeline = AdBlockMediaPipeline(this)
    }

    private fun initExoPlayer() {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(adBlockPipeline.createMediaSourceFactory())
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()

        exoPlayer.addListener(this)
        applyPlaybackModeTracks(PlaybackMode.AUDIO_ONLY)
    }

    private fun initMediaSession() {
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setId("NexusStreamMediaSession")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun registerBecomingNoisyReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            registerReceiver(becomingNoisyReceiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun unregisterBecomingNoisyReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    /**
     * Seamless video-to-audio-only seamless toggle.
     * Preserves exact millisecond position without reloading the pipeline.
     */
    fun togglePlaybackMode(targetMode: PlaybackMode) {
        if (currentMode == targetMode) return
        currentMode = targetMode

        applyPlaybackModeTracks(targetMode)
        _playbackState.value = _playbackState.value.copy(playbackMode = targetMode)
    }

    private fun applyPlaybackModeTracks(mode: PlaybackMode) {
        val currentParameters = exoPlayer.trackSelectionParameters
        val newParameters: TrackSelectionParameters = when (mode) {
            PlaybackMode.AUDIO_ONLY -> {
                currentParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
            }
            PlaybackMode.VIDEO -> {
                currentParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                    .build()
            }
        }
        exoPlayer.trackSelectionParameters = newParameters
    }

    /**
     * Starts playing a queue of items starting from a specific index.
     */
    fun playQueue(
        items: List<MediaItemModel>,
        startIndex: Int = 0,
        startInVideoMode: Boolean = false
    ) {
        if (items.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, items.lastIndex)

        _playbackState.value = _playbackState.value.copy(
            queue = items,
            queueIndex = validIndex
        )

        loadTrack(items[validIndex], startInVideoMode)
    }

    /**
     * Plays a single media item (replaces queue with this item).
     */
    fun playMediaItem(item: MediaItemModel, startInVideoMode: Boolean = false) {
        playQueue(listOf(item), 0, startInVideoMode)
    }

    private fun loadTrack(item: MediaItemModel, startInVideoMode: Boolean = false) {
        currentMode = if (startInVideoMode) PlaybackMode.VIDEO else PlaybackMode.AUDIO_ONLY
        applyPlaybackModeTracks(currentMode)

        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artistOrChannel)
            .setDisplayTitle(item.title)
            .build()

        val streamUrl = if (currentMode == PlaybackMode.VIDEO) item.videoUrl else item.audioUrl

        val mediaItem = MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        _playbackState.value = _playbackState.value.copy(
            currentItem = item,
            playbackMode = currentMode,
            durationMs = item.durationMs,
            currentPositionMs = 0L,
            bufferedPositionMs = 0L
        )

        updateForegroundNotification()
    }

    fun skipToNext() {
        val currentState = _playbackState.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        val nextIndex = if (currentState.shuffleEnabled) {
            queue.indices.filter { it != currentState.queueIndex }.randomOrNull() ?: 0
        } else {
            if (currentState.queueIndex + 1 < queue.size) {
                currentState.queueIndex + 1
            } else if (currentState.repeatMode == StreamRepeatMode.ALL) {
                0
            } else {
                return
            }
        }

        _playbackState.value = currentState.copy(queueIndex = nextIndex)
        loadTrack(queue[nextIndex], currentMode == PlaybackMode.VIDEO)
    }

    fun skipToPrevious() {
        val currentState = _playbackState.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        // If played more than 3 seconds, rewind to beginning
        if (exoPlayer.currentPosition > 3000) {
            seekTo(0)
            return
        }

        val prevIndex = if (currentState.queueIndex - 1 >= 0) {
            currentState.queueIndex - 1
        } else if (currentState.repeatMode == StreamRepeatMode.ALL) {
            queue.lastIndex
        } else {
            0
        }

        _playbackState.value = currentState.copy(queueIndex = prevIndex)
        loadTrack(queue[prevIndex], currentMode == PlaybackMode.VIDEO)
    }

    fun toggleShuffle() {
        val newState = !_playbackState.value.shuffleEnabled
        _playbackState.value = _playbackState.value.copy(shuffleEnabled = newState)
    }

    fun toggleRepeatMode() {
        val current = _playbackState.value.repeatMode
        val next = when (current) {
            StreamRepeatMode.OFF -> StreamRepeatMode.ALL
            StreamRepeatMode.ALL -> StreamRepeatMode.ONE
            StreamRepeatMode.ONE -> StreamRepeatMode.OFF
        }
        _playbackState.value = _playbackState.value.copy(repeatMode = next)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun getPlayer(): ExoPlayer = exoPlayer

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
        updateForegroundNotification()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val isBuffering = playbackState == Player.STATE_BUFFERING
        val duration = if (exoPlayer.duration > 0) exoPlayer.duration else (_playbackState.value.currentItem?.durationMs ?: 0L)
        _playbackState.value = _playbackState.value.copy(
            isBuffering = isBuffering,
            durationMs = duration
        )

        // Handle track completion
        if (playbackState == Player.STATE_ENDED) {
            when (_playbackState.value.repeatMode) {
                StreamRepeatMode.ONE -> {
                    seekTo(0)
                    exoPlayer.play()
                }
                else -> skipToNext()
            }
        }
    }

    private fun startPositionTrackingLoop() {
        serviceScope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    val currentPos = exoPlayer.currentPosition
                    val bufferedPos = exoPlayer.bufferedPosition
                    val dur = if (exoPlayer.duration > 0) exoPlayer.duration else (_playbackState.value.currentItem?.durationMs ?: 0L)

                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = currentPos,
                        bufferedPositionMs = bufferedPos,
                        durationMs = dur
                    )
                }
                delay(200)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "NexusStream Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous playback and media notification controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateForegroundNotification() {
        val item = _playbackState.value.currentItem ?: return
        val isPlaying = exoPlayer.isPlaying

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MediaPlaybackService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MediaPlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(item.title)
            .setContentText(item.artistOrChannel)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        unregisterBecomingNoisyReceiver()
        serviceScope.cancel()
        exoPlayer.removeListener(this)
        exoPlayer.release()
        mediaSession.release()
        super.onDestroy()
    }
}
