package tech.thothlab.dombra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.flowOf
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.theme.AuroraPurple
import tech.thothlab.dombra.theme.LocalAccentColor
import tech.thothlab.dombra.theme.auroraColors

internal enum class SearchScope(val label: String) { ALL("Все"), SONGS("Песни"), ARTISTS("Исполнители"), ALBUMS("Альбомы") }

/**
 * Состояние экрана поиска (запрос + область), живущее ВЫШЕ навигации — чтобы при уходе
 * на детали и возврате «назад» поиск восстанавливался, а не создавался заново.
 */
internal class SearchUiState {
    var query by mutableStateOf("")
    var scope by mutableStateOf(SearchScope.ALL)
}

private val ArtistGradient = Brush.linearGradient(listOf(Color(0xFF2DD4BF), Color(0xFF0E7490)))
private val AlbumGradient = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFB45309)))

/** Экран поиска (Aurora Glass): пилюля-поле + «Готово» + чипы-пилюли + сгруппированные результаты. */
@Composable
internal fun SearchScreen(
    graph: AppGraph,
    state: SearchUiState,
    onClose: () -> Unit,
    onPlaySong: (Track, List<Track>) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    val query = state.query
    val scope = state.scope
    val q = query.trim()
    val accent = LocalAccentColor.current
    val c = auroraColors()

    val wantSongs = scope == SearchScope.ALL || scope == SearchScope.SONGS
    val wantArtists = scope == SearchScope.ALL || scope == SearchScope.ARTISTS
    val wantAlbums = scope == SearchScope.ALL || scope == SearchScope.ALBUMS

    val songs by remember(q, wantSongs) {
        if (q.isNotEmpty() && wantSongs) graph.library.tracks(query = q) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val artists by remember(q, wantArtists) {
        if (q.isNotEmpty() && wantArtists) graph.library.artists(query = q) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val albums by remember(q, wantAlbums) {
        if (q.isNotEmpty() && wantAlbums) graph.library.albums(query = q) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    Box(Modifier.fillMaxSize()) {
        CosmosBackground(CosmosScreen.Secondary)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
        ) {
            // Поле-пилюля + «Готово».
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.glassFillStrong)
                        .border(1.dp, c.glassBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(Icons.Filled.Search, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Название, исполнитель, альбом",
                                style = TextStyle(fontSize = 15.sp),
                                color = c.textTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { state.query = it },
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 15.sp, color = c.textPrimary),
                            cursorBrush = SolidColor(accent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Clear, "очистить",
                            tint = c.textSecondary,
                            modifier = Modifier.size(19.dp).clickable { state.query = "" },
                        )
                    }
                }
                Text(
                    "Готово",
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    color = accent,
                    modifier = Modifier.clickable(onClick = onClose),
                )
            }

            // Чипы-пилюли (горизонтальный скролл).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchScope.entries.forEach { s ->
                    ScopeChip(s.label, selected = scope == s, accent = accent) { state.scope = s }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            ) {
                if (songs.isNotEmpty()) {
                    item { SectionLabel("Песни") }
                    items(songs, key = { "s_" + it.stableId }) { t ->
                        SongResultRow(graph, t) { onPlaySong(t, songs) }
                    }
                }
                if (artists.isNotEmpty()) {
                    item { SectionLabel("Исполнители") }
                    items(artists, key = { "ar_" + it.id }) { a ->
                        GroupResultRow(a.name, "исполнитель", ArtistGradient, circle = true) { onOpenArtist(a) }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { SectionLabel("Альбомы") }
                    items(albums, key = { "al_" + it.id }) { al ->
                        GroupResultRow(al.title, al.year?.toString() ?: "альбом", AlbumGradient, circle = false) { onOpenAlbum(al) }
                    }
                }
                if (q.isNotEmpty() && songs.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
                    item {
                        Text(
                            "Ничего не найдено",
                            color = c.textSecondary,
                            style = TextStyle(fontSize = 14.sp),
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val c = auroraColors()
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) Modifier.background(Brush.linearGradient(listOf(accent, AuroraPurple)))
                else Modifier.background(c.glassFillStrong).border(1.dp, c.glassBorder, shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = TextStyle(fontSize = 12.5.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (selected) Color.White else c.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = TextStyle(fontSize = 10.sp, letterSpacing = 1.4.sp),
        color = auroraColors().textTertiary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SongResultRow(graph: AppGraph, track: Track, onClick: () -> Unit) {
    val c = auroraColors()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(
            artwork = graph.artwork,
            stableId = track.stableId,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(44.dp),
            iconScale = 0.5f,
        )
        Column(Modifier.weight(1f)) {
            Text(track.title, style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Medium), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistName, style = TextStyle(fontSize = 12.sp), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        track.durationMs?.let {
            Text(formatTime(it), style = TextStyle(fontSize = 11.sp), color = c.textSecondary)
        }
    }
}

@Composable
private fun GroupResultRow(title: String, subtitle: String, gradient: Brush, circle: Boolean, onClick: () -> Unit) {
    val c = auroraColors()
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(if (circle) CircleShape else RoundedCornerShape(8.dp)).background(gradient))
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 14.5.sp, fontWeight = FontWeight.Medium), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = TextStyle(fontSize = 12.sp), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textFaint)
    }
}
