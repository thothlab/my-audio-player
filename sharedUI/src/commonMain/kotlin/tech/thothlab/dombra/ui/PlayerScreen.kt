package tech.thothlab.dombra.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.Playlist
import tech.thothlab.dombra.domain.model.RepeatMode
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.presentation.player.PlayerState
import tech.thothlab.dombra.theme.AuroraPurple
import tech.thothlab.dombra.theme.Sym
import tech.thothlab.dombra.theme.Symbol
import tech.thothlab.dombra.theme.auroraColors
import tech.thothlab.dombra.theme.LocalAccentColor
import tech.thothlab.dombra.theme.LocalThemeIsDark

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
    var showMenu by remember { mutableStateOf(false) }
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
            // Верхняя панель: свернуть (стекло-круг) · eyebrow · «ещё» (стекло-круг) — по макету.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassCircle(Sym.KeyboardArrowDown, glyphSize = 24.dp, onClick = onBack)
                Text(
                    "СЕЙЧАС ИГРАЕТ",
                    fontSize = 10.5.sp,
                    letterSpacing = 1.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Box {
                    GlassCircle(Sym.MoreVert, glyphSize = 20.dp, onClick = { showMenu = true })
                    val c = auroraColors()
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = c.popoverSurface,
                        border = BorderStroke(1.dp, c.popoverBorder),
                    ) {
                        DropdownMenuItem(
                            text = { Text("Добавить в плейлист", color = c.textPrimary) },
                            onClick = { showMenu = false; showPlaylistSheet = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Остановить", color = c.textPrimary) },
                            onClick = { showMenu = false; graph.playback.clear(); onBack() },
                        )
                    }
                }
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

            // Название/исполнитель слева, «в избранное»/«в плейлист» стекло-кругами справа (по макету).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track?.title ?: "нет трека",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track?.artistName ?: "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                if (track != null) {
                    val fav by remember(track.stableId) { graph.library.isFavorite(track.stableId) }
                        .collectAsState(initial = false)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        GlassCircle(
                            Sym.Favorite,
                            filled = fav,
                            glyphSize = 21.dp,
                            tint = if (fav) accent else MaterialTheme.colorScheme.onSurface,
                            onClick = { scope.launch { graph.library.setFavorite(track.stableId, !fav) } },
                        )
                        GlassCircle(Sym.Add, glyphSize = 21.dp, onClick = { showPlaylistSheet = true })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

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

/** Шит «Добавить в плейлист» (Ход 11): шапка с треком, «Создать плейлист» пунктиром,
 *  список плейлистов с числом треков; тап → галочка + тост «Добавлено в …» с «Отменить». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(graph: AppGraph, track: Track, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val c = auroraColors()
    val accent = LocalAccentColor.current
    val playlists by graph.playlists.playlists().collectAsState(initial = emptyList())
    var showCreate by remember { mutableStateOf(false) }
    val addedIds = remember { mutableStateListOf<String>() }
    var lastAdded by remember { mutableStateOf<Playlist?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.popoverSurface) {
        Column(Modifier.padding(bottom = 20.dp)) {
            // Шапка: обложка трека + заголовок + «Title · Artist».
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                ArtworkImage(graph.artwork, track.stableId, shape = RoundedCornerShape(9.dp), modifier = Modifier.size(40.dp), iconScale = 0.5f)
                Column(Modifier.weight(1f)) {
                    Text("Добавить в плейлист", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.textPrimary)
                    Text("${track.title} · ${track.artistName}", fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = c.glassBorder)

            // «Создать плейлист» — пунктирная accent-плитка.
            Row(
                modifier = Modifier.fillMaxWidth().clickable { showCreate = true }.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.glassFillStrong)
                        .drawBehind {
                            drawRoundRect(
                                color = accent.copy(alpha = 0.5f),
                                cornerRadius = CornerRadius(12.dp.toPx()),
                                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 6f))),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) { Symbol(Sym.Add, size = 24.dp, tint = accent) }
                Text("Создать плейлист", fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = accent)
            }

            // Список плейлистов.
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                playlists.forEach { pl ->
                    val added = pl.id in addedIds
                    val count by graph.playlists.playlist(pl.id).map { it?.items?.size ?: 0 }.collectAsState(initial = 0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch { graph.playlists.addTrack(pl.id, track.stableId) }
                                if (pl.id !in addedIds) addedIds.add(pl.id)
                                lastAdded = pl
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        IconTile(Sym.QueueMusic, PlaylistTileColor, size = 44.dp, iconSize = 22.dp)
                        Column(Modifier.weight(1f)) {
                            Text(pl.title, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$count треков", fontSize = 12.sp, color = c.textSecondary)
                        }
                        if (added) {
                            Symbol(Sym.Check, size = 24.dp, tint = accent)
                        } else {
                            Symbol(Sym.AddCircle, size = 24.dp, tint = c.textFaint)
                        }
                    }
                }
            }

            // Тост «Добавлено в …» с отменой.
            lastAdded?.let { pl ->
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x2422C55E))
                        .border(1.dp, Color(0x5222C55E), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Symbol(Sym.Check, size = 20.dp, tint = Color(0xFF4ADE80))
                    Text("Добавлено в «${pl.title}»", fontSize = 13.sp, color = Color(0xFFBBF7D0), modifier = Modifier.weight(1f))
                    Text(
                        "Отменить",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.clickable {
                            scope.launch {
                                val pwi = graph.playlists.playlist(pl.id).first()
                                pwi?.items?.lastOrNull { it.trackStableId == track.stableId }?.let {
                                    graph.playlists.removeItem(pl.id, it.position)
                                }
                            }
                            addedIds.remove(pl.id)
                            lastAdded = null
                        },
                    )
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Новый плейлист") },
            text = {
                Column {
                    Text("Трек «${track.title}» будет добавлен сразу", color = c.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        placeholder = { Text("Название") },
                    )
                }
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

/** Круглая «стеклянная» кнопка плеера (свернуть/ещё/избранное/плейлист) — по макету. */
@Composable
private fun GlassCircle(
    glyph: Char,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 40.dp,
    glyphSize: Dp = 21.dp,
    tint: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val dark = LocalThemeIsDark.current.value
    val bg = if (dark) Color(0x1AFFFFFF) else Color(0x0D000000)
    val stroke = if (dark) Color(0x26FFFFFF) else Color(0x14000000)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(glyph, filled = filled, size = glyphSize, tint = tint)
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
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
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
            val trackH = 5.dp.toPx()
            val w = size.width
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, cy - trackH / 2),
                size = Size(w, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(accent, AuroraPurple), startX = 0f, endX = w),
                    topLeft = Offset(0f, cy - trackH / 2),
                    size = Size(w * fraction, trackH),
                    cornerRadius = CornerRadius(trackH / 2),
                )
            }
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
