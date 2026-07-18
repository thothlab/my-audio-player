package tech.thothlab.dombra.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop: системной кнопки «назад» нет — no-op.
}
