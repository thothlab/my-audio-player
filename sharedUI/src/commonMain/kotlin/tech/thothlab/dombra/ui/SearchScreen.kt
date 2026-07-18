package tech.thothlab.dombra.ui

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.flowOf
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.Track

private enum class SearchScope(val label: String) { ALL("Все"), SONGS("Песни"), ARTISTS("Исполнители"), ALBUMS("Альбомы") }

/** Экран поиска (PRD-03 T04): поле + фильтр-чипы областей + сгруппированные результаты. */
@Composable
fun SearchScreen(
    graph: AppGraph,
    onClose: () -> Unit,
    onPlaySong: (Track, List<Track>) -> Unit,
    onOpenArtist: (Artist) -> Unit,
    onOpenAlbum: (Album) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(SearchScope.ALL) }
    val q = query.trim()

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
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClose) { Text("Готово") }
                Text("Поиск", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Название, исполнитель, альбом") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "очистить") }
                    }
                },
                keyboardActions = KeyboardActions(),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchScope.entries.forEach { s ->
                    FilterChip(
                        selected = scope == s,
                        onClick = { scope = s },
                        label = { Text(s.label) },
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (songs.isNotEmpty()) {
                    item { SectionLabel("Песни") }
                    items(songs, key = { "s_" + it.stableId }) { t ->
                        TrackRow(
                            track = t,
                            artwork = graph.artwork,
                            isCurrent = false,
                            onClick = { onPlaySong(t, songs) },
                        )
                    }
                }
                if (artists.isNotEmpty()) {
                    item { SectionLabel("Исполнители") }
                    items(artists, key = { "ar_" + it.id }) { a ->
                        SearchGroupRow(a.name, Icons.Filled.Group) { onOpenArtist(a) }
                    }
                }
                if (albums.isNotEmpty()) {
                    item { SectionLabel("Альбомы") }
                    items(albums, key = { "al_" + it.id }) { al ->
                        SearchGroupRow(al.title, Icons.Filled.Album) { onOpenAlbum(al) }
                    }
                }
                if (q.isNotEmpty() && songs.isEmpty() && artists.isEmpty() && albums.isEmpty()) {
                    item {
                        Text(
                            "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun SearchGroupRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp).padding(6.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
