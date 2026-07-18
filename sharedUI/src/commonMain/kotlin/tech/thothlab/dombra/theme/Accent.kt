package tech.thothlab.dombra.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import tech.thothlab.dombra.domain.model.AccentColor

/** Accent-цвет из Cosmos-палитры (hex RRGGBB → непрозрачный Color). */
fun AccentColor.toColor(): Color = Color(0xFF000000L or hex.toLong(16))

/** Текущий accent-цвет (по умолчанию Cosmos violet; задаётся из настроек — T05). */
val LocalAccentColor = compositionLocalOf { AccentColor.VIOLET.toColor() }
