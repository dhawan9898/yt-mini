package com.nexusstream.app.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
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
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.dash.manifest.DashManifestParser
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import com.nexusstream.app.data.AppSettings
import com.nexusstream.app.data.AudioTrack
import com.nexusstream.app.data.InnerTube
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.ResolvedStream
import com.nexusstream.app.data.SubtitleOption
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.model.PlaybackMode
import com.nexusstream.app.model.MediaContentType
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
    /**
     * True while the player is playing or trying to play (buffering with play pressed), like the
     * play / pause button on YouTube. Use [isBuffering] to tell the two apart.
     */
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
    /** Language / audio tracks offered for the current live item (original first). */
    val availableAudioTracks: List<AudioTrack> = emptyList(),
    val selectedAudioTrackId: String? = null,
    val availableSubtitles: List<SubtitleOption> = emptyList(),
    /** Language tag of the shown captions; null = captions off. */
    val selectedSubtitleTag: String? = null,
    /** Language the captions are auto-translated into (null = as published). */
    val subtitleTranslation: String? = null,
    val playbackSpeed: Float = 1f,
    /** Epoch millis when the sleep timer pauses playback; 0 = no timer. */
    val sleepTimerEndsAtMs: Long = 0L,
    /** Play a related video automatically when the current one ends. */
    val autoplayNext: Boolean = true,
    /** True when the user has not pinned a video / audio quality. */
    val videoQualityAuto: Boolean = true,
    val audioQualityAuto: Boolean = true,
    val queue: List<MediaItemModel> = emptyList(),
    val queueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: StreamRepeatMode = StreamRepeatMode.OFF,
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

/**
 * AndroidX MediaSessionService enabling foreground audio streaming,
 * ad-blocked media pipelines, queue navigation, and error-recovering playback.
 */
