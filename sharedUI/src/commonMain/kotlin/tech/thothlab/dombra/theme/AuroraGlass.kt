package tech.thothlab.dombra.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Aurora Glass — стеклянные поверхности и градиенты. Один язык, две базы:
 *  - тёмная: стекло (белый с низкой прозрачностью) на почти-чёрном фоне #0A0A12, светлый текст;
 *  - светлая: белое матовое стекло на тёплой базе #F4F1EC, тёмный текст.
 * Плитки-иконки — градиент accent → фиолетовый (одинаково в обеих темах).
 * Значения выверены по дизайн-макету (handoff, «Ход 8 · Светлая тема»).
 */

/** Дополнительный цвет Aurora — фиолетовый (второй конец градиентов плиток/обложек). */
val AuroraPurple = Color(0xFFA06BFF)

/** Тёмный текст светлой темы (тёплый near-black). */
private val InkLight = Color(0xFF1B1620)

/** Набор theme-aware токенов Aurora Glass. Берётся через [auroraColors]. */
class AuroraColors(
    /** Основной текст. */
    val textPrimary: Color,
    /** Вторичный текст (~55%). */
    val textSecondary: Color,
    /** Приглушённый текст / подписи (~45%). */
    val textTertiary: Color,
    /** Едва заметный текст / шевроны (~35%). */
    val textFaint: Color,
    /** Заливка карточки (стекло). */
    val glassFill: Color,
    /** Более заметная стеклянная заливка — пилюли/поля/чипы. */
    val glassFillStrong: Color,
    /** Тонкая рамка стекла. */
    val glassBorder: Color,
    /** Плавающие бары (мини-плеер, контролы) — матовая поверхность поверх контента. */
    val barSurface: Color,
    /** Рамка плавающих баров. */
    val barBorder: Color,
)

/** Тёмная база: белое стекло на #0A0A12. */
val DarkAuroraColors = AuroraColors(
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.55f),
    textTertiary = Color.White.copy(alpha = 0.45f),
    textFaint = Color.White.copy(alpha = 0.35f),
    glassFill = Color(0x12FFFFFF),
    glassFillStrong = Color(0x1FFFFFFF),
    glassBorder = Color(0x24FFFFFF),
    barSurface = Color(0xE016141E),
    barBorder = Color(0x1AFFFFFF),
)

/** Светлая база: белое матовое стекло на #F4F1EC, тёмный текст. */
val LightAuroraColors = AuroraColors(
    textPrimary = InkLight,
    textSecondary = InkLight.copy(alpha = 0.55f),
    textTertiary = InkLight.copy(alpha = 0.45f),
    textFaint = InkLight.copy(alpha = 0.32f),
    glassFill = Color.White.copy(alpha = 0.72f),
    glassFillStrong = Color.White.copy(alpha = 0.72f),
    glassBorder = InkLight.copy(alpha = 0.08f),
    barSurface = Color.White.copy(alpha = 0.82f),
    barBorder = InkLight.copy(alpha = 0.08f),
)

/** Токены Aurora Glass для текущей темы (тёмная/светлая). */
@Composable
fun auroraColors(): AuroraColors =
    if (LocalThemeIsDark.current.value) DarkAuroraColors else LightAuroraColors

/** Градиент плитки-иконки: accent → фиолетовый (как в макете Aurora Glass). */
fun iconTileBrush(accent: Color): Brush = Brush.linearGradient(listOf(accent, AuroraPurple))
