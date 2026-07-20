package tech.thothlab.dombra.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.AppSettings
import tech.thothlab.dombra.domain.model.HomeSectionId
import tech.thothlab.dombra.presentation.player.PlayerState
import tech.thothlab.dombra.theme.AppTheme

/**
 * Корневой UI Dombra: сплеш → (первый запуск) онбординг → основная оболочка.
 * Основная оболочка — собственная лёгкая навигация (Navigator/[Screen]): «Медиатека» →
 * разделы/группы → плеер/настройки; экраны держат нижний мини-плеер; системная «назад» — назад.
 */
@Composable
fun DombraApp(
    graph: AppGraph,
    onPickFolder: (() -> Unit)? = null,
    onThemeChanged: @Composable (isDark: Boolean) -> Unit = {},
) {
    // initial = null → пока реальные настройки не загрузились, держим сплеш и не мигаем онбордингом.
    val appSettings by graph.settings.settings.collectAsState(initial = null)
    val settings = appSettings ?: AppSettings()
    val scope = rememberCoroutineScope()

    AppTheme(onThemeChanged, accent = settings.accentColor, themeMode = settings.theme) {
        var splashElapsed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            delay(1000)
            splashElapsed = true
        }

        when {
            !(appSettings != null && splashElapsed) -> SplashScreen()

            !settings.onboardingDone -> OnboardingScreen(
                onPickFolder = onPickFolder,
                onFinish = { scope.launch { graph.settings.update { it.copy(onboardingDone = true) } } },
            )

            else -> MainShell(graph, onPickFolder, settings)
        }
    }
}

@Composable
private fun MainShell(
    graph: AppGraph,
    onPickFolder: (() -> Unit)?,
    settings: AppSettings,
) {
    val nav = remember { Navigator(Screen.Home) }
    val player: PlayerState by graph.playback.state.collectAsState()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    // Состояние поиска держим здесь (над навигацией), чтобы «назад» восстанавливал запрос/область.
    val searchState = remember { SearchUiState() }

    // Системная кнопка «назад» (Android) → предыдущий экран.
    PlatformBackHandler(enabled = nav.canPop) { nav.pop() }

    when (val screen = nav.current) {
        Screen.Home -> MediaHomeScreen(
            graph = graph,
            player = player,
            refreshing = refreshing,
            onOpenSection = { section ->
                if (section == HomeSectionId.ADD_SONGS) onPickFolder?.invoke()
                else nav.push(Screen.Collection(section))
            },
            onOpenPlayer = { nav.push(Screen.Player) },
            onOpenSettings = { nav.push(Screen.Settings) },
            onSearch = { nav.push(Screen.Search) },
            onRefresh = {
                scope.launch {
                    refreshing = true
                    try {
                        graph.refresh()
                    } finally {
                        refreshing = false
                    }
                }
            },
        )

        is Screen.Collection -> CollectionScreen(
            graph = graph,
            section = screen.section,
            player = player,
            onBack = { nav.pop() },
            onOpenGroup = { nav.push(it) },
            onOpenPlayer = { nav.push(Screen.Player) },
        )

        is Screen.Tracks -> TracksScreen(
            graph = graph,
            title = screen.title,
            ref = screen.ref,
            player = player,
            onBack = { nav.pop() },
            onOpenPlayer = { nav.push(Screen.Player) },
        )

        Screen.Search -> SearchScreen(
            graph = graph,
            state = searchState,
            onClose = { nav.pop() },
            onPlaySong = { track, list -> graph.playback.playNow(track, list) },
            onOpenArtist = { nav.push(Screen.Tracks(it.name, TrackListRef.Artist(it.id))) },
            onOpenAlbum = { nav.push(Screen.Tracks(it.title, TrackListRef.Album(it.id))) },
        )

        Screen.Server -> ServerScreen(
            graph = graph,
            onBack = { nav.pop() },
            onOpenAlbum = { id, title -> nav.push(Screen.RemoteAlbum(id, title)) },
        )

        is Screen.RemoteAlbum -> RemoteAlbumScreen(
            graph = graph,
            albumId = screen.albumId,
            title = screen.title,
            onBack = { nav.pop() },
        )

        Screen.Player -> {
            if (player.currentTrack != null) {
                PlayerScreen(graph, onBack = { nav.pop() })
            } else {
                // Трек исчез (очередь очищена) — вернуться из плеера.
                LaunchedEffect(Unit) { nav.pop() }
            }
        }

        Screen.Settings -> SettingsScreen(
            graph, settings,
            onBack = { nav.pop() },
            onOpenServer = { nav.push(Screen.Server) },
            onOpenEqualizer = { nav.push(Screen.Equalizer) },
        )

        Screen.Equalizer -> EqualizerScreen(
            enabled = settings.equalizerEnabled,
            onEnabledChange = { on -> scope.launch { graph.settings.update { it.copy(equalizerEnabled = on) } } },
            onBack = { nav.pop() },
        )
    }
}
