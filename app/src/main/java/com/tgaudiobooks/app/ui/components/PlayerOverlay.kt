package com.tgaudiobooks.app.ui.components

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tgaudiobooks.app.data.model.AudioBookmark
import com.tgaudiobooks.app.data.model.AudioChapter
import com.tgaudiobooks.app.data.model.AudioTrack
import com.tgaudiobooks.app.data.model.DspPreset
import com.tgaudiobooks.app.data.model.ProgressBarStyle
import com.tgaudiobooks.app.data.model.SleepTimerState
import com.tgaudiobooks.app.data.model.SourceType
import com.tgaudiobooks.app.data.model.TelegramChannel
import com.tgaudiobooks.app.data.model.UserPlaylist
import com.tgaudiobooks.app.data.telegram.TelegramDeepLinks
import com.tgaudiobooks.app.data.recommendations.RecommendationEngine
import com.tgaudiobooks.app.ui.screens.CoverArtworkBox
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun PlayingEqualizerBars(
    isPlaying: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val phase by if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "eq_mini")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "eq_mini_phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Canvas(modifier = modifier.size(14.dp)) {
        val barW = size.width * 0.22f
        val gap = (size.width - barW * 3f) / 2f
        val ratios = if (isPlaying) {
            listOf(
                0.35f + 0.65f * ((sin(phase.toDouble()) + 1.0) / 2.0).toFloat(),
                0.30f + 0.70f * ((sin(phase.toDouble() + 2.1) + 1.0) / 2.0).toFloat(),
                0.40f + 0.60f * ((sin(phase.toDouble() + 4.2) + 1.0) / 2.0).toFloat()
            )
        } else {
            listOf(0.42f, 0.74f, 0.54f)
        }
        ratios.forEachIndexed { idx, r ->
            val bh = (size.height * r).coerceAtLeast(barW)
            val left = idx * (barW + gap)
            drawRoundRect(
                color = color.copy(alpha = if (isPlaying) 1f else 0.72f),
                topLeft = Offset(left, size.height - bh),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}

private val EQUALIZER_PROFILE = List(34) { idx ->
    val raw = 0.42 + 0.56 * abs(sin(idx * 0.68 + 0.4) * cos(idx * 0.27))
    raw.coerceIn(0.32, 1.0).toFloat()
}

@Composable
fun AnimatedProgressBarCanvas(
    progress: Float,
    style: ProgressBarStyle,
    isPlaying: Boolean,
    interactive: Boolean = false,
    showThumb: Boolean = interactive,
    onSeekProgress: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val trackBg = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.24f)

    val phase by if (isPlaying && style != ProgressBarStyle.CLASSIC) {
        val transition = rememberInfiniteTransition(label = "progress_anim")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val latestOnSeekProgress by rememberUpdatedState(onSeekProgress)
    val seekModifier = if (interactive && onSeekProgress != null) {
        modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        latestOnSeekProgress?.invoke((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        latestOnSeekProgress?.invoke((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
            }
    } else {
        modifier
    }

    val wavePath = remember { Path() }
    val glowColors = remember(primary, tertiary) { listOf(primary, tertiary) }
    val neonColors = remember(primary, tertiary) { listOf(primary.copy(alpha = 0.65f), primary, tertiary) }
    val cometColors = remember { listOf(Color.Transparent, Color.White, Color.Transparent) }

    Canvas(modifier = seekModifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val cy = h / 2f
        val isMiniStrip = h < 12.dp.toPx()
        val fillW = (w * clamped).coerceAtLeast(if (clamped > 0f) 4f else 0f)

        when (style) {
            ProgressBarStyle.CLASSIC -> {
                val stroke = if (isMiniStrip) h else 5.dp.toPx()
                drawLine(trackBg, Offset(0f, cy), Offset(w, cy), strokeWidth = stroke, cap = StrokeCap.Round)
                if (fillW > 0f) {
                    drawLine(primary, Offset(0f, cy), Offset(fillW, cy), strokeWidth = stroke, cap = StrokeCap.Round)
                }
            }
            ProgressBarStyle.GLOW -> {
                val stroke = if (isMiniStrip) h else 6.dp.toPx()
                drawLine(trackBg, Offset(0f, cy), Offset(w, cy), strokeWidth = stroke, cap = StrokeCap.Round)
                if (fillW > 0f) {
                    if (!isMiniStrip && isPlaying) {
                        drawLine(
                            color = primary.copy(alpha = 0.28f),
                            start = Offset(0f, cy),
                            end = Offset(fillW, cy),
                            strokeWidth = stroke * 2.1f,
                            cap = StrokeCap.Round
                        )
                    }
                    drawLine(
                        brush = Brush.horizontalGradient(glowColors, endX = fillW.coerceAtLeast(1f)),
                        start = Offset(0f, cy),
                        end = Offset(fillW, cy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    if (isPlaying) {
                        val pulseX = fillW * phase
                        drawCircle(
                            color = Color.White.copy(alpha = 0.65f),
                            radius = stroke * 1.15f,
                            center = Offset(pulseX, cy)
                        )
                    }
                }
            }
            ProgressBarStyle.WAVE -> {
                val stroke = if (isMiniStrip) h.coerceAtLeast(2.dp.toPx()) else 3.5.dp.toPx()
                drawLine(trackBg, Offset(fillW, cy), Offset(w, cy), strokeWidth = stroke, cap = StrokeCap.Round)
                if (fillW > 0f) {
                    wavePath.reset()
                    val amplitude = if (isMiniStrip) h * 0.32f else if (isPlaying) 4.dp.toPx() else 1.5.dp.toPx()
                    val wavelength = 24.dp.toPx()
                    val step = 4.dp.toPx().coerceAtLeast(6f)
                    var x = 0f
                    while (x <= fillW) {
                        val angle = ((x / wavelength) - phase) * 2.0 * PI
                        val y = cy + (sin(angle) * amplitude).toFloat()
                        if (x == 0f) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                        x += step
                    }
                    if (fillW > 0f) {
                        val endAngle = ((fillW / wavelength) - phase) * 2.0 * PI
                        wavePath.lineTo(fillW, cy + (sin(endAngle) * amplitude).toFloat())
                    }
                    drawPath(path = wavePath, color = primary, style = Stroke(width = stroke, cap = StrokeCap.Round))
                }
            }
            ProgressBarStyle.SEGMENTS -> {
                val segCount = if (isMiniStrip) 22 else 26
                val gap = 3.dp.toPx()
                val segW = ((w - gap * (segCount - 1)) / segCount).coerceAtLeast(2f)
                val baseActiveH = if (isMiniStrip) h else 13.dp.toPx()
                val baseIdleH = if (isMiniStrip) h * 0.6f else 5.dp.toPx()
                for (i in 0 until segCount) {
                    val left = i * (segW + gap)
                    val ratio = (i + 0.5f) / segCount
                    val active = ratio <= clamped
                    val waveScale = if (active && isPlaying && !isMiniStrip) {
                        0.68f + 0.52f * ((sin((i * 0.8 + phase * 2.0 * PI)) + 1.0) / 2.0).toFloat()
                    } else 1f
                    val barH = (if (active) baseActiveH * waveScale else baseIdleH).coerceIn(2f, h)
                    drawRoundRect(
                        color = if (active) primary else trackBg,
                        topLeft = Offset(left, cy - barH / 2f),
                        size = Size(segW, barH),
                        cornerRadius = CornerRadius(segW / 2f, segW / 2f)
                    )
                }
            }
            ProgressBarStyle.EQUALIZER -> {
                val barCount = if (isMiniStrip) 26 else EQUALIZER_PROFILE.size
                val gap = if (isMiniStrip) 2.dp.toPx() else 2.2.dp.toPx()
                val barW = ((w - gap * (barCount - 1)) / barCount).coerceAtLeast(1.8f)
                for (i in 0 until barCount) {
                    val left = i * (barW + gap)
                    val ratio = (i + 0.5f) / barCount
                    val active = ratio <= clamped
                    val profile = EQUALIZER_PROFILE[i % EQUALIZER_PROFILE.size]
                    val mod = if (active && isPlaying && !isMiniStrip) {
                        0.55f + 0.55f * ((sin(i * 0.9 + phase * 2.0 * PI) + 1.0) / 2.0).toFloat()
                    } else 1f
                    val targetH = if (isMiniStrip) {
                        if (active) h * (0.5f + 0.5f * profile) else h * 0.45f
                    } else {
                        if (active) (6.dp.toPx() + profile * 14.dp.toPx()) * mod else 4.dp.toPx() + profile * 5.dp.toPx()
                    }
                    val barH = targetH.coerceIn(2.5f, h)
                    val barColor = when {
                        !active -> trackBg
                        i % 3 == 0 -> tertiary
                        else -> primary
                    }
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(left, cy - barH / 2f),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
            ProgressBarStyle.NEON -> {
                val stroke = if (isMiniStrip) h else 6.dp.toPx()
                drawLine(trackBg, Offset(0f, cy), Offset(w, cy), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
                if (fillW > 0f) {
                    if (!isMiniStrip && isPlaying) {
                        drawLine(
                            color = tertiary.copy(alpha = 0.34f),
                            start = Offset(0f, cy),
                            end = Offset(fillW, cy),
                            strokeWidth = stroke * 2.4f,
                            cap = StrokeCap.Round
                        )
                    }
                    drawLine(
                        brush = Brush.horizontalGradient(
                            neonColors,
                            endX = fillW.coerceAtLeast(1f)
                        ),
                        start = Offset(0f, cy),
                        end = Offset(fillW, cy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    // Bright inner white core
                    drawLine(
                        color = Color.White.copy(alpha = 0.6f),
                        start = Offset(0f, cy),
                        end = Offset(fillW, cy),
                        strokeWidth = (stroke * 0.34f).coerceAtLeast(1.2f),
                        cap = StrokeCap.Round
                    )
                    if (isPlaying) {
                        val cometX = fillW * phase
                        drawLine(
                            brush = Brush.horizontalGradient(
                                cometColors,
                                startX = (cometX - 24f).coerceAtLeast(0f),
                                endX = (cometX + 24f).coerceAtMost(fillW)
                            ),
                            start = Offset((cometX - 24f).coerceAtLeast(0f), cy),
                            end = Offset((cometX + 24f).coerceAtMost(fillW), cy),
                            strokeWidth = stroke * 0.9f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            ProgressBarStyle.DOTS -> {
                val dotCount = if (isMiniStrip) 20 else 24
                val stepX = if (dotCount > 1) w / (dotCount - 1) else w
                val activeBaseR = if (isMiniStrip) (h * 0.45f).coerceAtLeast(1.8f) else 3.2.dp.toPx()
                val idleR = if (isMiniStrip) (h * 0.32f).coerceAtLeast(1.2f) else 2.1.dp.toPx()
                for (i in 0 until dotCount) {
                    val cx = (i * stepX).coerceIn(activeBaseR, (w - activeBaseR).coerceAtLeast(activeBaseR))
                    val ratio = (i + 0.5f) / dotCount
                    val active = ratio <= clamped
                    val pulse = if (active && isPlaying && !isMiniStrip) {
                        0.72f + 0.52f * ((sin(i * 0.75 + phase * 2.0 * PI) + 1.0) / 2.0).toFloat()
                    } else 1f
                    drawCircle(
                        color = if (active) primary else trackBg,
                        radius = (if (active) activeBaseR * pulse else idleR).coerceAtMost(h / 2f),
                        center = Offset(cx, cy)
                    )
                }
            }
        }

        if (showThumb && !isMiniStrip) {
            val thumbColor = if (style == ProgressBarStyle.NEON) tertiary else primary
            val isPillThumb = style == ProgressBarStyle.WAVE ||
                style == ProgressBarStyle.SEGMENTS ||
                style == ProgressBarStyle.EQUALIZER
            val thumbCenterX = fillW.coerceIn(7.dp.toPx(), (w - 7.dp.toPx()).coerceAtLeast(7.dp.toPx()))
            if ((style == ProgressBarStyle.GLOW || style == ProgressBarStyle.NEON) && isPlaying) {
                drawCircle(
                    color = thumbColor.copy(alpha = 0.25f * (1f - phase * 0.5f)),
                    radius = 12.dp.toPx(),
                    center = Offset(thumbCenterX, cy)
                )
            }
            if (isPillThumb) {
                val pillW = 6.dp.toPx()
                val pillH = 20.dp.toPx().coerceAtMost(h)
                drawRoundRect(
                    color = surfaceColor,
                    topLeft = Offset(thumbCenterX - pillW / 2f - 2f, cy - pillH / 2f - 2f),
                    size = Size(pillW + 4f, pillH + 4f),
                    cornerRadius = CornerRadius(pillW, pillW)
                )
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(thumbCenterX - pillW / 2f, cy - pillH / 2f),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(pillW / 2f, pillW / 2f)
                )
            } else {
                drawCircle(
                    color = surfaceColor,
                    radius = 9.dp.toPx(),
                    center = Offset(thumbCenterX, cy)
                )
                drawCircle(
                    color = thumbColor,
                    radius = 7.dp.toPx(),
                    center = Offset(thumbCenterX, cy)
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerProgressStrip(
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    progressBarStyle: ProgressBarStyle,
    isPlaying: Boolean
) {
    val positionMs by positionMsFlow.collectAsState()
    val durationMs by durationMsFlow.collectAsState()
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    AnimatedProgressBarCanvas(
        progress = progress,
        style = progressBarStyle,
        isPlaying = isPlaying,
        modifier = Modifier.fillMaxWidth().height(3.dp)
    )
}

@Composable
private fun MiniPlayerSubtitle(
    authorLabel: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMsFlow: StateFlow<Long>
) {
    val positionMs by positionMsFlow.collectAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isPlaying && !isBuffering) {
            PlayingEqualizerBars(isPlaying = true)
        }
        Text(
            text = if (isBuffering) {
                "Буферизация аудио..."
            } else {
                "$authorLabel · ${formatPlayerTime(positionMs)}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MiniPlayerBar(
    track: AudioTrack,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    progressBarStyle: ProgressBarStyle = ProgressBarStyle.WAVE,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {}
) {
    val gradientStart = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val gradientEnd = MaterialTheme.colorScheme.surfaceContainerHigh
    val bgBrush = remember(gradientStart, gradientEnd) {
        Brush.linearGradient(colors = listOf(gradientStart, gradientEnd))
    }

    val latestOnExpand by rememberUpdatedState(onExpand)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
        shadowElevation = 8.dp,
        tonalElevation = 5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                var dragDx = 0f
                var dragDy = 0f
                detectDragGestures(
                    onDragStart = {
                        dragDx = 0f
                        dragDy = 0f
                    },
                    onDrag = { change, dragAmount ->
                        dragDx += dragAmount.x
                        dragDy += dragAmount.y
                        if (abs(dragDx) > 18f || abs(dragDy) > 18f) {
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (abs(dragDy) > abs(dragDx) && dragDy < -36f) {
                            latestOnExpand()
                        } else if (abs(dragDx) > abs(dragDy)) {
                            if (dragDx < -72f) {
                                latestOnNext()
                            } else if (dragDx > 72f) {
                                latestOnPrevious()
                            }
                        }
                    }
                )
            }
    ) {
        Column(modifier = Modifier.background(bgBrush)) {
            MiniPlayerProgressStrip(
                positionMsFlow = positionMsFlow,
                durationMsFlow = durationMsFlow,
                progressBarStyle = progressBarStyle,
                isPlaying = isPlaying
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                AnimatedContent(
                    targetState = track,
                    contentKey = { it.trackId },
                    transitionSpec = { BubbleMotion.bubbleContentTransform() },
                    label = "mini_cover_swap"
                ) { activeTrack ->
                    CoverArtworkBox(
                        coverPath = activeTrack.coverPath,
                        contentDescription = activeTrack.title,
                        size = 44.dp,
                        cornerRadius = 12.dp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val authorLabel = remember(track.author, track.channelTitle) {
                        track.author.ifBlank { track.channelTitle }
                    }
                    MiniPlayerSubtitle(
                        authorLabel = authorLabel,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        positionMsFlow = positionMsFlow
                    )
                }
                IconButton(onClick = onTogglePlayPause) {
                    if (isBuffering) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = { BubbleMotion.iconPopTransform() },
                            label = "mini_play_icon"
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}

@Composable
private fun FullPlayerSeekSection(
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    progressBarStyle: ProgressBarStyle,
    isPlaying: Boolean,
    seekStepSec: Int = 15,
    onSeekTo: (Long) -> Unit,
    onSkip15: (Int) -> Unit
) {
    val positionMs by positionMsFlow.collectAsState()
    val durationMs by durationMsFlow.collectAsState()
    val maxDur = durationMs.coerceAtLeast(1L).toFloat()
    val normProgress = (positionMs.toFloat() / maxDur).coerceIn(0f, 1f)

    AnimatedProgressBarCanvas(
        progress = normProgress,
        style = progressBarStyle,
        isPlaying = isPlaying,
        interactive = true,
        onSeekProgress = { p -> onSeekTo((p * maxDur).toLong()) },
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = formatPlayerTime(positionMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { onSkip15(-seekStepSec) }
            ) {
                Text(
                    text = "-$seekStepSec",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { onSkip15(seekStepSec) }
            ) {
                Text(
                    text = "+$seekStepSec",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        val remMs = (durationMs - positionMs).coerceAtLeast(0L)
        Text(
            text = if (durationMs > 0) "-${formatPlayerTime(remMs)}" else "0:00",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullScreenPlayerModal(
    track: AudioTrack,
    isPlaying: Boolean,
    isBuffering: Boolean = false,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    playbackSpeed: Float,
    progressBarStyle: ProgressBarStyle = ProgressBarStyle.WAVE,
    isShuffle: Boolean = false,
    repeatMode: Boolean = false,
    sleepTimer: SleepTimerState,
    isFavorite: Boolean,
    isCompleted: Boolean = false,
    isDisliked: Boolean = false,
    isOffline: Boolean = false,
    downloadProgress: Float? = null,
    dspPreset: DspPreset = DspPreset.STANDARD,
    skipSilence: Boolean = false,
    seekStepSec: Int = 15,
    embeddedChapters: List<AudioChapter> = emptyList(),
    bookmarks: List<AudioBookmark> = emptyList(),
    queue: List<AudioTrack>,
    tracklist: List<AudioTrack>,
    isTracklistMode: Boolean,
    playlists: List<UserPlaylist>,
    channels: List<TelegramChannel> = emptyList(),
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkip15: (Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onToggleCompleted: () -> Unit = {},
    onToggleDislike: (() -> Unit)? = null,
    onDownloadOffline: () -> Unit = {},
    onRemoveOffline: () -> Unit = {},
    onSelectSpeed: (Float) -> Unit,
    onSelectDspPreset: (DspPreset) -> Unit = {},
    onToggleSkipSilence: (Boolean) -> Unit = {},
    onSelectSleepTimer: (Int?) -> Unit,
    onAddBookmark: (Long, String) -> Unit = { _, _ -> },
    onDeleteBookmark: (String) -> Unit = {},
    onAddToPlaylist: (String, AudioTrack) -> Unit,
    onCreatePlaylistAndAdd: (String, AudioTrack) -> Unit = { _, _ -> },
    onPlayFromQueue: (AudioTrack) -> Unit,
    onPlayFromTracklist: (AudioTrack) -> Unit = onPlayFromQueue,
    onLongPressAddToTracklist: (AudioTrack) -> Unit,
    onRemoveFromTracklist: (String) -> Unit,
    onMoveTracklist: (Int, Int) -> Unit,
    onToggleTracklistMode: (Boolean) -> Unit,
    onOpenChannel: (String) -> Unit = {},
    onOpenTelegramChat: (channelId: String, title: String, username: String, focusMessageId: Long) -> Unit = { _, _, _, _ -> },
    onRequestCover: (AudioTrack) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    var showMenu by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showChaptersBookmarksDialog by remember { mutableStateOf(false) }
    var showDspDialog by remember { mutableStateOf(false) }
    var bookmarkNoteInput by remember { mutableStateOf("") }
    var newPlaylistName by remember { mutableStateOf("") }

    var queueTab by remember { mutableIntStateOf(0) }
    var draggedTrackId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    val latestTracklist by rememberUpdatedState(tracklist)

    val chapters = remember(track.trackId, track.description, track.durationSec, embeddedChapters) {
        val parsed = RecommendationEngine.extractBookChapters(track.description, track.durationSec)
        (embeddedChapters + parsed)
            .distinctBy { it.startSec }
            .sortedBy { it.startSec }
    }
    val trackBookmarks = remember(bookmarks, track.trackId) {
        bookmarks.filter { it.trackId == track.trackId }
    }

    val tracklistIds = remember(tracklist) { tracklist.map { it.trackId }.toSet() }
    val isInTracklist = track.trackId in tracklistIds

    BackHandler(enabled = true) {
        if (pagerState.currentPage == 1) {
            scope.launch { pagerState.animateScrollToPage(0) }
        } else {
            onCollapse()
        }
    }

    val latestOnCollapse by rememberUpdatedState(onCollapse)
    val sheetOffsetY = remember { Animatable(0f) }
    val bgTop = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
    val bgMid = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f)
    val bgBot = MaterialTheme.colorScheme.surface
    val bgBrush = remember(bgTop, bgMid, bgBot) {
        Brush.verticalGradient(colors = listOf(bgTop, bgMid, bgBot))
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val y = sheetOffsetY.value
                translationY = y
                val s = (1f - (y / 2400f)).coerceIn(0.92f, 1f)
                scaleX = s
                scaleY = s
            }
            .pointerInput(pagerState.currentPage) {
                if (pagerState.currentPage == 0) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            scope.launch {
                                sheetOffsetY.snapTo((sheetOffsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        },
                        onDragEnd = {
                            val current = sheetOffsetY.value
                            scope.launch {
                                if (current > 180f) {
                                    latestOnCollapse()
                                    sheetOffsetY.snapTo(0f)
                                } else {
                                    sheetOffsetY.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                sheetOffsetY.animateTo(
                                    0f,
                                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                                )
                            }
                        }
                    )
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 2.dp, bottom = 6.dp)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f))
                )
                // Header matching Web FullPlayer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = {
                        if (pagerState.currentPage == 1) {
                            scope.launch { pagerState.animateScrollToPage(0) }
                        } else {
                            onCollapse()
                        }
                    }) {
                        Icon(
                            imageVector = if (pagerState.currentPage == 1) {
                                Icons.AutoMirrored.Filled.ArrowBack
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = if (pagerState.currentPage == 1) "К плееру" else "Свернуть"
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = when {
                                pagerState.currentPage == 0 -> "Из канала"
                                queueTab == 1 -> "Выбранные главы"
                                else -> "Следующие треки"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when {
                                pagerState.currentPage == 0 -> track.channelTitle
                                queueTab == 1 -> "Треклист"
                                else -> "Очередь воспроизведения"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val sleepActiveInMenu = sleepTimer !is SleepTimerState.Off
                    val sleepStatusText = when (sleepTimer) {
                        is SleepTimerState.Off -> null
                        is SleepTimerState.Countdown -> "Осталось ${((sleepTimer.remainingSec + 59) / 60).coerceAtLeast(1)} мин"
                        is SleepTimerState.EndOfTrack -> "В конце текущей главы"
                    }
                    val trackTelegramLink = remember(track.trackId, track.channelId, track.channelUsername, track.messageId, channels) {
                        TelegramDeepLinks.buildTrackLinks(track, channels)
                    }
                    val resolvedChatUsername = remember(track.channelId, track.channelUsername, channels) {
                        track.channelUsername.ifBlank {
                            channels.firstOrNull { it.channelId == track.channelId }?.username.orEmpty()
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pagerState.currentPage == 0 && track.channelId.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    onOpenTelegramChat(
                                        track.channelId,
                                        track.channelTitle,
                                        resolvedChatUsername,
                                        track.messageId
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Открыть чат Telegram",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Меню",
                                    tint = if (sleepActiveInMenu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                                text = { Text("Добавить в плейлист") },
                                onClick = {
                                    showMenu = false
                                    showPlaylistDialog = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(if (isInTracklist) Icons.Default.Check else Icons.Default.Add, contentDescription = null) },
                                text = { Text(if (isInTracklist) "Убрать из Треклиста" else "Добавить в Треклист") },
                                onClick = {
                                    showMenu = false
                                    if (isInTracklist) {
                                        onRemoveFromTracklist(track.trackId)
                                    } else {
                                        onLongPressAddToTracklist(track)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null) },
                                text = { Text("Открыть Треклист (${tracklist.size})") },
                                onClick = {
                                    showMenu = false
                                    queueTab = 1
                                    scope.launch { pagerState.animateScrollToPage(1) }
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Bedtime,
                                        contentDescription = null,
                                        tint = if (sleepActiveInMenu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = {
                                    Column {
                                        Text("Таймер сна")
                                        if (sleepStatusText != null) {
                                            Text(
                                                text = sleepStatusText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    showSleepDialog = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = if (dspPreset != DspPreset.STANDARD || skipSilence) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = {
                                    Column {
                                        Text("Аудио DSP и тишина")
                                        if (dspPreset != DspPreset.STANDARD || skipSilence) {
                                            Text(
                                                text = buildString {
                                                    append(dspPreset.label)
                                                    if (skipSilence) append(" · Пропуск пауз")
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    showDspDialog = true
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                text = { Text(if (isCompleted) "Снять отметку «Прослушано»" else "Отметить «Прослушано»") },
                                onClick = {
                                    showMenu = false
                                    onToggleCompleted()
                                }
                            )
                            if (onToggleDislike != null) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.ThumbDown,
                                            contentDescription = null,
                                            tint = if (isDisliked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    text = { Text(if (isDisliked) "Вернуть в рекомендации" else "Не интересно") },
                                    onClick = {
                                        showMenu = false
                                        onToggleDislike()
                                    }
                                )
                            }
                            if (track.channelId.isNotBlank()) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Radio, contentDescription = null) },
                                    text = { Text("Перейти к каналу") },
                                    onClick = {
                                        showMenu = false
                                        onCollapse()
                                        onOpenChannel(track.channelId)
                                    }
                                )
                            }
                            if (track.channelId.isNotBlank()) {
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    text = { Text("Открыть чат Telegram") },
                                    onClick = {
                                        showMenu = false
                                        onOpenTelegramChat(
                                            track.channelId,
                                            track.channelTitle,
                                            resolvedChatUsername,
                                            track.messageId
                                        )
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                text = { Text("Поделиться") },
                                onClick = {
                                    showMenu = false
                                    val postUrl = trackTelegramLink?.webUrl
                                    val shareBody = buildString {
                                        append("${track.title} — ${track.author}")
                                        if (!postUrl.isNullOrBlank()) {
                                            append("\n")
                                            append(postUrl)
                                        }
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareBody)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Поделиться"))
                                }
                            )
                        }
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = (pagerState.currentPage == 0 || draggedTrackId == null),
                    modifier = Modifier.weight(1f)
                ) { page ->
                    if (page == 0) {
                        Column(
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            BoxWithConstraints(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                val coverSize = minOf(maxWidth, maxHeight, 368.dp)
                                AnimatedContent(
                                    targetState = track,
                                    contentKey = { it.trackId },
                                    transitionSpec = {
                                        (scaleIn(initialScale = 0.94f, animationSpec = tween(220)) + fadeIn(tween(220)))
                                            .togetherWith(scaleOut(targetScale = 0.94f, animationSpec = tween(180)) + fadeOut(tween(180)))
                                    },
                                    label = "cover_crossfade"
                                ) { activeTrack ->
                                    val coverModel = remember(activeTrack.coverPath, context) {
                                        activeTrack.coverPath?.takeIf { it.isNotBlank() }?.let { path ->
                                            val data: Any = if (path.startsWith("/")) File(path) else path
                                            ImageRequest.Builder(context)
                                                .data(data)
                                                .size(720)
                                                .crossfade(160)
                                                .build()
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(28.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                                        shadowElevation = 14.dp,
                                        modifier = Modifier.size(coverSize)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        if (coverModel != null) {
                                            AsyncImage(
                                                model = coverModel,
                                                contentDescription = activeTrack.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.LibraryMusic,
                                                contentDescription = activeTrack.title,
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                                                modifier = Modifier.size(96.dp)
                                            )
                                        }
                                        }
                                    }
                                }
                            }

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.headlineSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isCompleted) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Прослушано",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${track.author} · ${track.channelTitle}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (track.recommendationReason.isNotBlank()) {
                                            Text(
                                                text = track.recommendationReason,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        if (isTracklistMode && tracklist.isNotEmpty()) {
                                            Text(
                                                text = "Режим Треклиста · ${tracklist.size} глав",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .clickable {
                                                        queueTab = 1
                                                        scope.launch { pagerState.animateScrollToPage(1) }
                                                    }
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            if (isOffline) onRemoveOffline() else if (downloadProgress == null) onDownloadOffline()
                                        }
                                    ) {
                                        if (downloadProgress != null) {
                                            CircularProgressIndicator(
                                                progress = { downloadProgress.coerceIn(0f, 1f) },
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isOffline) Icons.Default.DownloadDone else Icons.Default.Download,
                                                contentDescription = if (isOffline) "Удалить из Скачанного" else "Скачать офлайн",
                                                tint = if (isOffline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    val favScale by animateFloatAsState(
                                        targetValue = if (isFavorite) 1.15f else 1.0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                        label = "fav_pop"
                                    )
                                    IconButton(onClick = onToggleFavorite) {
                                        Icon(
                                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Избранное",
                                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.graphicsLayer {
                                                scaleX = favScale
                                                scaleY = favScale
                                            }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                FullPlayerSeekSection(
                                    positionMsFlow = positionMsFlow,
                                    durationMsFlow = durationMsFlow,
                                    progressBarStyle = progressBarStyle,
                                    isPlaying = isPlaying,
                                    seekStepSec = seekStepSec,
                                    onSeekTo = onSeekTo,
                                    onSkip15 = onSkip15
                                )

                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                ) {
                                    IconButton(onClick = onToggleShuffle) {
                                        Icon(
                                            Icons.Default.Shuffle,
                                            contentDescription = "Перемешать",
                                            tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val prevInteraction = remember { MutableInteractionSource() }
                                    FilledTonalIconButton(
                                        onClick = onPrevious,
                                        interactionSource = prevInteraction,
                                        shape = CircleShape,
                                        modifier = Modifier.size(64.dp).bubblePressEffect(prevInteraction, 0.88f)
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущий", modifier = Modifier.size(30.dp))
                                    }
                                    val playBtnCorner by animateDpAsState(
                                        targetValue = if (isPlaying) 26.dp else 42.dp,
                                        animationSpec = BubbleMotion.bouncySpring(),
                                        label = "play_shape_morph"
                                    )
                                    val playInteraction = remember { MutableInteractionSource() }
                                    FilledIconButton(
                                        onClick = onTogglePlayPause,
                                        interactionSource = playInteraction,
                                        shape = RoundedCornerShape(playBtnCorner),
                                        modifier = Modifier.size(84.dp).bubblePressEffect(playInteraction, 0.90f)
                                    ) {
                                        if (isBuffering) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(34.dp),
                                                strokeWidth = 3.dp
                                            )
                                        } else {
                                            AnimatedContent(
                                                targetState = isPlaying,
                                                transitionSpec = { BubbleMotion.iconPopTransform() },
                                                label = "full_play_icon"
                                            ) { playing ->
                                                Icon(
                                                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    modifier = Modifier.size(38.dp)
                                                )
                                            }
                                        }
                                    }
                                    val nextInteraction = remember { MutableInteractionSource() }
                                    FilledTonalIconButton(
                                        onClick = onNext,
                                        interactionSource = nextInteraction,
                                        shape = CircleShape,
                                        modifier = Modifier.size(64.dp).bubblePressEffect(nextInteraction, 0.88f)
                                    ) {
                                        Icon(Icons.Default.SkipNext, contentDescription = "Следующий", modifier = Modifier.size(30.dp))
                                    }
                                    IconButton(onClick = onToggleRepeat) {
                                        Icon(
                                            Icons.Default.Repeat,
                                            contentDescription = "Повтор",
                                            tint = if (repeatMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Bottom dock: Speed, Bookmarks/Chapters, Queue, Tracklist (Sleep timer moved to 3-dots menu)
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = 4.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val hasBookmarks = trackBookmarks.isNotEmpty() || chapters.isNotEmpty()
                                    val tracklistActive = isTracklistMode && tracklist.isNotEmpty()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        BottomDockActionButton(
                                            icon = Icons.Default.Speed,
                                            label = if (playbackSpeed != 1.0f) "${playbackSpeed}x" else "Скорость",
                                            active = playbackSpeed != 1.0f,
                                            onClick = { showSpeedDialog = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                        BottomDockActionButton(
                                            icon = if (hasBookmarks) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                            label = if (hasBookmarks) "Главы (${chapters.size + trackBookmarks.size})" else "Закладки",
                                            active = hasBookmarks,
                                            onClick = { showChaptersBookmarksDialog = true },
                                            modifier = Modifier.weight(1f)
                                        )
                                        BottomDockActionButton(
                                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                                            label = if (queue.isNotEmpty()) "Очередь (${queue.size})" else "Очередь",
                                            active = !tracklistActive,
                                            onClick = {
                                                queueTab = 0
                                                scope.launch { pagerState.animateScrollToPage(1) }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        BottomDockActionButton(
                                            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                            label = if (tracklist.isNotEmpty()) "Треклист (${tracklist.size})" else "Треклист",
                                            active = tracklistActive,
                                            onClick = {
                                                queueTab = 1
                                                scope.launch { pagerState.animateScrollToPage(1) }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Page 1: Segmented Pill Queue & Drag-and-Drop Tracklist matching Web FullPlayer
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 6.dp)
                        ) {
                            // M3 Rounded Segmented Pill Switcher
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    listOf(
                                        0 to "Очередь (${queue.size})",
                                        1 to "Треклист (${tracklist.size})"
                                    ).forEach { (tabIndex, label) ->
                                        val active = queueTab == tabIndex
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { queueTab = tabIndex }
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(vertical = 11.dp, horizontal = 8.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            if (queueTab == 0) {
                                Text(
                                    text = "Нажмите «+» или удерживайте трек, чтобы добавить его в Треклист и слушать только выбранные главы.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                                Spacer(Modifier.height(4.dp))

                                if (queue.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Очередь пока пуста. Включите любую аудиокнигу.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(24.dp)
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        contentPadding = PaddingValues(bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        itemsIndexed(queue, key = { _, item -> item.trackId }) { _, item ->
                                            val isActiveItem = item.trackId == track.trackId
                                            val inTracklist = item.trackId in tracklistIds

                                            LaunchedEffect(item.trackId, item.coverPath) {
                                                if (item.coverPath.isNullOrBlank()) {
                                                    onRequestCover(item)
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(18.dp),
                                                color = if (isActiveItem) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceContainer
                                                },
                                                contentColor = if (isActiveItem) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .combinedClickable(
                                                        onClick = {
                                                            onToggleTracklistMode(false)
                                                            onPlayFromQueue(item)
                                                        },
                                                        onLongClick = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            if (!inTracklist) {
                                                                onLongPressAddToTracklist(item)
                                                            }
                                                        }
                                                    )
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                                ) {
                                                    CoverArtworkBox(
                                                        coverPath = item.coverPath,
                                                        contentDescription = item.title,
                                                        size = 44.dp,
                                                        cornerRadius = 12.dp
                                                    )
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = item.title,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = item.author.ifBlank { item.channelTitle },
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = if (isActiveItem) {
                                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                                },
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f, fill = false)
                                                            )
                                                            if (inTracklist) {
                                                                Text(
                                                                    text = " · В треклисте",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            if (inTracklist) {
                                                                onRemoveFromTracklist(item.trackId)
                                                            } else {
                                                                onLongPressAddToTracklist(item)
                                                            }
                                                        },
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (inTracklist) Icons.Default.Check else Icons.Default.Add,
                                                            contentDescription = if (inTracklist) "В треклисте" else "Добавить в Треклист",
                                                            tint = if (inTracklist) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else if (isActiveItem) {
                                                                MaterialTheme.colorScheme.onPrimaryContainer
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            },
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Tracklist Tab with Pill Mode Button + Swipe/Drag Handle Reordering
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Перетаскивайте за ручку слева, чтобы менять порядок.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    if (tracklist.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (isTracklistMode) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                            contentColor = if (isTracklistMode) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { onToggleTracklistMode(!isTracklistMode) }
                                        ) {
                                            Text(
                                                text = if (isTracklistMode) "Играет Треклист" else "Включить Треклист",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                if (tracklist.isEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Треклист пуст. Перейдите в «Очередь» и нажмите «+» или зажмите нужный трек на полсекунды.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(24.dp)
                                        )
                                    }
                                } else {
                                    val spacingPx = with(density) { 8.dp.toPx() }
                                    LazyColumn(
                                        contentPadding = PaddingValues(bottom = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        userScrollEnabled = draggedTrackId == null,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        itemsIndexed(tracklist, key = { _, item -> item.trackId }) { _, item ->
                                            val isDragged = draggedTrackId == item.trackId
                                            val isActiveItem = item.trackId == track.trackId

                                            LaunchedEffect(item.trackId, item.coverPath) {
                                                if (item.coverPath.isNullOrBlank()) {
                                                    onRequestCover(item)
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(18.dp),
                                                shadowElevation = if (isDragged) 8.dp else 0.dp,
                                                color = when {
                                                    isActiveItem -> MaterialTheme.colorScheme.primaryContainer
                                                    isDragged -> MaterialTheme.colorScheme.surfaceContainerHighest
                                                    else -> MaterialTheme.colorScheme.surfaceContainer
                                                },
                                                contentColor = if (isActiveItem) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .animateItem()
                                                    .onSizeChanged { itemHeights[item.trackId] = it.height }
                                                    .zIndex(if (isDragged) 2f else 0f)
                                                    .graphicsLayer {
                                                        translationY = if (isDragged) dragOffsetY else 0f
                                                        scaleX = if (isDragged) 1.02f else 1f
                                                        scaleY = if (isDragged) 1.02f else 1f
                                                    }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 6.dp, vertical = 8.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier
                                                            .size(40.dp)
                                                            .pointerInput(item.trackId) {
                                                                detectVerticalDragGestures(
                                                                    onDragStart = {
                                                                        draggedTrackId = item.trackId
                                                                        dragOffsetY = 0f
                                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    },
                                                                    onVerticalDrag = { change, dragAmount ->
                                                                        change.consume()
                                                                        dragOffsetY += dragAmount
                                                                        val listNow = latestTracklist
                                                                        var curIdx = listNow.indexOfFirst { it.trackId == item.trackId }
                                                                        if (curIdx == -1) return@detectVerticalDragGestures
                                                                        val step = ((itemHeights[item.trackId] ?: 150) + spacingPx).coerceAtLeast(80f)
                                                                        while (dragOffsetY > step * 0.55f && curIdx < listNow.lastIndex) {
                                                                            onMoveTracklist(curIdx, curIdx + 1)
                                                                            dragOffsetY -= step
                                                                            curIdx++
                                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        }
                                                                        while (dragOffsetY < -step * 0.55f && curIdx > 0) {
                                                                            onMoveTracklist(curIdx, curIdx - 1)
                                                                            dragOffsetY += step
                                                                            curIdx--
                                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                        }
                                                                    },
                                                                    onDragEnd = {
                                                                        draggedTrackId = null
                                                                        dragOffsetY = 0f
                                                                    },
                                                                    onDragCancel = {
                                                                        draggedTrackId = null
                                                                        dragOffsetY = 0f
                                                                    }
                                                                )
                                                            }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.DragHandle,
                                                            contentDescription = "Перетащить",
                                                            tint = (if (isActiveItem) {
                                                                MaterialTheme.colorScheme.onPrimaryContainer
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            }).copy(alpha = 0.72f),
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable {
                                                                onToggleTracklistMode(true)
                                                                onPlayFromTracklist(item)
                                                            }
                                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        CoverArtworkBox(
                                                            coverPath = item.coverPath,
                                                            contentDescription = item.title,
                                                            size = 44.dp,
                                                            cornerRadius = 12.dp
                                                        )
                                                        Spacer(Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = item.title,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = item.author.ifBlank { item.channelTitle },
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = if (isActiveItem) {
                                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                                } else {
                                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                                },
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }

                                                    IconButton(
                                                        onClick = { onRemoveFromTracklist(item.trackId) },
                                                        modifier = Modifier.size(40.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Удалить",
                                                            tint = if (isActiveItem) {
                                                                MaterialTheme.colorScheme.onPrimaryContainer
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            },
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Скорость воспроизведения") },
            text = {
                Column {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                        TextButton(onClick = {
                            onSelectSpeed(speed)
                            showSpeedDialog = false
                        }) { Text("${speed}x") }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Таймер сна") },
            text = {
                Column {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        TextButton(onClick = {
                            onSelectSleepTimer(mins)
                            showSleepDialog = false
                        }) { Text("$mins минут") }
                    }
                    TextButton(onClick = {
                        onSelectSleepTimer(-1)
                        showSleepDialog = false
                    }) { Text("До конца главы") }
                    if (sleepTimer !is SleepTimerState.Off) {
                        TextButton(onClick = {
                            onSelectSleepTimer(null)
                            showSleepDialog = false
                        }) { Text("Отключить таймер") }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("Добавить в плейлист") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    playlists.forEach { pl ->
                        TextButton(onClick = {
                            onAddToPlaylist(pl.id, track)
                            showPlaylistDialog = false
                        }) { Text("${pl.name} (${pl.items.size})") }
                    }
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Новый плейлист") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = newPlaylistName.isNotBlank(),
                        onClick = {
                            onCreatePlaylistAndAdd(newPlaylistName, track)
                            newPlaylistName = ""
                            showPlaylistDialog = false
                        }
                    ) { Text("Создать и добавить") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) { Text("Закрыть") }
            }
        )
    }

    if (showDspDialog) {
        AlertDialog(
            onDismissRequest = { showDspDialog = false },
            title = { Text("Аудио DSP и пропуск пауз") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    DspPreset.entries.forEach { preset ->
                        val active = dspPreset == preset
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSelectDspPreset(preset) }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preset.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        preset.hint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (active) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Пропуск тишины", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Ускорять длинные паузы чтеца",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = skipSilence, onCheckedChange = onToggleSkipSilence)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDspDialog = false }) { Text("Готово") }
            }
        )
    }

    if (showChaptersBookmarksDialog) {
        val dialogPositionMs by positionMsFlow.collectAsState()
        AlertDialog(
            onDismissRequest = { showChaptersBookmarksDialog = false },
            title = { Text("Главы и закладки") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(340.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = bookmarkNoteInput,
                                onValueChange = { bookmarkNoteInput = it },
                                label = { Text("Заметка к закладке (необязательно)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FilledTonalButton(
                                onClick = {
                                    onAddBookmark((dialogPositionMs / 1000L).coerceAtLeast(0L), bookmarkNoteInput)
                                    bookmarkNoteInput = ""
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Поставить на ${formatPlayerTime(dialogPositionMs)}")
                            }
                        }
                    }
                    if (trackBookmarks.isNotEmpty()) {
                        item {
                            Text(
                                "Ваши закладки (${trackBookmarks.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        itemsIndexed(trackBookmarks, key = { _, bm -> bm.bookmarkId }) { _, bm ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSeekTo(bm.positionSec * 1000L)
                                        showChaptersBookmarksDialog = false
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = formatPlayerTime(bm.positionSec * 1000L),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (bm.note.isNotBlank()) {
                                            Text(
                                                text = bm.note,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(onClick = { onDeleteBookmark(bm.bookmarkId) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (chapters.isNotEmpty()) {
                        item {
                            Text(
                                "Главы (${chapters.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        itemsIndexed(chapters, key = { _, ch -> ch.id }) { _, ch ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSeekTo(ch.startSec * 1000L)
                                        showChaptersBookmarksDialog = false
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = ch.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Text(
                                        text = formatPlayerTime(ch.startSec * 1000L),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                "В описании поста не найдено таймкодов глав — используйте закладки выше.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChaptersBookmarksDialog = false }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
private fun BottomDockActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatPlayerTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0L) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%d:%02d", mins, secs)
    }
}
