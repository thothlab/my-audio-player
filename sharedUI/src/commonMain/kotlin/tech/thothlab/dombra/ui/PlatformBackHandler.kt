package tech.thothlab.dombra.ui

import androidx.compose.runtime.Composable

/**
 * Перехват системной кнопки «назад». На Android — реальный `BackHandler`;
 * на остальных таргетах (jvm/web/ios) системной «назад» нет — no-op.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
