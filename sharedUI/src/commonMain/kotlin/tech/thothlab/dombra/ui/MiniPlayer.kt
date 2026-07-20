package tech.thothlab.dombra.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.theme.Sym
import tech.thothlab.dombra.theme.Symbol
import tech.thothlab.dombra.theme.auroraColors
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.presentation.player.PlayerState

private const val DISMISS_THRESHOLD = 200f

/**
 * Нижний мини-плеер (Cosmos): матовый бар с обложкой, названием, play/pause + next.
 * Тап → полный плеер. Смахивание влево/вправо убирает бар и **останавливает** воспроизведение.
 */
@Composable
fun MiniPlayer(
    graph: AppGraph,
    player: PlayerState,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track: Track = player.currentTrack ?: return
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    // Новый трек → вернуть бар в центр (сбросить остаточный сдвиг).
    LaunchedEffect(track.stableId) { offsetX.snapTo(0f) }

    val c = auroraColors()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer { alpha = (1f - abs(offsetX.value) / 700f).coerceIn(0.15f, 1f) }
            .pointerInput(track.stableId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(offsetX.value) > DISMISS_THRESHOLD) {
                                val target = if (offsetX.value > 0) 1400f else -1400f
                                offsetX.animateTo(target, tween(180))
                                graph.playback.clear() // смахнули → стоп + убрать «сейчас играет»
                            } else {
                                offsetX.animateTo(0f, spring())
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, spring()) } },
                ) { change, delta ->
                    change.consume()
                    scope.launch { offsetX.snapTo(offsetX.value + delta) }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { onExpand() }
            },
        shape = RoundedCornerShape(16.dp),
        color = c.barSurface,
        border = BorderStroke(1.dp, c.barBorder),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArtworkImage(
                artwork = graph.artwork,
                stableId = track.stableId,
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier.size(42.dp),
                iconScale = 0.5f,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { graph.playback.togglePlayPause() }) {
                Symbol(
                    if (player.isPlaying) Sym.Pause else Sym.PlayArrow,
                    filled = true,
                    size = 26.dp,
                )
            }
            IconButton(onClick = { graph.playback.next() }) {
                Symbol(Sym.SkipNext, size = 26.dp)
            }
        }
    }
}
