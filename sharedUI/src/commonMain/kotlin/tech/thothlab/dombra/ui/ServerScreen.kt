package tech.thothlab.dombra.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tech.thothlab.dombra.data.remote.subsonic.SubAlbum
import tech.thothlab.dombra.di.AppGraph

/** Экран удалённого источника: форма подключения (если не подключён) или просмотр библиотеки сервера. */
@Composable
fun ServerScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    onOpenAlbum: (albumId: String, title: String) -> Unit,
) {
    val config by graph.remote.config.collectAsState()

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
                    if (config == null) "Подключить сервер" else (config?.label ?: "Сервер"),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            if (config == null) ConnectForm(graph) else ServerBrowse(graph, onOpenAlbum)
        }
    }
}

@Composable
private fun ConnectForm(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("https://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
        Text(
            "Navidrome / Subsonic-совместимый сервер. Пароль не сохраняется — только соль и токен.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            url, { url = it },
            label = { Text("Адрес сервера") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, capitalization = KeyboardCapitalization.None),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            user, { user = it },
            label = { Text("Имя пользователя") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            pass, { pass = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = {
                scope.launch {
                    connecting = true
                    error = null
                    val result = graph.remote.connect(url, user, pass)
                    connecting = false
                    error = result.exceptionOrNull()?.message?.let { "Не удалось подключиться: $it" }
                }
            },
            enabled = !connecting && url.length > 8 && user.isNotBlank() && pass.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (connecting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Подключить")
        }
    }
}

@Composable
private fun ServerBrowse(graph: AppGraph, onOpenAlbum: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val q = query.trim()

    val albums by produceState(initialValue = emptyList<SubAlbum>(), q) {
        value = if (q.isEmpty()) {
            runCatching { graph.remote.albums() }.getOrDefault(emptyList())
        } else {
            runCatching { graph.remote.search(q).album }.getOrDefault(emptyList())
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            query, { query = it },
            placeholder = { Text("Поиск по серверу") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        OutlinedButton(
            onClick = { scope.launch { graph.remote.disconnect() } },
            modifier = Modifier.padding(bottom = 8.dp),
        ) { Text("Отключить сервер") }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(albums, key = { it.id }) { al ->
                AlbumRow(al, graph.remote.coverArtUrl(al.coverArt, 128), graph) { onOpenAlbum(al.id, al.name) }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: SubAlbum, coverUrl: String?, graph: AppGraph, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(
            artwork = graph.artwork,
            stableId = null,
            remoteUrl = coverUrl,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(48.dp),
            iconScale = 0.5f,
        )
        Column(Modifier.weight(1f)) {
            Text(album.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (album.songCount > 0) {
            Text("${album.songCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Треки удалённого альбома (просмотр; проигрывание по URL — T07). */
@Composable
fun RemoteAlbumScreen(graph: AppGraph, albumId: String, title: String, onBack: () -> Unit) {
    val tracks by produceState(initialValue = emptyList<tech.thothlab.dombra.domain.model.Track>(), albumId) {
        value = runCatching { graph.remote.albumTracks(albumId) }.getOrDefault(emptyList())
    }
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
                Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            }
            if (tracks.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text("Загрузка…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(tracks, key = { it.stableId }) { t ->
                    TrackRow(
                        track = t,
                        artwork = graph.artwork,
                        isCurrent = false,
                        onClick = { graph.playback.playNow(t, tracks) },
                    )
                }
            }
        }
    }
}
