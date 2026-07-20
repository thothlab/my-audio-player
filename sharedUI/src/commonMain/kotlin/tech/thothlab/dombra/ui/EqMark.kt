package tech.thothlab.dombra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.thothlab.dombra.theme.LocalAccentColor

/** Второй конец градиента плитки «Звук» — фиолетовый #7c3aed (из концепта 3C). */
private val EqViolet = Color(0xFF7C3AED)

/**
 * Знак «Звук» — концепт 3C из дизайн-хендофа: скруглённая плитка с
 * accent→фиолетовым градиентом (145°), глянцевым бликом сверху-слева и
 * четырьмя белыми полосами эквалайзера. Заливка следует за accent-цветом,
 * как `var(--acc-c)` в макете. Все пропорции — из референса 60px.
 */
@Composable
fun EqMark(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
) {
    val accent = LocalAccentColor.current
    // Пропорции полос из референса 60px: ширина 5.5, зазор 4, высоты 16/30/22/11,
    // нижний отступ 17, радиус полосы 3, радиус плитки 14.
    val barWidth = size * (5.5f / 60f)
    val barGap = size * (4f / 60f)
    val barRadius = size * (3f / 60f)
    val bottomPad = size * (17f / 60f)
    val tileRadius = size * (14f / 60f)
    val heights = listOf(16f, 30f, 22f, 11f).map { size * (it / 60f) }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(tileRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(accent, EqViolet),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Глянцевый блик: linear-gradient(160°, white .30, transparent 42%).
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.30f),
                    0.42f to Color.Transparent,
                ),
            ),
        )
        Row(
            modifier = Modifier.padding(bottom = bottomPad),
            horizontalArrangement = Arrangement.spacedBy(barGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            heights.forEach { h ->
                Box(
                    Modifier
                        .width(barWidth)
                        .height(h)
                        .clip(RoundedCornerShape(barRadius))
                        .background(Color.White),
                )
            }
        }
    }
}
