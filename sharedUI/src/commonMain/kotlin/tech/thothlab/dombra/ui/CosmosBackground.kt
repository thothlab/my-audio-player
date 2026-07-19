package tech.thothlab.dombra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import tech.thothlab.dombra.theme.AuroraPurple
import tech.thothlab.dombra.theme.LocalAccentColor

/**
 * Мягкий градиент-фон в духе Cosmos (`BackgroundTextureView`): цветной «глоу»
 * accent-цветом поверх фона темы. Разные экраны — разные варианты.
 *
 * Оригинал использует blur; на Android <12 blur — no-op, поэтому берём градиенты
 * с плавным затуханием альфы (тот же мягкий эффект, работает везде). Accent —
 * `colorScheme.primary` (пользовательский выбор — T05).
 */
enum class CosmosScreen { Player, Library, Secondary }

@Composable
fun CosmosBackground(
    screen: CosmosScreen,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val accent = LocalAccentColor.current
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        fun glow(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) = drawRect(
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = Offset(cx, cy),
                radius = r,
            ),
        )

        when (screen) {
            CosmosScreen.Player -> {
                // Aurora: accent-глоу справа-сверху + фиолетовый снизу-слева.
                glow(accent, 0.30f, w * 0.84f, h * 0.12f, w * 0.85f)
                glow(AuroraPurple, 0.18f, w * 0.12f, h * 0.86f, w * 0.8f)
            }
            CosmosScreen.Library -> {
                glow(accent, 0.24f, w * 0.05f, 0f, maxOf(w, h) * 0.9f)
                glow(AuroraPurple, 0.14f, w, h * 0.95f, maxOf(w, h) * 0.7f)
            }
            CosmosScreen.Secondary -> {
                glow(accent, 0.22f, w, 0f, maxOf(w, h) * 0.9f)
                glow(AuroraPurple, 0.12f, 0f, h, maxOf(w, h) * 0.7f)
            }
        }
    }
}
