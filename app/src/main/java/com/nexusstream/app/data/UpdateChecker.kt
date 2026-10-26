package com.nexusstream.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A published release of the app. */
data class ReleaseInfo(val tag: String, val version: Int, val downloadUrl: String, val pageUrl: String)

/**
 * Looks for a newer APK on the project's GitHub releases. Builds are numbered by commit count
 * ("1.0.37"), so the last number of the version decides which one is newer.
 */
object UpdateChecker {
    private const val LATEST_RELEASE = "https://api.github.com/repos/dhawan9898/yt-mini/releases/latest"

    /** The trailing number of a version name or tag: "v1.0.37" -> 37. */
    fun versionNumber(version: String): Int? =
        Regex("(\\d+)\\D*$").find(version.trim())?.groupValues?.get(1)?.toIntOrNull()

    fun isNewer(currentVersionName: String, latestVersion: Int): Boolean =
        (versionNumber(currentVersionName) ?: 0) < latestVersion

    /** Reads GitHub's "latest release" JSON; null if it is not in the expected shape. */
    fun parseLatest(json: String): ReleaseInfo? = runCatching {
        val release = JSONObject(json)
        val tag = release.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val version = versionNumber(tag) ?: return null
        val assets = release.optJSONArray("assets")
        var apk = ""
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val url = assets.getJSONObject(i).optString("browser_download_url")
                if (url.endsWith(".apk")) {
                    apk = url
                    break
                }
            }
        }
        ReleaseInfo(tag, version, apk, release.optString("html_url"))
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
}
