package com.nexusstream.app.ui.youtube

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.data.CommentModel
import com.nexusstream.app.data.CommentsApi
import com.nexusstream.app.data.CommentsPage
import com.nexusstream.app.data.GoogleAccount
import com.nexusstream.app.data.Paged
import com.nexusstream.app.ui.components.OnReachEnd
import com.nexusstream.app.ui.components.OriginalColors
import com.nexusstream.app.ui.components.OriginalFilterChips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Comments with infinite scroll; each comment can expand its replies. Signed in (and when the
 * list came from YouTube's own API) there is a box to comment, and like / reply on each comment.
 */
@Composable
internal fun CommentsList(page: CommentsPage, sort: CommentsApi.Sort, onSort: (CommentsApi.Sort) -> Unit) {
    var items by remember(page) { mutableStateOf(page.items) }
    var cursor by remember(page) { mutableStateOf<Paged<CommentModel>?>(page.paged) }
    var loadingMore by remember(page) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val profile by GoogleAccount.userProfile.collectAsState()

    fun loadMore() {
        val current = cursor ?: return
        if (!current.hasMore || loadingMore) return
        loadingMore = true
        scope.launch {
            current.next().onSuccess { next ->
                items = items + next.items
                cursor = next
            }
            loadingMore = false
        }
    }

    // YouTube's Top / Newest when the comments came from its API; otherwise the loaded
    // comments re-sorted by like count.
    var mostLiked by remember { mutableStateOf(false) }
    val shown = remember(items, mostLiked) { if (mostLiked) items.sortedByDescending { it.likes } else items }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "sort") {
            if (page.interactive) {
                OriginalFilterChips(
                    labels = listOf("Top", "Newest"),
                    selected = if (sort == CommentsApi.Sort.NEWEST) "Newest" else "Top",
                    onSelect = { onSort(if (it == "Newest") CommentsApi.Sort.NEWEST else CommentsApi.Sort.TOP) },
                    unselectedColor = OriginalColors.Chip
                )
            } else {
                OriginalFilterChips(
                    labels = listOf("Top", "Most liked"),
                    selected = if (mostLiked) "Most liked" else "Top",
                    onSelect = { mostLiked = it == "Most liked" },
                    unselectedColor = OriginalColors.Chip
                )
            }
        }
        val createParams = page.createParams
        if (createParams != null) {
            item(key = "compose") {
                CommentComposer(avatarUrl = profile?.avatarUrl.orEmpty(), placeholder = "Add a comment...") { text ->
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { CommentsApi.post(createParams, text) } }
                        result.onSuccess {
                            items = listOf(
                                CommentModel(
                                    author = profile?.displayName.orEmpty().ifEmpty { "You" },
                                    avatarUrl = profile?.avatarUrl.orEmpty(),
                                    text = text, likes = 0, date = "Just now", pinned = false
                                )
                            ) + items
                        }.onFailure {
                            Toast.makeText(context, it.message ?: "Couldn't post the comment", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        items(shown) { comment -> CommentRow(comment) }
        if (loadingMore) {
            item(key = "loading_more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = OriginalColors.TextPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
    OnReachEnd(listState) { loadMore() }
}

/** Avatar, a text field and a send button. [onSend] gets the trimmed text. */
@Composable
internal fun CommentComposer(avatarUrl: String, placeholder: String, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(OriginalColors.Divider)) {
            if (avatarUrl.isNotEmpty()) AsyncImage(avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            if (text.isEmpty()) Text(placeholder, fontSize = 14.sp, color = OriginalColors.TextSecondary)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(color = OriginalColors.TextPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(OriginalColors.TextPrimary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (text.isNotBlank()) {
            IconButton(onClick = {
                onSend(text.trim())
                text = ""
            }) {
                Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = OriginalColors.Accent)
            }
        }
    }
}

@Composable
internal fun CommentAvatar(comment: CommentModel, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(OriginalColors.Divider)
    ) {
        if (comment.avatarUrl.isNotEmpty()) {
            AsyncImage(
                model = comment.avatarUrl,
                contentDescription = comment.author,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
internal fun CommentRow(comment: CommentModel, isReply: Boolean = false) {
    var replies by remember(comment) { mutableStateOf<List<CommentModel>?>(null) }
    var loadingReplies by remember(comment) { mutableStateOf(false) }
    var liked by remember(comment) { mutableStateOf(comment.isLiked) }
    var replying by remember(comment) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val profile by GoogleAccount.userProfile.collectAsState()
    val likes = comment.likes + (if (liked && !comment.isLiked) 1 else 0) - (if (!liked && comment.isLiked) 1 else 0)

    Column(modifier = Modifier.padding(start = if (isReply) 48.dp else 0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            CommentAvatar(comment, size = if (isReply) 28.dp else 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.author, fontSize = 12.sp, color = OriginalColors.TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text(comment.date, fontSize = 12.sp, color = OriginalColors.TextSecondary)
                }
                Text(comment.text, fontSize = 14.sp, color = OriginalColors.TextPrimary, modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (!GoogleAccount.isSignedIn()) {
                                Toast.makeText(context, "Sign in to like comments", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            val target = !liked
                            liked = target
                            val action = comment.likeAction
                            if (action != null) {
                                scope.launch(Dispatchers.IO) {
                                    runCatching { CommentsApi.like(action, target) }.onFailure {
                                        liked = !target
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (liked) OriginalColors.Accent else OriginalColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (likes > 0) {
                        Text(likes.toString(), fontSize = 12.sp, color = OriginalColors.TextSecondary, modifier = Modifier.padding(end = 12.dp))
                    }
                    if (comment.replyAction != null) {
                        Text(
                            text = "Reply",
                            fontSize = 12.sp,
                            color = OriginalColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp).padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
        replies?.forEach { reply -> CommentRow(reply, isReply = true) }
    }
}
