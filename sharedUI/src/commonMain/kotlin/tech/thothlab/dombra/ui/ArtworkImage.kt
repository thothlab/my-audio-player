package tech.thothlab.dombra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import tech.thothlab.dombra.domain.ports.ArtworkRepository

/**
 * Обложка трека из файлового кэша `ArtworkRepository` (наполняется индексатором).
 * Fallback — иконка ноты. Загрузка байтов асинхронна, декод — Coil.
 */
@Composable
fun ArtworkImage(
    artwork: ArtworkRepository,
    stableId: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    iconScale: Float = 0.3f,
) {
    val bytes: ByteArray? by produceState<ByteArray?>(null, stableId) {
        value = stableId?.let { runCatching { artwork.load(it) }.getOrNull() }
    }
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        val b = bytes
        if (b != null) {
            AsyncImage(
                model = b,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxSize(iconScale),
            )
        }
    }
}
