package com.tgaudiobooks.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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

/**
 * Silky, critically damped (zero-jitter / zero-bounce) Material 3 Emphasized capsule animations.
 */
object BubbleMotion {

    // Material 3 Emphasized Decelerate & Accelerate curves — silky smooth, zero oscillation
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val SmoothStandard = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Smooth zero-bounce spring for pills, sliders, and icons. */
    fun <T> bouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 380f
    )

    /** Pure capsule expand from bottom-center pill into full screen (zero jitter). */
    fun modalEnter(): EnterTransition =
        scaleIn(
            initialScale = 0.76f,
            transformOrigin = TransformOrigin(0.5f, 0.92f),
            animationSpec = tween(durationMillis = 340, easing = EmphasizedDecelerate)
        ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = SmoothStandard))

    /** Pure capsule collapse back down into bottom-center pill (zero jitter). */
    fun modalExit(): ExitTransition =
        scaleOut(
            targetScale = 0.76f,
            transformOrigin = TransformOrigin(0.5f, 0.92f),
            animationSpec = tween(durationMillis = 250, easing = EmphasizedAccelerate)
        ) + fadeOut(animationSpec = tween(durationMillis = 190, easing = EmphasizedAccelerate))

    /** Smooth capsule expand for MiniPlayerBar appearing above the nav capsule. */
    fun miniBarEnter(): EnterTransition =
        scaleIn(
            initialScale = 0.84f,
            transformOrigin = TransformOrigin(0.5f, 1.0f),
            animationSpec = tween(durationMillis = 280, easing = EmphasizedDecelerate)
        ) + fadeIn(animationSpec = tween(durationMillis = 180))

    fun miniBarExit(): ExitTransition =
        scaleOut(
            targetScale = 0.84f,
            transformOrigin = TransformOrigin(0.5f, 1.0f),
            animationSpec = tween(durationMillis = 200, easing = EmphasizedAccelerate)
        ) + fadeOut(animationSpec = tween(durationMillis = 160))

    /** Capsule-scale content switch for tabs, categories, home & settings (zero jitter). */
    fun bubbleContentTransform(): ContentTransform =
        (scaleIn(
            initialScale = 0.94f,
            transformOrigin = TransformOrigin.Center,
            animationSpec = tween(durationMillis = 260, easing = EmphasizedDecelerate)
        ) + fadeIn(tween(durationMillis = 200, easing = SmoothStandard)))
            .togetherWith(
                scaleOut(
                    targetScale = 0.96f,
                    transformOrigin = TransformOrigin.Center,
                    animationSpec = tween(durationMillis = 170, easing = EmphasizedAccelerate)
                ) + fadeOut(tween(durationMillis = 150))
            )

    /** Crisp smooth icon morph without bounce wobble. */
    fun iconPopTransform(): ContentTransform =
        (scaleIn(
            initialScale = 0.78f,
            animationSpec = tween(durationMillis = 200, easing = EmphasizedDecelerate)
        ) + fadeIn(tween(durationMillis = 150)))
            .togetherWith(
                scaleOut(
                    targetScale = 0.78f,
                    animationSpec = tween(durationMillis = 140, easing = EmphasizedAccelerate)
                ) + fadeOut(tween(durationMillis = 120))
            )
}

/**
 * Wraps a modal overlay (FullScreenPlayer / TelegramChat) in a smooth capsule expand/collapse
 * that morphs corner radius (34.dp <-> 0.dp) along with scale and fade — zero jitter.
 */
@Composable
fun CapsuleModalOverlay(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    val cornerDp by animateDpAsState(
        targetValue = if (visible) 0.dp else 36.dp,
        animationSpec = tween(
            durationMillis = if (visible) 340 else 250,
            easing = if (visible) BubbleMotion.EmphasizedDecelerate else BubbleMotion.EmphasizedAccelerate
        ),
        label = "capsule_modal_corner"
    )
    AnimatedVisibility(
        visible = visible,
        enter = BubbleMotion.modalEnter(),
        exit = BubbleMotion.modalExit()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerDp))
        ) {
            content()
        }
    }
}

fun Modifier.bubblePressEffect(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.94f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 460f
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
 * Floating capsule bottom navigation bar with a silky sliding pill indicator
 * synced directly to HorizontalPager's continuous [pagerPosition] (zero jitter).
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
    val fractional = abs(clampedPos - clampedPos.roundToInt())
    val stretchX = 1f + (fractional * 0.16f)

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
                            scaleY = 1f - (fractional * 0.05f)
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
                            animationSpec = tween(200, easing = BubbleMotion.SmoothStandard),
                            label = "nav_item_color"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 0.98f,
                            animationSpec = BubbleMotion.bouncySpring(),
                            label = "nav_item_icon_scale"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .bubblePressEffect(interaction, 0.94f)
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
 * Silky capsule tab bar with a zero-jitter sliding pill underneath the active tab.
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
        animationSpec = tween(durationMillis = 280, easing = BubbleMotion.EmphasizedDecelerate),
        label = "sliding_tab_pos"
    )
    val velocityStretch = abs(animatedIndex - selectedIndex).coerceAtMost(0.45f)

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
                        scaleX = 1f + velocityStretch * 0.16f
                        scaleY = 1f - velocityStretch * 0.04f
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
                        animationSpec = tween(200, easing = BubbleMotion.SmoothStandard),
                        label = "sliding_tab_fg"
                    )
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .bubblePressEffect(interaction, 0.94f)
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
