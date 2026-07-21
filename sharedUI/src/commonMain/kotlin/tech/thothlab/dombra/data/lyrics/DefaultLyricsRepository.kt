package tech.thothlab.dombra.data.lyrics

import kotlinx.coroutines.withContext
import tech.thothlab.dombra.core.Clock
import tech.thothlab.dombra.core.DispatcherProvider
import tech.thothlab.dombra.core.DombraResult
import tech.thothlab.dombra.core.Log
import tech.thothlab.dombra.data.metadata.MetadataParser
import tech.thothlab.dombra.data.metadata.StableId
import tech.thothlab.dombra.data.store.LibraryStore
import tech.thothlab.dombra.domain.model.Lyrics
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.ports.LyricsRepository
import tech.thothlab.dombra.domain.ports.SourceRef
import tech.thothlab.dombra.domain.ports.StorageProvider

/**
 * Ленивое разрешение текста песни (§5.10 ТЗ): кэш → встроенный в файл → null.
 * «Ленивое» важно, потому что индексатор инкрементален и пропускает неизменившиеся
 * файлы — если бы текст сохранялся только при индексации, вся уже импортированная
 * библиотека осталась бы без текста до пере-импорта. Здесь при первом открытии
 * встроенный текст дочитывается из файла и кладётся в кэш ([saveLyrics]).
 *
 * Сеть (LRCLIB и т.п.) пока не подключена — при промахе возвращается null (empty state).
 */
class DefaultLyricsRepository(
    private val store: LibraryStore,
    private val storage: StorageProvider,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val maxFullReadBytes: Long = 128L * 1024 * 1024,
) : LyricsRepository {

    private val log = Log.withTag("Lyrics")

    override suspend fun lyricsFor(track: Track, networkAllowed: Boolean): DombraResult<Lyrics?> {
        store.getLyrics(track.stableId)?.let { return DombraResult.Ok(it) }

        if (track.hasEmbeddedLyrics) {
            val embedded = runCatching { readEmbedded(track) }
                .onFailure { log.w { "embedded read failed for ${Log.redactUri(track.sourceUri)}" } }
                .getOrNull()
            if (!embedded.isNullOrBlank()) {
                val parsed = LyricsParser.parse(track.stableId, embedded, source = "embedded", fetchedAt = clock.nowMs())
                store.saveLyrics(parsed)
                return DombraResult.Ok(parsed)
            }
        }
        return DombraResult.Ok(null)
    }

    override suspend fun invalidate(trackStableId: String) {
        store.deleteLyrics(trackStableId)
    }

    /**
     * Дочитывает встроенный текст, повторяя head/tail/full-логику индексатора.
     * Всё тело — на IO: `readBytes` уже переключается на IO сам, но парсинг
     * ([MetadataParser.parse]) — CPU-bound (на полном буфере для m4a-без-moov/DSF),
     * а вызывается из main (LaunchedEffect). Без этого — рывок/ANR на крупных файлах.
     */
    private suspend fun readEmbedded(track: Track): String? = withContext(dispatchers.io) {
        val ref = SourceRef(uri = track.sourceUri, displayName = track.sourceDisplayName)
        val stat = storage.stat(ref) ?: return@withContext null
        val chunk = StableId.CHUNK.toLong()
        val head = storage.readBytes(ref, 0, chunk)
        val tail = if (stat.size > StableId.CHUNK) {
            storage.readBytes(ref, (stat.size - StableId.CHUNK).coerceAtLeast(0), chunk)
        } else head
        val full = if (MetadataParser.needsFullRead(head) && stat.size <= maxFullReadBytes) {
            storage.readBytes(ref)
        } else null
        MetadataParser.parse(head, tail, stat.size, full)?.embeddedLyrics
    }
}
