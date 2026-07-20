package tech.thothlab.dombra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import tech.thothlab.dombra.theme.Sym
import tech.thothlab.dombra.theme.Symbol
import tech.thothlab.dombra.theme.auroraColors
import tech.thothlab.dombra.theme.iconTileBrush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Метаданные раздела «Медиатеки» — глиф (Material Symbols Rounded), цвет плитки, заголовок и подпись. */
private data class SectionMeta(
    val title: String,
    val subtitle: String,
    val glyph: Char,
    val color: Color,
    val filled: Boolean = true,
)

private fun sectionMeta(id: HomeSectionId, count: Int): SectionMeta = when (id) {
    HomeSectionId.ALL_SONGS -> SectionMeta("Все песни", "$count песен", Sym.MusicNote, Color(0xFFB07CE8))
    HomeSectionId.LIKED_SONGS -> SectionMeta("Любимые песни", "Ваше избранное", Sym.Favorite, Color(0xFFE0607A))
    HomeSectionId.PLAYLISTS -> SectionMeta("Плейлисты", "Ваши плейлисты", Sym.QueueMusic, Color(0xFF63C67F))
    HomeSectionId.ARTISTS -> SectionMeta("Исполнители", "Просмотр по исполнителям", Sym.Group, Color(0xFFB07CE8))
    HomeSectionId.ALBUMS -> SectionMeta("Альбомы", "Просмотр по альбомам", Sym.Album, Color(0xFFEEA95C))
    HomeSectionId.ADD_SONGS -> SectionMeta("Открыть файлы", "Импортировать музыкальные файлы", Sym.Add, Color(0xFF5B9BE8), filled = false)
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
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp),
        ) {
            // Шапка.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EqMark(size = 38.dp)
                Text(
                    "Медиатека",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                // Прогресс обновления — ровно на месте иконки «Обновить» (тот же 48dp-слот).
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefresh) { Symbol(Sym.Refresh, size = 23.dp) }
                    }
                }
                IconButton(onClick = onSearch) { Symbol(Sym.Search, size = 23.dp) }
                IconButton(onClick = onOpenSettings) {
                    Symbol(Sym.Settings, size = 23.dp, tint = LocalAccentColorSafe())
                }
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
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun SectionCard(meta: SectionMeta, onClick: () -> Unit) {
    val c = auroraColors()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = c.glassFill,
        border = BorderStroke(1.dp, c.glassBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(meta.glyph, meta.color, size = 54.dp, iconSize = 28.dp, filled = meta.filled)
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
            Symbol(Sym.ChevronRight, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Градиентная скруглённая плитка-иконка (Aurora Glass: цвет секции → фиолетовый, белый глиф). */
@Composable
internal fun IconTile(glyph: Char, color: Color, size: Dp = 48.dp, iconSize: Dp = 24.dp, filled: Boolean = true) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(iconTileBrush(color)),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(glyph, filled = filled, size = iconSize, tint = Color.White)
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
    glyph: Char,
    iconColor: Color,
    onClick: () -> Unit,
    cover: @Composable (() -> Unit)? = null,
) {
    val c = auroraColors()
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = c.glassFill,
        border = BorderStroke(1.dp, c.glassBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (cover != null) cover() else IconTile(glyph, iconColor, size = 48.dp, iconSize = 24.dp)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            Symbol(Sym.ChevronRight, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Кэш «альбом → stableId первого трека» — чтобы список альбомов не перечитывал обложку при скролле. */
private val albumCoverIdCache = HashMap<String, String?>()

/** Карточка альбома: обложка из артворка первого трека, иначе стилизованная плитка-диск. */
@Composable
private fun AlbumGroupCard(graph: AppGraph, album: Album, onClick: () -> Unit) {
    var coverId by remember(album.id) { mutableStateOf(albumCoverIdCache[album.id]) }
    LaunchedEffect(album.id) {
        if (!albumCoverIdCache.containsKey(album.id)) {
            val id = runCatching { graph.library.albumTracks(album.id).first().firstOrNull()?.stableId }.getOrNull()
            albumCoverIdCache[album.id] = id
            coverId = id
        }
    }
    MediaGroupCard(album.title, Sym.Album, AlbumTileColor, onClick) {
        if (coverId != null) {
            ArtworkImage(
                artwork = graph.artwork,
                stableId = coverId,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp),
                iconScale = 0.5f,
            )
        } else {
            IconTile(Sym.Album, AlbumTileColor)
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
                        a.name, Sym.Group, ArtistTileColor,
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
                        pl.title, Sym.QueueMusic, PlaylistTileColor,
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
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Symbol(Sym.ChevronLeft, size = 28.dp, tint = MaterialTheme.colorScheme.onSurface) }
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
                    val current = track.stableId == player.currentTrack?.stableId
                    TrackRow(
                        track = track,
                        artwork = graph.artwork,
                        isCurrent = current,
                        isPlaying = current && player.isPlaying,
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
                .windowInsetsPadding(WindowInsets.navigationBars)
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
    val c = auroraColors()
    val dark = tech.thothlab.dombra.theme.LocalThemeIsDark.current.value
    val popupBg = if (dark) Color(0xFF1B1922) else Color(0xFFF5F3F6)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = accent.copy(alpha = 0.14f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffle) {
                Symbol(Sym.Shuffle, size = 20.dp, tint = accent)
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Symbol(Sym.SwapVert, size = 20.dp, tint = accent)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = popupBg,
                    border = BorderStroke(1.dp, c.glassBorder),
                ) {
                    Text(
                        "СОРТИРОВКА",
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp,
                        color = c.textTertiary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                    )
                    SortOrder.entries.forEach { o ->
                        val selected = o == order
                        DropdownMenuItem(
                            text = {
                                Text(
                                    o.label,
                                    color = if (selected) accent else c.textPrimary,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            onClick = { onPick(o); menuOpen = false },
                            trailingIcon = {
                                if (selected) Symbol(Sym.Check, size = 20.dp, tint = accent)
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
                .windowInsetsPadding(WindowInsets.systemBars)
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
                .windowInsetsPadding(WindowInsets.navigationBars)
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
        IconButton(onClick = onBack) { Symbol(Sym.ChevronLeft, size = 28.dp, tint = MaterialTheme.colorScheme.onSurface) }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Ряд трека: миниатюра + название (accent если текущий) + исполнитель. Текущий — стеклянная
 *  подсветка (по макету) + анимированный индикатор «звучания» справа. */
@Composable
internal fun TrackRow(
    track: Track,
    artwork: tech.thothlab.dombra.domain.ports.ArtworkRepository,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalAccentColorSafe()
    val c = auroraColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCurrent) {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.glassFillStrong)
                        .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (isCurrent) 8.dp else 0.dp, vertical = if (isCurrent) 8.dp else 6.dp),
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
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
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
        if (isCurrent) PlayingBars(color = accent, playing = isPlaying)
    }
}

@Composable
private fun LocalAccentColorSafe(): Color = tech.thothlab.dombra.theme.LocalAccentColor.current
