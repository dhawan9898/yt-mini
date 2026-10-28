package com.nexusstream.app.data

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject

/**
 * Uploading a video to the signed-in channel, the way the YouTube app's Create (+) button does:
 * open a resumable upload, send the file, then create the video with its title, description and
 * visibility.
 */
object VideoUpload {
    enum class Privacy(val label: String) { PRIVATE("Private"), UNLISTED("Unlisted"), PUBLIC("Public") }

    private const val UPLOAD_ORIGIN = "https://upload.youtube.com"
    private val FORM = "application/x-www-form-urlencoded".toMediaType()

    /**
     * Uploads [uri] and returns the new video's id. [onProgress] gets 0..1 while the file is sent.
     * Runs on the calling thread; call it off the main thread.
     */
    fun upload(
        context: Context,
        uri: Uri,
        title: String,
        description: String,
        privacy: Privacy,
        onProgress: (Float) -> Unit
    ): String {
        check(GoogleAccount.isSignedIn()) { "Sign in to upload videos" }
        val frontendId = "innertube_android:${java.util.UUID.randomUUID()}:0:v=3,api=1,cf=3"
        val uploadUrl = start(frontendId)
        val scottyId = send(context, uri, uploadUrl, onProgress)
        val created = InnerTube.web("upload/createvideo", createBody(scottyId, frontendId, title, description, privacy))
        return parseVideoId(created) ?: error("YouTube didn't accept the video")
    }

    private fun start(frontendId: String): String {
        val payload = JSONObject()
            .put("frontendUploadId", frontendId)
            .put("deviceDisplayName", android.os.Build.MODEL ?: "Android")
            .put("fileId", "goog-edited-video://generated?videoFileUri=content://media/external/video/media/${java.util.UUID.randomUUID()}")
            .put("mp4MoovAtomRelocationStatus", "UNSUPPORTED")
            .put("transcodeResult", "DISABLED")
            .put("connectionType", "WIFI")
        val request = authorized(Request.Builder().url("$UPLOAD_ORIGIN/upload/youtubei"))
            .post(payload.toString().toRequestBody(FORM))
            .header("x-goog-upload-command", "start")
            .header("x-goog-upload-protocol", "resumable")
            .build()
        NewPipeDownloader.instance.client.newCall(request).execute().use { res ->
            check(res.isSuccessful) { "Couldn't start the upload (${res.code})" }
            return res.header("x-goog-upload-url") ?: error("Couldn't start the upload")
        }
    }

    private fun send(context: Context, uri: Uri, url: String, onProgress: (Float) -> Unit): String {
        val resolver = context.contentResolver
        val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        val body = object : RequestBody() {
            override fun contentType() = FORM
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                val input = resolver.openInputStream(uri) ?: error("Couldn't open the video")
                input.use {
                    val buffer = ByteArray(256 * 1024)
                    var sent = 0L
                    while (true) {
                        val n = it.read(buffer)
                        if (n < 0) break
                        sink.write(buffer, 0, n)
                        sent += n
                        if (size > 0) onProgress((sent.toFloat() / size).coerceIn(0f, 1f))
                    }
                }
            }
        }
        val request = authorized(Request.Builder().url(url))
            .post(body)
            .header("x-goog-upload-command", "upload, finalize")
            .header("x-goog-upload-file-name", "file-${System.currentTimeMillis()}")
            .header("x-goog-upload-offset", "0")
            .build()
        // Uploads take as long as they take: no read/write timeout for this one call.
        val client = NewPipeDownloader.instance.client.newBuilder()
            .writeTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        client.newCall(request).execute().use { res ->
            check(res.isSuccessful) { "The upload failed (${res.code})" }
            val result = JSONObject(res.body?.string().orEmpty())
            check(result.optString("status") == "STATUS_SUCCESS") { "YouTube couldn't process the video" }
            return result.optString("scottyResourceId").ifEmpty { error("YouTube couldn't process the video") }
        }
    }

    internal fun createBody(scottyId: String, frontendId: String, title: String, description: String, privacy: Privacy): JSONObject =
        JSONObject()
            .put("resourceId", JSONObject().put("scottyResourceId", JSONObject().put("id", scottyId)))
            .put("frontendUploadId", frontendId)
            .put(
                "initialMetadata", JSONObject()
                    .put("title", JSONObject().put("newTitle", title.ifBlank { "Untitled video" }.take(100)))
                    .put("description", JSONObject().put("newDescription", description.take(5000)).put("shouldSegment", true))
                    .put("privacy", JSONObject().put("newPrivacy", privacy.name))
                    .put("draftState", JSONObject().put("isDraft", false))
            )

    internal fun parseVideoId(root: JSONObject): String? =
        root.optString("videoId").ifEmpty { root.optJSONObject("contents")?.optString("videoId").orEmpty() }.ifEmpty { null }

    private fun authorized(builder: Request.Builder): Request.Builder {
        builder.header("User-Agent", NewPipeDownloader.USER_AGENT)
        GoogleAccount.cookieHeaderFor("https://www.youtube.com")?.let { cookies ->
            builder.header("Cookie", cookies)
            InnerTube.sapisidHash(cookies, origin = "https://www.youtube.com")?.let { builder.header("Authorization", it) }
            builder.header("X-Goog-AuthUser", GoogleAccount.identity.value.authUser.toString())
            builder.header("Origin", "https://www.youtube.com")
        }
        return builder
    }
}
