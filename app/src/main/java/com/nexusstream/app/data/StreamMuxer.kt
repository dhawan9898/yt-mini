package com.nexusstream.app.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.ByteBuffer

/**
 * Merges a downloaded video-only file and audio-only file into one playable file, without
 * re-encoding. MP4 (H.264 + AAC) and WebM (VP9 + Opus) are supported, matching what
 * [YouTubeRepository.downloadOptions] offers.
 */
object StreamMuxer {

    suspend fun merge(video: File, audio: File, output: File, webm: Boolean) {
        output.delete()
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            videoExtractor.setDataSource(video.absolutePath)
            audioExtractor.setDataSource(audio.absolutePath)
            val videoFormat = selectTrack(videoExtractor, "video/") ?: error("Downloaded video has no video track")
            val audioFormat = selectTrack(audioExtractor, "audio/") ?: error("Downloaded audio has no audio track")

            muxer = MediaMuxer(
                output.absolutePath,
                if (webm) MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            val videoTrack = muxer.addTrack(videoFormat)
            val audioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val bufferSize = maxOf(maxInputSize(videoFormat), maxInputSize(audioFormat), 4 * 1024 * 1024)
            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val info = MediaCodec.BufferInfo()
            var videoDone = false
            var audioDone = false
            // Interleave by timestamp so the file streams well and the muxer doesn't buffer a whole track.
            while (!videoDone || !audioDone) {
                currentCoroutineContext().ensureActive()
                val useVideo = when {
                    videoDone -> false
                    audioDone -> true
                    else -> videoExtractor.sampleTime <= audioExtractor.sampleTime
                }
                val extractor = if (useVideo) videoExtractor else audioExtractor
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    if (useVideo) videoDone = true else audioDone = true
                    continue
                }
                info.set(
                    0,
                    size,
                    extractor.sampleTime,
                    if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                )
                muxer.writeSampleData(if (useVideo) videoTrack else audioTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } catch (e: Throwable) {
            output.delete()
            throw e
        } finally {
            runCatching { muxer?.release() }
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): MediaFormat? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith(mimePrefix) == true) {
                extractor.selectTrack(i)
                return format
            }
        }
        return null
    }

    private fun maxInputSize(format: MediaFormat): Int =
        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 0
}
