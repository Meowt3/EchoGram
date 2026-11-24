package com.echogram.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.echogram.app.data.model.AudioChapter
import com.echogram.app.data.model.AudioTrack
import com.echogram.app.data.model.SleepTimerState
import com.echogram.app.ui.screens.formatDurationSec
import java.io.File
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MiniPlayerBar(
    track: AudioTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
    ) {
        Column {
            MiniProgressLine(positionMsFlow, durationMsFlow)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    val coverFile = track.coverPath?.let { File(it) }?.takeIf { it.exists() }
                    if (coverFile != null) {
                        AsyncImage(
                            model = coverFile,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.author.ifBlank { track.channelTitle }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onTogglePlayPause) {
                    if (isBuffering) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Следующая")
                }
            }
        }
    }
}

@Composable
private fun MiniProgressLine(
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>
) {
    val pos by positionMsFlow.collectAsState()
    val dur by durationMsFlow.collectAsState()
    val frac = if (dur > 0L) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(
        progress = { frac },
        modifier = Modifier.fillMaxWidth().height(2.5.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    track: AudioTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    playbackSpeed: Float,
    seekStepSec: Int,
    sleepTimer: SleepTimerState,
    chapters: List<AudioChapter>,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipSeconds: (Int) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onSelectSleepTimer: (Int) -> Unit,
    onAddBookmark: (Long, String) -> Unit
) {
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkNote by remember { mutableStateOf("") }
    var showSleepDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(210.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                val coverFile = track.coverPath?.let { File(it) }?.takeIf { it.exists() }
                if (coverFile != null) {
                    AsyncImage(
                        model = coverFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(210.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(80.dp)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.author.ifBlank { track.channelTitle }} · ${track.channelTitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Isolated leaf slider so full sheet doesn't recompose every 500ms
            PlayerSeekSection(
                positionMsFlow = positionMsFlow,
                durationMsFlow = durationMsFlow,
                onSeekTo = onSeekTo
            )

            // Main transport controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Предыдущая", modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { onSkipSeconds(-seekStepSec) }) {
                    Icon(Icons.Default.Replay10, contentDescription = "-${seekStepSec}с", modifier = Modifier.size(30.dp))
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(66.dp).clip(CircleShape).clickable(onClick = onTogglePlayPause)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isBuffering) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        } else {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = { onSkipSeconds(seekStepSec) }) {
                    Icon(Icons.Default.Forward10, contentDescription = "+${seekStepSec}с", modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Следующая", modifier = Modifier.size(30.dp))
                }
            }

            // Speed chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { sp ->
                    FilterChip(
                        selected = kotlin.math.abs(playbackSpeed - sp) < 0.05f,
                        onClick = { onSelectSpeed(sp) },
                        label = { Text("${sp}x") }
                    )
                }
            }

            // Sleep timer & bookmark buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = { showSleepDialog = true }) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    val label = when (sleepTimer) {
                        is SleepTimerState.Off -> "Таймер сна"
                        is SleepTimerState.EndOfTrack -> "До конца трека"
                        is SleepTimerState.Countdown -> "Сон: ${formatDurationSec(sleepTimer.remainingSec.toLong())}"
                    }
                    Text(label)
                }
                TextButton(onClick = { showBookmarkDialog = true }) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Закладка")
                }
            }

            if (chapters.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Главы (${chapters.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    chapters.forEach { ch ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSeekTo(ch.startSec * 1000L) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(ch.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(formatDurationSec(ch.startSec), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showBookmarkDialog) {
        val posSec = positionMsFlow.collectAsState().value / 1000L
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text("Новая закладка (${formatDurationSec(posSec)})") },
            text = {
                OutlinedTextField(
                    value = bookmarkNote,
                    onValueChange = { bookmarkNote = it },
                    label = { Text("Заметка") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddBookmark(posSec, bookmarkNote)
                    bookmarkNote = ""
                    showBookmarkDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Таймер сна") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Выключить", 15 to "15 минут", 30 to "30 минут", 45 to "45 минут", 60 to "60 минут", -1 to "До конца трека").forEach { (min, label) ->
                        TextButton(
                            onClick = { onSelectSleepTimer(min); showSleepDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepDialog = false }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
private fun PlayerSeekSection(
    positionMsFlow: StateFlow<Long>,
    durationMsFlow: StateFlow<Long>,
    onSeekTo: (Long) -> Unit
) {
    val pos by positionMsFlow.collectAsState()
    val dur by durationMsFlow.collectAsState()
    val safeDur = dur.coerceAtLeast(1L)
    var draggingValue by remember { mutableStateOf<Float?>(null) }
    val shownMs = draggingValue?.toLong() ?: pos

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = (shownMs.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f),
            onValueChange = { frac -> draggingValue = frac * safeDur },
            onValueChangeFinished = {
                draggingValue?.let { onSeekTo(it.toLong()) }
                draggingValue = null
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDurationSec(shownMs / 1000L), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDurationSec(dur / 1000L), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
