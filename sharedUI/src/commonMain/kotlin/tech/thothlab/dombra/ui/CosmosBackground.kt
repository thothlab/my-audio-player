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
import tech.thothlab.dombra.theme.LocalThemeIsDark

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
    val dark = LocalThemeIsDark.current.value
    // Свечения на светлой базе — заметно мягче (по макету «Светлая тема»): accent ~.16, фиолет ~.12.
    val k = if (dark) 1f else 1.15f
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

        // Aurora: тонкие глубокие свечения (как в макете) — фиолетовый ведущий,
        // accent аккуратным акцентом. На светлой базе альфы ниже.
        when (screen) {
            CosmosScreen.Player -> {
                glow(accent, if (dark) 0.16f else 0.18f * k, w * 0.84f, h * 0.10f, w * 0.9f)
                glow(AuroraPurple, if (dark) 0.14f else 0.14f * k, w * 0.10f, h * 0.9f, w * 0.9f)
            }
            CosmosScreen.Library -> {
                glow(accent, if (dark) 0.11f else 0.14f * k, w * 0.85f, -h * 0.04f, maxOf(w, h) * 0.95f)
                glow(AuroraPurple, if (dark) 0.12f else 0.11f * k, 0f, h * 0.5f, maxOf(w, h) * 0.85f)
            }
            CosmosScreen.Secondary -> {
                glow(AuroraPurple, if (dark) 0.13f else 0.11f * k, w * 0.9f, 0f, maxOf(w, h) * 0.95f)
                glow(accent, if (dark) 0.08f else 0.12f * k, 0f, h * 0.35f, maxOf(w, h) * 0.8f)
            }
        }
    }
}
