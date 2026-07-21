package tech.thothlab.dombra.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.AlbumSort
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.HomeSectionId
import tech.thothlab.dombra.domain.model.Playlist
import tech.thothlab.dombra.domain.model.SortOrder
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.model.sortedByOrder
import tech.thothlab.dombra.i18n.LocalStrings
import tech.thothlab.dombra.i18n.Strings
import tech.thothlab.dombra.presentation.player.PlayerState
import tech.thothlab.dombra.theme.AuroraPurple
import tech.thothlab.dombra.theme.Sym
import tech.thothlab.dombra.theme.Symbol
import tech.thothlab.dombra.theme.auroraColors
import tech.thothlab.dombra.theme.iconTileBrush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Метаданные раздела «Медиатеки» — глиф (Material Symbols Rounded), двухцветный градиент плитки, заголовок и подпись. */
private data class SectionMeta(
    val title: String,
    val subtitle: String,
    val glyph: Char,
    val startColor: Color,
    val endColor: Color,
    val filled: Boolean = true,
)

/** Двухцветные градиенты плиток разделов — по макету «Медиатека» (turn-2 §2A). */
private fun sectionMeta(id: HomeSectionId, count: Int, strings: Strings, accent: Color): SectionMeta = when (id) {
    HomeSectionId.ALL_SONGS -> SectionMeta(strings.allSongs, strings.songsCount(count), Sym.MusicNote, accent, AuroraPurple)
    HomeSectionId.LIKED_SONGS -> SectionMeta(strings.likedSongs, strings.yourFavorites, Sym.Favorite, Color(0xFFFF5A7A), Color(0xFFE11D48))
    HomeSectionId.PLAYLISTS -> SectionMeta(strings.playlists, strings.yourPlaylists, Sym.QueueMusic, Color(0xFFA06BFF), Color(0xFF6D28D9))
    HomeSectionId.ARTISTS -> SectionMeta(strings.artists, strings.browseByArtists, Sym.Group, Color(0xFF2DD4BF), Color(0xFF0D9488))
    HomeSectionId.ALBUMS -> SectionMeta(strings.albums, strings.browseByAlbums, Sym.Album, Color(0xFFF59E0B), Color(0xFFB45309))
    HomeSectionId.ADD_SONGS -> SectionMeta(strings.openFiles, strings.importMusicFiles, Sym.Add, Color(0xFF38BDF8), Color(0xFF2563EB), filled = false)
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
    val strings = LocalStrings.current
    val accent = LocalAccentColorSafe()

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
                    strings.mediaLibrary,
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
                    SectionCard(sectionMeta(id, count, strings, accent)) { onOpenSection(id) }
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
            IconTile(meta.glyph, meta.startColor, size = 46.dp, iconSize = 24.dp, filled = meta.filled, endColor = meta.endColor)
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                Text(meta.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    meta.subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Symbol(Sym.ChevronRight, size = 20.dp, tint = c.textFaint)
        }
    }
}

/** Градиентная скруглённая плитка-иконка (Aurora Glass: двухцветный градиент, белый глиф). */
@Composable
internal fun IconTile(
    glyph: Char,
    color: Color,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    filled: Boolean = true,
    endColor: Color = AuroraPurple,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(13.dp))
            .background(iconTileBrush(color, endColor)),
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

