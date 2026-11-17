package com.nexusstream.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A published release of the app. */
data class ReleaseInfo(
    val tag: String,
    /** The last number of the version ("v2.1.15" -> 15). */
    val version: Int,
    val downloadUrl: String,
    val pageUrl: String,
    /** Size of the APK in bytes, when GitHub says; 0 otherwise. */
    val sizeBytes: Long = 0L,
    val notes: String = ""
)

/**
 * Looks for a newer APK on the project's GitHub releases, downloads it inside the app and hands it
 * to Android's installer. Releases are signed with one fixed key, so each installs over the last.
 */
object UpdateChecker {
    private const val LATEST_RELEASE = "https://api.github.com/repos/dhawan9898/yt-mini/releases/latest"
    private const val APK_FILE = "yt-mini-update.apk"

    /** The trailing number of a version name or tag: "v1.0.37" -> 37. */
    fun versionNumber(version: String): Int? =
        Regex("(\\d+)\\D*$").find(version.trim())?.groupValues?.get(1)?.toIntOrNull()

    private fun parts(version: String): List<Int> =
        Regex("\\d+").findAll(version.trim().removePrefix("v")).map { it.value.toIntOrNull() ?: 0 }.toList()

    /**
     * Whether [latestTag] is newer than the installed [currentVersionName], comparing every number
     * ("v2.2.0" is newer than "2.1.15", which the last number alone would get wrong).
     */
    fun isNewer(currentVersionName: String, latestTag: String): Boolean {
        val current = parts(currentVersionName)
        val latest = parts(latestTag)
        if (latest.isEmpty()) return false
        for (i in 0 until maxOf(current.size, latest.size)) {
            val l = latest.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    /** Reads GitHub's "latest release" JSON; null if it is not in the expected shape. */
    fun parseLatest(json: String): ReleaseInfo? = runCatching {
        val release = JSONObject(json)
        val tag = release.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val version = versionNumber(tag) ?: return null
        val assets = release.optJSONArray("assets")
        var apk = ""
        var size = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val url = asset.optString("browser_download_url")
                if (url.endsWith(".apk")) {
                    apk = url
                    size = asset.optLong("size", 0L)
                    break
                }
            }
        }
        ReleaseInfo(tag, version, apk, release.optString("html_url"), size, release.optString("body"))
    }.getOrNull()

    suspend fun fetchLatest(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = okhttp3.Request.Builder()
                .url(LATEST_RELEASE)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", NewPipeDownloader.USER_AGENT)
                .build()
            NewPipeDownloader.instance.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "GitHub answered HTTP ${response.code}" }
                parseLatest(response.body?.string().orEmpty()) ?: error("Unexpected release data")
            }
        }
    }

    /**
     * True when Android lets this app hand an APK to the installer. If not, opens the settings page
     * where the user allows it ("Install unknown apps") and returns false.
     */
    fun ensureInstallAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        if (context.packageManager.canRequestPackageInstalls()) return true
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return false
    }

    /**
     * Downloads [release]'s APK into the app's cache ([onProgress] 0..1, on the main thread) and opens
     * Android's installer on it. Fails with a readable message; a cut-off download is never installed.
     */
    suspend fun downloadAndInstall(context: Context, release: ReleaseInfo, onProgress: (Float) -> Unit): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(release.downloadUrl.isNotBlank()) { "This release has no APK to install" }
                // A whole APK on a slow connection needs far longer than a page request.
                val client = NewPipeDownloader.instance.client.newBuilder()
                    .callTimeout(15, TimeUnit.MINUTES)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(release.downloadUrl)
                    .header("User-Agent", NewPipeDownloader.USER_AGENT)
                    .build()
                val apk = File(context.cacheDir, APK_FILE)
                val part = File(context.cacheDir, "$APK_FILE.part")
                part.delete()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("The download failed (HTTP ${response.code})")
                    val body = response.body ?: throw IOException("The download was empty")
                    val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            var shown = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                copied += read
                                if (total > 0) {
                                    val percent = (copied * 100 / total).toInt()
                                    if (percent != shown) {
                                        shown = percent
                                        withContext(Dispatchers.Main) { onProgress((copied.toFloat() / total).coerceIn(0f, 1f)) }
                                    }
                                }
                            }
                        }
                    }
                    val expected = if (release.sizeBytes > 0) release.sizeBytes else total
                    if (expected > 0 && part.length() < expected) {
                        part.delete()
                        throw IOException("The download stopped early. Try again.")
                    }
                    if (part.length() < 1_000_000L) {
                        part.delete()
                        throw IOException("The downloaded file is not the app. Try again.")
                    }
                }
                apk.delete()
                if (!part.renameTo(apk)) throw IOException("Couldn't save the update")
                withContext(Dispatchers.Main) {
                    onProgress(1f)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
            }
        }
}
