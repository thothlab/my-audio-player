package tech.thothlab.dombra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.HomeSectionId
import tech.thothlab.dombra.domain.model.Playlist
import tech.thothlab.dombra.domain.model.SortOrder
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.model.sortedByOrder
import tech.thothlab.dombra.presentation.player.PlayerState

/** Метаданные раздела «Медиатеки» — иконка, цвет плитки, заголовок и подпись (по образцу Cosmos). */
private data class SectionMeta(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
)

private fun sectionMeta(id: HomeSectionId, count: Int): SectionMeta = when (id) {
    HomeSectionId.ALL_SONGS -> SectionMeta("Все песни", "$count песен", Icons.Filled.MusicNote, Color(0xFFB07CE8))
    HomeSectionId.LIKED_SONGS -> SectionMeta("Любимые песни", "Ваше избранное", Icons.Filled.Favorite, Color(0xFFE0607A))
    HomeSectionId.PLAYLISTS -> SectionMeta("Плейлисты", "Ваши плейлисты", Icons.AutoMirrored.Filled.QueueMusic, Color(0xFF63C67F))
    HomeSectionId.ARTISTS -> SectionMeta("Исполнители", "Просмотр по исполнителям", Icons.Filled.Group, Color(0xFFB07CE8))
    HomeSectionId.ALBUMS -> SectionMeta("Альбомы", "Просмотр по альбомам", Icons.Filled.Album, Color(0xFFEEA95C))
    HomeSectionId.ADD_SONGS -> SectionMeta("Открыть файлы", "Импортировать музыкальные файлы", Icons.Filled.AddCircle, Color(0xFF5B9BE8))
}

/** Главный экран «Медиатека»: шапка (иконка/обновить/поиск/настройки) + карточки-разделы. */
@Composable
fun MediaHomeScreen(
    graph: AppGraph,
    player: PlayerState,
    refreshing: Boolean,
    onOpenSection: (HomeSectionId) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    val songCount by graph.library.tracks().map { it.size }.collectAsState(initial = 0)

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Library)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            // Шапка.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = LocalAccentColorSafe(), modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(20.dp))
                    }
                }
                Text(
                    "Медиатека",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "обновить") }
                }
                IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, "поиск") }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "настройки") }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = if (player.currentTrack != null) 92.dp else 12.dp),
            ) {
                items(HomeSectionId.entries) { id ->
                    val count = if (id == HomeSectionId.ALL_SONGS) songCount else 0
                    SectionCard(sectionMeta(id, count)) { onOpenSection(id) }
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
private fun SectionCard(meta: SectionMeta, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(meta.icon, meta.color, size = 54.dp, iconSize = 28.dp)
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(meta.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    meta.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Цветная скруглённая плитка-иконка в стиле карточек «Медиатеки». */
@Composable
internal fun IconTile(icon: ImageVector, color: Color, size: Dp = 48.dp, iconSize: Dp = 24.dp) {
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.20f), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(iconSize))
        }
    }
}

/** Цвета плиток разделов (для карточек списков групп). */
internal val ArtistTileColor = Color(0xFFB07CE8)
internal val AlbumTileColor = Color(0xFFEEA95C)
internal val PlaylistTileColor = Color(0xFF63C67F)

/** Карточка группы в списках (плейлист/исполнитель/альбом) — в стиле «Медиатеки». */
@Composable
private fun MediaGroupCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    cover: @Composable (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (cover != null) cover() else IconTile(icon, iconColor, size = 48.dp, iconSize = 24.dp)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Карточка альбома: обложка из артворка первого трека, иначе стилизованная плитка-диск. */
@Composable
private fun AlbumGroupCard(graph: AppGraph, album: Album, onClick: () -> Unit) {
    val coverId by produceState<String?>(null, album.id) {
        value = runCatching { graph.library.albumTracks(album.id).first().firstOrNull()?.stableId }.getOrNull()
    }
    MediaGroupCard(album.title, Icons.Filled.Album, AlbumTileColor, onClick) {
        if (coverId != null) {
            ArtworkImage(
                artwork = graph.artwork,
                stableId = coverId,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp),
                iconScale = 0.5f,
            )
        } else {
            IconTile(Icons.Filled.Album, AlbumTileColor)
        }
    }
}

