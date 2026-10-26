package com.nexusstream.app.data

import androidx.compose.ui.graphics.Color
import com.nexusstream.app.model.LyricLine
import com.nexusstream.app.model.LyricWord
import com.nexusstream.app.model.MediaCategory
import com.nexusstream.app.model.MediaItemModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Production media repository supplying unified stream feeds,
 * searchable music and video libraries, and synced karaoke lyrics.
 */
class MediaRepository {

    private val _catalog = MutableStateFlow(createInitialCatalog())
    val catalog: Flow<List<MediaItemModel>> = _catalog.asStateFlow()

    fun getAllItems(): List<MediaItemModel> = _catalog.value

    fun getItemById(id: String): MediaItemModel? = _catalog.value.find { it.id == id }

    fun getMusicDiscoverFeed(): List<MediaCategory> {
        val items = _catalog.value
        return listOf(
            MediaCategory(
                id = "cat_featured",
                title = "Curated Liquid Flow",
                subtitle = "Handcrafted high-bitrate spatial soundscapes",
                items = items.take(4)
            ),
            MediaCategory(
                id = "cat_cyberpunk",
                title = "Midnight Cyberia",
                subtitle = "Vibrant synthwave and neon electronic pulses",
                items = items.filter { it.genre == "Synthwave" || it.genre == "Cyberpunk" }
            ),
            MediaCategory(
                id = "cat_chill",
                title = "Ambient Horizon",
                subtitle = "Soothing acoustic and atmospheric depth",
                items = items.filter { it.genre == "Ambient" || it.genre == "Chillout" }
            )
        )
    }

    fun getVideoFeed(): List<MediaItemModel> {
        return _catalog.value.filter { it.videoUrl.isNotEmpty() }
    }

    fun search(query: String): List<MediaItemModel> {
        if (query.isBlank()) return emptyList()
        val normalized = query.trim().lowercase()
        return _catalog.value.filter {
            it.title.lowercase().contains(normalized) ||
                    it.artistOrChannel.lowercase().contains(normalized) ||
                    it.genre.lowercase().contains(normalized)
        }
    }

