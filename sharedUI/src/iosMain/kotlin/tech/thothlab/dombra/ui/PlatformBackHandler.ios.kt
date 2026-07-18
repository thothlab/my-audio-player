package tech.thothlab.dombra.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS: назад через жест/навбар хоста — no-op на уровне общего UI.
}
