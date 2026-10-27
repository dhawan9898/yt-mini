package com.nexusstream.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusstream.app.ui.theme.ThemeState

/**
 * A floating "liquid glass" navigation bar: a translucent capsule hovering above the content with
 * a specular edge and sheen. The selection is a glass bubble that slides (with a little overshoot)
 * between items, and the selected icon swells slightly.
 *
 * True backdrop blur is not available to Compose, so the glass look is built from translucency,
 * gradients and a bright rim rather than blurring what is behind it.
 */
@Composable
fun GlassNavBar(
    entries: List<BottomNavEntry>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val onSelectState by rememberUpdatedState(onSelect)
    val dark = ThemeState.palette.isDark
    val shape = RoundedCornerShape(28.dp)
    val tint = if (dark) Color(0xFF16161B) else Color(0xFFFFFFFF)
    val rim = if (dark) Color.White else Color(0xFF9A9AA5)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .clip(shape)
            .background(tint.copy(alpha = if (dark) 0.62f else 0.70f))
            .drawBehind {
                // Sheen: light falling on the top of the glass.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = if (dark) 0.16f else 0.55f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.55f
                    )
                )
                // A soft glow at the bottom edge.
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, rim.copy(alpha = 0.07f)),
                        startY = size.height * 0.5f,
                        endY = size.height
                    )
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(rim.copy(alpha = 0.55f), rim.copy(alpha = 0.08f), rim.copy(alpha = 0.30f)),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = shape
            )
            .padding(4.dp)
            .pointerInput(entries.size) {
                // Slide a finger along the bar to move the selection.
                var last = -1
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        last = (offset.x / size.width * entries.size).toInt().coerceIn(0, entries.lastIndex)
                        onSelectState(last)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val index = (change.position.x / size.width * entries.size).toInt().coerceIn(0, entries.lastIndex)
                        if (index != last) {
                            last = index
                            onSelectState(index)
                        }
                    }
                )
            }
    ) {
        val itemWidth = maxWidth / entries.size
        val bubbleX by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMediumLow),
            label = "GlassBubbleX"
        )

        // The sliding glass bubble behind the selected item.
        Spacer(
            modifier = Modifier
                .offset(x = bubbleX)
                .width(itemWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            rim.copy(alpha = if (dark) 0.26f else 0.20f),
                            rim.copy(alpha = if (dark) 0.10f else 0.08f)
                        )
                    )
                )
                .border(0.8.dp, rim.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
        )

        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            entries.forEachIndexed { index, entry ->
                val selected = index == selectedIndex
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.38f else 1f,
                    animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
                    label = "GlassIconScale"
                )
                val color = if (selected) OriginalColors.TextPrimary else OriginalColors.TextSecondary
                Column(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClickLabel = entry.label
                        ) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (selected) entry.selectedIcon else entry.icon,
                        contentDescription = entry.label,
                        tint = color,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(scale)
                    )
                    Spacer(modifier = Modifier.height(if (selected) 4.dp else 1.dp))
                    Text(
                        text = entry.label,
                        fontSize = 9.sp,
                        color = color,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
