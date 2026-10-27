package com.nexusstream.app.data

import android.media.MediaCodecList
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A step-by-step check of everything playback depends on, with a plain-text report the user can
 * share. It finds out which stage fails on this phone and network: reaching YouTube, loading the
 * feed, extracting a stream, downloading its bytes, and decoding on this device.
 */
object PlaybackDiagnostics {

    suspend fun run(): List<String> = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        fun ok(text: String) { lines += "OK    $text" }
        fun fail(text: String) { lines += "FAIL  $text" }
        fun info(text: String) { lines += "      $text" }

        lines += "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        lines += "Region: ${ContentRegions.nameOf(AppSettings.contentCountry.value)}, signed in: ${GoogleAccount.isSignedIn()}"

        // 1. Can we reach YouTube at all?
        runCatching {
            val started = System.currentTimeMillis()
            val code = NewPipeDownloader.instance.client.newCall(
                okhttp3.Request.Builder().url("https://www.youtube.com/generate_204")
                    .header("User-Agent", NewPipeDownloader.USER_AGENT).build()
            ).execute().use { it.code }
            ok("youtube.com reachable (HTTP $code, ${System.currentTimeMillis() - started} ms)")
        }.onFailure { fail("Can't reach youtube.com: ${it.javaClass.simpleName}: ${it.message}") }

        // 2. Feed
        val feed = YouTubeRepository.videoFeedPaged("All", emptyList(), refresh = true)
        val video = feed.getOrNull()?.items?.firstOrNull()
        if (video == null) {
            fail("Feed: ${feed.exceptionOrNull()?.javaClass?.simpleName}: ${feed.exceptionOrNull()?.message ?: "no videos returned"}")
            return@withContext lines
        }
        ok("Feed loaded (${feed.getOrNull()?.items?.size} videos)")

        // 3. Stream extraction
        val resolved = YouTubeRepository.resolveStream(video.watchUrl)
        val streams = resolved.getOrNull()
        if (streams == null) {
            fail("Stream extraction for \"${video.title.take(40)}\": ${resolved.exceptionOrNull()?.javaClass?.simpleName}: ${resolved.exceptionOrNull()?.message}")
            return@withContext lines
        }
        ok("Stream extracted: video ${streams.videoOptions.map { it.height }}, audio ${streams.audioOptions.map { it.kbps }.distinct()} kbps, combined stream ${if (streams.muxedUrl != null) "yes" else "no"}")

        // 4. Can the bytes be downloaded?
        fun probe(label: String, url: String?) {
            if (url == null) { info("$label: none"); return }
            runCatching {
                val started = System.currentTimeMillis()
                NewPipeDownloader.instance.client.newCall(
                    okhttp3.Request.Builder().url(url).header("Range", "bytes=0-524287")
                        .header("User-Agent", NewPipeDownloader.USER_AGENT).build()
                ).execute().use { response ->
                    val bytes = response.body?.bytes()?.size ?: 0
                    val ms = System.currentTimeMillis() - started
                    if (response.code in 200..299) ok("$label download: HTTP ${response.code}, $bytes bytes in $ms ms")
                    else fail("$label download: HTTP ${response.code} (the link was refused)")
                }
            }.onFailure { fail("$label download: ${it.javaClass.simpleName}: ${it.message}") }
        }
        probe("Video", streams.pickVideo(null)?.url)
        probe("Audio", streams.pickAudio(null)?.url)
        probe("Combined", streams.muxedUrl)

        // 5. Decoders on this device
        fun decoder(label: String, mime: String) {
            val hardware = runCatching {
                MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                    .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
            }.getOrDefault(emptyList())
            if (hardware.isEmpty()) {
                lines += "WARN  $label decoder: not available"
            } else {
                val accelerated = Build.VERSION.SDK_INT >= 29 && hardware.any { it.isHardwareAccelerated }
                ok("$label decoder: ${hardware.first().name}${if (accelerated) " (hardware)" else ""}")
            }
        }
        decoder("H.264", "video/avc")
        decoder("VP9", "video/x-vnd.on2.vp9")
        decoder("AV1", "video/av01")
        decoder("AAC", "audio/mp4a-latm")
        decoder("Opus", "audio/opus")
        lines
    }
}
