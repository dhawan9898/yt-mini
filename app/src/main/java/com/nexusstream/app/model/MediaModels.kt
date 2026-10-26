package com.nexusstream.app.model

import androidx.compose.ui.graphics.Color

/**
 * Playback mode determining stream rendering and decoder pipeline.
 */
enum class PlaybackMode {
    AUDIO_ONLY,
    VIDEO
}

/**
 * Loop / repeat state for continuous streaming queue.
 */
enum class StreamRepeatMode {
    OFF,
    ALL,
    ONE
}

/**
 * Streaming quality setting.
 */
enum class AudioQuality(val label: String, val bitrateKbps: Int) {
    HIGH_FIDELITY("Lossless / 320 kbps", 320),
    STANDARD("Balanced / 192 kbps", 192),
    DATA_SAVER("Data Saver / 96 kbps", 96)
}

/**
 * Media content classification type.
 */
enum class MediaContentType {
    MUSIC_TRACK,
    YOUTUBE_VIDEO,
    YOUTUBE_SHORT,
    GAME_PLAYABLE
}

/**
 * Represents a track or video item within the NexusStream unified ecosystem.
 */
data class MediaItemModel(
    val id: String,
    val title: String,
    val artistOrChannel: String,
    val audioUrl: String,
    val videoUrl: String,
    val artworkUrl: String,
    val durationMs: Long,
    val dominantColor: Color = Color(0xFF6C5CE7),
    val secondaryColor: Color = Color(0xFFFF7675),
    val isExplicit: Boolean = false,
    val viewsOrPlays: String = "2.4M",
    val lyrics: List<LyricLine> = emptyList(),
    val genre: String = "Electronic",
    val contentType: MediaContentType = MediaContentType.YOUTUBE_VIDEO,
    val isShort: Boolean = false,
    val isGame: Boolean = false,
    val channelAvatarUrl: String = "",
    val publishDate: String = "2 days ago",
    val likesCount: String = "Like",
    val description: String = "",
    /** Public watch URL; when set, direct stream URLs are resolved at play time. */
    val sourceUrl: String = ""
)

/**
 * Synchronized lyric line containing syllable/word level timestamps for karaoke streaming.
 */
data class LyricLine(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
)

/**
 * Word-level timestamp for smooth continuous karaoke sweep effect.
 */
data class LyricWord(
    val word: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

/**
 * Category grouping for music & video feed sections.
 */
data class MediaCategory(
    val id: String,
    val title: String,
    val subtitle: String,
    val items: List<MediaItemModel>
)
