package com.nexusstream.app.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.nexusstream.app.data.NewPipeDownloader
import java.io.IOException
import java.util.regex.Pattern

/**
 * High-performance Ad-blocking interceptor and MediaSource factory for AndroidX Media3 pipeline.
 * Filters ad-serving hosts, tracking beacons, and mid-roll injection query parameters.
 */
@OptIn(UnstableApi::class)
class AdBlockMediaPipeline(
    private val context: Context,
    private val userAgent: String = NewPipeDownloader.USER_AGENT
) {

    companion object {
        // Known tracking and ad-delivery domains / patterns
        private val AD_DOMAIN_PATTERNS = listOf(
            Pattern.compile(".*adservice\\.google\\..*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*pagead2\\.googlesyndication\\..*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*doubleclick\\.net.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*youtube\\.com/api/stats/ads.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*youtube\\.com/pagead/.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile(".*googlevideo\\.com/videoplayback.*[&?]adformat=.*", Pattern.CASE_INSENSITIVE)
        )

        // NOTE: "ctier" and "oad" appear on ordinary (non-ad) googlevideo stream URLs, so they
        // must not be treated as ad markers or every real stream would be blocked.
        private val AD_QUERY_PARAMS = setOf(
            "adformat", "ad_type", "ad_unit", "masthead"
        )
    }

    /**
     * Intercepting HttpDataSource that inspects outgoing media and segment queries.
     * Throws an AdBlockedException for identified ad requests, preventing ExoPlayer
     * from stalling on interstitial sponsor ads.
     */
    inner class AdFilteringHttpDataSource(
        private val delegate: DataSource
    ) : DataSource {

        override fun addTransferListener(transferListener: TransferListener) {
            delegate.addTransferListener(transferListener)
        }

        @Throws(IOException::class)
        override fun open(dataSpec: DataSpec): Long {
            val uriString = dataSpec.uri.toString()

            if (isAdUri(dataSpec.uri, uriString)) {
                // Return empty/eof immediately or abort ad chunk
                throw AdBlockedException("NexusStream intercepted and blocked ad stream: ${dataSpec.uri.host}")
            }

            // Sanitize query params if video stream contains inline ad trackers
            val sanitizedDataSpec = sanitizeDataSpec(dataSpec)
            return delegate.open(sanitizedDataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return delegate.read(buffer, offset, length)
        }

        override fun getUri(): Uri? = delegate.uri

        override fun close() {
            delegate.close()
        }
    }

    /**
     * Inspects whether the URI targets an advertisement network or contains ad format payloads.
     */
    fun isAdUri(uri: Uri, fullUriString: String): Boolean {
        // Fast host matching
        val host = uri.host.orEmpty()
        for (pattern in AD_DOMAIN_PATTERNS) {
            if (pattern.matcher(host).matches() || pattern.matcher(fullUriString).matches()) {
                return true
            }
        }

        // Query param validation
        if (uri.isHierarchical) {
            for (adParam in AD_QUERY_PARAMS) {
                if (uri.getQueryParameter(adParam) != null) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Strips ad tracking flags from video chunks to preserve clean stream playback.
     */
    private fun sanitizeDataSpec(original: DataSpec): DataSpec {
        val uri = original.uri
        if (!uri.isHierarchical || uri.query.isNullOrEmpty()) return original
        // Signed stream URLs must be passed through untouched or the signature breaks.
        if (uri.host.orEmpty().endsWith("googlevideo.com")) return original

        val queryParams = uri.queryParameterNames
        var hasAdParam = false
        for (param in AD_QUERY_PARAMS) {
            if (queryParams.contains(param)) {
                hasAdParam = true
                break
            }
        }

        if (!hasAdParam) return original

        val cleanUriBuilder = uri.buildUpon().clearQuery()
        for (param in queryParams) {
            if (!AD_QUERY_PARAMS.contains(param.lowercase())) {
                uri.getQueryParameters(param).forEach { value ->
                    cleanUriBuilder.appendQueryParameter(param, value)
                }
            }
        }

        return original.buildUpon()
            .setUri(cleanUriBuilder.build())
            .build()
        }

    /**
     * Builds the upstream HttpDataSource Factory with custom timeout and agent settings.
     */
    fun createDataSourceFactory(): DataSource.Factory {
        val baseHttpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(12000)
            .setReadTimeoutMs(15000)
            .setAllowCrossProtocolRedirects(true)

        // DefaultDataSource routes http(s) to the factory above and file:// to local storage
        // (needed for offline downloads).
        val routed = DefaultDataSource.Factory(context, baseHttpFactory)
        return DataSource.Factory {
            AdFilteringHttpDataSource(ChunkedStreamDataSource(routed.createDataSource()))
        }
    }

    /**
     * Builds a MediaSourceFactory with the injected Ad-Block pipeline.
     */
    fun createMediaSourceFactory(): MediaSource.Factory {
        return DefaultMediaSourceFactory(createDataSourceFactory())
    }
}

/**
 * Exception raised when an ad request is blocked by the interceptor.
 */
class AdBlockedException(message: String) : IOException(message)
