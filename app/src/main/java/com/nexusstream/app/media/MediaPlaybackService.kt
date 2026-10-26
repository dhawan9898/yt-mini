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
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nexusstream.app.data.ResolvedStream
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.model.StreamRepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State representing continuous playback, error conditions, and queue status for Jetpack Compose UI.
 */
data class PlaybackState(
    val currentItem: MediaItemModel? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackMode: PlaybackMode = PlaybackMode.AUDIO_ONLY,
    val isBuffering: Boolean = false,
    /** True while fresh stream URLs are being fetched, before the player has a source. */
    val isResolving: Boolean = false,
    /** Video heights (e.g. 1080, 720) offered for the current live item, best first. */
    val availableVideoHeights: List<Int> = emptyList(),
    /** Audio bitrates in kbps offered for the current live item, best first. */
    val availableAudioKbps: List<Int> = emptyList(),
    val selectedVideoHeight: Int? = null,
    val selectedAudioKbps: Int? = null,
    /** True when the user has not pinned a video / audio quality. */
    val videoQualityAuto: Boolean = true,
    val audioQualityAuto: Boolean = true,
    val queue: List<MediaItemModel> = emptyList(),
    val queueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: StreamRepeatMode = StreamRepeatMode.ALL,
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

/**
 * AndroidX MediaSessionService enabling foreground audio streaming,
 * ad-blocked media pipelines, queue navigation, and error-recovering playback.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService(), Player.Listener {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loadJob: Job? = null
    private var currentResolved: ResolvedStream? = null
    private var preferredVideoHeight: Int? = null
    private var preferredAudioKbps: Int? = null

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var adBlockPipeline: AdBlockMediaPipeline

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentMode: PlaybackMode = PlaybackMode.AUDIO_ONLY
    private var isReceiverRegistered = false
    private var retryCount = 0

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (::exoPlayer.isInitialized && exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MediaPlaybackService"
        const val NOTIFICATION_CHANNEL_ID = "nexus_stream_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.nexusstream.action.PLAY"
        const val ACTION_PAUSE = "com.nexusstream.action.PAUSE"
        const val ACTION_NEXT = "com.nexusstream.action.NEXT"
        const val ACTION_PREV = "com.nexusstream.action.PREV"
        const val ACTION_STOP = "com.nexusstream.action.STOP"
        private const val MAX_RETRY_ATTEMPTS = 3
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
        createNotificationChannel()
        startForegroundWithInitialNotification()
        initAdBlockPipeline()
        initExoPlayer()
        initMediaSession()
        registerBecomingNoisyReceiver()
        startPositionTrackingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> if (::exoPlayer.isInitialized && !exoPlayer.isPlaying) exoPlayer.play()
            ACTION_PAUSE -> if (::exoPlayer.isInitialized && exoPlayer.isPlaying) exoPlayer.pause()
            ACTION_NEXT -> skipToNext()
            ACTION_PREV -> skipToPrevious()
            ACTION_STOP -> {
                if (::exoPlayer.isInitialized) exoPlayer.pause()
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
        return if (::mediaSession.isInitialized) mediaSession else null
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
            } catch (e: Exception) {
                Log.w(TAG, "Receiver unregister failed: ${e.message}")
            }
            isReceiverRegistered = false
        }
    }

    /**
     * Seamless video-to-audio-only toggle preserving position.
     */
    fun togglePlaybackMode(targetMode: PlaybackMode) {
        if (currentMode == targetMode) return
        currentMode = targetMode

        applyPlaybackModeTracks(targetMode)
        _playbackState.value = _playbackState.value.copy(playbackMode = targetMode)
    }

    private fun applyPlaybackModeTracks(mode: PlaybackMode) {
        if (!::exoPlayer.isInitialized) return
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
            queueIndex = validIndex,
            hasError = false,
            errorMessage = null
        )

        loadTrack(items[validIndex], startInVideoMode)
    }

    /**
     * Plays a single media item (replaces queue with this item).
     */
    fun playMediaItem(item: MediaItemModel, startInVideoMode: Boolean = false) {
        playQueue(listOf(item), 0, startInVideoMode)
    }

    private fun loadTrack(
        item: MediaItemModel,
        startInVideoMode: Boolean = false,
        isRetry: Boolean = false,
        startPositionMs: Long = 0L
    ) {
        if (!::exoPlayer.isInitialized) return

        loadJob?.cancel()
        currentResolved = null
        if (!isRetry) retryCount = 0
        currentMode = if (startInVideoMode) PlaybackMode.VIDEO else PlaybackMode.AUDIO_ONLY
        applyPlaybackModeTracks(currentMode)

        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artistOrChannel)
            .setDisplayTitle(item.title)
            .build()

        val validDuration = if (item.durationMs > 0) item.durationMs else 0L

        _playbackState.value = _playbackState.value.copy(
            currentItem = item,
            playbackMode = currentMode,
            durationMs = validDuration,
            currentPositionMs = startPositionMs,
            bufferedPositionMs = 0L,
            hasError = false,
            errorMessage = null,
            availableVideoHeights = emptyList(),
            availableAudioKbps = emptyList(),
            selectedVideoHeight = null,
            selectedAudioKbps = null
        )
        updateForegroundNotification()
        _playbackState.value = _playbackState.value.copy(isResolving = false)

        if (item.sourceUrl.isEmpty()) {
            // Item already carries direct URLs.
            val streamUrl = if (currentMode == PlaybackMode.VIDEO) item.videoUrl else item.audioUrl
            val mediaItem = MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(streamUrl)
                .setMediaMetadata(metadata)
                .build()
            exoPlayer.setMediaItem(mediaItem, startPositionMs)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            return
        }

        // Live YouTube item: resolve fresh stream URLs first (they expire), then play.
        _playbackState.value = _playbackState.value.copy(isResolving = true)
        loadJob = serviceScope.launch {
            val resolved = YouTubeRepository.resolveStream(item.sourceUrl).getOrElse { error ->
                Log.e(TAG, "Stream resolution failed for ${item.id}: ${error.message}", error)
                _playbackState.value = _playbackState.value.copy(
                    isResolving = false,
                    hasError = true,
                    errorMessage = "Couldn't load stream: ${error.message ?: "unknown error"}"
                )
                return@launch
            }

            currentResolved = resolved
            applyResolved(item, resolved, metadata, startPositionMs, playWhenReady = true)
            _playbackState.value = _playbackState.value.copy(isResolving = false)
        }
    }

    /** Builds and starts the merged video + audio source for the preferred qualities. */
    private fun applyResolved(
        item: MediaItemModel,
        resolved: ResolvedStream,
        metadata: MediaMetadata,
        positionMs: Long,
        playWhenReady: Boolean
    ) {
        val audio = resolved.pickAudio(preferredAudioKbps) ?: return
        val video = resolved.pickVideo(preferredVideoHeight)

        val factory = ProgressiveMediaSource.Factory(adBlockPipeline.createDataSourceFactory())
        val audioSource = factory.createMediaSource(
            MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(audio.url)
                .setMediaMetadata(metadata)
                .build()
        )
        // Always merge video when available; the video renderer stays disabled (and
        // downloads nothing) in audio-only mode, so toggling modes is instant.
        val source = video?.let {
            MergingMediaSource(factory.createMediaSource(MediaItem.fromUri(it.url)), audioSource)
        } ?: audioSource

        exoPlayer.setMediaSource(source, positionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady

        _playbackState.value = _playbackState.value.copy(
            availableVideoHeights = resolved.videoOptions.map { it.height },
            availableAudioKbps = resolved.audioOptions.map { it.kbps },
            selectedVideoHeight = video?.height,
            selectedAudioKbps = audio.kbps,
            videoQualityAuto = preferredVideoHeight == null,
            audioQualityAuto = preferredAudioKbps == null
        )
    }

    /** Pins the video quality (null = auto, capped at 720p). Keeps position and play state. */
    fun setVideoQuality(height: Int?) {
        preferredVideoHeight = height
        reapplyQuality()
    }

    /** Pins the audio quality (null = best available). Keeps position and play state. */
    fun setAudioQuality(kbps: Int?) {
        preferredAudioKbps = kbps
        reapplyQuality()
    }

    private fun reapplyQuality() {
        val item = _playbackState.value.currentItem ?: return
        val resolved = currentResolved ?: return
        if (!::exoPlayer.isInitialized) return
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artistOrChannel)
            .setDisplayTitle(item.title)
            .build()
        applyResolved(item, resolved, metadata, exoPlayer.currentPosition, exoPlayer.playWhenReady)
    }

    fun skipToNext() {
        val currentState = _playbackState.value
        val queue = currentState.queue
        if (queue.isEmpty()) return

        val nextIndex = if (currentState.shuffleEnabled) {
            val remainingIndices = queue.indices.filter { it != currentState.queueIndex }
            if (remainingIndices.isNotEmpty()) remainingIndices.random() else currentState.queueIndex
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

        if (::exoPlayer.isInitialized && exoPlayer.currentPosition > 3000) {
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
        if (!::exoPlayer.isInitialized) return
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (!::exoPlayer.isInitialized) return
        exoPlayer.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun getPlayer(): ExoPlayer = exoPlayer

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
        updateForegroundNotification()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (!::exoPlayer.isInitialized) return

        val isBuffering = playbackState == Player.STATE_BUFFERING
        val playerDuration = exoPlayer.duration
        val duration = if (playerDuration > 0 && playerDuration != C.TIME_UNSET) {
            playerDuration
        } else {
            (_playbackState.value.currentItem?.durationMs ?: 0L)
        }

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

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "ExoPlayer error occurred: ${error.message}", error)
        
        val cause = error.cause
        if (cause is AdBlockedException) {
            Log.i(TAG, "Ad request intercepted cleanly. Resuming main playback stream.")
            // Resume or prepare next segment seamlessly
            if (::exoPlayer.isInitialized) {
                exoPlayer.prepare()
                exoPlayer.play()
            }
            return
        }

        // Handle transient stream loading or network errors
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            retryCount++
            Log.i(TAG, "Retrying playback attempt $retryCount of $MAX_RETRY_ATTEMPTS...")
            val current = _playbackState.value.currentItem
            serviceScope.launch {
                delay(1000L * retryCount)
                if (!::exoPlayer.isInitialized) return@launch
                if (current != null && current.sourceUrl.isNotEmpty()) {
                    // Stream URLs expire; fetch fresh ones and resume where we were.
                    loadTrack(
                        current,
                        startInVideoMode = currentMode == PlaybackMode.VIDEO,
                        isRetry = true,
                        startPositionMs = exoPlayer.currentPosition
                    )
                } else {
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        } else {
            // Auto-advance to next track if retry limit reached
            Log.w(TAG, "Max retry attempts reached. Auto-skipping to next queue item.")
            _playbackState.value = _playbackState.value.copy(
                hasError = true,
                errorMessage = "Stream playback failed. Auto-skipping..."
            )
            skipToNext()
        }
    }

    private fun startPositionTrackingLoop() {
        serviceScope.launch {
            while (true) {
                try {
                    if (::exoPlayer.isInitialized && exoPlayer.isPlaying) {
                        val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val bufferedPos = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                        val playerDur = exoPlayer.duration
                        val dur = if (playerDur > 0 && playerDur != C.TIME_UNSET) {
                            playerDur
                        } else {
                            (_playbackState.value.currentItem?.durationMs ?: 0L)
                        }

                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = currentPos,
                            bufferedPositionMs = bufferedPos,
                            durationMs = dur
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Position tracking loop error: ${e.message}")
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

    private fun startForegroundWithInitialNotification() {
        val initialNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("NexusStream Engine")
            .setContentText("Unified Audio & Video Pipeline Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, initialNotification)
    }

    private fun updateForegroundNotification() {
        val item = _playbackState.value.currentItem ?: return
        if (!::exoPlayer.isInitialized || !::mediaSession.isInitialized) return

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
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        unregisterBecomingNoisyReceiver()
        serviceScope.cancel()
        try {
            if (::exoPlayer.isInitialized) {
                exoPlayer.removeListener(this)
                exoPlayer.release()
            }
            if (::mediaSession.isInitialized) {
                mediaSession.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up playback service resources: ${e.message}")
        }
        super.onDestroy()
    }
}
