package tech.thothlab.dombra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.RepeatMode
import tech.thothlab.dombra.presentation.player.PlayerState

/**
 * Полноэкранный плеер: seek-бар, prev/play-pause/next, shuffle/repeat.
 * Управление — текстовыми символами (без зависимости от icon-паков; заменить
 * на нормальные иконки — полировка).
 */
@Composable
fun PlayerScreen(graph: AppGraph, onBack: () -> Unit) {
    val state: PlayerState by graph.playback.state.collectAsState()
    val track = state.currentTrack

    var scrub by remember { mutableStateOf<Float?>(null) }

    Box(Modifier.fillMaxSize()) {
    CosmosBackground(CosmosScreen.Player)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("⌄  свернуть") }
        }

        Spacer(Modifier.height(24.dp))

        // Обложка-заглушка (артворк — полировка)
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", fontSize = 96.sp, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = track?.title ?: "нет трека",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track?.artistName ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(24.dp))

        val dur = (state.durationMs ?: 0L).toFloat()
        val progress = scrub ?: if (dur > 0f) state.positionMs.toFloat() / dur else 0f
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = { scrub = it },
            onValueChangeFinished = {
                scrub?.let { graph.playback.seekTo((it * dur).toLong()) }
                scrub = null
            },
            enabled = track != null && dur > 0f,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(state.positionMs), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(state.durationMs ?: 0L), style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { graph.playback.toggleShuffle() }) {
                Text(if (state.shuffled) "🔀 вкл" else "🔀", style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = { graph.playback.previous() }) {
                Text("⏮", style = MaterialTheme.typography.headlineSmall)
            }
            Button(
                onClick = { graph.playback.togglePlayPause() },
                enabled = track != null,
                shape = CircleShape,
                contentPadding = ButtonDefaults.ContentPadding,
                modifier = Modifier.size(72.dp),
            ) {
                Text(if (state.isPlaying) "⏸" else "▶", fontSize = 28.sp)
            }
            TextButton(onClick = { graph.playback.next() }) {
                Text("⏭", style = MaterialTheme.typography.headlineSmall)
            }
            TextButton(onClick = { graph.playback.cycleRepeatMode() }) {
                Text(
                    when (state.repeatMode) {
                        RepeatMode.OFF -> "🔁"
                        RepeatMode.ALL -> "🔁 всё"
                        RepeatMode.ONE -> "🔂 один"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        state.error?.let { err ->
            Spacer(Modifier.height(12.dp))
            Text("⚠ ${err.message}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
    }
}

internal fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
