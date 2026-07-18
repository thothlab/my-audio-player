package tech.thothlab.dombra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.RepeatMode
import tech.thothlab.dombra.presentation.player.PlayerState
import tech.thothlab.dombra.theme.LocalAccentColor

/**
 * Полноэкранный плеер в облике Cosmos (`Views/Player/PlayerViews.swift`):
 * крупная обложка со скруглением/тенью, title/artist, тонкий accent seek-бар,
 * контролы в матовом скруглённом контейнере (иконки монохромные, accent на активных).
 */
@Composable
fun PlayerScreen(graph: AppGraph, onBack: () -> Unit) {
    val state: PlayerState by graph.playback.state.collectAsState()
    val track = state.currentTrack
    val accent = LocalAccentColor.current

    var scrub by remember { mutableStateOf<Float?>(null) }
    var artDrag by remember { mutableStateOf(0f) }
    val dur = (state.durationMs ?: 0L).toFloat()
    val fraction = (scrub ?: if (dur > 0f) state.positionMs.toFloat() / dur else 0f).coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Player)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "свернуть")
                }
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.weight(1f))

            // Свайп обложки влево/вправо → смена трека (как в Cosmos).
            ArtworkImage(
                artwork = graph.artwork,
                stableId = track?.stableId,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f)
                    .offset { IntOffset(artDrag.roundToInt(), 0) }
                    .pointerInput(track?.stableId) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    artDrag <= -SWIPE_THRESHOLD -> graph.playback.next()
                                    artDrag >= SWIPE_THRESHOLD -> graph.playback.previous()
                                }
                                artDrag = 0f
                            },
                            onDragCancel = { artDrag = 0f },
                        ) { _, delta -> artDrag += delta }
                    }
                    .shadow(12.dp, RoundedCornerShape(12.dp)),
                iconScale = 0.22f,
            )

            Spacer(Modifier.height(28.dp))

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

            Spacer(Modifier.height(20.dp))

            SeekBar(
                fraction = fraction,
                accent = accent,
                enabled = track != null && dur > 0f,
                onScrub = { scrub = it },
                onSeekFinished = {
                    scrub?.let { graph.playback.seekTo((it * dur).toLong()) }
                    scrub = null
                },
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.positionMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatTime(state.durationMs ?: 0L), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(20.dp))

            ControlsBar(graph = graph, state = state, accent = accent, enabled = track != null)

            state.error?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text("⚠ ${err.message}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ControlsBar(graph: AppGraph, state: PlayerState, accent: Color, enabled: Boolean) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, onSurface.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { graph.playback.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "перемешать",
                    tint = if (state.shuffled) accent else onSurface,
                )
            }
            IconButton(onClick = { graph.playback.previous() }, enabled = enabled) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "предыдущий", tint = onSurface, modifier = Modifier.size(32.dp))
            }
            IconButton(
                onClick = { graph.playback.togglePlayPause() },
                enabled = enabled,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "пауза" else "играть",
                    tint = onSurface,
                    modifier = Modifier.size(44.dp),
                )
            }
            IconButton(onClick = { graph.playback.next() }, enabled = enabled) {
                Icon(Icons.Filled.SkipNext, contentDescription = "следующий", tint = onSurface, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = { graph.playback.cycleRepeatMode() }) {
                when (state.repeatMode) {
                    RepeatMode.OFF -> Icon(Icons.Filled.Repeat, contentDescription = "повтор", tint = onSurface)
                    RepeatMode.ALL -> Icon(Icons.Filled.Repeat, contentDescription = "повтор всё", tint = accent)
                    RepeatMode.ONE -> Icon(Icons.Filled.RepeatOne, contentDescription = "повтор один", tint = accent)
                }
            }
        }
    }
}

/** Тонкий seek-бар как в Cosmos: серый трек (4dp) + accent-заливка + accent-кружок 12dp. */
@Composable
private fun SeekBar(
    fraction: Float,
    accent: Color,
    enabled: Boolean,
    onScrub: (Float) -> Unit,
    onSeekFinished: () -> Unit,
) {
    var width by remember { mutableStateOf(1f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                    detectTapGestures { onScrub((it.x / width).coerceIn(0f, 1f)); onSeekFinished() }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { onSeekFinished() },
                    ) { change, _ -> onScrub((change.position.x / width).coerceIn(0f, 1f)) }
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val cy = size.height / 2
            val trackH = 4.dp.toPx()
            val w = size.width
            drawRoundRect(
                color = Color.Gray.copy(alpha = 0.3f),
                topLeft = Offset(0f, cy - trackH / 2),
                size = Size(w, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(0f, cy - trackH / 2),
                size = Size(w * fraction, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            drawCircle(color = accent, radius = 6.dp.toPx(), center = Offset(w * fraction, cy))
        }
    }
}

private const val SWIPE_THRESHOLD = 120f

internal fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
