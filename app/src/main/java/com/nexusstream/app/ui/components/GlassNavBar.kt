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
import androidx.compose.foundation.layout.statusBarsPadding
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
import com.nexusstream.app.ui.theme.GlassMotion
import com.nexusstream.app.ui.theme.GlassWeight
import com.nexusstream.app.ui.theme.ThemeState
import com.nexusstream.app.ui.theme.liquidGlass
import androidx.compose.animation.animateColorAsState
import dev.chrisbanes.haze.HazeState

/**
 * A floating "liquid glass" navigation bar: a capsule hovering above the content that blurs what
 * scrolls underneath it ([backdrop]), with a bright rim and a soft sheen. The selection is a glass
 * droplet that glides between tabs and stretches a little while it moves, and the selected icon
 * swells slightly. Slide a finger along the bar to scrub between tabs.
 */
@Composable
fun GlassNavBar(
    entries: List<BottomNavEntry>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: HazeState? = null
) {
    val onSelectState by rememberUpdatedState(onSelect)
    val dark = ThemeState.palette.isDark
    val shape = RoundedCornerShape(percent = 50)
    val ink = OriginalColors.TextPrimary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .liquidGlass(shape, backdrop, GlassWeight.Thick, elevation = 14.dp)
            .padding(5.dp)
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
        val target = itemWidth * selectedIndex
        val bubbleX by animateDpAsState(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f),
            label = "GlassBubbleX"
        )
        // The droplet stretches with the distance it still has to travel, then settles round.
        val stretch = kotlin.math.min(kotlin.math.abs((target - bubbleX).value), itemWidth.value).dp * 0.45f

        Spacer(
            modifier = Modifier
                .offset(x = bubbleX - stretch / 2)
                .width(itemWidth + stretch)
                .fillMaxHeight()
                .clip(shape)
                .background(ink.copy(alpha = if (dark) 0.11f else 0.07f))
                .border(
                    0.75.dp,
                    Brush.verticalGradient(listOf(ink.copy(alpha = if (dark) 0.22f else 0.10f), Color.Transparent)),
                    shape
                )
        )

        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            entries.forEachIndexed { index, entry ->
                val selected = index == selectedIndex
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.1f else 1f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
                    label = "GlassIconScale"
                )
                val color by animateColorAsState(
                    targetValue = if (selected) OriginalColors.TextPrimary else OriginalColors.TextSecondary,
                    animationSpec = GlassMotion.snappy(),
                    label = "GlassIconColor"
                )
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
                            .size(22.dp)
                            .scale(scale)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.label,
                        fontSize = 10.sp,
                        color = color,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Tablets and unfolded foldables: the same tabs as a side rail, like the official apps. */
@Composable
fun SideNavRail(
    entries: List<BottomNavEntry>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.NavigationRail(
        modifier = modifier.fillMaxHeight().statusBarsPadding(),
        containerColor = Color.Transparent
    ) {
        androidx.compose.foundation.layout.Spacer(Modifier.height(56.dp))
        entries.forEachIndexed { index, entry ->
            val selected = index == selectedIndex
            androidx.compose.material3.NavigationRailItem(
                selected = selected,
                onClick = { onSelect(index) },
                icon = { androidx.compose.material3.Icon(if (selected) entry.selectedIcon else entry.icon, contentDescription = entry.label) },
                label = { androidx.compose.material3.Text(entry.label, fontSize = 11.sp) },
                colors = androidx.compose.material3.NavigationRailItemDefaults.colors(
                    selectedIconColor = OriginalColors.TextPrimary,
                    selectedTextColor = OriginalColors.TextPrimary,
                    unselectedIconColor = OriginalColors.TextSecondary,
                    unselectedTextColor = OriginalColors.TextSecondary,
                    indicatorColor = OriginalColors.TextPrimary.copy(alpha = 0.10f)
                )
            )
        }
    }
}

/** Width at which the bottom bar becomes a side rail. */
const val RAIL_MIN_WIDTH_DP = 600
