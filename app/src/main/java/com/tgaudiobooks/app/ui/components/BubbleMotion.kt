package com.tgaudiobooks.app.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

object BubbleMotion {

    fun <T> bouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Capsule-style bottom-anchored expand transition for FullPlayer & Chat modals. */
    fun modalEnter(): EnterTransition =
        slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) { it / 2 } +
            scaleIn(
                initialScale = 0.84f,
                transformOrigin = TransformOrigin(0.5f, 1.0f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) +
            fadeIn(tween(220, easing = FastOutSlowInEasing))

    fun modalExit(): ExitTransition =
        slideOutVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 2 } +
            scaleOut(
                targetScale = 0.86f,
                transformOrigin = TransformOrigin(0.5f, 1.0f),
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) +
            fadeOut(tween(170))

    /** Capsule pop-in for floating MiniPlayerBar above the nav capsule. */
    fun miniBarEnter(): EnterTransition =
        slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) { it } +
            scaleIn(
                initialScale = 0.86f,
                transformOrigin = TransformOrigin(0.5f, 1.0f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) +
            fadeIn(tween(200))

    fun miniBarExit(): ExitTransition =
        slideOutVertically(animationSpec = tween(180)) { it } +
            scaleOut(
                targetScale = 0.88f,
                transformOrigin = TransformOrigin(0.5f, 1.0f),
                animationSpec = tween(160)
            ) +
            fadeOut(tween(150))

    fun bubbleContentTransform(): ContentTransform =
        (scaleIn(
            initialScale = 0.94f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(tween(200)))
            .togetherWith(
                scaleOut(targetScale = 0.96f, animationSpec = tween(140)) +
                    fadeOut(tween(130))
            )

    fun iconPopTransform(): ContentTransform =
        (scaleIn(
            initialScale = 0.60f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(tween(150)))
            .togetherWith(
                scaleOut(targetScale = 0.65f, animationSpec = tween(110)) +
                    fadeOut(tween(110))
            )
}

fun Modifier.bubblePressEffect(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.93f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubble_press_scale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

data class BubbleNavItem(
    val label: String,
    val icon: ImageVector
)

/**
 * Floating capsule bottom navigation bar with a live sliding & stretching bubble indicator
 * synced directly to HorizontalPager's continuous [pagerPosition] (currentPage + offsetFraction).
 */
@Composable
fun FloatingBubbleNavBar(
    items: List<BubbleNavItem>,
    selectedIndex: Int,
    pagerPosition: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val clampedPos = pagerPosition.coerceIn(0f, (items.size - 1).toFloat())
    // Stretch the sliding bubble slightly mid-swipe for a liquid-pill feel
    val fractional = abs(clampedPos - clampedPos.roundToInt())
    val stretchX = 1f + (fractional * 0.24f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            shadowElevation = 10.dp,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            ) {
                val count = items.size
                val slotWidth = maxWidth / count

                // Sliding liquid bubble indicator
                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .fillMaxHeight()
                        .offset {
                            IntOffset(
                                x = (slotWidth.toPx() * clampedPos).roundToInt(),
                                y = 0
                            )
                        }
                        .graphicsLayer {
                            scaleX = stretchX
                            scaleY = 1f - (fractional * 0.08f)
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    items.forEachIndexed { index, item ->
                        val isSelected = selectedIndex == index
                        val interaction = remember { MutableInteractionSource() }
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(180),
                            label = "nav_item_color"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 0.96f,
                            animationSpec = BubbleMotion.bouncySpring(),
                            label = "nav_item_icon_scale"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .bubblePressEffect(interaction, 0.92f)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    onClick = { onSelect(index) }
                                )
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable capsule tab bar with a springy sliding bubble slider underneath the active tab.
 */
@Composable
fun SlidingBubbleTabBar(
    items: List<BubbleNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sliding_tab_pos"
    )
    val velocityStretch = abs(animatedIndex - selectedIndex).coerceAtMost(0.6f)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val slotWidth = maxWidth / items.size

            // Sliding bubble pill
            Box(
                modifier = Modifier
                    .width(slotWidth)
                    .fillMaxHeight()
                    .offset {
                        IntOffset(
                            x = (slotWidth.toPx() * animatedIndex).roundToInt(),
                            y = 0
                        )
                    }
                    .graphicsLayer {
                        scaleX = 1f + velocityStretch * 0.22f
                        scaleY = 1f - velocityStretch * 0.06f
                    }
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, item ->
                    val active = index == selectedIndex
                    val fg by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(180),
                        label = "sliding_tab_fg"
                    )
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .bubblePressEffect(interaction, 0.93f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = { onSelect(index) }
                            )
                    ) {
                        Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