/** Экран «Альбомы»: список/плитка + сортировка (А–Я / Сначала новые / По году) + поиск. Выбор запоминается. */
@Composable
private fun AlbumsScreen(
    graph: AppGraph,
    player: PlayerState,
    onBack: () -> Unit,
    onOpenAlbum: (Screen.Tracks) -> Unit,
    onOpenPlayer: () -> Unit,
    onSearch: () -> Unit,
) {
    val strings = LocalStrings.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.settings.collectAsState(initial = null)
    val sort = settings?.albumSort ?: AlbumSort.TITLE
    val desc = settings?.albumSortDesc ?: false
    val grid = settings?.albumGridView ?: false
    val albumsData by graph.library.albums().collectAsState(initial = null)
    val loading = albumsData == null
    val albums = albumsData ?: emptyList()
    val tracks by graph.library.tracks().collectAsState(initial = emptyList())

    val sorted = remember(albums, tracks, sort, desc) {
        val recency: Map<String, Long> =
            if (sort == AlbumSort.RECENT) tracks.groupBy { it.albumId }.mapValues { e -> e.value.maxOf { it.addedAt } }
            else emptyMap()
        // Восходящая база + разворот по направлению; вторичный ключ — название (стабильность).
        val base: Comparator<Album> = when (sort) {
            AlbumSort.TITLE -> compareBy { it.title.lowercase() }
            AlbumSort.YEAR -> compareBy { it.year ?: Int.MIN_VALUE }
            AlbumSort.RECENT -> compareBy { recency[it.id] ?: Long.MIN_VALUE }
        }
        // Разворот всей сортировки (включая вторичный ключ) → направление меняет порядок
        // даже у альбомов с одинаковой датой/годом (иначе tiebreak «зависал» на А–Я).
        val full = base.thenBy { it.title.lowercase() }
        albums.sortedWith(if (desc) full.reversed() else full)
    }

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Secondary)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp),
        ) {
            // Тулбар: назад · «Альбомы» · вид · сортировка · поиск.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Symbol(Sym.ChevronLeft, size = 28.dp, tint = MaterialTheme.colorScheme.onSurface) }
                Text(
                    strings.albums,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp).weight(1f),
                )
                IconButton(onClick = { scope.launch { graph.settings.update { it.copy(albumGridView = !grid) } } }) {
                    ViewModeIcon(showGrid = !grid, tint = MaterialTheme.colorScheme.onSurface)
                }
                AlbumSortButton(
                    current = sort,
                    desc = desc,
                    onPick = { picked, pickedDesc ->
                        scope.launch { graph.settings.update { it.copy(albumSort = picked, albumSortDesc = pickedDesc) } }
                    },
                )
                IconButton(onClick = onSearch) { Symbol(Sym.Search, size = 23.dp, tint = MaterialTheme.colorScheme.onSurface) }
            }

            val bottomPad = if (player.currentTrack != null) 92.dp else 8.dp
            when {
                loading && grid -> AlbumGridSkeleton()
                loading -> GroupListSkeleton()
                grid -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = bottomPad),
                ) {
                    gridItems(sorted, key = { it.id }) { al ->
                        AlbumGridCard(graph, al) { onOpenAlbum(Screen.Tracks(al.title, TrackListRef.Album(al.id))) }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = bottomPad),
                ) {
                    items(sorted, key = { it.id }) { al ->
                        AlbumGroupCard(graph, al) { onOpenAlbum(Screen.Tracks(al.title, TrackListRef.Album(al.id))) }
                    }
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

/** Плиточная карточка альбома (обложка + название + год). */
@Composable
private fun AlbumGridCard(graph: AppGraph, album: Album, onClick: () -> Unit) {
    val c = auroraColors()
    var coverId by remember(album.id) { mutableStateOf(albumCoverIdCache[album.id]) }
    LaunchedEffect(album.id) {
        if (!albumCoverIdCache.containsKey(album.id)) {
            val id = runCatching { graph.library.albumTracks(album.id).first().firstOrNull()?.stableId }.getOrNull()
            albumCoverIdCache[album.id] = id
            coverId = id
        }
    }
    Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp)) {
        if (coverId != null) {
            ArtworkImage(
                artwork = graph.artwork,
                stableId = coverId,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                iconScale = 0.3f,
            )
        } else {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                    .background(iconTileBrush(AlbumTileColor, Color(0xFFB45309))),
                contentAlignment = Alignment.Center,
            ) { Symbol(Sym.Album, filled = true, size = 40.dp, tint = Color.White) }
        }
        Spacer(Modifier.height(8.dp))
        Text(album.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        album.year?.let { Text(it.toString(), fontSize = 11.sp, color = c.textSecondary) }
    }
}

/** Нарисованная иконка вида (шрифт-сабсет без grid/list глифов): 2×2 плитка или 3 строки. */
@Composable
private fun ViewModeIcon(showGrid: Boolean, tint: Color) {
    if (showGrid) {
        Column(Modifier.size(20.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(2) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(2) { Box(Modifier.fillMaxHeight().weight(1f).clip(RoundedCornerShape(2.dp)).background(tint)) }
                }
            }
        }
    } else {
        Column(Modifier.size(20.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) { Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(2.dp)).background(tint)) }
        }
    }
}

