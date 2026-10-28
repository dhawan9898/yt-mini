package com.nexusstream.app.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional display info for the signed-in account (the web session itself carries no profile). */
data class GoogleUserProfile(
    val email: String,
    val displayName: String,
    val avatarUrl: String,
    val id: String
)

/**
 * Google sign-in via the web session. Login state is the presence of Google auth cookies in the
 * WebView CookieManager (persisted to disk). A copy of those cookies is also kept in
 * SharedPreferences and restored on launch, so the login survives cookie-store resets.
 */
object GoogleAccount {
    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _userProfile = MutableStateFlow<GoogleUserProfile?>(null)
    val userProfile: StateFlow<GoogleUserProfile?> = _userProfile.asStateFlow()

    private var prefs: SharedPreferences? = null

    private val COOKIE_DOMAINS = listOf(
        "https://www.youtube.com",
        "https://m.youtube.com",
        "https://youtube.com",
        "https://accounts.google.com"
    )

    private const val KEY_COOKIES = "backup_cookies"
    private const val KEY_AUTH_USER = "auth_user"
    private const val KEY_PAGE_ID = "page_id"
    private const val COOKIE_EXPIRES = "Fri, 31 Dec 2038 23:59:59 GMT"

    fun isSignedIn(): Boolean = _signedIn.value

    /**
     * Incognito: a temporary signed-out session. Requests carry no account cookies, nothing is
     * written to the account or to watch history; turning it off (or restarting) signs back in.
     */
    private val _incognito = MutableStateFlow(false)
    val incognito: StateFlow<Boolean> = _incognito.asStateFlow()

    fun setIncognito(on: Boolean) {
        if (_incognito.value == on) return
        _incognito.value = on
        _signedIn.value = !on && hasAuthCookie()
        YouTubeRepository.clearFeedCaches()
    }

    /** The account / channel requests act as (account switcher): Google account index and Brand page id. */
    data class Identity(val authUser: Int = 0, val pageId: String = "")

    private val _identity = MutableStateFlow(Identity())
    val identity: StateFlow<Identity> = _identity.asStateFlow()

    fun setIdentity(identity: Identity) {
        _identity.value = identity
        prefs?.edit()?.putInt(KEY_AUTH_USER, identity.authUser)?.putString(KEY_PAGE_ID, identity.pageId)?.apply()
        YouTubeRepository.clearFeedCaches()
    }

    fun setProfile(profile: GoogleUserProfile?) {
        _userProfile.value = profile
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("nexus_google_auth", Context.MODE_PRIVATE)
        CookieManager.getInstance().setAcceptCookie(true)
        if (!hasAuthCookie()) restoreCookies()
        _signedIn.value = hasAuthCookie() && !_incognito.value
        if (hasAuthCookie()) backupCookies()
        prefs?.let { _identity.value = Identity(it.getInt(KEY_AUTH_USER, 0), it.getString(KEY_PAGE_ID, "").orEmpty()) }
    }

    /** Re-reads the cookie jar; call after the sign-in WebView navigates. */
    fun refresh() {
        CookieManager.getInstance().flush()
        val now = hasAuthCookie() && !_incognito.value
        if (hasAuthCookie()) backupCookies()
        if (now != _signedIn.value) {
            _signedIn.value = now
            YouTubeRepository.clearFeedCaches()
        }
    }

    private fun backupCookies() {
        val sp = prefs ?: return
        val youtube = cookiesFor("https://www.youtube.com")
        if (youtube.isEmpty()) return
        // Google and YouTube set cookies with the same names (SID, SAPISID...) but different
        // values, so each domain's set is kept apart.
        val json = org.json.JSONObject()
            .put("youtube", org.json.JSONObject(youtube as Map<*, *>))
            .put("google", org.json.JSONObject(cookiesFor("https://accounts.google.com") as Map<*, *>))
        sp.edit().putString(KEY_COOKIES, json.toString()).apply()
    }

    private fun restoreCookies() {
        val raw = prefs?.getString(KEY_COOKIES, null) ?: return
        val manager = CookieManager.getInstance()
        runCatching {
            val json = org.json.JSONObject(raw)
            fun put(url: String, domain: String, set: org.json.JSONObject) {
                for (name in set.keys()) {
                    manager.setCookie(url, "$name=${set.getString(name)}; Domain=$domain; Path=/; Secure; Expires=$COOKIE_EXPIRES")
                }
            }
            val youtube = json.optJSONObject("youtube")
            if (youtube != null) {
                put("https://www.youtube.com", ".youtube.com", youtube)
                json.optJSONObject("google")?.let { put("https://accounts.google.com", ".google.com", it) }
            } else {
                // Older backups kept one merged set; it is only safe to put back on YouTube.
                put("https://www.youtube.com", ".youtube.com", json)
            }
            manager.flush()
        }
    }

    /** The cookies the browser would send to [url]. */
    private fun cookiesFor(url: String): Map<String, String> {
        val raw = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull().orEmpty()
        val map = linkedMapOf<String, String>()
        raw.split(';').forEach { cookie ->
            val trimmed = cookie.trim()
            val idx = trimmed.indexOf('=')
            if (idx > 0) map[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
        }
        return map
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
        // Only the cookies the target site itself would get: Google's own SID / SAPISID values
        // differ from YouTube's, and mixing them in made YouTube answer 401.
        val origin = "https://" + host.ifEmpty { "www.youtube.com" }
        val cookies = cookiesFor(origin).ifEmpty { return null }
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    fun signOut(@Suppress("UNUSED_PARAMETER") context: Context? = null) {
        val manager = CookieManager.getInstance()
        manager.removeAllCookies { manager.flush() }
        manager.removeSessionCookies(null)
        manager.flush()
        // Google's sign-in page also keeps state in web storage; a stale copy left the next
        // sign-in page blank.
        runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }
        LibraryRepository.clearAccountData()
        _userProfile.value = null
        _signedIn.value = false
        _identity.value = Identity()
        prefs?.edit()?.clear()?.apply()
        YouTubeRepository.clearFeedCaches()
    }

    /** True once YouTube itself has the session cookies (not just Google's sign-in page). */
    fun hasAuthCookie(): Boolean {
        val cookies = cookiesFor("https://www.youtube.com")
        return cookies.keys.any { name ->
            name == "SAPISID" || name == "__Secure-3PAPISID" || name == "LOGIN_INFO" || name == "SID" || name == "__Secure-1PAPISID"
        }
    }
}
