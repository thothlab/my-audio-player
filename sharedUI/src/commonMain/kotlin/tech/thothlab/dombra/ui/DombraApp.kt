package tech.thothlab.dombra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.presentation.player.PlayerState
import tech.thothlab.dombra.theme.AppTheme

/**
 * Корневой UI Dombra. Пока — вертикальный срез-скелет: проигрывание демо-трека
 * через реальный `PlaybackController` + платформенный `AudioEngine` (проверка
 * тракта на устройстве). Экран библиотеки/плеера — T06/T08.
 */
@Composable
fun DombraApp(
    graph: AppGraph,
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {},
) = AppTheme(onThemeChanged) {
    val state: PlayerState by graph.playback.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dombra", style = MaterialTheme.typography.displaySmall)

        Text(
            text = state.currentTrack?.title ?: "нет трека",
            style = MaterialTheme.typography.titleMedium,
        )
        Text("состояние: ${stateLabel(state)}", style = MaterialTheme.typography.bodyMedium)
        Text(
            "позиция: ${state.positionMs} мс" + (state.durationMs?.let { " / $it мс" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
        )
        state.error?.let { Text("ошибка: ${it.message}", style = MaterialTheme.typography.bodySmall) }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = graph.demoTrack != null,
                onClick = { graph.demoTrack?.let { graph.playback.playNow(it) } },
            ) { Text("Демо-трек") }

            Button(
                enabled = state.currentTrack != null,
                onClick = { graph.playback.togglePlayPause() },
            ) { Text(if (state.isPlaying) "Пауза" else "Играть") }

            Button(
                enabled = state.hasQueue,
                onClick = { graph.playback.stop() },
            ) { Text("Стоп") }
        }
    }
}

private fun stateLabel(state: PlayerState): String = when {
    state.error != null -> "ошибка"
    state.isBuffering -> "буферизация"
    state.isPlaying -> "играет"
    state.currentTrack != null -> "пауза"
    else -> "простой"
}