/** Кнопка сортировки альбомов: тап по выбранному пункту разворачивает направление (стрелка ↑/↓). */
@Composable
private fun AlbumSortButton(current: AlbumSort, desc: Boolean, onPick: (AlbumSort, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val c = auroraColors()
    val accent = LocalAccentColorSafe()
    val strings = LocalStrings.current
    val dark = tech.thothlab.dombra.theme.LocalThemeIsDark.current.value
    val popupBg = if (dark) Color(0xFF1B1922) else Color(0xFFF5F3F6)
    Box {
        IconButton(onClick = { open = true }) { Symbol(Sym.SwapVert, size = 22.dp, tint = c.textPrimary) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = popupBg,
            border = BorderStroke(1.dp, c.glassBorder),
        ) {
            Text(
                strings.sortTitle,
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                color = c.textTertiary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            )
            AlbumSort.entries.forEach { s ->
                val sel = s == current
                DropdownMenuItem(
                    text = {
                        Text(
                            albumSortLabel(s, strings),
                            color = if (sel) accent else c.textPrimary,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        // Новый пункт — по возрастанию (прямой порядок); повторный тап по выбранному — разворот.
                        if (sel) onPick(s, !desc) else onPick(s, false)
                        open = false
                    },
                    trailingIcon = { if (sel) SortArrow(up = !desc, tint = accent) },
                )
            }
        }
    }
}

/** Нарисованная стрелка направления (↑ по возрастанию / ↓ по убыванию) — в шрифт-сабсете стрелок нет. */
@Composable
private fun SortArrow(up: Boolean, tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val stroke = w * 0.12f
        val top = h * 0.20f
        val bottom = h * 0.80f
        val headDx = w * 0.24f
        val headDy = h * 0.24f
        drawLine(tint, Offset(cx, top), Offset(cx, bottom), strokeWidth = stroke, cap = StrokeCap.Round)
        if (up) {
            drawLine(tint, Offset(cx, top), Offset(cx - headDx, top + headDy), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(tint, Offset(cx, top), Offset(cx + headDx, top + headDy), strokeWidth = stroke, cap = StrokeCap.Round)
        } else {
            drawLine(tint, Offset(cx, bottom), Offset(cx - headDx, bottom - headDy), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(tint, Offset(cx, bottom), Offset(cx + headDx, bottom - headDy), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

private fun albumSortLabel(s: AlbumSort, strings: Strings): String = when (s) {
    AlbumSort.TITLE -> strings.albumSortAZ
    AlbumSort.RECENT -> strings.albumSortRecent
    AlbumSort.YEAR -> strings.albumSortYear
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
    onSearch: () -> Unit = {},
) {
    val strings = LocalStrings.current
    when (section) {
        HomeSectionId.ALL_SONGS -> {
            val data by graph.library.tracks().collectAsState(initial = null)
            TrackListScreen(graph, strings.allSongs, "all", data ?: emptyList(), player, onBack, onOpenPlayer, loading = data == null)
        }
        HomeSectionId.LIKED_SONGS -> {
            val data by graph.library.favoriteTracks().collectAsState(initial = null)
            TrackListScreen(graph, strings.likedSongs, "liked", data ?: emptyList(), player, onBack, onOpenPlayer, loading = data == null)
        }
        HomeSectionId.ARTISTS -> {
            val artists by graph.library.artists().collectAsState(initial = null)
            GroupListScreen(strings.artists, player, onBack, onOpenPlayer, graph, loading = artists == null) {
                items(artists ?: emptyList(), key = { it.id }) { a: Artist ->
                    MediaGroupCard(
                        a.name, Sym.Group, ArtistTileColor,
                        onClick = { onOpenGroup(Screen.Tracks(a.name, TrackListRef.Artist(a.id))) },
                    )
                }
            }
        }
        HomeSectionId.ALBUMS -> AlbumsScreen(
            graph = graph,
            player = player,
            onBack = onBack,
            onOpenAlbum = onOpenGroup,
            onOpenPlayer = onOpenPlayer,
            onSearch = onSearch,
        )
        HomeSectionId.PLAYLISTS -> {
            val playlists by graph.playlists.playlists().collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            var showCreate by remember { mutableStateOf(false) }
            GroupListScreen(
                strings.playlists, player, onBack, onOpenPlayer, graph,
                loading = playlists == null,
                showEmpty = playlists?.isEmpty() == true,
                emptyContent = { PlaylistsEmptyState(onCreate = { showCreate = true }) },
            ) {
                items(playlists ?: emptyList(), key = { it.id }) { pl: Playlist ->
                    MediaGroupCard(
                        pl.title, Sym.QueueMusic, PlaylistTileColor,
                        onClick = { onOpenGroup(Screen.Tracks(pl.title, TrackListRef.Playlist(pl.id))) },
                    )
                }
            }
            if (showCreate) {
                CreatePlaylistDialog(
                    onDismiss = { showCreate = false },
                    onConfirm = { name ->
                        scope.launch { graph.playlists.create(name) }
                        showCreate = false
                    },
                )
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
    onOpenAlbum: ((albumId: String, title: String) -> Unit)? = null,
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
    val tracksData by flow.collectAsState(initial = null)
    val tracks = tracksData ?: emptyList()
    // Hero-шапка детального экрана (обложка/аватар + название + мета + «Слушать»/«Микс») — по макету turn-2 §2E.
    val hero: @Composable () -> Unit = {
        DetailHero(
            graph = graph,
            title = title,
            ref = ref,
            tracks = tracks,
            onPlay = { if (tracks.isNotEmpty()) graph.playback.playQueue(tracks, 0) },
            onShuffle = { if (tracks.isNotEmpty()) graph.playback.shufflePlay(tracks) },
            onOpenAlbum = onOpenAlbum,
        )
    }
    TrackListScreen(graph, title, collectionKey, tracks, player, onBack, onOpenPlayer, hero = hero, loading = tracksData == null)
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
    hero: (@Composable () -> Unit)? = null,
    loading: Boolean = false,
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
                // Детальный экран (hero != null): заголовок/мета — в hero, тулбар минимальный, без сортировки.
                if (hero == null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 4.dp).weight(1f),
                    )
                    SortShufflePill(
                        order = order,
                        shuffled = player.shuffled,
                        // Тап: не перемешано → перемешать эти треки; перемешано → вернуть исходный порядок.
                        onShuffle = {
                            if (player.shuffled) graph.playback.toggleShuffle()
                            else if (sorted.isNotEmpty()) graph.playback.shufflePlay(sorted)
                        },
                        onPick = { picked ->
                            scope.launch {
                                graph.settings.update { it.copy(sortOrders = it.sortOrders + (collectionKey to picked)) }
                            }
                        },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (loading) TrackListSkeleton() else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = if (player.currentTrack != null) 92.dp else 8.dp),
            ) {
                if (hero != null) item(key = "hero") { hero() }
                items(sorted, key = { it.stableId }) { track ->
                    val current = track.stableId == player.currentTrack?.stableId
                    TrackRow(
                        track = track,
                        artwork = graph.artwork,
                        isCurrent = current,
                        isPlaying = current && player.isPlaying,
                        onClick = { graph.playback.playNow(track, sorted) },
                        onPlayNext = { graph.playback.addNext(track) },
                        onAddToQueue = { graph.playback.addLast(track) },
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

/** Hero-шапка детального экрана (альбом/исполнитель/плейлист): обложка/аватар + название + мета +
 *  «Слушать» (градиент) / «Микс» (стекло). У исполнителя — полоса «Альбомы». Макет turn-2 §2E / turn-11. */
@Composable
private fun DetailHero(
    graph: AppGraph,
    title: String,
    ref: TrackListRef,
    tracks: List<Track>,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onOpenAlbum: ((albumId: String, title: String) -> Unit)?,
) {
    val c = auroraColors()
    val accent = LocalAccentColorSafe()
    val strings = LocalStrings.current
    val count = tracks.size
    val meta = when (ref) {
        is TrackListRef.Artist -> {
            val albumCount = tracks.map { it.albumId }.distinct().size
            listOf(strings.tracksCount(count), strings.albumsCount(albumCount)).joinToString(" · ")
        }
        is TrackListRef.Album -> {
            val first = tracks.firstOrNull()
            listOfNotNull(first?.artistName, first?.year?.toString(), strings.tracksCount(count)).joinToString(" · ")
        }
        is TrackListRef.Playlist -> {
            val totalMs = tracks.sumOf { it.durationMs ?: 0L }
            listOf(strings.playlist, strings.tracksCount(count), formatTime(totalMs)).joinToString(" · ")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Обложка (альбом/плейлист) или круглый аватар (исполнитель).
        if (ref is TrackListRef.Artist) {
            Box(
                Modifier.size(112.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF2DD4BF), Color(0xFF0E7490), AuroraPurple))),
                contentAlignment = Alignment.Center,
            ) { Symbol(Sym.Group, filled = true, size = 46.dp, tint = Color.White) }
        } else {
            ArtworkImage(
                artwork = graph.artwork,
                stableId = tracks.firstOrNull()?.stableId,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.size(150.dp),
                iconScale = 0.34f,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
            color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(meta, fontSize = 13.5.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        // «Слушать» (градиент) + «Микс» (стекло-кнопка shuffle).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(accent, AuroraPurple)))
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 26.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Symbol(Sym.PlayArrow, filled = true, size = 20.dp, tint = Color.White)
                Text(strings.listen, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.glassFillStrong)
                    .border(1.dp, c.glassBorderStrong, RoundedCornerShape(14.dp))
                    .clickable(onClick = onShuffle),
                contentAlignment = Alignment.Center,
            ) { Symbol(Sym.Shuffle, size = 21.dp, tint = accent) }
        }
        // Полоса «Альбомы» у исполнителя (по макету F13).
        if (ref is TrackListRef.Artist && onOpenAlbum != null) {
            val albums by graph.library.artistAlbums(ref.id).collectAsState(initial = emptyList())
            if (albums.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    strings.albums.uppercase(), fontSize = 10.sp, letterSpacing = 1.4.sp, color = c.textTertiary,
                    modifier = Modifier.fillMaxWidth().padding(start = 2.dp, bottom = 10.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    albums.forEach { al ->
                        Column(
                            Modifier.width(120.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpenAlbum(al.id, al.title) },
                        ) {
                            Box(
                                Modifier.size(120.dp).clip(RoundedCornerShape(14.dp))
                                    .background(iconTileBrush(Color(0xFFF59E0B), Color(0xFFB45309))),
                                contentAlignment = Alignment.Center,
                            ) { Symbol(Sym.Album, filled = true, size = 40.dp, tint = Color.White) }
                            Spacer(Modifier.height(8.dp))
                            Text(al.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            al.year?.let { Text(it.toString(), fontSize = 11.sp, color = c.textSecondary) }
                        }
                    }
                }
            }
        }
    }
}

/** Пилюля справа в шапке списка: тумблер «Перемешать» (подсвечен, когда включён) + меню сортировки. */
@Composable
private fun SortShufflePill(
    order: SortOrder,
    shuffled: Boolean,
    onShuffle: () -> Unit,
    onPick: (SortOrder) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = LocalAccentColorSafe()
    val c = auroraColors()
    val strings = LocalStrings.current
    val dark = tech.thothlab.dombra.theme.LocalThemeIsDark.current.value
    val popupBg = if (dark) Color(0xFF1B1922) else Color(0xFFF5F3F6)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = c.glassFillStrong,
        border = BorderStroke(1.dp, c.glassBorderStrong),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Тумблер перемешивания: залит accent-washem, когда включён → повторный тап выключает.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .then(if (shuffled) Modifier.background(accent.copy(alpha = 0.22f)) else Modifier)
                    .clickable(onClick = onShuffle),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(Sym.Shuffle, size = 20.dp, tint = accent)
            }
            // Разделитель по макету (1px, высота 16).
            Box(Modifier.size(1.dp, 16.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Symbol(Sym.SwapVert, size = 20.dp, tint = MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = popupBg,
                    border = BorderStroke(1.dp, c.glassBorder),
                ) {
                    Text(
                        strings.sortTitle,
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
                                    strings.sortLabel(o),
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
    loading: Boolean = false,
    showEmpty: Boolean = false,
    emptyContent: (@Composable () -> Unit)? = null,
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
            when {
                loading -> GroupListSkeleton()
                showEmpty && emptyContent != null ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { emptyContent() }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = if (player.currentTrack != null) 92.dp else 8.dp),
                    content = content,
                )
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

/** Пустое состояние «Плейлисты»: по центру знак + текст + кнопка «Создать плейлист». */
@Composable
private fun PlaylistsEmptyState(onCreate: () -> Unit) {
    val c = auroraColors()
    val accent = LocalAccentColorSafe()
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent))),
            contentAlignment = Alignment.Center,
        ) { Symbol(Sym.QueueMusic, size = 46.dp, tint = accent) }
        Spacer(Modifier.height(20.dp))
        Text(strings.noPlaylistsTitle, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(strings.noPlaylistsBody, fontSize = 13.sp, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(accent, AuroraPurple)))
                .clickable(onClick = onCreate)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Symbol(Sym.Add, size = 20.dp, tint = Color.White)
            Text(strings.createPlaylist, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

/** Диалог создания плейлиста (пустое имя → «Новый плейлист»). */
@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val c = auroraColors()
    val strings = LocalStrings.current
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.popoverSurface,
        title = { Text(strings.newPlaylist, color = c.textPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(strings.name) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.ifBlank { strings.newPlaylist }) }) { Text(strings.create) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
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
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
) {
    val accent = LocalAccentColorSafe()
    val c = auroraColors()
    val strings = LocalStrings.current
    Row(
        // Подсветка/рамка — на всю ширину строки (снаружи паддинга), а внутренний
        // отступ ОДИНАКОВ для всех строк → иконки строго в одну вертикаль (как в макете).
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCurrent) {
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.glassFillStrong)
                        .border(1.dp, c.glassBorderStrong, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            // end = 14dp: правый край трейлинга (эквалайзер-бары / длительность / «ещё») выровнен
            // по правому краю глифа «сортировка» в тулбаре (20dp-глиф в 48dp IconButton → инсет 14).
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(
            artwork = artwork,
            stableId = track.stableId,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(44.dp),
            iconScale = 0.5f,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.5.sp,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistName,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            PlayingBars(color = accent, playing = isPlaying)
        } else {
            track.durationMs?.let {
                Text(formatTime(it), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = c.textTertiary)
            }
            if (onPlayNext != null || onAddToQueue != null) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    Symbol(
                        Sym.MoreHoriz,
                        size = 20.dp,
                        tint = c.textFaint,
                        modifier = Modifier.clip(CircleShape).clickable { menuOpen = true }.padding(2.dp),
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = c.popoverSurface,
                        border = BorderStroke(1.dp, c.popoverBorder),
                    ) {
                        onPlayNext?.let { act ->
                            DropdownMenuItem(text = { Text(strings.playNext, color = c.textPrimary) }, onClick = { act(); menuOpen = false })
                        }
                        onAddToQueue?.let { act ->
                            DropdownMenuItem(text = { Text(strings.addToQueue, color = c.textPrimary) }, onClick = { act(); menuOpen = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalAccentColorSafe(): Color = tech.thothlab.dombra.theme.LocalAccentColor.current

// ---- Скелетоны загрузки списков (пульсирующие плейсхолдеры под форму строк) ----

/** Пульсирующий цвет-плейсхолдер (белый/чёрный с «дыханием» альфы). */
@Composable
private fun rememberSkeletonColor(): Color {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val dark = tech.thothlab.dombra.theme.LocalThemeIsDark.current.value
    return if (dark) Color.White.copy(alpha = a) else Color.Black.copy(alpha = a)
}

@Composable
private fun SkeletonBox(modifier: Modifier, corner: Dp, color: Color) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(color))
}

private val skeletonWidths = listOf(0.62f, 0.48f, 0.70f, 0.55f, 0.42f, 0.66f, 0.5f, 0.58f)

/** Скелетон списка треков: обложка + две строки текста. */
@Composable
private fun TrackListSkeleton() {
    val col = rememberSkeletonColor()
    Column(Modifier.fillMaxSize().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(8) { i ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(Modifier.size(44.dp), 10.dp, col)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(Modifier.fillMaxWidth(skeletonWidths[i % skeletonWidths.size]).height(13.dp), 4.dp, col)
                    SkeletonBox(Modifier.fillMaxWidth(0.32f).height(11.dp), 4.dp, col)
                }
            }
        }
    }
}

/** Скелетон списка групп-карточек (плитка + строка в стеклянной карточке). */
@Composable
private fun GroupListSkeleton() {
    val c = auroraColors()
    val col = rememberSkeletonColor()
    Column(Modifier.fillMaxSize().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) { i ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.glassFill)
                    .border(1.dp, c.glassBorder, RoundedCornerShape(16.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBox(Modifier.size(48.dp), 13.dp, col)
                SkeletonBox(Modifier.fillMaxWidth(skeletonWidths[i % skeletonWidths.size]).height(15.dp), 4.dp, col)
            }
        }
    }
}

/** Скелетон плиточной сетки альбомов (6 плиток, 2 колонки). */
@Composable
private fun AlbumGridSkeleton() {
    val col = rememberSkeletonColor()
    Column(Modifier.fillMaxSize().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    Column(Modifier.weight(1f).padding(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f), 14.dp, col)
                        SkeletonBox(Modifier.fillMaxWidth(0.7f).height(13.dp), 4.dp, col)
                        SkeletonBox(Modifier.fillMaxWidth(0.3f).height(11.dp), 4.dp, col)
                    }
                }
            }
        }
    }
}
