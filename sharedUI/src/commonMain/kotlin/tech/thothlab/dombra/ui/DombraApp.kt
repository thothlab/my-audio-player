package tech.thothlab.dombra.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.AppSettings
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.presentation.player.PlayerState
import tech.thothlab.dombra.theme.AppTheme

/**
 * Корневой UI Dombra: собственная лёгкая навигация (Navigator/[Screen]) —
 * библиотека ↔ плеер ↔ настройки. Библиотека держит нижний мини-плеер (тап → плеер);
 * системная «назад» уходит на предыдущий экран.
 */
@Composable
fun DombraApp(
    graph: AppGraph,
    onPickFolder: (() -> Unit)? = null,
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {},
) {
    val appSettings by graph.settings.settings.collectAsState(initial = AppSettings())
    AppTheme(onThemeChanged, accent = appSettings.accentColor, themeMode = appSettings.theme) {
        val nav = remember { Navigator(Screen.Library) }
        val player: PlayerState by graph.playback.state.collectAsState()

        // Системная кнопка «назад» (Android) → предыдущий экран.
        PlatformBackHandler(enabled = nav.canPop) { nav.pop() }

        when (nav.current) {
            Screen.Library -> LibraryScreen(
                graph = graph,
                player = player,
                onPickFolder = onPickFolder,
                onOpenSettings = { nav.push(Screen.Settings) },
                onOpenPlayer = { nav.push(Screen.Player) },
            )

            Screen.Player -> {
                if (player.currentTrack != null) {
                    PlayerScreen(graph, onBack = { nav.pop() })
                } else {
                    // Трек исчез (очередь очищена) — вернуться из плеера.
                    LaunchedEffect(Unit) { nav.pop() }
                }
            }

            Screen.Settings -> SettingsScreen(graph, appSettings, onBack = { nav.pop() })
        }
    }
}

@Composable
private fun LibraryScreen(
    graph: AppGraph,
    player: PlayerState,
    onPickFolder: (() -> Unit)?,
    onOpenSettings: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val tracks: List<Track> by graph.library.tracks().collectAsState(initial = emptyList())
    val scanning: Boolean by graph.indexer.isScanning.collectAsState(initial = false)

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Library)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Dombra", style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onPickFolder != null) {
                        Button(onClick = onPickFolder, enabled = !scanning) { Text("Выбрать папку") }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "настройки")
                    }
                }
            }

            val status = when {
                scanning -> "Индексация…"
                tracks.isEmpty() -> "Выберите папку с музыкой"
                else -> "Треков: ${tracks.size}"
            }
            Text(status, style = MaterialTheme.typography.bodyMedium)

            player.error?.let { err ->
                Text(
                    text = "⚠ ${err.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = if (player.currentTrack != null) 84.dp else 0.dp),
            ) {
                items(tracks, key = { it.stableId }) { track ->
                    TrackRow(
                        track = track,
                        artwork = graph.artwork,
                        isCurrent = track.stableId == player.currentTrack?.stableId,
                        onClick = { graph.playback.playNow(track, tracks) },
                    )
                }
            }
        }

        MiniPlayer(
            graph = graph,
            player = player,
            onExpand = onOpenPlayer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TrackRow(
    track: Track,
    artwork: tech.thothlab.dombra.domain.ports.ArtworkRepository,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val accent = tech.thothlab.dombra.theme.LocalAccentColor.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(
            artwork = artwork,
            stableId = track.stableId,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            modifier = Modifier.size(48.dp),
            iconScale = 0.5f,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
