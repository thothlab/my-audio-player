package tech.thothlab.dombra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import tech.thothlab.dombra.core.getOrNull
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.Lyrics
import tech.thothlab.dombra.domain.model.LyricsType
import tech.thothlab.dombra.i18n.LocalStrings
import tech.thothlab.dombra.presentation.player.PlayerState
import tech.thothlab.dombra.theme.AuroraPurple
import tech.thothlab.dombra.theme.LocalAccentColor
import tech.thothlab.dombra.theme.Sym
import tech.thothlab.dombra.theme.Symbol
import tech.thothlab.dombra.theme.auroraColors

/**
 * Экран текста песни (Ход 10 · доп. экраны). Синхронный текст (LRC-таймкоды) едет
 * караоке-скроллом: активная строка крупнее и подсвечена акцентом, соседние гаснут
 * по мере удаления. Простой текст — обычный скролл. Снизу — мини-плеер (прогресс +
 * обложка + play/pause). Данные разрешаются лениво через [AppGraph.lyrics].
 */
@Composable
fun LyricsScreen(graph: AppGraph, onBack: () -> Unit) {
    val state: PlayerState by graph.playback.state.collectAsState()
    val track = state.currentTrack
    val accent = LocalAccentColor.current
    val c = auroraColors()
    val strings = LocalStrings.current

    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(track?.stableId) {
        val t = track
        if (t == null) {
            lyrics = null; loading = false
        } else {
            loading = true
            lyrics = graph.lyrics.lyricsFor(t).getOrNull()
            loading = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Player)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // Шапка: свернуть · «ТЕКСТ ПЕСНИ» · знак «текст».
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(
                    Sym.KeyboardArrowDown,
                    size = 28.dp,
                    tint = c.textPrimary,
                    modifier = Modifier.clickable(onClick = onBack),
                )
                Text(
                    strings.lyricsTitle,
                    fontSize = 10.5.sp,
                    letterSpacing = 1.5.sp,
                    color = c.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Symbol(Sym.Lyrics, size = 22.dp, tint = accent)
            }

            // Тело: синхрон / простой / загрузка / пусто.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val syncedLines = lyrics?.takeIf { it.type == LyricsType.SYNCED }?.lines.orEmpty()
                val plainText = lyrics?.takeIf { it.type == LyricsType.PLAIN }?.lines
                    ?.joinToString("\n") { it.text }?.trim()
                when {
                    loading -> LyricsPlaceholder(Sym.Lyrics, strings.lyricsLoading, null, accent, c.textSecondary, spinner = true)
                    syncedLines.isNotEmpty() -> SyncedLyrics(syncedLines, state.positionMs, accent, c.textPrimary)
                    !plainText.isNullOrBlank() -> PlainLyrics(plainText, c.textPrimary)
                    else -> LyricsPlaceholder(Sym.Lyrics, strings.noLyricsTitle, strings.noLyricsBody, accent, c.textSecondary, spinner = false)
                }
            }

            // Нижний мини-плеер: прогресс + обложка + название + play/pause.
            LyricsMiniBar(graph, state, accent)
        }
    }
}

/** Караоке-скролл: активная строка крупная и акцентная, соседние гаснут по расстоянию. */
@Composable
private fun SyncedLyrics(
    lines: List<tech.thothlab.dombra.domain.model.LyricsLine>,
    positionMs: Long,
    accent: Color,
    textPrimary: Color,
) {
    val activeIndex = remember(lines, positionMs) {
        lines.indexOfLast { (it.timeMs ?: 0L) <= positionMs }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val halfPx = with(density) { (maxHeight / 2).toPx() }
        // Центрируем активную строку: половина вьюпорта сверху/снизу как contentPadding,
        // и доводим её к центру отрицательным скролл-офсетом (минус ~высота строки).
        LaunchedEffect(activeIndex, halfPx) {
            if (activeIndex in lines.indices) {
                listState.animateScrollToItem(activeIndex, scrollOffset = -(halfPx - with(density) { 24.dp.toPx() }).toInt())
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentPadding = PaddingValues(vertical = maxHeight / 2),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(lines) { i, line ->
                val distance = abs(i - activeIndex)
                val isActive = i == activeIndex
                Text(
                    line.text,
                    style = TextStyle(
                        fontSize = if (isActive) 23.sp else 17.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isActive) accent else textPrimary.copy(alpha = lineAlpha(distance)),
                        lineHeight = if (isActive) 29.sp else 22.sp,
                        shadow = if (isActive) Shadow(accent.copy(alpha = 0.4f), Offset.Zero, blurRadius = 24f) else null,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun lineAlpha(distance: Int): Float = when (distance) {
    1 -> 0.5f
    2 -> 0.3f
    else -> 0.22f
}

@Composable
private fun PlainLyrics(text: String, textPrimary: Color) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 8.dp)) {
        Text(
            text,
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = textPrimary.copy(alpha = 0.9f), lineHeight = 27.sp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LyricsPlaceholder(
    icon: Char,
    title: String,
    body: String?,
    accent: Color,
    subtle: Color,
    spinner: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) {
            if (spinner) CircularProgressIndicator(color = accent, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            else Symbol(icon, size = 46.dp, tint = accent)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold), color = subtle, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = TextStyle(fontSize = 13.sp), color = subtle.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LyricsMiniBar(graph: AppGraph, state: PlayerState, accent: Color) {
    val c = auroraColors()
    val track = state.currentTrack ?: return
    val dur = (state.durationMs ?: 0L).toFloat()
    val fraction = (if (dur > 0f) state.positionMs / dur else 0f).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 14.dp, bottom = 22.dp)) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.glassBorderStrong)) {
            Box(
                Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(Brush.horizontalGradient(listOf(accent, AuroraPurple))),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            ArtworkImage(graph.artwork, track.stableId, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(40.dp), iconScale = 0.5f)
            Column(Modifier.weight(1f)) {
                Text(track.title, style = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artistName, style = TextStyle(fontSize = 11.sp), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Symbol(
                if (state.isPlaying) Sym.PauseCircle else Sym.PlayArrow,
                filled = true,
                size = 32.dp,
                tint = c.textPrimary,
                modifier = Modifier.clickable { graph.playback.togglePlayPause() },
            )
        }
    }
}
