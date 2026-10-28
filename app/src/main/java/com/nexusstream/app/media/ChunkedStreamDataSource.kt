package com.nexusstream.app.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Reads YouTube (googlevideo.com) streams as a series of bounded range requests.
 *
 * googlevideo throttles one long open-ended request to roughly playback speed after the first
 * few megabytes, which shows up as constant rebuffering. Asking for [chunkBytes] at a time keeps
 * every request at full speed, the same way YouTube's own players fetch. Other URLs, and streams
 * whose length is unknown, are passed through unchanged.
 */
@OptIn(UnstableApi::class)
class ChunkedStreamDataSource(
    private val delegate: DataSource,
    private val chunkBytes: Long = DEFAULT_CHUNK_BYTES
) : DataSource {

    companion object {
        const val DEFAULT_CHUNK_BYTES = 4L * 1024 * 1024
    }

    private var spec: DataSpec? = null
    private var position = 0L
    private var end = C.LENGTH_UNSET.toLong()
    private var chunkStart = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val total = streamEnd(dataSpec)
        if (total == null || total <= dataSpec.position) {
            spec = null
            return delegate.open(dataSpec)
        }
        spec = dataSpec
        position = dataSpec.position
        end = total
        openChunk(dataSpec)
        return end - dataSpec.position
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val original = spec ?: return delegate.read(buffer, offset, length)
        if (length == 0) return 0
        while (true) {
            if (position >= end) return C.RESULT_END_OF_INPUT
            val read = delegate.read(buffer, offset, length)
            if (read != C.RESULT_END_OF_INPUT) {
                position += read
                return read
            }
            // A chunk that ended without giving any data means the stream is shorter than expected.
            if (position == chunkStart) return C.RESULT_END_OF_INPUT
            // This chunk is done (or the server sent less than asked): fetch the next range.
            delegate.close()
            openChunk(original)
        }
    }

    private fun openChunk(original: DataSpec) {
        chunkStart = position
        val length = minOf(chunkBytes, end - position)
        delegate.open(original.subrange(position - original.position, length))
    }

    /** Absolute end offset of the requested data, or null when the stream is not chunked. */
    private fun streamEnd(dataSpec: DataSpec): Long? {
        val uri = dataSpec.uri
        if (!uri.host.orEmpty().endsWith("googlevideo.com") || !uri.isHierarchical) return null
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) return dataSpec.position + dataSpec.length
        return uri.getQueryParameter("clen")?.toLongOrNull()
    }

    override fun getUri(): Uri? = delegate.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() {
        spec = null
        delegate.close()
    }
}
