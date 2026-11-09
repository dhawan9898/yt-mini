package com.nexusstream.app.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.ui.components.OriginalColors

/** Description text with tappable web links and timestamps (timestamps seek the video). */
@Composable
internal fun LinkifiedText(text: String, onSeek: (Long) -> Unit) {
    val currentSeek by rememberUpdatedState(onSeek)
    val annotated = remember(text) {
        buildAnnotatedString {
            var cursor = 0
            val linkStyle = SpanStyle(color = OriginalColors.Accent)
            DescriptionLinks.find(text).forEach { link ->
                if (link.start < cursor) return@forEach
                append(text.substring(cursor, link.start))
                val shown = text.substring(link.start, link.end)
                when (link.kind) {
                    DescriptionLink.Kind.URL ->
                        withLink(LinkAnnotation.Url(link.value)) { withStyle(linkStyle) { append(shown) } }
                    DescriptionLink.Kind.TIMESTAMP ->
                        withLink(LinkAnnotation.Clickable("timestamp") { currentSeek(link.value.toLong()) }) {
                            withStyle(linkStyle) { append(shown) }
                        }
                }
                cursor = link.end
            }
            append(text.substring(cursor))
        }
    }
    Text(text = annotated, fontSize = 14.sp, color = OriginalColors.TextPrimary, lineHeight = 20.sp)
}

/** One of the grey pill buttons under the channel row ("Share", "Download", ...). [label] null makes it icon-only. */
@Composable
internal fun WatchActionChip(
    label: String?,
    description: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(OriginalColors.Chip)
            .clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = if (label == null) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        if (label != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = OriginalColors.TextPrimary,
                maxLines = 1
            )
        }
    }
}