@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaLibraryService(), Player.Listener {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loadJob: Job? = null
    private var currentResolved: ResolvedStream? = null
    private var preferredVideoHeight: Int? = null
    private var preferredAudioKbps: Int? = null
    private var preferredAudioTrackId: String? = null
    private var preferredSubtitleTag: String? = null
    /** Once the user picks a caption language (or Off) their choice wins over "captions by default". */
    private var subtitleChosenByUser = false
    private var preferredTranslation: String? = null
    /** True once playback switched to the single combined stream because the merged ones failed. */
    private var usingMuxedFallback = false
    /** The current item plays from a generated DASH manifest (quality adapts to the network). */
    private var dashActive = false
    /** DASH failed for the current item: play the separate streams merged instead. */
    private var dashDisabled = false
    private val precaching = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    @kotlin.OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val precacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    /** The current video's loudness relative to YouTube's target (dB), for Stable volume. */
    private var currentLoudnessDb: Double? = null
    private var loudnessJob: Job? = null
    /** Watch-history pings for the current video (signed in), and its playback nonce. */
    private var currentExtras: InnerTube.PlayerExtras? = null
    /** End screen and info cards of the current video, for the watch page (item id to extras). */
    private val _watchExtras = MutableStateFlow<Pair<String, InnerTube.PlayerExtras>?>(null)
    val watchExtras: StateFlow<Pair<String, InnerTube.PlayerExtras>?> = _watchExtras.asStateFlow()
    private var currentCpn = ""
    private var sleepJob: Job? = null
    /** Related videos to continue with when the queue runs out (set by the watch page). */
    private var autoplayQueue: List<MediaItemModel> = emptyList()
    /** True while the queue was started from the music player (not the video watch page). */
    private var musicSession = false
    /** Token for the next page of the YT Music radio feeding the queue (null: start one from the last track). */
    private var radioContinuation: String? = null
    private var radioJob: Job? = null
    /** Ids of queue entries that came from radio / autoplay rather than from the user. */
    private val radioIds = HashSet<String>()

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaLibraryService.MediaLibrarySession
    private lateinit var autoLibrary: AutoLibrary
    private lateinit var adBlockPipeline: AdBlockMediaPipeline
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

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
        /** Fetch more radio once this few tracks are left in Up next. */
        private const val RADIO_LOW_WATER = 2
        /** About the first five seconds of a 720p short, and of its audio. */
        private const val PRECACHE_VIDEO_BYTES = 1_200_000L
        private const val PRECACHE_AUDIO_BYTES = 160_000L
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
        AppSettings.init(this)
        com.nexusstream.app.data.LibraryRepository.init(this)
        // Settings -> defaults for playback (changes apply to the next video, not the current one).
        serviceScope.launch {
            AppSettings.autoplay.collect { enabled ->
                _playbackState.value = _playbackState.value.copy(autoplayNext = enabled)
            }
        }
        serviceScope.launch {
            AppSettings.stableVolume.collect { enabled ->
                updateStableVolume(enabled)
            }
        }
        createNotificationChannel()
        startForegroundWithInitialNotification()
        initAdBlockPipeline()
        initExoPlayer()
        initMediaSession()
        registerBecomingNoisyReceiver()
        startPositionTrackingLoop()
    }

    /**
     * Stable volume: brings the video to YouTube's playback level using its measured loudness.
     * Loud videos are turned down with the player volume; quiet ones are boosted (at most 6 dB)
     * with a LoudnessEnhancer. Unknown loudness leaves the level as it is.
     */
    private fun updateStableVolume(enabled: Boolean) {
        if (!::exoPlayer.isInitialized) return
        val gainDb = if (enabled) currentLoudnessDb?.let { (-it).coerceIn(-12.0, 6.0) } ?: 0.0 else 0.0
        exoPlayer.volume = if (gainDb < 0) Math.pow(10.0, gainDb / 20).toFloat() else 1f
        val sessionId = exoPlayer.audioSessionId
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        try {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(sessionId)
            }
            val boost = gainDb > 0.05
            loudnessEnhancer?.setTargetGain(if (boost) (gainDb * 100).toInt() else 0)
            loudnessEnhancer?.enabled = boost
        } catch (e: Exception) {
            Log.w(TAG, "LoudnessEnhancer could not be configured: ${e.message}")
        }
    }

    private fun resumable(item: MediaItemModel): Boolean =
        item.sourceUrl.isNotEmpty() && !item.isShort && !item.isLive &&
            item.contentType == MediaContentType.YOUTUBE_VIDEO

    /** Remembers where the current video is, so opening it again resumes there. */
    private fun saveCurrentPosition() {
        if (!::exoPlayer.isInitialized) return
        val item = _playbackState.value.currentItem ?: return
        if (!resumable(item) || exoPlayer.currentMediaItem == null) return
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: item.durationMs
        com.nexusstream.app.data.WatchPositions.save(item.id, exoPlayer.currentPosition, duration)
    }

    /** Sends how long the current video was watched, so the account's history shows progress. */
    private fun reportWatchtimeOfCurrent() {
        val extras = currentExtras ?: return
        if (!::exoPlayer.isInitialized) return
        val seconds = exoPlayer.currentPosition.coerceAtLeast(0L) / 1000.0
        val cpn = currentCpn
        currentExtras = null
        serviceScope.launch(Dispatchers.IO) { InnerTube.reportWatchtime(extras, cpn, seconds) }
    }

    /** Video quality cap when the user hasn't picked one: the Wi-Fi or mobile-data setting. */
    private fun networkDefaultHeight(): Int? {
        val metered = runCatching {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered
        }.getOrDefault(false)
        val height = if (metered) AppSettings.mobileVideoHeight.value else AppSettings.defaultVideoHeight.value
        return height.takeIf { it > 0 }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> if (::exoPlayer.isInitialized && !exoPlayer.playWhenReady) exoPlayer.play()
            ACTION_PAUSE -> if (::exoPlayer.isInitialized && exoPlayer.playWhenReady) exoPlayer.pause()
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
                // Keep more buffered ahead so short network dips don't stall the video, and after a
                // stall gather a few seconds before resuming instead of stopping again at once.
                /* minBufferMs = */ 50_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            // If a hardware decoder fails to start, try another one instead of giving up.
            .setEnableDecoderFallback(true)

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
        // A library session, so Android Auto and other media browsers can browse and search.
        autoLibrary = AutoLibrary(this, serviceScope)
        mediaSession = MediaLibraryService.MediaLibrarySession.Builder(this, autoLibrary.wrap(exoPlayer), autoLibrary)
            .setId("NexusStreamMediaSession")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? {
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
        // Related-video autoplay belongs to the video watch page, never to music playback.
        if (!startInVideoMode) autoplayQueue = emptyList()
        // The music player can show a song as video too, so music tracks count whatever the mode.
        musicSession = !startInVideoMode || items[validIndex].contentType == MediaContentType.MUSIC_TRACK
        radioContinuation = null
        radioJob?.cancel()
        radioIds.clear()

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
        requestedPositionMs: Long = 0L
    ) {
        if (!::exoPlayer.isInitialized) return
        if (!isRetry) saveCurrentPosition()
        // A video left part way resumes there, like YouTube; music and Shorts start over.
        val startPositionMs = if (requestedPositionMs == 0L && !isRetry && !musicSession && resumable(item)) {
            com.nexusstream.app.data.WatchPositions.resumeAt(item.id)
        } else requestedPositionMs

        loadJob?.cancel()
        currentResolved = null
        if (!isRetry) {
            retryCount = 0
            usingMuxedFallback = false
            // Shorts are short: progressive streams start sooner and match what was precached.
            dashDisabled = item.isShort
            reportWatchtimeOfCurrent()
            currentLoudnessDb = null
            currentExtras = null
            loudnessJob?.cancel()
            if (item.sourceUrl.isNotEmpty()) {
                // YT Music plays stay out of YouTube watch history, like the official apps.
                val recordHistory = !musicSession && item.contentType != MediaContentType.MUSIC_TRACK &&
                    !LibraryRepository.historyPaused.value
                loudnessJob = serviceScope.launch {
                    val extras = kotlinx.coroutines.withContext(Dispatchers.IO) { InnerTube.playerExtras(item.id) }
                    if (_playbackState.value.currentItem?.id != item.id) return@launch
                    _watchExtras.value = extras?.let { item.id to it }
                    currentLoudnessDb = extras?.loudnessDb
                    updateStableVolume(AppSettings.stableVolume.value)
                    if (extras != null && recordHistory && com.nexusstream.app.data.GoogleAccount.isSignedIn()) {
                        currentExtras = extras
                        currentCpn = InnerTube.newCpn()
                        val cpn = currentCpn
                        kotlinx.coroutines.withContext(Dispatchers.IO) { InnerTube.reportPlayback(extras, cpn) }
                    }
                }
            } else {
                updateStableVolume(AppSettings.stableVolume.value)
            }
        }
        dashActive = false
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
            availableAudioTracks = emptyList(),
            selectedAudioTrackId = null,
            availableSubtitles = emptyList(),
            selectedSubtitleTag = null,
            selectedVideoHeight = null,
            selectedAudioKbps = null
        )
        updateForegroundNotification()
        _playbackState.value = _playbackState.value.copy(isResolving = false)
        if (!isRetry) extendRadioIfNeeded()

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
        // Drop the previous video now, so it neither keeps playing nor shows its last frame while
        // the new one resolves.
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
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

    /** Video height and audio kbps caps. Data saver caps video at 480p and audio at 96 kbps unless the user pinned a lower quality. */
    private fun qualityCaps(): Pair<Int?, Int?> {
        val saver = AppSettings.dataSaver.value
        val wanted = preferredVideoHeight ?: networkDefaultHeight()
        val heightCap = if (saver) minOf(wanted ?: 480, 480) else wanted
        val audioCap = if (saver && preferredAudioKbps == null) 96 else preferredAudioKbps
        return heightCap to audioCap
    }

    /**
     * Downloads the first seconds of a short that is coming up into the playback cache, so it
     * starts the moment it is swiped to. Shorts play the progressive streams (not DASH), so the
     * cached bytes are exactly the ones the player asks for.
     */
    fun precacheShort(watchUrl: String) {
        if (!::exoPlayer.isInitialized || watchUrl.isBlank()) return
        if (!precaching.add(watchUrl)) return
        precacheScope.launch {
            try {
                val resolved = YouTubeRepository.prefetchedStream(watchUrl) ?: return@launch
                val (heightCap, audioCap) = qualityCaps()
                val targets = listOfNotNull(
                    resolved.pickVideo(heightCap)?.url?.let { it to PRECACHE_VIDEO_BYTES },
                    resolved.pickAudio(audioCap, preferredAudioTrackId)?.url?.let { it to PRECACHE_AUDIO_BYTES }
                )
                for ((url, bytes) in targets) {
                    val source = adBlockPipeline.createStreamingDataSourceFactory().createDataSource()
                        as? androidx.media3.datasource.cache.CacheDataSource ?: return@launch
                    val spec = androidx.media3.datasource.DataSpec.Builder().setUri(Uri.parse(url)).setPosition(0).setLength(bytes).build()
                    runCatching { androidx.media3.datasource.cache.CacheWriter(source, spec, null, null).cache() }
                        .onFailure { Log.d(TAG, "Precache skipped: ${it.message}") }
                }
            } finally {
                if (precaching.size > 20) precaching.clear()
            }
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
        // Live stream: one HLS playlist, played from the live edge (no quality / caption choices).
        resolved.hlsUrl?.let { hlsUrl ->
            val hlsSource = HlsMediaSource.Factory(adBlockPipeline.createDataSourceFactory())
                .createMediaSource(
                    MediaItem.Builder().setMediaId(item.id).setUri(hlsUrl).setMediaMetadata(metadata).build()
                )
            exoPlayer.setMediaSource(hlsSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = playWhenReady
            return
        }

        val (heightCap, audioCap) = qualityCaps()
        val audio = resolved.pickAudio(audioCap, preferredAudioTrackId) ?: return
        val video = resolved.pickVideo(heightCap)

        val subtitleTag = if (subtitleChosenByUser) preferredSubtitleTag else defaultSubtitleTag(resolved)
        val subtitle = subtitleTag?.let { tag -> resolved.subtitles.firstOrNull { it.languageTag == tag } }
        val subtitleSource = subtitle?.let { sub ->
            val url = preferredTranslation?.let { com.nexusstream.app.data.WatchExtras.translatedUrl(sub.url, it) } ?: sub.url
            val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                .setMimeType(sub.mimeType)
                .setLanguage(sub.languageTag)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            SingleSampleMediaSource.Factory(adBlockPipeline.createDataSourceFactory())
                .createMediaSource(config, C.TIME_UNSET)
        }

        // Adaptive playback: every video quality in one DASH manifest, switched with the network.
        val dashAudio = resolved.dashAudio.filter { it.audioTrackId == audio.trackId }.ifEmpty { resolved.dashAudio }
        val dashSource = if (!dashDisabled && !usingMuxedFallback && dashAudio.isNotEmpty()) {
            runCatching {
                val xml = DashManifest.build(resolved.dashVideo, dashAudio, resolved.durationSec)
                val manifest = DashManifestParser().parse(Uri.parse(item.sourceUrl), xml.byteInputStream())
                DashMediaSource.Factory(adBlockPipeline.createStreamingDataSourceFactory())
                    .createMediaSource(manifest, MediaItem.Builder().setMediaId(item.id).setMediaMetadata(metadata).build())
            }.onFailure { Log.w(TAG, "DASH manifest failed: ${it.message}") }.getOrNull()
        }  else null
        if (dashSource != null) {
            dashActive = true
            applyDashConstraints(resolved, heightCap, audioCap)
            val source: MediaSource = if (subtitleSource != null) MergingMediaSource(dashSource, subtitleSource) else dashSource
            exoPlayer.setMediaSource(source, positionMs)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = playWhenReady
            val heights = resolved.dashVideo.map { it.qualityHeight }.distinct().sortedDescending()
            _playbackState.value = _playbackState.value.copy(
                availableVideoHeights = heights,
                availableAudioKbps = resolved.kbpsFor(audio.trackId),
                availableAudioTracks = resolved.audioTracks,
                selectedAudioTrackId = audio.trackId,
                availableSubtitles = resolved.subtitles,
                selectedSubtitleTag = subtitle?.languageTag,
                subtitleTranslation = preferredTranslation.takeIf { subtitle != null },
                selectedVideoHeight = preferredVideoHeight,
                selectedAudioKbps = preferredAudioKbps ?: audio.kbps,
                videoQualityAuto = preferredVideoHeight == null,
                audioQualityAuto = preferredAudioKbps == null
            )
            return
        }
        dashActive = false
        clearDashConstraints()

        val factory = ProgressiveMediaSource.Factory(adBlockPipeline.createStreamingDataSourceFactory())
        val audioSource = factory.createMediaSource(
            MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(audio.url)
                .setMediaMetadata(metadata)
                .build()
        )
        // Always merge video when available; the video renderer stays disabled (and
        // downloads nothing) in audio-only mode, so toggling modes is instant.
        val sources = listOfNotNull(
            video?.let { factory.createMediaSource(MediaItem.fromUri(it.url)) },
            audioSource,
            subtitleSource
        )
        val source: MediaSource = if (sources.size == 1) sources.first() else MergingMediaSource(*sources.toTypedArray())

        exoPlayer.setMediaSource(source, positionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady

        _playbackState.value = _playbackState.value.copy(
            availableVideoHeights = resolved.videoOptions.map { it.height },
            availableAudioKbps = resolved.kbpsFor(audio.trackId),
            availableAudioTracks = resolved.audioTracks,
            selectedAudioTrackId = audio.trackId,
            availableSubtitles = resolved.subtitles,
            selectedSubtitleTag = subtitle?.languageTag,
            subtitleTranslation = preferredTranslation.takeIf { subtitle != null },
            selectedVideoHeight = video?.height,
            selectedAudioKbps = audio.kbps,
            videoQualityAuto = preferredVideoHeight == null,
            audioQualityAuto = preferredAudioKbps == null
        )
    }

    /** The caption track to show when "captions by default" is on: the device language, manual first. */
    private fun defaultSubtitleTag(resolved: ResolvedStream): String? {
        if (!AppSettings.captionsByDefault.value) return null
        val language = java.util.Locale.getDefault().language
        val matching = resolved.subtitles.filter { it.languageTag.startsWith(language) }
        return (matching.firstOrNull { !it.autoGenerated } ?: matching.firstOrNull())?.languageTag
    }

    /**
     * DASH quality limits as track-selection constraints, so a change applies without reloading:
     * a pinned height allows only that quality; Auto adapts up to [heightCap] (none = no cap).
     * Heights are the shorter side, so vertical videos are capped by their width.
     */
    private fun applyDashConstraints(resolved: ResolvedStream, heightCap: Int?, audioKbpsCap: Int?) {
        val portrait = resolved.dashVideo.any { it.width in 1 until it.height }
        val pinned = preferredVideoHeight
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
            .clearVideoSizeConstraints()
            .setMinVideoSize(0, 0)
        val cap = pinned ?: heightCap
        if (cap != null) {
            if (portrait) builder.setMaxVideoSize(cap, Int.MAX_VALUE) else builder.setMaxVideoSize(Int.MAX_VALUE, cap)
        }
        if (pinned != null) {
            if (portrait) builder.setMinVideoSize(pinned, 0) else builder.setMinVideoSize(0, pinned)
        }
        builder.setMaxAudioBitrate(audioKbpsCap?.let { it * 1000 + 999 } ?: Int.MAX_VALUE)
        exoPlayer.trackSelectionParameters = builder.build()
    }

    private fun clearDashConstraints() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearVideoSizeConstraints()
            .setMinVideoSize(0, 0)
            .setMaxAudioBitrate(Int.MAX_VALUE)
            .build()
    }

    /** Shows the quality DASH is actually playing while on Auto. */
    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
        if (!dashActive) return
        val playing = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }?.let { group ->
            (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
        }
        if (playing != null && preferredVideoHeight == null) {
            val h = if (playing.width > 0 && playing.height > 0) minOf(playing.width, playing.height) else playing.height
            _playbackState.value = _playbackState.value.copy(selectedVideoHeight = h)
        }
    }

    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
        if (!dashActive || preferredVideoHeight != null || videoSize.height <= 0) return
        val h = if (videoSize.width > 0) minOf(videoSize.width, videoSize.height) else videoSize.height
        _playbackState.value = _playbackState.value.copy(selectedVideoHeight = h)
    }

    /** Pins the video quality (null = Auto, which adapts to the network). Keeps position and play state. */
    fun setVideoQuality(height: Int?) {
        preferredVideoHeight = height
        val resolved = currentResolved
        if (dashActive && resolved != null) {
            val saver = AppSettings.dataSaver.value
            val wanted = height ?: networkDefaultHeight()
            applyDashConstraints(resolved, if (saver) minOf(wanted ?: 480, 480) else wanted, if (saver && preferredAudioKbps == null) 96 else preferredAudioKbps)
            _playbackState.value = _playbackState.value.copy(videoQualityAuto = height == null, selectedVideoHeight = height ?: _playbackState.value.selectedVideoHeight)
            return
        }
        reapplyQuality()
    }

    /** Pins the audio quality (null = best available). Keeps position and play state. */
    fun setAudioQuality(kbps: Int?) {
        preferredAudioKbps = kbps
        val resolved = currentResolved
        if (dashActive && resolved != null) {
            val saver = AppSettings.dataSaver.value
            val wanted = preferredVideoHeight ?: networkDefaultHeight()
            applyDashConstraints(resolved, if (saver) minOf(wanted ?: 480, 480) else wanted, if (saver && kbps == null) 96 else kbps)
            _playbackState.value = _playbackState.value.copy(
                audioQualityAuto = kbps == null,
                selectedAudioKbps = kbps ?: resolved.kbpsFor(_playbackState.value.selectedAudioTrackId).firstOrNull()
            )
            return
        }
        reapplyQuality()
    }

    /** Shows captions in the given language, or turns them off (null). */
    fun setSubtitle(languageTag: String?, translateTo: String? = null) {
        subtitleChosenByUser = true
        preferredSubtitleTag = languageTag
        preferredTranslation = translateTo.takeIf { languageTag != null }
        reapplyQuality()
    }

    fun setPlaybackSpeed(speed: Float) {
        if (!::exoPlayer.isInitialized) return
        exoPlayer.setPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    /** Pauses playback after [minutes]; 0 cancels the timer. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _playbackState.value = _playbackState.value.copy(sleepTimerEndsAtMs = 0L)
            return
        }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _playbackState.value = _playbackState.value.copy(sleepTimerEndsAtMs = endsAt)
        sleepJob = serviceScope.launch {
            delay(minutes * 60_000L)
            if (::exoPlayer.isInitialized) exoPlayer.pause()
            _playbackState.value = _playbackState.value.copy(sleepTimerEndsAtMs = 0L)
        }
    }

    fun setAutoplayNext(enabled: Boolean) {
        _playbackState.value = _playbackState.value.copy(autoplayNext = enabled)
    }

    /** Loop the current video / track. */
    fun setLoopOne(enabled: Boolean) {
        _playbackState.value = _playbackState.value.copy(
            repeatMode = if (enabled) StreamRepeatMode.ONE else StreamRepeatMode.OFF
        )
    }

    /** Replaces everything after the current item in the queue (used by "Start radio"). */
    fun setUpNext(items: List<MediaItemModel>, continuation: String? = null) {
        val state = _playbackState.value
        val current = state.currentItem ?: return
        val head = state.queue.take(state.queueIndex + 1).ifEmpty { listOf(current) }
        radioJob?.cancel()
        radioContinuation = continuation
        radioIds.clear()
        radioIds.addAll(items.map { it.id })
        _playbackState.value = state.copy(
            queue = head + items.filter { it.id != current.id },
            queueIndex = head.lastIndex
        )
    }

    /**
     * "Play next": puts [items] right after the playing item. With nothing playing they start
     * playing instead (as video when [asVideo]).
     */
    fun playNext(items: List<MediaItemModel>, asVideo: Boolean = false) {
        val state = _playbackState.value
        val current = state.currentItem
        if (current == null || state.queue.isEmpty()) {
            playQueue(items, 0, startInVideoMode = asVideo)
            return
        }
        val fresh = items.filter { it.id != current.id }
        if (fresh.isEmpty()) return
        val ids = fresh.map { it.id }.toSet()
        val head = state.queue.take(state.queueIndex + 1)
        val tail = state.queue.drop(state.queueIndex + 1).filter { it.id !in ids }
        radioIds.removeAll(ids)
        _playbackState.value = state.copy(queue = head + fresh + tail, queueIndex = head.lastIndex)
    }

    /**
     * "Add to queue": puts [items] after everything the user queued, ahead of radio tracks that
     * autoplay added. With nothing playing they start playing instead.
     */
    fun addToQueue(items: List<MediaItemModel>, asVideo: Boolean = false) {
        val state = _playbackState.value
        val current = state.currentItem
        if (current == null || state.queue.isEmpty()) {
            playQueue(items, 0, startInVideoMode = asVideo)
            return
        }
        val fresh = items.filter { it.id != current.id }
        if (fresh.isEmpty()) return
        val ids = fresh.map { it.id }.toSet()
        val head = state.queue.take(state.queueIndex + 1)
        val tail = state.queue.drop(state.queueIndex + 1).filter { it.id !in ids }
        val firstRadio = tail.indexOfFirst { it.id in radioIds }.let { if (it < 0) tail.size else it }
        radioIds.removeAll(ids)
        _playbackState.value = state.copy(
            queue = head + tail.take(firstRadio) + fresh + tail.drop(firstRadio),
            queueIndex = head.lastIndex
        )
    }

    /** "Start radio": replaces Up next with the YT Music radio for the current track. */
    fun startRadio() {
        val current = _playbackState.value.currentItem ?: return
        radioJob?.cancel()
        radioJob = serviceScope.launch {
            YouTubeRepository.startRadio(current).onSuccess { radio ->
                if (_playbackState.value.currentItem?.id == current.id) setUpNext(radio.tracks, radio.continuation)
            }
        }
    }

    /** Moves a queue entry (Up next drag and drop), keeping the playing track playing. */
    fun moveQueueItem(from: Int, to: Int) {
        val state = _playbackState.value
        if (from !in state.queue.indices || to !in state.queue.indices || from == to) return
        val queue = state.queue.toMutableList()
        queue.add(to, queue.removeAt(from))
        val playing = state.queueIndex
        val newIndex = when {
            from == playing -> to
            from < playing && to >= playing -> playing - 1
            from > playing && to <= playing -> playing + 1
            else -> playing
        }
        _playbackState.value = state.copy(queue = queue, queueIndex = newIndex)
    }

    /** Removes a queue entry (Up next swipe). The playing track cannot be removed. */
    fun removeQueueItem(index: Int) {
        val state = _playbackState.value
        if (index !in state.queue.indices || index == state.queueIndex) return
        val queue = state.queue.toMutableList().apply { removeAt(index) }
        _playbackState.value = state.copy(
            queue = queue,
            queueIndex = if (index < state.queueIndex) state.queueIndex - 1 else state.queueIndex
        )
    }

    /**
     * Endless music radio: when autoplay is on and Up next is almost empty, appends the next page of
     * the YT Music radio (or a new radio seeded by the last track).
     */
    private fun extendRadioIfNeeded() {
        val state = _playbackState.value
        if (!musicSession || !state.autoplayNext || state.repeatMode != StreamRepeatMode.OFF) return
        if (state.queue.size - state.queueIndex - 1 > RADIO_LOW_WATER) return
        if (radioJob?.isActive == true) return
        val seed = state.queue.lastOrNull() ?: return
        val continuation = radioContinuation
        radioJob = serviceScope.launch {
            val radio = if (continuation != null) YouTubeRepository.moreRadio(continuation)
            else YouTubeRepository.startRadio(seed)
            radio.onSuccess { page ->
                val now = _playbackState.value
                if (!musicSession) return@onSuccess
                val fresh = page.tracks.filter { t -> now.queue.none { it.id == t.id } }
                radioContinuation = page.continuation
                radioIds.addAll(fresh.map { it.id })
                if (fresh.isNotEmpty()) _playbackState.value = now.copy(queue = now.queue + fresh)
            }
        }
    }

    /** Related videos to play when the queue runs out (ignored when autoplay is off). */
    fun setAutoplayQueue(related: List<MediaItemModel>) {
        autoplayQueue = related
    }

    /** Stops playback, clears the queue and removes the notification (the "close video" action). */
    fun closePlayback() {
        saveCurrentPosition()
        reportWatchtimeOfCurrent()
        loadJob?.cancel()
        sleepJob?.cancel()
        currentResolved = null
        autoplayQueue = emptyList()
        radioJob?.cancel()
        musicSession = false
        radioContinuation = null
        radioIds.clear()
        if (::exoPlayer.isInitialized) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setPlaybackSpeed(1f)
        }
        _playbackState.value = PlaybackState(autoplayNext = AppSettings.autoplay.value)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /** Switches the audio language track (null = original). Keeps position and play state. */
    fun setAudioTrack(trackId: String?) {
        preferredAudioTrackId = trackId
        // Bitrates differ per track, so drop a pinned bitrate that the new track may not offer.
        preferredAudioKbps = null
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
                // Queue exhausted: continue with a related video if autoplay is on.
                val upNext = autoplayQueue.firstOrNull { rel -> queue.none { it.id == rel.id } }
                if (currentState.autoplayNext && upNext != null) {
                    playQueue(listOf(upNext), 0, currentMode == PlaybackMode.VIDEO)
                }
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
        // playWhenReady, not isPlaying: while buffering isPlaying is false but a tap must still pause.
        if (exoPlayer.playWhenReady) {
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

    override fun onEvents(player: Player, events: Player.Events) {
        com.nexusstream.app.data.WatchTime.playing(player.isPlaying)
        // Buffering while play is pressed counts as playing, so the UI shows a spinner and the
        // pause button instead of looking paused (ExoPlayer's own isPlaying is false then).
        val playing = player.playWhenReady &&
            player.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
        if (playing != _playbackState.value.isPlaying) {
            if (!playing && player.playbackState != Player.STATE_ENDED) saveCurrentPosition()
            _playbackState.value = _playbackState.value.copy(isPlaying = playing)
            updateForegroundNotification()
        }
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

        if (playbackState == Player.STATE_READY) {
            updateStableVolume(AppSettings.stableVolume.value)
        }

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

        // The DASH manifest will not play on this device / video: retry with the merged streams.
        if (dashActive && !dashDisabled) {
            Log.w(TAG, "DASH playback failed (${error.errorCodeName}); using the merged streams.")
            dashDisabled = true
            dashActive = false
            val current = _playbackState.value.currentItem
            val resolved = currentResolved
            if (current != null && resolved != null) {
                val metadata = MediaMetadata.Builder().setTitle(current.title).setArtist(current.artistOrChannel)
                    .setDisplayTitle(current.title).build()
                applyResolved(current, resolved, metadata, exoPlayer.currentPosition, playWhenReady = true)
                return
            }
        }

        // The merged video + audio streams will not play: fall back to one combined file (usually 360p).
        val item = _playbackState.value.currentItem
        val muxed = currentResolved?.muxedUrl
        if (retryCount >= 1 && muxed != null && item != null && !usingMuxedFallback && ::exoPlayer.isInitialized) {
            Log.w(TAG, "Merged streams failed; switching to the combined stream.")
            usingMuxedFallback = true
            val metadata = MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.artistOrChannel)
                .setDisplayTitle(item.title)
                .build()
            exoPlayer.setMediaItem(
                MediaItem.Builder().setMediaId(item.id).setUri(muxed).setMediaMetadata(metadata).build(),
                exoPlayer.currentPosition
            )
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            _playbackState.value = _playbackState.value.copy(
                availableVideoHeights = emptyList(),
                availableAudioKbps = emptyList(),
                availableAudioTracks = emptyList(),
                selectedVideoHeight = null,
                hasError = false,
                errorMessage = null
            )
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
                        requestedPositionMs = exoPlayer.currentPosition
                    )
                } else {
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        } else {
            // Give up: say what went wrong, and only move on if there is a next item in the queue.
            val detail = listOfNotNull(error.errorCodeName, cause?.message?.take(120)).joinToString(": ")
            Log.w(TAG, "Max retry attempts reached: $detail")
            val state = _playbackState.value
            _playbackState.value = state.copy(
                hasError = true,
                errorMessage = "Playback failed ($detail)"
            )
            if (state.queueIndex + 1 < state.queue.size) skipToNext()
        }
    }

    private fun startPositionTrackingLoop() {
        serviceScope.launch {
            var ticks = 0
            while (true) {
                try {
                    if (::exoPlayer.isInitialized && exoPlayer.isPlaying) {
                        // Every 10 s, so the place is kept even if the app is killed mid-video.
                        if (++ticks % 25 == 0) saveCurrentPosition()
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
                // Seek bars and lyrics glide between these updates on their own, so a slower tick
                // only means the screens around them recompose less often.
                delay(400)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "yt-mini Playback",
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
            .setContentTitle("yt-mini")
            .setContentText("Unified Audio & Video Pipeline Active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, initialNotification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    private fun updateForegroundNotification() {
        val item = _playbackState.value.currentItem ?: return
        if (!::exoPlayer.isInitialized || !::mediaSession.isInitialized) return

        val isPlaying = _playbackState.value.isPlaying

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
        updateWidget(item, isPlaying)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private var widgetArtKey: String? = null
    private var widgetArt: android.graphics.Bitmap? = null

    /** Keeps the home-screen widget in step with the notification; loads a small artwork once per item. */
    private fun updateWidget(item: MediaItemModel, isPlaying: Boolean) {
        com.nexusstream.app.widget.NowPlayingWidget.update(this, item.title, item.artistOrChannel, isPlaying, widgetArt.takeIf { widgetArtKey == item.id })
        if (widgetArtKey == item.id || item.artworkUrl.isBlank()) return
        widgetArtKey = item.id
        widgetArt = null
        serviceScope.launch {
            val request = coil.request.ImageRequest.Builder(this@MediaPlaybackService)
                .data(item.artworkUrl).size(192).allowHardware(false).build()
            val bitmap = (coil.Coil.imageLoader(this@MediaPlaybackService).execute(request).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (widgetArtKey != item.id || bitmap == null) return@launch
            widgetArt = bitmap
            com.nexusstream.app.widget.NowPlayingWidget.update(this@MediaPlaybackService, item.title, item.artistOrChannel, _playbackState.value.isPlaying, bitmap)
        }
    }

    override fun onDestroy() {
        unregisterBecomingNoisyReceiver()
        serviceScope.cancel()
        precacheScope.cancel()
        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing LoudnessEnhancer: ${e.message}")
        }
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
