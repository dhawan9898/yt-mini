package com.nexusstream.app.ui.youtube

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.data.GoogleAccount
import com.nexusstream.app.data.AccountApi
import com.nexusstream.app.data.TvLink
import com.nexusstream.app.data.GoogleUserProfile
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.nexusstream.app.data.AppSettings
import com.nexusstream.app.data.ContentRegions
import com.nexusstream.app.data.CrashReporter
import com.nexusstream.app.data.DownloadRepository
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.PlaybackDiagnostics
import com.nexusstream.app.data.ReleaseInfo
import com.nexusstream.app.data.ThemeMode
import com.nexusstream.app.data.UpdateChecker
import com.nexusstream.app.data.formatBytes
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.MiniPlayerHooks
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalFilterChips
import com.nexusstream.app.ui.components.PageScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LinkBlue = Color(0xFF3EA6FF)
private val QUALITY_OPTIONS = listOf(
    "Auto" to 0, "2160p" to 2160, "1440p" to 1440, "1080p" to 1080, "720p" to 720, "480p" to 480, "360p" to 360
)

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

/** App settings: appearance, playback defaults, downloads, region, privacy, storage and about. */
@Composable
fun SettingsScreen(nav: YouTubeNav, miniPlayer: MiniPlayerHooks) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val googleSignedIn by GoogleAccount.signedIn.collectAsState()
    val theme by AppSettings.themeMode.collectAsState()
    val defaultHeight by AppSettings.defaultVideoHeight.collectAsState()
    val mobileHeight by AppSettings.mobileVideoHeight.collectAsState()
    val stableVolume by AppSettings.stableVolume.collectAsState()
    val dataSaver by AppSettings.dataSaver.collectAsState()
    val autoplay by AppSettings.autoplay.collectAsState()
    val captions by AppSettings.captionsByDefault.collectAsState()
    val wifiOnly by AppSettings.wifiOnlyDownloads.collectAsState()
    val country by AppSettings.contentCountry.collectAsState()
    val dislikeCounts by AppSettings.showDislikeCounts.collectAsState()
    val historyPaused by LibraryRepository.historyPaused.collectAsState()
    val allHistory by LibraryRepository.history.collectAsState()
    val history = remember(allHistory) { LibraryRepository.watchHistory(allHistory) }
    val downloads by LibraryRepository.downloads.collectAsState()
    val pending by DownloadRepository.pending.collectAsState()

    val incognito by GoogleAccount.incognito.collectAsState()
    val restrictedMode by AppSettings.restrictedMode.collectAsState()
    var showAccounts by remember { mutableStateOf(false) }
    var showTvLink by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var selfTestReport by remember { mutableStateOf<List<String>?>(null) }
    var selfTestRunning by remember { mutableStateOf(false) }

    // Backup: a JSON file you choose where to save / read; there is no cloud sync.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(LibraryRepository.exportBackup().toByteArray()) }
                            ?: error("Couldn't open the file")
                    }.isSuccess
                }
                Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_LONG).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                }
                if (text == null) {
                    Toast.makeText(context, "Couldn't read that file", Toast.LENGTH_LONG).show()
                } else {
                    pendingRestore = text // confirmed below before anything is replaced
                }
            }
        }
    }
    var checking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var crashReport by remember { mutableStateOf(CrashReporter.lastReport(context)) }
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val storageBytes = remember(downloads, pending) { DownloadRepository.totalBytesOnDisk(context) }

    PageScaffold(title = "Settings", onBack = nav.back, miniPlayer = miniPlayer) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // ---- Appearance ----
            item(key = "h_appearance") { SettingsHeader("Appearance") }
            item(key = "theme") {
                OriginalFilterChips(
                    labels = ThemeMode.entries.map { themeLabel(it) },
                    selected = themeLabel(theme),
                    onSelect = { label -> AppSettings.setThemeMode(ThemeMode.entries.first { themeLabel(it) == label }) },
                    unselectedColor = OriginalColors.Chip
                )
            }

            // ---- Playback ----
            item(key = "h_playback") { SettingsHeader("Playback") }
            item(key = "quality_label") { SettingsHint("Video quality on Wi-Fi (Auto adapts to the connection; a quality is the most it will use)") }
            item(key = "quality") {
                OriginalFilterChips(
                    labels = QUALITY_OPTIONS.map { it.first },
                    selected = QUALITY_OPTIONS.firstOrNull { it.second == defaultHeight }?.first ?: "Auto",
                    onSelect = { label -> AppSettings.setDefaultVideoHeight(QUALITY_OPTIONS.first { it.first == label }.second) },
                    unselectedColor = OriginalColors.Chip
                )
            }
            item(key = "mobile_quality_label") { SettingsHint("Video quality on mobile data") }
            item(key = "mobile_quality") {
                OriginalFilterChips(
                    labels = QUALITY_OPTIONS.map { it.first },
                    selected = QUALITY_OPTIONS.firstOrNull { it.second == mobileHeight }?.first ?: "Auto",
                    onSelect = { label -> AppSettings.setMobileVideoHeight(QUALITY_OPTIONS.first { it.first == label }.second) },
                    unselectedColor = OriginalColors.Chip
                )
            }
            item(key = "autoplay") {
                SwitchRow("Autoplay", "Play a related video when one ends", autoplay) { AppSettings.setAutoplay(it) }
            }
            item(key = "captions") {
                SwitchRow("Captions by default", "Show captions in your device language when available", captions) {
                    AppSettings.setCaptionsByDefault(it)
                }
            }
            item(key = "seek_label") { SettingsHint("Double-tap to seek") }
            item(key = "seek") {
                val seek by AppSettings.doubleTapSeekSec.collectAsState()
                val options = listOf(5, 10, 15, 20, 30, 60)
                OriginalFilterChips(
                    labels = options.map { "$it seconds" },
                    selected = "$seek seconds",
                    onSelect = { label -> AppSettings.setDoubleTapSeekSec(label.substringBefore(' ').toInt()) },
                    unselectedColor = OriginalColors.Chip
                )
            }
            item(key = "caption_size_label") { SettingsHint("Caption size") }
            item(key = "caption_size") {
                val scale by AppSettings.captionScale.collectAsState()
                val options = listOf("Small" to 0.75f, "Normal" to 1f, "Large" to 1.5f, "Huge" to 2f)
                OriginalFilterChips(
                    labels = options.map { it.first },
                    selected = options.minByOrNull { kotlin.math.abs(it.second - scale) }?.first ?: "Normal",
                    onSelect = { label -> AppSettings.setCaptionScale(options.first { it.first == label }.second) },
                    unselectedColor = OriginalColors.Chip
                )
            }
            item(key = "caption_bg_label") { SettingsHint("Caption background") }
            item(key = "caption_bg") {
                val bg by AppSettings.captionBackground.collectAsState()
                val options = listOf("None", "Translucent", "Opaque")
                OriginalFilterChips(
                    labels = options,
                    selected = options[bg.coerceIn(0, 2)],
                    onSelect = { label -> AppSettings.setCaptionBackground(options.indexOf(label)) },
                    unselectedColor = OriginalColors.Chip
                )
            }
            item(key = "smart_label") { SettingsHint("Smart downloads: keep recommended songs offline (daily, on Wi-Fi)") }
            item(key = "smart") {
                val smart by AppSettings.smartDownloads.collectAsState()
                val options = listOf("Off" to 0, "10 songs" to 10, "25 songs" to 25, "50 songs" to 50)
                OriginalFilterChips(
                    labels = options.map { it.first },
                    selected = options.firstOrNull { it.second == smart }?.first ?: "Off",
                    onSelect = { label -> AppSettings.setSmartDownloads(options.first { it.first == label }.second) },
                    unselectedColor = OriginalColors.Chip
                )
            }
            item(key = "stable_volume") {
                SwitchRow("Stable volume", "Evens out loudness between videos and songs", stableVolume) { AppSettings.setStableVolume(it) }
            }
            item(key = "saver") {
                SwitchRow("Data saver", "Limit video to 480p and audio to 96 kbps", dataSaver) { AppSettings.setDataSaver(it) }
            }

            item(key = "dislikes") {
                SwitchRow(
                    "Show dislike counts",
                    "Asks returnyoutubedislike.com for the count (only the video id is sent)",
                    dislikeCounts
                ) { AppSettings.setShowDislikeCounts(it) }
            }

            // ---- Downloads ----
            item(key = "h_downloads") { SettingsHeader("Downloads") }
            item(key = "wifi") {
                SwitchRow("Download over Wi-Fi only", "Wait for an unmetered network", wifiOnly) {
                    AppSettings.setWifiOnlyDownloads(it)
                }
            }
            item(key = "manage_downloads") {
                ActionRow("Manage downloads", "${formatBytes(storageBytes)} used on this device") {
                    nav.open(Route.Downloads)
                }
            }

            // ---- Region ----
            item(key = "h_region") { SettingsHeader("Content region") }
            item(key = "region_label") {
                SettingsHint("Changes what trending and search results you see. Pull down on Home to refresh.")
            }
            item(key = "region") {
                OriginalFilterChips(
                    labels = ContentRegions.countries.map { it.second },
                    selected = ContentRegions.nameOf(country),
                    onSelect = { label ->
                        ContentRegions.countries.firstOrNull { it.second == label }?.let { AppSettings.setContentCountry(it.first) }
                    },
                    unselectedColor = OriginalColors.Chip
                )
            }

            // ---- Notifications ----
            item(key = "h_notifications") { SettingsHeader("Notifications") }
            item(key = "notification_settings") {
                ActionRow("Notifications and reminders", "Upload alerts, take a break, bedtime") {
                    nav.open(Route.NotificationSettings)
                }
            }

            // ---- Privacy ----
            item(key = "h_privacy") { SettingsHeader("Privacy and storage") }
            item(key = "pause_history") {
                SwitchRow("Pause watch history", "Don't record new videos in your history", historyPaused) {
                    LibraryRepository.setHistoryPaused(it)
                }
            }
            item(key = "clear_history") {
                ActionRow("Clear watch history", "${history.size} videos") { confirmClearHistory = true }
            }
            item(key = "clear_search") {
                ActionRow("Clear search history", null) {
                    LibraryRepository.clearSearchHistory()
                    Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                }
            }
            item(key = "reset_hidden") {
                ActionRow("Reset hidden videos and channels", "Undo Not interested and Don't recommend channel") {
                    LibraryRepository.resetHidden()
                    Toast.makeText(context, "Hidden items restored", Toast.LENGTH_SHORT).show()
                }
            }
            item(key = "clear_playback_cache") {
                var cacheBytes by remember { mutableStateOf(com.nexusstream.app.media.PlaybackCache.sizeBytes()) }
                ActionRow("Clear playback cache", "${formatBytes(cacheBytes)} of recently played video and music") {
                    scope.launch {
                        withContext(Dispatchers.IO) { com.nexusstream.app.media.PlaybackCache.clear() }
                        cacheBytes = com.nexusstream.app.media.PlaybackCache.sizeBytes()
                        Toast.makeText(context, "Playback cache cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            item(key = "clear_images") {
                ActionRow("Clear image cache", "Thumbnails are downloaded again when needed") {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            val loader = coil.Coil.imageLoader(context)
                            loader.memoryCache?.clear()
                            loader.diskCache?.clear()
                        }
                        Toast.makeText(context, "Image cache cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // ---- Backup ----
            item(key = "h_backup") { SettingsHeader("Backup") }
            item(key = "export") {
                ActionRow("Export backup", "Playlists, history, subscriptions, likes and settings to a file") {
                    exportLauncher.launch("nexusstream-backup.json")
                }
            }
            item(key = "import") {
                ActionRow("Restore backup", "Replaces your current library with a backup file") {
                    importLauncher.launch(arrayOf("*/*"))
                }
            }

            // ---- Account ----
            item(key = "h_account") { SettingsHeader("Account") }
            item(key = "google") {
                val userProfile by GoogleAccount.userProfile.collectAsState()
                if (googleSignedIn) {
                    val title = userProfile?.displayName?.ifBlank { null } ?: "Signed in to Google"
                    val sub = userProfile?.email?.ifBlank { null } ?: "Google Account"
                    ActionRow(title, "$sub • Tap to switch or sign out") { nav.open(Route.SignIn) }
                } else {
                    ActionRow("Sign in with Google", "Choose an account from your phone") { nav.open(Route.SignIn) }
                }
            }
            if (googleSignedIn) {
                item(key = "switch_account") {
                    ActionRow("Switch account", "Brand accounts and other Google accounts signed in on this device") { showAccounts = true }
                }
            }
            if (googleSignedIn || incognito) {
                item(key = "incognito") {
                    SwitchRow("Turn on Incognito", "Browse signed out; nothing is saved to your account or watch history", incognito) { GoogleAccount.setIncognito(it) }
                }
            }
            item(key = "language_label") { SettingsHint("App language (YouTube content and system text; the app's own labels stay in English)") }
            item(key = "language") {
                val lang by AppSettings.appLanguage.collectAsState()
                OriginalFilterChips(
                    labels = ContentRegions.appLanguages.map { it.second },
                    selected = ContentRegions.appLanguages.firstOrNull { it.first == lang }?.second ?: "Device language",
                    onSelect = { label -> AppSettings.setAppLanguage(ContentRegions.appLanguages.first { it.second == label }.first) },
                    unselectedColor = OriginalColors.Chip
                )
            }
            item(key = "restricted") {
                SwitchRow("Restricted mode", "Hides videos that may be mature", restrictedMode) { AppSettings.setRestrictedMode(it) }
            }

            // ---- Watch on TV ----
            item(key = "h_tv") { SettingsHeader("Watch on TV") }
            item(key = "tv_link") {
                val tv by TvLink.linked.collectAsState()
                val linked = tv
                if (linked == null) {
                    ActionRow("Link with TV code", "On your TV open YouTube > Settings > Link with TV code, then enter the code here") { showTvLink = true }
                } else {
                    ActionRow("Linked: ${linked.name}", "Use Play on TV on a video. Tap to unlink") {
                        TvLink.unlink()
                        Toast.makeText(context, "TV unlinked", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // ---- About ----
            item(key = "h_about") { SettingsHeader("About") }
            item(key = "version") { ActionRow("Version", versionName) {} }
            item(key = "self_test") {
                ActionRow(
                    title = "Playback self-test",
                    subtitle = if (selfTestRunning) "Running..." else "Checks network, extraction, downloads and decoders; share the result if videos won't play"
                ) {
                    if (!selfTestRunning) {
                        selfTestRunning = true
                        scope.launch {
                            selfTestReport = PlaybackDiagnostics.run()
                            selfTestRunning = false
                        }
                    }
                }
            }
            item(key = "update") {
                ActionRow(
                    title = "Check for updates",
                    subtitle = when {
                        checking -> "Checking..."
                        else -> updateMessage
                    }
                ) {
                    if (!checking) {
                        checking = true
                        scope.launch {
                            val result = UpdateChecker.fetchLatest()
                            checking = false
                            release = result.getOrNull()
                            updateMessage = result.fold(
                                onSuccess = { latest ->
                                    if (UpdateChecker.isNewer(versionName, latest.version)) {
                                        "Version ${latest.tag} is available"
                                    } else {
                                        "You have the latest version"
                                    }
                                },
                                onFailure = { "Couldn't check: ${it.message}" }
                            )
                        }
                    }
                }
            }
            release?.takeIf { UpdateChecker.isNewer(versionName, it.version) }?.let { latest ->
                item(key = "update_download") {
                    ActionRow("Download ${latest.tag}", "Opens the APK in your browser; it installs over this app") {
                        val target = latest.downloadUrl.ifBlank { latest.pageUrl }
                        if (target.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                        }
                    }
                }
            }
            crashReport?.let { report ->
                item(key = "crash") {
                    ActionRow("Share last crash report", "Saved on this device when the app last crashed") {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, "Share crash report"))
                    }
                }
                item(key = "crash_clear") {
                    ActionRow("Delete crash report", null) {
                        CrashReporter.clear(context)
                        crashReport = null
                    }
                }
            }
            item(key = "legal") {
                Text(
                    text = "Built with NewPipeExtractor (GPL-3.0), AndroidX Media3, Jetpack Compose, OkHttp, Coil and " +
                        "Roboto. Not affiliated with or endorsed by YouTube or Google. Videos remain the property " +
                        "of their creators.",
                    fontSize = 12.sp,
                    color = OriginalColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    selfTestReport?.let { report ->
        val text = report.joinToString("\n")
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selfTestReport = null },
            containerColor = OriginalColors.Sheet,
            title = { Text("Playback self-test", color = OriginalColors.TextPrimary) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(report.size) { index ->
                        val line = report[index]
                        Text(
                            text = line,
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (line.startsWith("FAIL")) Color(0xFFFF6B6B) else OriginalColors.TextPrimary
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "yt-mini playback self-test\n$text")
                    }
                    context.startActivity(Intent.createChooser(send, "Share self-test"))
                }) { Text("Share", color = LinkBlue) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { selfTestReport = null }) {
                    Text("Close", color = OriginalColors.TextSecondary)
                }
            }
        )
    }

    pendingRestore?.let { json ->
        ConfirmDialog(
            title = "Restore this backup?",
            message = "Your current playlists, history, subscriptions, likes and settings will be replaced.",
            confirmLabel = "Restore",
            onConfirm = {
                val ok = LibraryRepository.importBackup(json)
                Toast.makeText(context, if (ok) "Backup restored" else "That isn't a yt-mini backup", Toast.LENGTH_LONG).show()
                pendingRestore = null
            },
            onDismiss = { pendingRestore = null }
        )
    }

    if (showAccounts) AccountSwitcherDialog(onDismiss = { showAccounts = false })
    if (showTvLink) TvLinkDialog(onDismiss = { showTvLink = false })

    if (confirmClearHistory) {
        ConfirmDialog(
            title = "Clear watch history?",
            message = "This removes all ${history.size} videos from your history on this device.",
            confirmLabel = "Clear",
            onConfirm = {
                LibraryRepository.clearWatchHistory()
                confirmClearHistory = false
            },
            onDismiss = { confirmClearHistory = false }
        )
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = OriginalColors.TextSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsHint(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = OriginalColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = OriginalColors.TextPrimary)
            Text(subtitle, fontSize = 12.sp, color = OriginalColors.TextSecondary)
        }
        // The whole row is the toggle; the switch only displays the state.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(title, fontSize = 16.sp, color = OriginalColors.TextPrimary)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, fontSize = 12.sp, color = OriginalColors.TextSecondary)
        }
    }
}

/** Enter the code the YouTube TV app shows to pair it as a remote-controlled screen. */
@Composable
private fun TvLinkDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link with TV code") },
        text = {
            Column {
                Text("On your TV, open YouTube, go to Settings > Link with TV code and enter the code shown.", fontSize = 13.sp, color = OriginalColors.TextSecondary)
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() || it == ' ' }.take(16); error = null },
                    singleLine = true,
                    label = { Text("TV code") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                error?.let { Text(it, fontSize = 12.sp, color = Color(0xFFFF6B6B)) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = !busy && code.count { it.isDigit() } >= 6,
                onClick = {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { TvLink.pair(code) } }
                            .onSuccess {
                                Toast.makeText(context, "Linked to ${it.name}", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                            .onFailure { error = it.message ?: "Couldn't link the TV" }
                        busy = false
                    }
                }
            ) { Text(if (busy) "Linking..." else "Link") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** YouTube's channel switcher: every channel (Brand accounts included) the signed-in Google accounts own. */
@Composable
private fun AccountSwitcherDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var accounts by remember { mutableStateOf<List<AccountApi.SwitchableAccount>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { AccountApi.accounts() } }
            .onSuccess { accounts = it }
            .onFailure { error = it.message ?: "Couldn't load your accounts" }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accounts") },
        text = {
            val list = accounts
            when {
                error != null -> Text(error.orEmpty(), color = OriginalColors.TextSecondary)
                list == null -> Text("Loading...", color = OriginalColors.TextSecondary)
                list.isEmpty() -> Text("No other channels on this account", color = OriginalColors.TextSecondary)
                else -> LazyColumn {
                    items(list) { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    GoogleAccount.setIdentity(account.identity)
                                    val old = GoogleAccount.userProfile.value
                                    GoogleAccount.setProfile(
                                        GoogleUserProfile(
                                            email = account.group.ifBlank { old?.email.orEmpty() },
                                            displayName = account.name,
                                            avatarUrl = account.photoUrl,
                                            id = account.identity.pageId.ifBlank { old?.id.orEmpty() }
                                        )
                                    )
                                    LibraryRepository.syncAccount(force = true)
                                    Toast.makeText(context, "Switched to ${account.name}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = account.photoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(36.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(account.name, fontSize = 15.sp, color = OriginalColors.TextPrimary, fontWeight = if (account.selected) FontWeight.Bold else FontWeight.Normal)
                                val sub = listOf(account.handle, account.group).filter { it.isNotBlank() }.joinToString(" • ")
                                if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = OriginalColors.TextSecondary)
                            }
                            if (account.selected) Text("✓", color = OriginalColors.TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
