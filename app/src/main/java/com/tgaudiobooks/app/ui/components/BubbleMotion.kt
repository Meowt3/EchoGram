package com.tgaudiobooks.app.ui.components

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object BubbleMotion {

    fun <T> bouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> gentleSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun modalEnter(): EnterTransition =
        slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) { it / 3 } +
            scaleIn(
                initialScale = 0.90f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) +
            fadeIn(tween(220, easing = FastOutSlowInEasing))

    fun modalExit(): ExitTransition =
        slideOutVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) { it / 3 } +
            scaleOut(targetScale = 0.92f, animationSpec = tween(200)) +
            fadeOut(tween(180))

    fun miniBarEnter(): EnterTransition =
        slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) { it } +
            scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) +
            fadeIn(tween(200))

    fun miniBarExit(): ExitTransition =
        slideOutVertically(animationSpec = tween(180)) { it } +
            scaleOut(targetScale = 0.94f, animationSpec = tween(160)) +
            fadeOut(tween(160))

    fun bubbleContentTransform(): ContentTransform =
        (scaleIn(
            initialScale = 0.93f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(tween(220)))
            .togetherWith(
                scaleOut(targetScale = 0.96f, animationSpec = tween(150)) +
                    fadeOut(tween(140))
            )

    fun iconPopTransform(): ContentTransform =
        (scaleIn(
            initialScale = 0.62f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(tween(160)))
            .togetherWith(
                scaleOut(targetScale = 0.68f, animationSpec = tween(120)) +
                    fadeOut(tween(120))
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

/**
 * Soft floating translucent Material 3 gradient bubbles for ambient depth.
 * Runs smoothly on GPU without per-frame allocations when [active] is true.
 */
@Composable
fun AmbientBubbleLayer(
    modifier: Modifier = Modifier,
    active: Boolean = true,
    intensity: Float = 0.32f
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val container = MaterialTheme.colorScheme.primaryContainer

    val phase = if (active) {
        val infinite = rememberInfiniteTransition(label = "ambient_bubbles")
        infinite.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "bubble_phase"
        ).value
    } else {
        0.6f
    }

    val breathe = if (active) {
        val infinite = rememberInfiniteTransition(label = "ambient_bubbles_breathe")
        infinite.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 7_500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bubble_breathe"
        ).value
    } else {
        1f
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val minDim = minOf(w, h)
        val alphaScale = intensity.coerceIn(0.08f, 0.75f)

        // Bubble 1 — top-left soft glow
        val r1 = minDim * 0.48f * breathe
        val c1 = Offset(
            x = w * (0.18f + 0.08f * cos(phase)),
            y = h * (0.16f + 0.06f * sin(phase))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.30f * alphaScale), Color.Transparent),
                center = c1,
                radius = r1
            ),
            radius = r1,
            center = c1
        )

        // Bubble 2 — mid-right drifting orb
        val r2 = minDim * 0.42f * (2f - breathe)
        val c2 = Offset(
            x = w * (0.84f + 0.07f * sin(phase * 1.15f)),
            y = h * (0.44f + 0.08f * cos(phase * 0.9f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.26f * alphaScale), Color.Transparent),
                center = c2,
                radius = r2
            ),
            radius = r2,
            center = c2
        )

        // Bubble 3 — bottom-left rising bubble
        val r3 = minDim * 0.36f * breathe
        val c3 = Offset(
            x = w * (0.28f + 0.09f * sin(phase + 1.7f)),
            y = h * (0.82f + 0.05f * cos(phase * 1.3f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(container.copy(alpha = 0.36f * alphaScale), Color.Transparent),
                center = c3,
                radius = r3
            ),
            radius = r3,
            center = c3
        )

        // Bubble 4 — delicate crisp floating soap-bubble rings
        val r4 = minDim * 0.14f * breathe
        val c4 = Offset(
            x = w * (0.72f + 0.06f * cos(phase * 1.4f + 0.8f)),
            y = h * (0.22f + 0.05f * sin(phase * 1.4f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.18f * alphaScale), Color.Transparent),
                center = c4,
                radius = r4
            ),
            radius = r4,
            center = c4
        )
    }
}
