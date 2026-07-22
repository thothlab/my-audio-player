package tech.thothlab.dombra.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.i18n.LocalStrings
import tech.thothlab.dombra.presentation.player.QueueItem
import tech.thothlab.dombra.theme.LocalAccentColor
import tech.thothlab.dombra.theme.Sym
import tech.thothlab.dombra.theme.Symbol
import tech.thothlab.dombra.theme.auroraColors

/** Фикс. высота строки «Далее» — чтобы расчёт зазора при реордере был точным. */
private val UP_NEXT_ROW_HEIGHT = 56.dp

/**
 * Экран очереди (макет turn-2 · F5): карточка «Сейчас играет» + список «Далее».
 * Тап по строке «Далее» — перейти к треку; перетаскивание за ручку [Sym.DragIndicator] — реордер.
 */
@Composable
fun QueueScreen(graph: AppGraph, onBack: () -> Unit) {
    val state by graph.playback.state.collectAsState()
    val c = auroraColors()
    val accent = LocalAccentColor.current
    val strings = LocalStrings.current

    val current = state.currentItem
    val baseIndex = state.currentIndex
    val played = remember(state.queue, baseIndex) {
        if (baseIndex > 0) state.queue.take(baseIndex.coerceAtMost(state.queue.size)) else emptyList()
    }
    val upNext = remember(state.queue, baseIndex) {
        if (baseIndex in state.queue.indices) state.queue.drop(baseIndex + 1) else emptyList()
    }

    // Состояние перетаскивания строки «Далее» (индекс в upNext + накопленный сдвиг).
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val rowHeightPx = with(LocalDensity.current) { UP_NEXT_ROW_HEIGHT.toPx() }

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Player)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            // Шапка: свернуть · «Очередь» · «Очистить».
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(
                    Sym.KeyboardArrowDown, size = 28.dp, tint = c.textPrimary,
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onBack).padding(2.dp),
                )
                Text(
                    strings.queue,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                if (upNext.isNotEmpty()) {
                    Text(
                        strings.clearQueue,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = accent,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { upNext.forEach { graph.playback.removeEntry(it.entryId) } }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            ) {
                if (played.isNotEmpty()) {
                    item(key = "played-label") { QueueLabel(strings.played, c.textTertiary) }
                    itemsIndexed(played, key = { _, it -> "p_" + it.entryId }) { i, qi ->
                        PlayedRow(graph, qi) { graph.playback.jumpTo(i) }
                    }
                }
                if (current != null) {
                    item(key = "np-label") { QueueLabel(strings.nowPlaying, c.textTertiary) }
                    item(key = "np-card") {
                        NowPlayingCard(
                            graph = graph,
                            item = current,
                            accent = accent,
                            isPlaying = state.isPlaying,
                            onToggle = { graph.playback.togglePlayPause() },
                        )
                    }
                }
                if (upNext.isNotEmpty()) {
                    item(key = "next-label") { QueueLabel(strings.upNext, c.textTertiary) }
                    itemsIndexed(upNext, key = { _, it -> it.entryId }) { i, qi ->
                        val from = dragFrom
                        // Целевая позиция и сдвиг соседей: строки между from и target раздвигаются,
                        // открывая зазор в целевой позиции (сдвиг = высоте строки → приземление без прыжка).
                        val target = from?.let { (it + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, upNext.lastIndex) }
                        val dragging = from == i
                        val shift = when {
                            from == null || target == null || dragging -> 0f
                            from < i && i <= target -> -rowHeightPx
                            from > i && i >= target -> rowHeightPx
                            else -> 0f
                        }
                        // Плавное раздвижение соседей (spring во время перетаскивания); на отпускании
                        // (from == null) — snap, чтобы мгновенно совпасть с новой раскладкой без прыжка.
                        val animShift by animateFloatAsState(
                            targetValue = shift,
                            animationSpec = if (from == null) snap() else spring(stiffness = Spring.StiffnessMediumLow),
                            label = "reorder-shift",
                        )
                        UpNextRow(
                            graph = graph,
                            item = qi,
                            dragging = dragging,
                            offsetY = if (dragging) dragOffset else animShift,
                            onTap = { graph.playback.jumpTo(baseIndex + 1 + i) },
                            onDragStart = { dragFrom = i; dragOffset = 0f },
                            onDrag = { dragOffset += it },
                            onDragEnd = {
                                val from = dragFrom
                                if (from != null) {
                                    val target = (from + (dragOffset / rowHeightPx).roundToInt())
                                        .coerceIn(0, upNext.lastIndex)
                                    if (target != from) {
                                        graph.playback.moveEntry(baseIndex + 1 + from, baseIndex + 1 + target)
                                    }
                                }
                                dragFrom = null; dragOffset = 0f
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Подпись-разделитель очереди (mono, uppercase, приглушённая) — как в макете. */
@Composable
private fun QueueLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        letterSpacing = 1.4.sp,
        color = color,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

/** Подсвеченная карточка «Сейчас играет». */
@Composable
private fun NowPlayingCard(
    graph: AppGraph,
    item: QueueItem,
    accent: androidx.compose.ui.graphics.Color,
    isPlaying: Boolean,
    onToggle: () -> Unit,
) {
    val c = auroraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.glassFillStrong)
            .border(1.dp, c.glassBorderStrong, RoundedCornerShape(14.dp))
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(graph.artwork, item.track.stableId, shape = RoundedCornerShape(11.dp), modifier = Modifier.size(46.dp), iconScale = 0.5f)
        Column(Modifier.weight(1f)) {
            Text(item.track.title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.track.artistName, fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Symbol(
            if (isPlaying) Sym.Pause else Sym.PlayArrow,
            filled = true, size = 26.dp, tint = c.textPrimary,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onToggle).padding(4.dp),
        )
    }
}

/** Строка «Проиграно»: приглушённая, тап — перейти к треку. */
@Composable
private fun PlayedRow(graph: AppGraph, item: QueueItem, onTap: () -> Unit) {
    val c = auroraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(UP_NEXT_ROW_HEIGHT)
            .graphicsLayer { alpha = 0.5f }
            .clickable(onClick = onTap)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(graph.artwork, item.track.stableId, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(42.dp), iconScale = 0.5f)
        Column(Modifier.weight(1f)) {
            Text(item.track.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.track.artistName, fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Строка «Далее»: тап — перейти; ручка [Sym.DragIndicator] — перетаскивание для реордера. */
@Composable
private fun UpNextRow(
    graph: AppGraph,
    item: QueueItem,
    dragging: Boolean,
    offsetY: Float,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val c = auroraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(UP_NEXT_ROW_HEIGHT)
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = offsetY
                if (dragging) shadowElevation = 8.dp.toPx()
            }
            .then(if (dragging) Modifier.clip(RoundedCornerShape(12.dp)).background(c.glassFill) else Modifier)
            .clickable(onClick = onTap)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(graph.artwork, item.track.stableId, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(42.dp), iconScale = 0.5f)
        Column(Modifier.weight(1f)) {
            Text(item.track.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.track.artistName, fontSize = 12.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Symbol(
            Sym.DragIndicator, size = 22.dp, tint = c.textFaint,
            modifier = Modifier.pointerInput(item.entryId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, delta -> change.consume(); onDrag(delta.y) }
            },
        )
    }
}
