package com.nexusstream.app.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.theme.DarkPalette

/** A queue row with a key that stays with the track while rows move (the queue may repeat a track). */
private class QueueRow(val key: String, val item: MediaItemModel)

/**
 * The UP NEXT tab, as in YouTube Music: the queue with the playing track highlighted, drag handles
 * to reorder, swipe to remove, and an Autoplay switch that keeps the radio going.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UpNextPanel(
    queue: List<MediaItemModel>,
    currentIndex: Int,
    autoplay: Boolean,
    onSelect: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onStartRadio: () -> Unit
) {
    // Local copy the drag reorders live; the service gets one move when the drag ends.
    val rows = remember(queue) {
        val seen = HashMap<String, Int>()
        queue.map { q ->
            val n = (seen[q.id] ?: 0) + 1
            seen[q.id] = n
            QueueRow("${q.id}#$n", q)
        }.toMutableStateList()
    }
    val listState = rememberLazyListState()
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val playingKey = remember(rows, currentIndex) { rows.getOrNull(currentIndex)?.key }
    val playingRow = rows.indexOfFirst { it.key == playingKey }
    val headerCount = 1

    // Open with the playing track near the top, as the official app does.
    LaunchedEffect(Unit) {
        if (currentIndex > 0) listState.scrollToItem(headerCount + currentIndex)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "header") {
            UpNextHeader(autoplay = autoplay, onAutoplayChange = onAutoplayChange, onStartRadio = onStartRadio)
        }
        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
            val isCurrent = row.key == playingKey
            val isDragging = row.key == draggingKey
            val currentIndexState by rememberUpdatedState(index)
            QueueItemRow(
                item = row.item,
                isCurrent = isCurrent,
                isPast = index < playingRow,
                onClick = { onSelect(index) },
                onRemove = {
                    val at = rows.indexOfFirst { it.key == row.key }
                    if (at >= 0) {
                        rows.removeAt(at)
                        onRemove(at)
                    }
                },
                dragHandle = Modifier.pointerInput(row.key) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            draggingKey = row.key
                            dragStartIndex = currentIndexState
                            dragOffset = 0f
                        },
                        onDragEnd = {
                            val to = rows.indexOfFirst { it.key == row.key }
                            if (dragStartIndex >= 0 && to >= 0 && to != dragStartIndex) onMove(dragStartIndex, to)
                            draggingKey = null
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            draggingKey = null
                            dragOffset = 0f
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                            // Swap with the neighbour once the row has moved past half of it.
                            val info = listState.layoutInfo.visibleItemsInfo
                            val self = info.firstOrNull { it.key == row.key } ?: return@detectVerticalDragGestures
                            val center = self.offset + dragOffset + self.size / 2f
                            val target = info.firstOrNull {
                                it.key != row.key && it.key != "header" &&
                                    center > it.offset && center < it.offset + it.size
                            } ?: return@detectVerticalDragGestures
                            val from = rows.indexOfFirst { it.key == row.key }
                            val to = rows.indexOfFirst { it.key == target.key }
                            if (from >= 0 && to >= 0) {
                                rows.add(to, rows.removeAt(from))
                                dragOffset += (self.offset - target.offset).toFloat()
                            }
                        }
                    )
                },
                modifier = Modifier
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else 0f
                        shadowElevation = if (isDragging) 12f else 0f
                    }
            )
        }
    }
}

@Composable
private fun UpNextHeader(autoplay: Boolean, onAutoplayChange: (Boolean) -> Unit, onStartRadio: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Autoplay", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(
                    "Add similar content to the end of the queue",
                    fontSize = 12.sp,
                    color = DarkPalette.textSecondary
                )
            }
            Switch(
                checked = autoplay,
                onCheckedChange = onAutoplayChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = DarkPalette.textSecondary,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f))
                .clickable(onClick = onStartRadio)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.Radio, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("Start radio from this song", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueItemRow(
    item: MediaItemModel,
    isCurrent: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: Modifier,
    modifier: Modifier = Modifier
) {
    val currentOnRemove by rememberUpdatedState(onRemove)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                currentOnRemove()
                true
            } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        // The playing track stays put, as in the official app.
        enableDismissFromStartToEnd = !isCurrent,
        enableDismissFromEndToStart = !isCurrent,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF3A1F1F))
                    .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else Alignment.CenterEnd
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove from queue", tint = Color.White)
            }
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isCurrent) Color(0xFF2A2A2A) else Color(0xFF0F0F0F))
                .clickable(onClick = onClick)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(item.dominantColor),
                contentAlignment = Alignment.Center
            ) {
                if (item.artworkUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.artworkUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Equalizer, contentDescription = "Now playing", tint = Color.White)
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isPast) DarkPalette.textSecondary else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        item.artistOrChannel.takeIf { it.isNotBlank() },
                        item.durationMs.takeIf { it > 0 }?.let(::formatQueueTime)
                    ).joinToString(" • "),
                    fontSize = 12.sp,
                    color = DarkPalette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = dragHandle
                    .size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DragHandle, contentDescription = "Drag to reorder", tint = DarkPalette.textSecondary)
            }
        }
    }
}

private fun formatQueueTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
