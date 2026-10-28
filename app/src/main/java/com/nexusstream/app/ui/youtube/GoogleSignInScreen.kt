package com.nexusstream.app.ui.youtube

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.nexusstream.app.data.GoogleAccount
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.OriginalColors

private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"

/** Signs in to Google inside a WebView; the session cookies are then used for YouTube requests. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleSignInScreen(nav: YouTubeNav) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val signedIn by GoogleAccount.signedIn.collectAsState()
    if (signedIn) {
        // Sign out closes this page; signing in again starts from a fresh page later, once the
        // old session's cookies are gone (loading it straight away showed a black screen).
        SignedInPanel(nav, onSignOut = { GoogleAccount.signOut(context); nav.back() })
        return
    }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else nav.back()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OriginalColors.YouTubeBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = nav.back) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = OriginalColors.TextPrimary)
            }
            Text("Sign in to Google", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary)
        }
        loadError?.let {
            Text(it, fontSize = 12.sp, color = OriginalColors.Danger, modifier = Modifier.padding(12.dp))
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = OriginalColors.Red)

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    // White until Google's page paints, instead of a black box.
                    setBackgroundColor(android.graphics.Color.WHITE)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportMultipleWindows(true)
                    settings.javaScriptCanOpenWindowsAutomatically = true

                    // Remove X-Requested-With header to prevent Google from detecting embedded WebView
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                    }

                    // Present as standard Mobile Chrome browser without WebView markers (no '; wv' or 'Version/4.0')
                    val defaultUa = settings.userAgentString
                    val cleanUa = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
                    settings.userAgentString = if (cleanUa.contains("Chrome/")) {
                        cleanUa
                    } else {
                        "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.6723.107 Mobile Safari/537.36"
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            val href = view?.hitTestResult?.extra
                            if (!href.isNullOrBlank()) {
                                view.loadUrl(href)
                                return true
                            }
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            if (transport != null) {
                                transport.webView = view
                                resultMsg.sendToTarget()
                                return true
                            }
                            return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
                        }
                    }

                    // Only finish once the session is on youtube.com, so every auth cookie has been set.
                    fun checkAuthSuccess(url: String) {
                        if (url.contains("youtube.com") && GoogleAccount.hasAuthCookie()) {
                            GoogleAccount.refresh()
                            if (GoogleAccount.isSignedIn()) nav.back()
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                            checkAuthSuccess(url)
                        }

                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            checkAuthSuccess(url)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val targetUrl = request.url.toString()
                            checkAuthSuccess(targetUrl)
                            return false
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                loadError = "${error.description} (${request.url.host})"
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            checkAuthSuccess(url)
                        }
                    }
                    webView = this
                    loadUrl(SIGN_IN_URL)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
    }
}

@Composable
private fun SignedInPanel(nav: YouTubeNav, onSignOut: () -> Unit) {
    BackHandler { nav.back() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OriginalColors.YouTubeBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = nav.back) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = OriginalColors.TextPrimary)
            }
            Text("Google Account", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary)
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            Text("You're signed in", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OriginalColors.TextPrimary)
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Text(
                "Your login is saved on this device and stays until you sign out.",
                fontSize = 14.sp, color = OriginalColors.TextSecondary
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
            androidx.compose.material3.OutlinedButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}
