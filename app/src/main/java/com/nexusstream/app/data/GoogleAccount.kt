package com.nexusstream.app.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User's Google Account profile data obtained from the Android on-device Google Play Services.
 */
data class GoogleUserProfile(
    val email: String,
    val displayName: String,
    val avatarUrl: String,
    val id: String
)

/**
 * Manages Google Account sign-in. Supports:
 * 1. Direct native on-device Google Sign-in via Google Play Services (one-tap account chooser).
 * 2. Web session cookie jar (CookieManager) for authenticated YouTube requests.
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

    fun isSignedIn(): Boolean = _signedIn.value

    fun init(context: Context) {
        val sp = context.getSharedPreferences("nexus_google_auth", Context.MODE_PRIVATE)
        prefs = sp

        // 1. Check native Play Services signed in account on this Android device
        val nativeAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (nativeAccount != null) {
            val profile = GoogleUserProfile(
                email = nativeAccount.email.orEmpty(),
                displayName = nativeAccount.displayName ?: nativeAccount.email.orEmpty().substringBefore('@'),
                avatarUrl = nativeAccount.photoUrl?.toString().orEmpty(),
                id = nativeAccount.id.orEmpty()
            )
            _userProfile.value = profile
            _signedIn.value = true
            persistProfile(profile)
            return
        }

        // 2. Check saved profile in SharedPreferences
        val savedEmail = sp.getString("account_email", null)
        if (!savedEmail.isNullOrBlank()) {
            val profile = GoogleUserProfile(
                email = savedEmail,
                displayName = sp.getString("account_name", savedEmail.substringBefore('@')).orEmpty(),
                avatarUrl = sp.getString("account_avatar", "").orEmpty(),
                id = sp.getString("account_id", "").orEmpty()
            )
            _userProfile.value = profile
            _signedIn.value = true
            return
        }

        // 3. Fallback to auth cookies check
        refresh()
    }

    /** Called when the user signs in natively via the Android system account picker. */
    fun signInWithGoogleAccount(profile: GoogleUserProfile) {
        _userProfile.value = profile
        _signedIn.value = true
        persistProfile(profile)
        YouTubeRepository.clearFeedCaches()
    }

    private fun persistProfile(profile: GoogleUserProfile?) {
        val sp = prefs ?: return
        if (profile != null) {
            sp.edit()
                .putString("account_email", profile.email)
                .putString("account_name", profile.displayName)
                .putString("account_avatar", profile.avatarUrl)
                .putString("account_id", profile.id)
                .apply()
        } else {
            sp.edit().clear().apply()
        }
    }

    /** Re-reads the cookie jar and local account state. */
    fun refresh() {
        val hasCookies = hasAuthCookie()
        val hasProfile = _userProfile.value != null
        val now = hasCookies || hasProfile
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

    fun signOut(context: Context? = null) {
        val manager = CookieManager.getInstance()
        manager.removeAllCookies { }
        manager.flush()
        _userProfile.value = null
        _signedIn.value = false
        persistProfile(null)
        if (context != null) {
            runCatching {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                GoogleSignIn.getClient(context, gso).signOut()
            }
        }
        YouTubeRepository.clearFeedCaches()
    }

    fun hasAuthCookie(): Boolean {
        val cookies = getAllCookies()
        return cookies.keys.any { name ->
            name == "SAPISID" || name == "__Secure-3PAPISID" || name == "LOGIN_INFO" || name == "SID" || name == "__Secure-1PAPISID"
        }
    }
}
