package com.nexusstream.app.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/**
 * OkHttp-backed [Downloader] that NewPipeExtractor uses for every network call.
 */
class NewPipeDownloader private constructor(
    val client: OkHttpClient
) : Downloader() {

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val payload = request.dataToSend()
        val body = when {
            payload != null -> payload.toRequestBody()
            method == "POST" || method == "PUT" -> ByteArray(0).toRequestBody()
            else -> null
        }

        val builder = okhttp3.Request.Builder()
            .method(method, body)
            .url(request.url())
            .header("User-Agent", USER_AGENT)

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { builder.addHeader(name, it) }
        }

        if (request.headers().keys.none { it.equals("Cookie", ignoreCase = true) }) {
            GoogleAccount.cookieHeaderFor(request.url())?.let { builder.header("Cookie", it) }
        }

        return call(builder.build(), retry = method == "GET")
    }

    /**
     * Runs the request. A GET gets one more try after a dropped connection, a timeout or a 5xx
     * answer (YouTube and its CDNs hiccup constantly; the second try nearly always works).
     * A CAPTCHA demand is never retried.
     */
    private fun call(request: okhttp3.Request, retry: Boolean): Response {
        val attempts = if (retry) 2 else 1
        var lastError: java.io.IOException? = null
        for (attempt in 1..attempts) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        throw ReCaptchaException("reCaptcha challenge requested", request.url.toString())
                    }
                    if (!(response.code in 500..599 && attempt < attempts)) {
                        return Response(
                            response.code,
                            response.message,
                            response.headers.toMultimap(),
                            response.body?.string(),
                            response.request.url.toString()
                        )
                    }
                }
            } catch (e: ReCaptchaException) {
                throw e
            } catch (e: java.io.IOException) {
                lastError = e
                if (attempt >= attempts) throw e
            }
            Thread.sleep(RETRY_DELAY_MS)
        }
        throw lastError ?: java.io.IOException("Request failed")
    }

    companion object {
        private const val RETRY_DELAY_MS = 400L
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        val instance: NewPipeDownloader by lazy {
            NewPipeDownloader(
                OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    // Never hang for longer than this, whatever the server does.
                    .callTimeout(60, TimeUnit.SECONDS)
                    .build()
            )
        }
    }
}
