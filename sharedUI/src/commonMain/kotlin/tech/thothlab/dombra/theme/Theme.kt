package tech.thothlab.dombra.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tech.thothlab.dombra.domain.model.AccentColor
import tech.thothlab.dombra.domain.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    scrim = ScrimLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,
    surfaceDim = SurfaceDimLight,
    surfaceBright = SurfaceBrightLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    scrim = ScrimDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
    surfaceDim = SurfaceDimDark,
    surfaceBright = SurfaceBrightDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
)

internal val LocalThemeIsDark = compositionLocalOf { mutableStateOf(true) }

@Composable
internal fun AppTheme(
    onThemeChanged: @Composable (isDark: Boolean) -> Unit,
    accent: AccentColor = AccentColor.VIOLET,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemIsDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val isDarkState = remember(isDark) { mutableStateOf(isDark) }
    // Accent Aurora Glass — единый #b11491 (Plum) по умолчанию в обеих темах (решение по макету).
    val accentColor = accent.toColor()
    val symbolFonts = rememberSymbolFonts()
    CompositionLocalProvider(
        LocalThemeIsDark provides isDarkState,
        LocalAccentColor provides accentColor,
        LocalSymbolFonts provides symbolFonts,
    ) {
        val dark by isDarkState
        onThemeChanged(!dark)
        // Accent (Aurora) управляет Material-компонентами: кнопки, чипы, слайдеры, индикаторы.
        // Поверхности/текст — по базе Aurora Glass: тёмная #0A0A12 / светлая #F4F1EC.
        val base = if (dark) DarkColorScheme else LightColorScheme
        val ink = if (dark) Color(0xFFF2F2F5) else Color(0xFF1B1620)
        val bg = if (dark) Color(0xFF0A0A12) else Color(0xFFF4F1EC)
        // Нейтральные Aurora-поверхности вместо мёртвой Cosmos-blue палитры (Color.kt):
        // сюда падают M3-компоненты (меню, sheets, диалоги, плейсхолдер обложки, обводки).
        val scheme = base.copy(
            primary = accentColor,
            onPrimary = Color.White,
            primaryContainer = accentColor,
            onPrimaryContainer = Color.White,
            secondary = accentColor,
            onSecondary = Color.White,
            secondaryContainer = accentColor,
            onSecondaryContainer = Color.White,
            tertiary = AuroraPurple,
            surfaceTint = accentColor,
            background = bg,
            surface = bg,
            onBackground = ink,
            onSurface = ink,
            onSurfaceVariant = ink.copy(alpha = 0.55f),
            surfaceVariant = if (dark) Color(0xFF272230) else Color(0xFFE7E3EC),
            outline = if (dark) Color(0x33FFFFFF) else Color(0x33000000),
            outlineVariant = if (dark) Color(0x1FFFFFFF) else Color(0x1F000000),
            surfaceContainerLowest = if (dark) Color(0xFF0C0A12) else Color(0xFFFFFFFF),
            surfaceContainerLow = if (dark) Color(0xFF141019) else Color(0xFFF6F3F8),
            surfaceContainer = if (dark) Color(0xFF17141E) else Color(0xFFF1EDF3),
            surfaceContainerHigh = if (dark) Color(0xFF1F1B27) else Color(0xFFECE7EF),
            surfaceContainerHighest = if (dark) Color(0xFF272230) else Color(0xFFE6E1EA),
        )
        // Заголовки экранов — жирные и чуть компактнее (по макету заголовки 20–23px, w700;
        // Material headlineSmall по умолчанию 24sp/regular — выглядел крупнее и легче макета).
        val baseType = Typography()
        val typography = baseType.copy(
            headlineSmall = baseType.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = (-0.3).sp,
            ),
            titleMedium = baseType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = { Surface(content = content) }
        )
    }
}
