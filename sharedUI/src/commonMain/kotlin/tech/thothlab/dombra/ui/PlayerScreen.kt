package tech.thothlab.dombra.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.RepeatMode
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.presentation.player.PlayerState
import tech.thothlab.dombra.theme.AuroraPurple
import tech.thothlab.dombra.theme.Sym
import tech.thothlab.dombra.theme.Symbol
import tech.thothlab.dombra.theme.auroraColors
import tech.thothlab.dombra.theme.LocalAccentColor

/**
 * Полноэкранный плеер в облике Cosmos (`Views/Player/PlayerViews.swift`):
 * обложка-карусель (соседние обложки выглядывают) со скруглением/тенью, title/artist,
 * кнопки «в избранное»/«в плейлист», тонкий accent seek-бар, матовые контролы.
 */
@Composable
fun PlayerScreen(graph: AppGraph, onBack: () -> Unit) {
    val state: PlayerState by graph.playback.state.collectAsState()
    val track = state.currentTrack
    val accent = LocalAccentColor.current
    val scope = rememberCoroutineScope()

    var scrub by remember { mutableStateOf<Float?>(null) }
    val artDrag = remember { Animatable(0f) }
    var carouselStep by remember { mutableStateOf(0f) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val dur = (state.durationMs ?: 0L).toFloat()

    // Смена трека кнопками «вперёд/назад» — с тем же плавным слайдом карусели, что и свайп.
    val skipTo: (Boolean) -> Unit = { forward ->
        val hasNeighbor =
            if (forward) state.currentIndex + 1 <= state.queue.lastIndex else state.currentIndex - 1 >= 0
        scope.launch {
            if (hasNeighbor && carouselStep > 1f) {
                artDrag.animateTo(if (forward) -carouselStep else carouselStep, tween(220))
                if (forward) graph.playback.next() else graph.playback.previous()
                artDrag.snapTo(0f)
            } else {
                if (forward) graph.playback.next() else graph.playback.previous()
            }
        }
    }
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
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Symbol(Sym.KeyboardArrowDown, size = 32.dp)
                }
                Spacer(Modifier.weight(1f))
            }

            // Обложка-карусель: соседние обложки выглядывают слева/справа, свайп меняет трек.
            // Область обложки — гибкая (weight): забирает свободную высоту, а карточка
            // ограничена min(ширина·0.82, доступная высота) — на низком экране ужимается
            // сама, а не выталкивает панель управления вниз.
            val prevId = state.queue.getOrNull(state.currentIndex - 1)?.track?.stableId
            val nextId = state.queue.getOrNull(state.currentIndex + 1)?.track?.stableId
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val cardW = minOf(maxWidth * 0.82f, maxHeight)
                val stepPx = with(LocalDensity.current) { (cardW + 18.dp).toPx() }
                SideEffect { carouselStep = stepPx }
                if (prevId != null) CarouselCard(graph, prevId, -1, cardW, stepPx, artDrag.value)
                if (nextId != null) CarouselCard(graph, nextId, 1, cardW, stepPx, artDrag.value)
                CarouselCard(graph, track?.stableId, 0, cardW, stepPx, artDrag.value)

                // Жест поверх: следует за пальцем, на смену трека — плавный слайд на позицию соседа.
                Box(
                    Modifier.matchParentSize().pointerInput(track?.stableId) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    when {
                                        artDrag.value <= -SWIPE_THRESHOLD && nextId != null -> {
                                            artDrag.animateTo(-stepPx, tween(180))
                                            graph.playback.next()
                                            artDrag.snapTo(0f)
                                        }
                                        artDrag.value >= SWIPE_THRESHOLD && prevId != null -> {
                                            artDrag.animateTo(stepPx, tween(180))
                                            graph.playback.previous()
                                            artDrag.snapTo(0f)
                                        }
                                        else -> artDrag.animateTo(0f, spring())
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { artDrag.animateTo(0f, spring()) } },
                        ) { change, delta ->
                            change.consume()
                            scope.launch { artDrag.snapTo(artDrag.value + delta) }
                        }
                    },
                )
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

            // Кнопки «в избранное» / «в плейлист».
            if (track != null) {
                val fav by remember(track.stableId) { graph.library.isFavorite(track.stableId) }
                    .collectAsState(initial = false)
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { scope.launch { graph.library.setFavorite(track.stableId, !fav) } }) {
                        Symbol(
                            Sym.Favorite,
                            filled = fav,
                            size = 24.dp,
                            tint = if (fav) accent else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { showPlaylistSheet = true }) {
                        Symbol(Sym.Add, size = 24.dp, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

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

            ControlsBar(
                graph = graph,
                state = state,
                accent = accent,
                enabled = track != null,
                onSkipPrevious = { skipTo(false) },
                onSkipNext = { skipTo(true) },
            )

            state.error?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text("⚠ ${err.message}", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            // Небольшой фиксированный отступ снизу — панель управления держится над
            // системной навигацией и не «схлопывается» на низких экранах.
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPlaylistSheet && track != null) {
        AddToPlaylistSheet(graph = graph, track = track, onDismiss = { showPlaylistSheet = false })
    }
}

/**
 * Обложка в карусели: масштаб/тень/α зависят от расстояния до центра (t=0 центр … t=1 сосед),
 * поэтому при свайпе/смене трека соседняя обложка плавно «накатывает» (увеличивается), центральная — уменьшается.
 */
@Composable
private fun CarouselCard(
    graph: AppGraph,
    stableId: String?,
    slot: Int,
    cardWidth: Dp,
    stepPx: Float,
    dragPx: Float,
) {
    val pos = slot * stepPx + dragPx
    val t = if (stepPx > 1f) (abs(pos) / stepPx).coerceIn(0f, 1f) else if (slot == 0) 0f else 1f
    val scale = lerp(1f, 0.82f, t)
    ArtworkImage(
        artwork = graph.artwork,
        stableId = stableId,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(1f)
            .offset { IntOffset(pos.roundToInt(), 0) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = lerp(1f, 0.5f, t)
                shadowElevation = 12.dp.toPx() * (1f - t)
                shape = RoundedCornerShape(12.dp)
            },
        iconScale = 0.22f,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(graph: AppGraph, track: Track, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val playlists by graph.playlists.playlists().collectAsState(initial = emptyList())
    var showCreate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Добавить в плейлист",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            playlists.forEach { pl ->
                ListItem(
                    headlineContent = { Text(pl.title) },
                    modifier = Modifier.clickable {
                        scope.launch {
                            graph.playlists.addTrack(pl.id, track.stableId)
                            onDismiss()
                        }
                    },
                )
            }
            ListItem(
                headlineContent = { Text("Создать плейлист") },
                leadingContent = { Symbol(Sym.Add, size = 24.dp) },
                modifier = Modifier.clickable { showCreate = true },
            )
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Новый плейлист") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Название") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val pl = graph.playlists.create(name.ifBlank { "Новый плейлист" })
                        graph.playlists.addTrack(pl.id, track.stableId)
                        showCreate = false
                        onDismiss()
                    }
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ControlsBar(
    graph: AppGraph,
    state: PlayerState,
    accent: Color,
    enabled: Boolean,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val c = auroraColors()
    Surface(
        color = c.barSurface,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, c.barBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { graph.playback.toggleShuffle() }) {
                Symbol(Sym.Shuffle, size = 23.dp, tint = if (state.shuffled) accent else onSurface)
            }
            IconButton(onClick = onSkipPrevious, enabled = enabled) {
                Symbol(Sym.SkipPrevious, size = 30.dp, tint = onSurface)
            }
            // Центральная кнопка — акцентный градиентный круг (как в макете).
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent, AuroraPurple)))
                    .clickable(enabled = enabled) { graph.playback.togglePlayPause() },
                contentAlignment = Alignment.Center,
            ) {
                Symbol(
                    if (state.isPlaying) Sym.Pause else Sym.PlayArrow,
                    filled = true,
                    size = 31.dp,
                    tint = Color.White,
                )
            }
            IconButton(onClick = onSkipNext, enabled = enabled) {
                Symbol(Sym.SkipNext, size = 30.dp, tint = onSurface)
            }
            IconButton(onClick = { graph.playback.cycleRepeatMode() }) {
                when (state.repeatMode) {
                    RepeatMode.OFF -> Symbol(Sym.Repeat, size = 23.dp, tint = onSurface)
                    RepeatMode.ALL -> Symbol(Sym.Repeat, size = 23.dp, tint = accent)
                    RepeatMode.ONE -> Symbol(Sym.RepeatOne, size = 23.dp, tint = accent)
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
