package com.nexusstream.app.ui.youtube

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.nexusstream.app.data.GoogleAccount
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.OriginalColors

private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F"

/** Signs in to Google inside a WebView; the session cookies are then used for YouTube requests. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleSignInScreen(nav: YouTubeNav) {
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
            Text(it, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFFFF6B6B), modifier = Modifier.padding(12.dp))
        }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = OriginalColors.Red)

        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Google refuses sign-in from embedded WebViews; present as plain mobile Chrome.
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/126.0.0.0 Mobile Safari/537.36"
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            error: android.webkit.WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                loadError = "${error.description} (${request.url.host})"
                            }
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            loading = false
                            CookieManager.getInstance().flush()
                            GoogleAccount.refresh()
                            if (GoogleAccount.isSignedIn() && url.contains("youtube.com")) nav.back()
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
