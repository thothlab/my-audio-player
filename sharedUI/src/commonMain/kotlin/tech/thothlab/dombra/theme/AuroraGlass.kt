package tech.thothlab.dombra.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Aurora Glass (направление 1C) — стеклянные поверхности и градиенты на тёмном фоне #141417.
 * Карточки — белая заливка с низкой прозрачностью + тонкая светлая рамка; плитки-иконки —
 * градиент accent → фиолетовый.
 */

/** Дополнительный цвет Aurora — фиолетовый (второй конец градиентов плиток/обложек). */
val AuroraPurple = Color(0xFFA06BFF)

/** «Стеклянная» заливка карточки (белый ~6%). */
val GlassFill = Color(0x10FFFFFF)

/** Более заметная стеклянная заливка (белый ~9%) — контролы/пилюли. */
val GlassFillStrong = Color(0x17FFFFFF)

/** Тонкая светлая рамка стекла (белый ~10%). */
val GlassBorder = Color(0x1AFFFFFF)

/** Тёмное стекло для плавающих баров/листов (мини-плеер, контролы) — читаемо поверх контента. */
val GlassDark = Color(0xE016141E)

/** Градиент плитки-иконки: accent → фиолетовый (как в макете Aurora Glass). */
fun iconTileBrush(accent: Color): Brush = Brush.linearGradient(listOf(accent, AuroraPurple))
