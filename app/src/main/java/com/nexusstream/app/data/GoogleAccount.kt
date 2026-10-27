package com.nexusstream.app.data

import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's Google sign-in. Login happens in a WebView ([com.nexusstream.app.ui.youtube.GoogleSignInScreen]);
 * the resulting youtube.com / google.com cookies are kept by [CookieManager] and attached to YouTube
 * requests made through [NewPipeDownloader], which makes requests look like a logged-in browser
 * (fewer "sign in to confirm you're not a bot" blocks, age-gated videos, personalised results).
 */
object GoogleAccount {
    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val COOKIE_DOMAINS = listOf(
        "https://www.youtube.com",
        "https://m.youtube.com",
        "https://youtube.com",
        "https://accounts.google.com"
    )

    fun isSignedIn(): Boolean = _signedIn.value

    /** Re-reads the cookie jar; call on startup and after the sign-in page finishes. */
    fun refresh() {
        val now = hasAuthCookie()
        if (now != _signedIn.value) {
            _signedIn.value = now
            YouTubeRepository.clearFeedCaches()
        }
    }

    /** Aggregates cookies across all relevant YouTube and Google account domains. */
    fun getAllCookies(): Map<String, String> {
        val manager = CookieManager.getInstance()
        val map = linkedMapOf<String, String>()
        for (domain in COOKIE_DOMAINS) {
            val raw = runCatching { manager.getCookie(domain) }.getOrNull().orEmpty()
            if (raw.isNotBlank()) {
                raw.split(';').forEach { cookie ->
                    val trimmed = cookie.trim()
                    val idx = trimmed.indexOf('=')
                    if (idx > 0) {
                        val name = trimmed.substring(0, idx).trim()
                        val value = trimmed.substring(idx + 1).trim()
                        if (name.isNotEmpty()) {
                            map[name] = value
                        }
                    }
                }
            }
        }
        return map
    }

    /** The Cookie header for a YouTube / Google request, or null when signed out. */
    fun cookieHeaderFor(url: String): String? {
        if (!_signedIn.value) return null
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        if (!(host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") || host.endsWith("google.com"))) return null
        val cookies = getAllCookies()
        if (cookies.isEmpty()) return null
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun signOut() {
        val manager = CookieManager.getInstance()
        manager.removeAllCookies { }
        manager.flush()
        _signedIn.value = false
        YouTubeRepository.clearFeedCaches()
    }

    fun hasAuthCookie(): Boolean {
        val cookies = getAllCookies()
        return cookies.keys.any { name ->
            name == "SAPISID" || name == "__Secure-3PAPISID" || name == "LOGIN_INFO" || name == "SID" || name == "__Secure-1PAPISID"
        }
    }
}
