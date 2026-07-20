package tech.thothlab.dombra.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Aurora Glass — стеклянные поверхности и градиенты. Один язык, две базы:
 *  - тёмная: стекло (белый с низкой прозрачностью) на почти-чёрном фоне #0A0A12, светлый текст;
 *  - светлая: белое матовое стекло на тёплой базе #F4F1EC, тёмный текст.
 * Плитки-иконки — градиент accent → фиолетовый (одинаково в обеих темах).
 * Все alpha/цвета выверены по дизайн-макету (handoff turn-2 · тёмная, turn-8 · светлая).
 */

/** Дополнительный цвет Aurora — фиолетовый (второй конец градиентов плиток/обложек). */
val AuroraPurple = Color(0xFFA06BFF)

/** Тёмный текст светлой темы (тёплый near-black). */
private val InkLight = Color(0xFF1B1620)

/**
 * Набор theme-aware токенов Aurora Glass. Берётся через [auroraColors].
 * Разделение поверхностей — по макету: мягкая карточка (.06) / поле-чип-активная строка (.08) /
 * плавающий бар (.09) / тёмный поповер (#16141E .86).
 */
class AuroraColors(
    /** Основной текст (#fff / #1B1620). */
    val textPrimary: Color,
    /** Вторичный текст — подзаголовки (~.55). */
    val textSecondary: Color,
    /** Приглушённый текст — eyebrow/meta (~.45). */
    val textTertiary: Color,
    /** Едва заметный текст — шевроны (~.35 / .32). */
    val textFaint: Color,
    /** Заливка мягкой карточки (список «Медиатека», карта звука, сегмент-трек). */
    val glassFill: Color,
    /** Рамка мягкой карточки. */
    val glassBorder: Color,
    /** Более заметная заливка — поля/чипы/активная строка/круглые кнопки. */
    val glassFillStrong: Color,
    /** Рамка заметной заливки. */
    val glassBorderStrong: Color,
    /** Плавающие бары (мини-плеер, транспорт, пилюля сортировки). */
    val barSurface: Color,
    /** Рамка плавающих баров. */
    val barBorder: Color,
    /** Поповер/выпадающее меню (тёмное стекло / светлая плашка). */
    val popoverSurface: Color,
    /** Рамка поповера. */
    val popoverBorder: Color,
)

/** Тёмная база: белое стекло на #0A0A12. */
val DarkAuroraColors = AuroraColors(
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.55f),
    textTertiary = Color.White.copy(alpha = 0.45f),
    textFaint = Color.White.copy(alpha = 0.35f),
    glassFill = Color(0x0FFFFFFF),        // white .06
    glassBorder = Color(0x1AFFFFFF),      // white .10
    glassFillStrong = Color(0x14FFFFFF),  // white .08
    glassBorderStrong = Color(0x1FFFFFFF),// white .12
    barSurface = Color(0x17FFFFFF),       // white .09
    barBorder = Color(0x24FFFFFF),        // white .14
    popoverSurface = Color(0xDB16141E),   // #16141E .86
    popoverBorder = Color(0x24FFFFFF),    // white .14
)

/** Светлая база: белое матовое стекло на #F4F1EC, тёмный текст. */
val LightAuroraColors = AuroraColors(
    textPrimary = InkLight,
    textSecondary = InkLight.copy(alpha = 0.55f),
    textTertiary = InkLight.copy(alpha = 0.45f),
    textFaint = InkLight.copy(alpha = 0.32f),
    glassFill = Color.White.copy(alpha = 0.72f),
    glassBorder = InkLight.copy(alpha = 0.07f),
    glassFillStrong = Color.White.copy(alpha = 0.72f),
    glassBorderStrong = InkLight.copy(alpha = 0.08f),
    barSurface = Color.White.copy(alpha = 0.82f),
    barBorder = InkLight.copy(alpha = 0.08f),
    popoverSurface = Color(0xFFF5F3F6),
    popoverBorder = InkLight.copy(alpha = 0.08f),
)

/** Токены Aurora Glass для текущей темы (тёмная/светлая). */
@Composable
fun auroraColors(): AuroraColors =
    if (LocalThemeIsDark.current.value) DarkAuroraColors else LightAuroraColors

/** Градиент плитки-иконки: accent → фиолетовый (как в макете Aurora Glass). */
fun iconTileBrush(accent: Color): Brush = Brush.linearGradient(listOf(accent, AuroraPurple))