    companion object {
        /**
         * Parses standard LRC timestamped lyrics (e.g., "[00:14.20] Hello world")
         * into synchronous LyricLine models.
         */
        fun parseLrc(lrcText: String): List<LyricLine> {
            val lines = lrcText.lines()
            val parsed = mutableListOf<Pair<Long, String>>()
            val timestampRegex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

            for (raw in lines) {
                val match = timestampRegex.find(raw.trim())
                if (match != null) {
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val millisStr = match.groupValues[3]
                    val millis = if (millisStr.length == 2) {
                        millisStr.toLongOrNull()?.times(10) ?: 0L
                    } else {
                        millisStr.toLongOrNull() ?: 0L
                    }
                    val totalMs = (minutes * 60 + seconds) * 1000 + millis
                    val text = match.groupValues[4].trim()
                    if (text.isNotEmpty()) {
                        parsed.add(totalMs to text)
                    }
                }
            }

            return parsed.mapIndexed { index, pair ->
                val startTime = pair.first
                val endTime = if (index < parsed.lastIndex) {
                    parsed[index + 1].first
                } else {
                    startTime + 4000L
                }
                LyricLine(
                    startTimeMs = startTime,
                    endTimeMs = endTime,
                    text = pair.second
                )
            }
        }

        private fun createInitialCatalog(): List<MediaItemModel> {
            return listOf(
                MediaItemModel(
                    id = "track_1",
                    title = "Starfall Horizons (Liquid Edit)",
                    artistOrChannel = "Aetheria & Kroma",
                    audioUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    artworkUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&q=80",
                    durationMs = 214000L,
                    dominantColor = Color(0xFF6C5CE7),
                    secondaryColor = Color(0xFFFF7675),
                    genre = "Synthwave",
                    viewsOrPlays = "4.8M",
                    lyrics = listOf(
                        LyricLine(
                            startTimeMs = 0L,
                            endTimeMs = 4000L,
                            text = "Light dissolves through frosted crystal skies",
                            words = listOf(
                                LyricWord("Light", 0L, 800L),
                                LyricWord("dissolves", 800L, 1600L),
                                LyricWord("through", 1600L, 2200L),
                                LyricWord("frosted", 2200L, 2900L),
                                LyricWord("crystal", 2900L, 3500L),
                                LyricWord("skies", 3500L, 4000L)
                            )
                        ),
                        LyricLine(
                            startTimeMs = 4200L,
                            endTimeMs = 9500L,
                            text = "We drift beyond the edge of sound and time",
                            words = listOf(
                                LyricWord("We", 4200L, 4800L),
                                LyricWord("drift", 4800L, 5700L),
                                LyricWord("beyond", 5700L, 6600L),
                                LyricWord("the", 6600L, 7000L),
                                LyricWord("edge", 7000L, 7800L),
                                LyricWord("of", 7800L, 8200L),
                                LyricWord("sound", 8200L, 8900L),
                                LyricWord("and", 8900L, 9100L),
                                LyricWord("time", 9100L, 9500L)
                            )
                        ),
                        LyricLine(
                            startTimeMs = 9800L,
                            endTimeMs = 15000L,
                            text = "Echoes colliding in a liquid glass cascade",
                            words = listOf(
                                LyricWord("Echoes", 9800L, 11000L),
                                LyricWord("colliding", 11000L, 12200L),
                                LyricWord("in", 12200L, 12600L),
                                LyricWord("a", 12600L, 12900L),
                                LyricWord("liquid", 12900L, 13700L),
                                LyricWord("glass", 13700L, 14300L),
                                LyricWord("cascade", 14300L, 15000L)
                            )
                        ),
                        LyricLine(
                            startTimeMs = 15200L,
                            endTimeMs = 21000L,
                            text = "No boundaries left when horizons fade away",
                            words = listOf(
                                LyricWord("No", 15200L, 15800L),
                                LyricWord("boundaries", 15800L, 17200L),
                                LyricWord("left", 17200L, 18000L),
                                LyricWord("when", 18000L, 18600L),
                                LyricWord("horizons", 18600L, 19800L),
                                LyricWord("fade", 19800L, 20400L),
                                LyricWord("away", 20400L, 21000L)
                            )
                        )
                    )
                ),
                MediaItemModel(
                    id = "track_2",
                    title = "Midnight Cyberia",
                    artistOrChannel = "Neon Vanguard",
                    audioUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    artworkUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&q=80",
                    durationMs = 188000L,
                    dominantColor = Color(0xFF00CEC9),
                    secondaryColor = Color(0xFFE84393),
                    genre = "Cyberpunk",
                    viewsOrPlays = "8.2M",
                    lyrics = listOf(
                        LyricLine(0L, 5000L, "Signals in the neon rainfall glow"),
                        LyricLine(5200L, 11000L, "Lost beneath the chrome and wire below"),
                        LyricLine(11500L, 17000L, "Speeding through the grid into the endless night"),
                        LyricLine(17500L, 24000L, "Awakening our senses to digital light")
                    )
                ),
                MediaItemModel(
                    id = "track_3",
                    title = "Velvet Pulse",
                    artistOrChannel = "Solstice Duo",
                    audioUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                    artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=800&q=80",
                    durationMs = 245000L,
                    dominantColor = Color(0xFFFD79A8),
                    secondaryColor = Color(0xFFFDCB6E),
                    genre = "Ambient",
                    viewsOrPlays = "1.9M",
                    lyrics = listOf(
                        LyricLine(0L, 6000L, "Soft murmurs breathe in twilight haze"),
                        LyricLine(6200L, 13000L, "Dancing slow through golden amber rays"),
                        LyricLine(13500L, 20000L, "A heartbeat steady in the quiet warm retreat")
                    )
                ),
                MediaItemModel(
                    id = "track_4",
                    title = "Chromium Dreams",
                    artistOrChannel = "Hyperion Protocol",
                    audioUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                    artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&q=80",
                    durationMs = 302000L,
                    dominantColor = Color(0xFF0984E3),
                    secondaryColor = Color(0xFF00CEC9),
                    genre = "Synthwave",
                    viewsOrPlays = "3.1M",
                    lyrics = listOf(
                        LyricLine(0L, 5000L, "Sub-bass vibration shaking through the glass"),
                        LyricLine(5200L, 12000L, "Future silhouettes watch the minutes pass")
                    )
                ),
                MediaItemModel(
                    id = "track_5",
                    title = "Solaris Resonance",
                    artistOrChannel = "Aura Borealis",
                    audioUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                    videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                    artworkUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=800&q=80",
                    durationMs = 260000L,
                    dominantColor = Color(0xFFE17055),
                    secondaryColor = Color(0xFFFAB1A0),
                    genre = "Chillout",
                    viewsOrPlays = "920K",
                    lyrics = listOf(
                        LyricLine(0L, 6000L, "Ripples in the solar sea"),
                        LyricLine(6500L, 14000L, "Carrying timeless tranquility")
                    )
                )
            )
        }
    }
}
