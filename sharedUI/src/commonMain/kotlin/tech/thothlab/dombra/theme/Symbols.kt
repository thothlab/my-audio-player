package tech.thothlab.dombra.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import dombra.sharedui.generated.resources.Res
import dombra.sharedui.generated.resources.material_symbols_rounded
import dombra.sharedui.generated.resources.material_symbols_rounded_fill
import org.jetbrains.compose.resources.Font

/**
 * Иконки дизайна — шрифт Material Symbols Rounded (тот же набор, что в handoff-макете),
 * а не приближённый Icons.Rounded.*. Две статические инстанции (обычная и залитая, FILL 1)
 * забандлены в composeResources/font/, глифы рисуются по codepoint из [Sym].
 */
class SymbolFonts(val regular: FontFamily, val filled: FontFamily)

val LocalSymbolFonts = compositionLocalOf<SymbolFonts?> { null }

/** Загружает обе инстанции символьного шрифта (кэшируется композицией). */
@Composable
fun rememberSymbolFonts(): SymbolFonts {
    val reg = Font(Res.font.material_symbols_rounded)
    val fil = Font(Res.font.material_symbols_rounded_fill)
    return remember(reg, fil) { SymbolFonts(FontFamily(reg), FontFamily(fil)) }
}

/**
 * Рисует один глиф Material Symbols Rounded. Аналог androidx.compose.material3.Icon,
 * но по шрифту-иконам дизайна. size задаётся в dp и не масштабируется системным font-scale.
 */
@Composable
fun Symbol(
    icon: Char,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    val fonts = LocalSymbolFonts.current ?: rememberSymbolFonts()
    val sizeSp = with(LocalDensity.current) { size.toSp() }
    BasicText(
        text = icon.toString(),
        modifier = modifier,
        style = TextStyle(
            color = tint,
            fontFamily = if (filled) fonts.filled else fonts.regular,
            fontSize = sizeSp,
            lineHeight = sizeSp,
            textAlign = TextAlign.Center,
        ),
    )
}

/**
 * Codepoints Material Symbols Rounded (сабсет — только используемые глифы).
 * Значения из MaterialSymbolsRounded[...].codepoints (Google, OFL).
 */
object Sym {
    const val Add = '\ue145'
    const val AddCircle = '\ue990'
    const val Airplay = '\ue055'
    const val Album = '\ue019'
    const val Cancel = '\ue888'
    const val Check = '\ue668'
    const val ChevronLeft = '\ue5cb'
    const val ChevronRight = '\ue5cc'
    const val Close = '\ue5cd'
    const val Cloud = '\uf15c'
    const val Dns = '\ue875'
    const val DragIndicator = '\ue945'
    const val ExpandMore = '\ue5cf'
    const val Favorite = '\ue87e'
    const val Folder = '\ue2c7'
    const val FolderOpen = '\ue2c8'
    const val GraphicEq = '\ue1b8'
    const val Group = '\uea21'
    const val Info = '\ue88e'
    const val KeyboardArrowDown = '\ue313'
    const val LibraryMusic = '\ue030'
    const val Lock = '\ue899'
    const val Lyrics = '\uec0b'
    const val MoreHoriz = '\ue5d3'
    const val MoreVert = '\ue5d4'
    const val MusicNote = '\ue405'
    const val Pause = '\ue034'
    const val PauseCircle = '\ue1a2'
    const val PlayArrow = '\ue037'
    const val PlaylistAdd = '\ue03b'
    const val QueueMusic = '\ue03d'
    const val Refresh = '\ue5d5'
    const val Repeat = '\ue040'
    const val RepeatOne = '\ue041'
    const val Search = '\uef7a'
    const val Settings = '\ue8b8'
    const val Shuffle = '\ue043'
    const val SkipNext = '\ue044'
    const val SkipPrevious = '\ue045'
    const val SwapVert = '\ue8d5'
    const val UploadFile = '\ue9fc'
    const val VolumeUp = '\ue050'
}
