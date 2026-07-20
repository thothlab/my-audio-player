package tech.thothlab.dombra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import tech.thothlab.dombra.theme.Sym
import tech.thothlab.dombra.theme.Symbol
import tech.thothlab.dombra.domain.ports.ArtworkRepository

/** Простой in-memory LRU-кэш байтов обложек (commonMain): убирает повторное чтение с диска и «моргание». */
private object ArtCache {
    private const val MAX = 400
    private val map = HashMap<String, ByteArray?>()
    private val order = ArrayDeque<String>()

    fun has(key: String): Boolean = map.containsKey(key)
    fun get(key: String): ByteArray? = map[key]
    fun put(key: String, value: ByteArray?) {
        if (!map.containsKey(key)) {
            order.addLast(key)
            if (order.size > MAX) map.remove(order.removeFirst())
        }
        map[key] = value
    }
}

/**
 * Обложка трека из кэша `ArtworkRepository` (по stableId) либо по удалённому URL (`remoteUrl`).
 * Байты кэшируются в памяти (мгновенный повторный показ, без «моргания»); появление — с crossfade.
 * Fallback — иконка ноты.
 */
@Composable
fun ArtworkImage(
    artwork: ArtworkRepository,
    stableId: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    iconScale: Float = 0.3f,
    remoteUrl: String? = null,
) {
    // Стартовое значение — из in-memory кэша (если уже загружали этот stableId), иначе null.
    var bytes by remember(stableId, remoteUrl) {
        mutableStateOf(if (remoteUrl == null && stableId != null) ArtCache.get(stableId) else null)
    }
    LaunchedEffect(stableId, remoteUrl) {
        if (remoteUrl == null && stableId != null && !ArtCache.has(stableId)) {
            val loaded = runCatching { artwork.load(stableId) }.getOrNull()
            ArtCache.put(stableId, loaded)
            bytes = loaded
        }
    }

    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        val model: Any? = remoteUrl ?: bytes
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(model)
                    .memoryCacheKey(stableId ?: remoteUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Symbol(
                Sym.MusicNote,
                filled = true,
                size = maxWidth * iconScale,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
