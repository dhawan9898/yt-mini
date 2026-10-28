package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexusstream.app.model.MediaItemModel
import com.nexusstream.app.ui.components.OriginalColors

/**
 * The watch page's queue card (videos added with "Play next" / "Add to queue"), like YouTube's:
 * "Queue · 2 / 5" collapsed, the list with remove buttons expanded.
 */
@Composable
internal fun WatchQueueCard(
    queue: List<MediaItemModel>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(OriginalColors.Chip)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Queue", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = OriginalColors.TextPrimary)
                val next = queue.getOrNull(currentIndex + 1)
                Text(
                    text = if (next != null) "Next: ${next.title}" else "${currentIndex + 1} / ${queue.size}",
                    fontSize = 12.sp,
                    color = OriginalColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("${currentIndex + 1} / ${queue.size}", fontSize = 12.sp, color = OriginalColors.TextSecondary)
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse queue" else "Expand queue",
                    tint = OriginalColors.TextPrimary
                )
            }
        }
        if (expanded) {
            queue.forEachIndexed { index, item ->
                val playing = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (playing) OriginalColors.Divider else Color.Transparent)
                        .clickable(enabled = !playing) { onSelect(index) }
                        .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (playing) "▶" else "${index + 1}",
                        fontSize = 12.sp,
                        color = OriginalColors.TextSecondary,
                        modifier = Modifier.width(22.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(OriginalColors.Divider)
                    ) {
                        if (item.artworkUrl.isNotEmpty()) {
                            AsyncImage(
                                model = item.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontSize = 13.sp, color = OriginalColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.artistOrChannel, fontSize = 12.sp, color = OriginalColors.TextSecondary, maxLines = 1)
                    }
                    if (!playing) {
                        IconButton(onClick = { onRemove(index) }) {
                            Icon(Icons.Rounded.Close, "Remove from queue", tint = OriginalColors.TextSecondary, modifier = Modifier.size(20.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}
