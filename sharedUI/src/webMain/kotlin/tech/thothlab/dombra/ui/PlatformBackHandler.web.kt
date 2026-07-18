package tech.thothlab.dombra.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Web: браузерная «назад» не перехватывается здесь — no-op.
}
