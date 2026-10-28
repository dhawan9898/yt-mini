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
private val QUALITY_OPTIONS = listOf("Auto" to 0, "1080p" to 1080, "720p" to 720, "480p" to 480, "360p" to 360)

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
    val dataSaver by AppSettings.dataSaver.collectAsState()
    val autoplay by AppSettings.autoplay.collectAsState()
    val captions by AppSettings.captionsByDefault.collectAsState()
    val wifiOnly by AppSettings.wifiOnlyDownloads.collectAsState()
    val country by AppSettings.contentCountry.collectAsState()
    val dislikeCounts by AppSettings.showDislikeCounts.collectAsState()
    val historyPaused by LibraryRepository.historyPaused.collectAsState()
    val history by LibraryRepository.history.collectAsState()
    val downloads by LibraryRepository.downloads.collectAsState()
    val pending by DownloadRepository.pending.collectAsState()

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
            item(key = "quality_label") { SettingsHint("Preferred video quality (never above what the video offers)") }
            item(key = "quality") {
                OriginalFilterChips(
                    labels = QUALITY_OPTIONS.map { it.first },
                    selected = QUALITY_OPTIONS.firstOrNull { it.second == defaultHeight }?.first ?: "Auto",
                    onSelect = { label -> AppSettings.setDefaultVideoHeight(QUALITY_OPTIONS.first { it.first == label }.second) },
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

    if (confirmClearHistory) {
        ConfirmDialog(
            title = "Clear watch history?",
            message = "This removes all ${history.size} videos from your history on this device.",
            confirmLabel = "Clear",
            onConfirm = {
                LibraryRepository.clearHistory()
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