/** Экран раздела: песни/любимые → список треков; исполнители/альбомы/плейлисты → список групп. */
@Composable
fun CollectionScreen(
    graph: AppGraph,
    section: HomeSectionId,
    player: PlayerState,
    onBack: () -> Unit,
    onOpenGroup: (Screen.Tracks) -> Unit,
    onOpenPlayer: () -> Unit,
) {
    when (section) {
        HomeSectionId.ALL_SONGS -> {
            val tracks by graph.library.tracks().collectAsState(initial = emptyList())
            TrackListScreen(graph, "Все песни", "all", tracks, player, onBack, onOpenPlayer)
        }
        HomeSectionId.LIKED_SONGS -> {
            val tracks by graph.library.favoriteTracks().collectAsState(initial = emptyList())
            TrackListScreen(graph, "Любимые песни", "liked", tracks, player, onBack, onOpenPlayer)
        }
        HomeSectionId.ARTISTS -> {
            val artists by graph.library.artists().collectAsState(initial = emptyList())
            GroupListScreen("Исполнители", player, onBack, onOpenPlayer, graph) {
                items(artists, key = { it.id }) { a: Artist ->
                    MediaGroupCard(
                        a.name, Icons.Filled.Group, ArtistTileColor,
                        onClick = { onOpenGroup(Screen.Tracks(a.name, TrackListRef.Artist(a.id))) },
                    )
                }
            }
        }
        HomeSectionId.ALBUMS -> {
            val albums by graph.library.albums().collectAsState(initial = emptyList())
            GroupListScreen("Альбомы", player, onBack, onOpenPlayer, graph) {
                items(albums, key = { it.id }) { al: Album ->
                    AlbumGroupCard(graph, al) {
                        onOpenGroup(Screen.Tracks(al.title, TrackListRef.Album(al.id)))
                    }
                }
            }
        }
        HomeSectionId.PLAYLISTS -> {
            val playlists by graph.playlists.playlists().collectAsState(initial = emptyList())
            GroupListScreen("Плейлисты", player, onBack, onOpenPlayer, graph) {
                items(playlists, key = { it.id }) { pl: Playlist ->
                    MediaGroupCard(
                        pl.title, Icons.AutoMirrored.Filled.QueueMusic, PlaylistTileColor,
                        onClick = { onOpenGroup(Screen.Tracks(pl.title, TrackListRef.Playlist(pl.id))) },
                    )
                }
            }
        }
        HomeSectionId.ADD_SONGS -> Unit // обрабатывается вызывающим (SAF-пикер)
    }
}

/** Список треков конкретной группы (исполнитель/альбом/плейлист). */
@Composable
fun TracksScreen(
    graph: AppGraph,
    title: String,
    ref: TrackListRef,
    player: PlayerState,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val flow = when (ref) {
        is TrackListRef.Artist -> graph.library.artistTracks(ref.id)
        is TrackListRef.Album -> graph.library.albumTracks(ref.id)
        is TrackListRef.Playlist -> combine(
            graph.playlists.playlist(ref.id),
            graph.library.tracks(),
        ) { pwi, all ->
            val byId = all.associateBy { it.stableId }
            pwi?.items?.mapNotNull { byId[it.trackStableId] } ?: emptyList()
        }
    }
    val collectionKey = when (ref) {
        is TrackListRef.Artist -> "artist:${ref.id}"
        is TrackListRef.Album -> "album:${ref.id}"
        is TrackListRef.Playlist -> "playlist:${ref.id}"
    }
    val tracks by flow.collectAsState(initial = emptyList())
    TrackListScreen(graph, title, collectionKey, tracks, player, onBack, onOpenPlayer)
}

/** Общий скаффолд списка треков: шапка (назад + заголовок + пилюля shuffle/sort) + список + мини-плеер. */
@Composable
private fun TrackListScreen(
    graph: AppGraph,
    title: String,
    collectionKey: String,
    tracks: List<Track>,
    player: PlayerState,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val settings by graph.settings.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val order = settings?.sortOrders?.get(collectionKey) ?: SortOrder.MANUAL
    val sorted = remember(tracks, order) { tracks.sortedByOrder(order) }

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Secondary)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "назад") }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp).weight(1f),
                )
                SortShufflePill(
                    order = order,
                    onShuffle = { if (sorted.isNotEmpty()) graph.playback.shufflePlay(sorted) },
                    onPick = { picked ->
                        scope.launch {
                            graph.settings.update { it.copy(sortOrders = it.sortOrders + (collectionKey to picked)) }
                        }
                    },
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = if (player.currentTrack != null) 92.dp else 8.dp),
            ) {
                items(sorted, key = { it.stableId }) { track ->
                    TrackRow(
                        track = track,
                        artwork = graph.artwork,
                        isCurrent = track.stableId == player.currentTrack?.stableId,
                        onClick = { graph.playback.playNow(track, sorted) },
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

/** Пилюля справа в шапке списка: кнопка shuffle-play + меню сортировки (по образцу Cosmos). */
@Composable
private fun SortShufflePill(
    order: SortOrder,
    onShuffle: () -> Unit,
    onPick: (SortOrder) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = LocalAccentColorSafe()
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = accent.copy(alpha = 0.14f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffle) {
                Icon(Icons.Filled.Shuffle, "в случайном порядке", tint = accent)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, "сортировка", tint = accent)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SortOrder.entries.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.label) },
                            onClick = { onPick(o); menuOpen = false },
                            trailingIcon = {
                                if (o == order) Icon(Icons.Filled.Check, null, tint = accent)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupListScreen(
    title: String,
    player: PlayerState,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    graph: AppGraph,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Secondary)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            CollectionHeader(title, onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = if (player.currentTrack != null) 92.dp else 8.dp),
                content = content,
            )
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
private fun CollectionHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "назад") }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Ряд трека: миниатюра + название (accent если текущий) + исполнитель. */
@Composable
internal fun TrackRow(
    track: Track,
    artwork: tech.thothlab.dombra.domain.ports.ArtworkRepository,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAccentColorSafe()
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
            shape = RoundedCornerShape(8.dp),
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

@Composable
private fun LocalAccentColorSafe(): Color = tech.thothlab.dombra.theme.LocalAccentColor.current
