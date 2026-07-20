package tech.thothlab.dombra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tech.thothlab.dombra.data.remote.subsonic.SubAlbum
import tech.thothlab.dombra.di.AppGraph
import tech.thothlab.dombra.theme.AuroraPurple
import tech.thothlab.dombra.theme.LocalAccentColor
import tech.thothlab.dombra.theme.Sym
import tech.thothlab.dombra.theme.Symbol
import tech.thothlab.dombra.theme.auroraColors

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
                IconButton(onClick = onBack) { Symbol(Sym.ChevronLeft, size = 28.dp, tint = MaterialTheme.colorScheme.onSurface) }
                Text(
                    if (config == null) "Сервер Subsonic" else (config?.label ?: "Сервер"),
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

/** Стеклянное поле формы с лейблом-сверху (Aurora Glass). */
@Composable
private fun GlassField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = auroraColors()
    val accent = LocalAccentColor.current
    Column {
        Text(
            label,
            style = TextStyle(fontSize = 11.sp),
            color = c.textTertiary,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(c.glassFillStrong)
                .border(1.dp, c.glassBorder, RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = TextStyle(fontSize = 14.sp), color = c.textTertiary)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontSize = 14.sp, color = c.textPrimary),
                cursorBrush = SolidColor(accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Кнопка-градиент Aurora (accent → фиолетовый); неактивная — приглушённое стекло. */
@Composable
private fun GradientButton(text: String, enabled: Boolean, loading: Boolean, onClick: () -> Unit) {
    val c = auroraColors()
    val accent = LocalAccentColor.current
    val shape = RoundedCornerShape(15.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (enabled) Modifier.background(Brush.linearGradient(listOf(accent, AuroraPurple)))
                else Modifier.background(c.glassFillStrong),
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(
                text,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = if (enabled) Color.White else c.textTertiary,
            )
        }
    }
}

@Composable
private fun ConnectForm(graph: AppGraph) {
    val scope = rememberCoroutineScope()
    val c = auroraColors()
    var url by remember { mutableStateOf("https://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(
            "Navidrome / OpenSubsonic. Стрим без потерь качества.",
            style = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
            color = c.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        GlassField("Адрес сервера", url, { url = it }, placeholder = "https://", keyboardType = KeyboardType.Uri)
        GlassField("Имя пользователя", user, { user = it })
        GlassField("Пароль", pass, { pass = it }, password = true, keyboardType = KeyboardType.Password)
        error?.let {
            Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = TextStyle(fontSize = 13.sp))
        }
        Spacer(Modifier.height(2.dp))
        GradientButton(
            text = "Проверить и подключить",
            enabled = url.length > 8 && user.isNotBlank() && pass.isNotBlank(),
            loading = connecting,
        ) {
            scope.launch {
                connecting = true
                error = null
                val result = graph.remote.connect(url, user, pass)
                connecting = false
                error = result.exceptionOrNull()?.message?.let { "Не удалось подключиться: $it" }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Symbol(Sym.Lock, size = 16.dp, tint = c.textFaint)
            Text(
                "Пароль не хранится в открытом виде — только salt + token (md5). Поток запрашивается как format=raw.",
                style = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
                color = c.textTertiary,
            )
        }
    }
}

@Composable
private fun ServerBrowse(graph: AppGraph, onOpenAlbum: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    val c = auroraColors()
    val accent = LocalAccentColor.current
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.glassFillStrong)
                .border(1.dp, c.glassBorder, RoundedCornerShape(13.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Symbol(Sym.Search, size = 20.dp, tint = c.textSecondary)
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Поиск по серверу", style = TextStyle(fontSize = 14.sp), color = c.textTertiary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = c.textPrimary),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            "Отключить сервер",
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = accent,
            modifier = Modifier
                .clickable { scope.launch { graph.remote.disconnect() } }
                .padding(vertical = 6.dp, horizontal = 4.dp),
        )

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
    val c = auroraColors()
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
            Text(album.name, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.artist, style = TextStyle(fontSize = 12.sp), color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (album.songCount > 0) {
            Text("${album.songCount}", style = TextStyle(fontSize = 12.sp), color = c.textTertiary)
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
                IconButton(onClick = onBack) { Symbol(Sym.ChevronLeft, size = 28.dp, tint = MaterialTheme.colorScheme.onSurface) }
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
                        isPlaying = false,
                        onClick = { graph.playback.playNow(t, tracks) },
                    )
                }
            }
        }
    }
}
