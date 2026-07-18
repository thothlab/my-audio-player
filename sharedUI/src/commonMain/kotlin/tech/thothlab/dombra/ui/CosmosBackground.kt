package tech.thothlab.dombra.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (screen) {
            CosmosScreen.Player -> {
                // Радиальный акцент справа-сверху + нижнее свечение (subtleRadial).
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(w * 0.82f, h * 0.14f),
                        radius = w * 0.75f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(w * 0.15f, h * 0.85f),
                        radius = w * 0.7f,
                    ),
                )
            }
            CosmosScreen.Library -> {
                // Гало из верхнего-левого угла.
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = maxOf(w, h) * 0.85f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(w, h),
                        radius = maxOf(w, h) * 0.6f,
                    ),
                )
            }
            CosmosScreen.Secondary -> {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(w, 0f),
                        radius = maxOf(w, h) * 0.85f,
                    ),
                )
            }
        }
    }
}
