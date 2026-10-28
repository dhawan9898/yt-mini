package com.nexusstream.app.ui.youtube

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.GoogleAccount
import com.nexusstream.app.data.LibraryRepository
import com.nexusstream.app.data.TakeoutParsing
import com.nexusstream.app.data.YouTubeRepository
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.Route
import com.nexusstream.app.ui.YouTubeNav
import com.nexusstream.app.ui.components.FeedState
import com.nexusstream.app.ui.components.MiniPlayerHooks
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalEmptyState
import com.nexusstream.app.ui.components.OriginalError
import com.nexusstream.app.ui.components.OriginalFilterChips
import com.nexusstream.app.ui.components.OriginalLoading
import com.nexusstream.app.ui.components.PageScaffold
import com.nexusstream.app.ui.components.toFeedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NewDotBlue: Color get() = OriginalColors.Accent
private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Subscriptions tab: channel avatars (blue dot = new upload since you last opened the channel),
 * filter chips, and the newest uploads from your subscriptions (Google Account personalized feed or local RSS).
 */
@Composable
internal fun SubscriptionsTab(
    nav: YouTubeNav,
    onSelectVideo: (MediaItemModel, List<MediaItemModel>) -> Unit
) {
    val signedIn by GoogleAccount.signedIn.collectAsState()
    val subs by LibraryRepository.subscriptions.collectAsState()
    val hidden by LibraryRepository.hidden.collectAsState()
    var reloadKey by remember { mutableStateOf(0) }
    var forceRefresh by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("All") }
    var feed by remember {
        mutableStateOf<FeedState<List<MediaItemModel>>>(
            YouTubeRepository.cachedSubscriptionFeed(subs)?.let { FeedState.Data(it) } ?: FeedState.Loading
        )
    }

    LaunchedEffect(subs.map { it.name to it.channelUrl }, signedIn, reloadKey) {
        if (!signedIn && subs.isEmpty()) return@LaunchedEffect
        val force = forceRefresh
        forceRefresh = false
        if (force || feed !is FeedState.Data) feed = FeedState.Loading
        feed = YouTubeRepository.subscriptionFeed(subs, refresh = force).toFeedState()
    }

    val now = System.currentTimeMillis()
    val items = (feed as? FeedState.Data)?.value.orEmpty()
        .filter { it.id !in hidden.videoIds && it.artistOrChannel !in hidden.channels }
    val shown = when (filter) {
        "Today" -> items.filter { it.publishedAtMs > now - DAY_MS }
        "This week" -> items.filter { it.publishedAtMs > now - 7 * DAY_MS }
        else -> items
    }

    val displaySubs = if (subs.isNotEmpty()) subs else {
        items.distinctBy { it.artistOrChannel }.map {
            LibraryRepository.ChannelSub(
                name = it.artistOrChannel,
                avatarUrl = it.channelAvatarUrl,
                channelUrl = it.channelUrl,
                lastSeenMs = 0L
            )
        }
    }

    if (subs.isEmpty() && shown.isEmpty() && feed !is FeedState.Loading) {
        SubscriptionsDiscoveryView(nav = nav)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (displaySubs.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                items(displaySubs, key = { it.name }) { sub ->
                    val hasNew = items.any {
                        (it.channelUrl == sub.channelUrl && sub.channelUrl.isNotBlank() || it.artistOrChannel == sub.name) &&
                            it.publishedAtMs > sub.lastSeenMs
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(68.dp)
                            .clickable {
                                LibraryRepository.markSeen(sub.name)
                                if (sub.channelUrl.isNotBlank()) nav.openChannel(sub.channelUrl, sub.name, sub.avatarUrl)
                            }
                    ) {
                        Box {
                            Avatar(sub.avatarUrl, sub.name, 56.dp)
                            if (hasNew) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(NewDotBlue)
                                        .border(2.dp, OriginalColors.YouTubeBackground, CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = sub.name,
                            fontSize = 12.sp,
                            color = OriginalColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                item(key = "all_button") {
                    Box(
                        modifier = Modifier
                            .height(56.dp)
                            .clickable { nav.openAllSubscriptions() }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("All", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = NewDotBlue)
                    }
                }
            }
        }

        OriginalFilterChips(
            labels = listOf("All", "Today", "This week"),
            selected = filter,
            onSelect = { filter = it },
            unselectedColor = OriginalColors.Chip
        )

        when (val state = feed) {
            is FeedState.Loading -> OriginalLoading()
            is FeedState.Error -> OriginalError(
                message = state.message,
                onRetry = {
                    forceRefresh = true
                    reloadKey++
                }
            )
            is FeedState.Data -> if (shown.isEmpty()) {
                val emptyMsg = if (filter != "All") "No uploads match this filter." else "No uploads found in your subscriptions yet."
                OriginalEmptyState("Nothing new", emptyMsg)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(shown, key = { "sub_${it.id}" }) { video ->
                        YouTubeVideoCard(
                            item = video,
                            onClick = { onSelectVideo(video, shown) },
                            onChannelClick = video.channelUrl.takeIf { it.isNotBlank() }
                                ?.let { { nav.openChannelOf(video) } }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shown when the user has no subscriptions yet.
 * Gives 1-tap Google sign in, Google Takeout CSV import, Search, and popular creator subscribe buttons.
 */
@Composable
private fun SubscriptionsDiscoveryView(nav: YouTubeNav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn by GoogleAccount.signedIn.collectAsState()
    val subs by LibraryRepository.subscriptions.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    }.getOrNull()
                }
                if (text != null) {
                    val imported = TakeoutParsing.parseSubscriptionsCsv(text)
                    if (imported.isNotEmpty()) {
                        val count = LibraryRepository.importSubscriptions(imported)
                        Toast.makeText(context, "Imported $count subscriptions!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "No subscriptions found in that file.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val featuredChannels = remember {
        listOf(
            Triple("MKBHD", "https://yt3.googleusercontent.com/lkH37D712tiyphnu0Id0D5MwwQ7IRuwgQLVD05iMXlDWO-kDHqqd8EM52qDxYdraQac7Geat=s176-c-k-c0x00ffffff-no-rj", "https://www.youtube.com/@mkbhd"),
            Triple("MrBeast", "https://yt3.googleusercontent.com/fxGKYucJAVme-YzgnKiYGasPuoJ1NpdPfNutv5af4oN45DisgEBlcxCraIYAzTvAM-hTxC19=s176-c-k-c0x00ffffff-no-rj", "https://www.youtube.com/@MrBeast"),
            Triple("Veritasium", "https://yt3.googleusercontent.com/ytc/AIdro_n_9n25J4_8_o04_v=s176-c-k-c0x00ffffff-no-rj", "https://www.youtube.com/@veritasium"),
            Triple("Lofi Girl", "https://yt3.googleusercontent.com/w2Y_b4a8eA5PZ1g5z2Q=s176-c-k-c0x00ffffff-no-rj", "https://www.youtube.com/@LofiGirl"),
            Triple("Kurzgesagt – In a Nutshell", "https://yt3.googleusercontent.com/ytc/AIdro_nU8k=s176-c-k-c0x00ffffff-no-rj", "https://www.youtube.com/@kurzgesagt"),
            Triple("IGN", "https://yt3.googleusercontent.com/H_2Y=s176-c-k-c0x00ffffff-no-rj", "https://www.youtube.com/@IGN"),
            Triple("National Geographic", "https://yt3.googleusercontent.com/ytc/AIdro_k2=s176-c-k-c0x00ffffff-no-rj", "https://www.youtube.com/@NatGeo")
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp)
    ) {
        item {
            Icon(
                Icons.Rounded.Subscriptions,
                contentDescription = null,
                tint = OriginalColors.Red,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Don't miss new videos",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = OriginalColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Subscribe to channels you love to see their latest uploads here, or import your existing YouTube subscriptions.",
                fontSize = 14.sp,
                color = OriginalColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!signedIn) {
                    Button(
                        onClick = { nav.open(Route.SignIn) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text("Sign In", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OriginalColors.Chip),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(16.dp), tint = OriginalColors.TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import CSV", color = OriginalColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Button(
                    onClick = nav.openSearch,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OriginalColors.Chip),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp), tint = OriginalColors.TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Search", color = OriginalColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Popular creators to subscribe:",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = OriginalColors.TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(featuredChannels, key = { it.first }) { channel ->
            val isSubbed = subs.any { it.name == channel.first }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(channel.second, channel.first, 48.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.first,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OriginalColors.TextPrimary
                    )
                    Text(
                        text = "Official Channel",
                        fontSize = 12.sp,
                        color = OriginalColors.TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSubbed) OriginalColors.Chip else Color.White)
                        .clickable {
                            LibraryRepository.toggleSubscribe(channel.first, channel.second, channel.third)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isSubbed) "Subscribed" else "+ Subscribe",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSubbed) OriginalColors.TextPrimary else Color.Black
                    )
                }
            }
        }
    }
}

/** Every subscribed channel, with a toggle to unsubscribe. */
@Composable
fun AllSubscriptionsScreen(
    nav: YouTubeNav,
    miniPlayer: MiniPlayerHooks
) {
    val signedIn by GoogleAccount.signedIn.collectAsState()
    val subs by LibraryRepository.subscriptions.collectAsState()

    LaunchedEffect(signedIn) {
        if (signedIn && subs.isEmpty()) {
            YouTubeRepository.refreshGoogleSubscriptions()
        }
    }

    PageScaffold(title = "All subscriptions", onBack = nav.back, miniPlayer = miniPlayer) {
        if (subs.isEmpty()) {
            OriginalEmptyState("No subscriptions", "Channels you subscribe to will be listed here.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(subs, key = { "all_${it.name}" }) { sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibraryRepository.markSeen(sub.name)
                                if (sub.channelUrl.isNotBlank()) nav.openChannel(sub.channelUrl, sub.name, sub.avatarUrl)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(sub.avatarUrl, sub.name, 48.dp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = sub.name,
                            fontSize = 16.sp,
                            color = OriginalColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(OriginalColors.Chip)
                                .clickable { LibraryRepository.toggleSubscribe(sub.name, sub.avatarUrl, sub.channelUrl) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("Subscribed", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OriginalColors.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Avatar(url: String, name: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(OriginalColors.Chip),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(name.take(1).uppercase(), fontSize = (size.value / 2.5f).sp, color = OriginalColors.TextPrimary)
        }
    }
}
