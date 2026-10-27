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

    fun isSignedIn(): Boolean = _signedIn.value

    /** Re-reads the cookie jar; call on startup and after the sign-in page finishes. */
    fun refresh() {
        val now = hasAuthCookie()
        if (now != _signedIn.value) {
            _signedIn.value = now
            YouTubeRepository.clearFeedCaches()
        }
    }

    /** The Cookie header for a YouTube / Google request, or null when signed out. */
    fun cookieHeaderFor(url: String): String? {
        if (!_signedIn.value) return null
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        if (!(host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com"))) return null
        return CookieManager.getInstance().getCookie("https://www.youtube.com")?.takeIf { it.isNotBlank() }
    }

    fun signOut() {
        val manager = CookieManager.getInstance()
        manager.removeAllCookies { }
        manager.flush()
        _signedIn.value = false
        YouTubeRepository.clearFeedCaches()
    }

    private fun hasAuthCookie(): Boolean {
        val cookies = CookieManager.getInstance().getCookie("https://www.youtube.com").orEmpty()
        return cookies.split(';').any {
            val name = it.trim().substringBefore('=')
            name == "SAPISID" || name == "__Secure-3PAPISID" || name == "LOGIN_INFO"
        }
    }
}
