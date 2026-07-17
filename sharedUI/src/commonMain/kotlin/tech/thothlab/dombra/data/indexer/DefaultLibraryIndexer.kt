package tech.thothlab.dombra.data.indexer

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import tech.thothlab.dombra.core.Clock
import tech.thothlab.dombra.core.DispatcherProvider
import tech.thothlab.dombra.core.DombraException
import tech.thothlab.dombra.core.Log
import tech.thothlab.dombra.data.metadata.MetadataParser
import tech.thothlab.dombra.data.metadata.StableId
import tech.thothlab.dombra.data.store.LibraryStore
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.model.ScanStats
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.model.TrackAvailability
import tech.thothlab.dombra.domain.ports.ArtworkRepository
import tech.thothlab.dombra.domain.ports.LibraryIndexer
import tech.thothlab.dombra.domain.ports.ScanEvent
import tech.thothlab.dombra.domain.ports.SourceRef
import tech.thothlab.dombra.domain.ports.StorageProvider

/**
 * Индексатор (§5.3 ТЗ): батчевые транзакции, прогресс, отмена, идемпотентность
 * по stableId, reconcile после успешного полного scan, fallback-метаданные.
 */
class DefaultLibraryIndexer(
    private val storage: StorageProvider,
    private val store: LibraryStore,
    private val artworkRepository: ArtworkRepository,
    private val clock: Clock,
    private val dispatchers: DispatcherProvider,
    private val batchSize: Int = 32,
    /** Максимальный размер файла для дочитывания целиком (m4a/dsf-теги). */
    private val maxFullReadBytes: Long = 128L * 1024 * 1024,
) : LibraryIndexer {

    private val log = Log.withTag("Indexer")
    private val scanning = MutableStateFlow(false)
    private var cancelRequested = false

    override val isScanning: Flow<Boolean> = scanning

    override fun cancel() {
        cancelRequested = true
    }

    override fun scan(sources: List<SourceRef>, fullScan: Boolean): Flow<ScanEvent> = flow {
        scanning.value = true
        cancelRequested = false
        var stats = ScanStats()
        try {
            // 1. Собираем список файлов
            val files = mutableListOf<SourceRef>()
            for (src in sources) {
                currentCoroutineContext().ensureActive()
                if (src.isDirectory) {
                    files += runCatching { storage.listAudioFiles(src) }
                        .getOrElse {
                            log.w { "enumerate failed: ${Log.redactUri(src.uri)}" }
                            emptyList()
                        }
                } else {
                    files += src
                }
            }
            stats = stats.copy(found = files.size)
            emit(ScanEvent.Progress(stats, null))

            val seenStableIds = mutableSetOf<String>()
            val batchTracks = mutableListOf<Track>()
            val batchArtists = mutableMapOf<String, Artist>()
            val batchAlbums = mutableMapOf<String, Album>()

            suspend fun flushBatch() {
                if (batchTracks.isEmpty() && batchArtists.isEmpty()) return
                // Порядок важен: сначала справочники, затем треки (FK на Room-стороне)
                store.upsertArtists(batchArtists.values.toList())
                store.upsertAlbums(batchAlbums.values.toList())
                store.upsertTracks(batchTracks.toList())
                batchTracks.clear(); batchArtists.clear(); batchAlbums.clear()
            }

            for (file in files) {
                currentCoroutineContext().ensureActive()
                if (cancelRequested) {
                    flushBatch()
                    emit(ScanEvent.Cancelled(stats))
                    return@flow
                }
                val result = processFile(file, seenStableIds, batchArtists, batchAlbums)
                stats = when (result) {
                    is FileResult.Added -> { batchTracks += result.track; stats.copy(added = stats.added + 1) }
                    is FileResult.Updated -> { batchTracks += result.track; stats.copy(updated = stats.updated + 1) }
                    FileResult.Skipped -> stats.copy(skipped = stats.skipped + 1)
                    FileResult.Unavailable -> stats.copy(unavailable = stats.unavailable + 1)
                    FileResult.Failed -> stats.copy(failed = stats.failed + 1)
                }
                if (batchTracks.size >= batchSize) {
                    flushBatch()
                    emit(ScanEvent.Progress(stats, file.displayName))
                }
            }
            flushBatch()

            // 2. Reconcile — только после успешного полного scan (§5.3)
            if (fullScan && !cancelRequested) {
                val known = store.getAllTracks()
                val vanished = known.filter { it.stableId !in seenStableIds }
                if (vanished.isNotEmpty()) {
                    store.deleteTracks(vanished.map { it.stableId })
                    log.i { "reconcile: removed ${vanished.size} vanished tracks" }
                }
                store.pruneOrphans()
            }

            emit(ScanEvent.Done(stats))
        } catch (e: DombraException) {
            emit(ScanEvent.Failed(e.error.message, stats))
        } finally {
            scanning.value = false
        }
    }.flowOn(dispatchers.io)

    private sealed interface FileResult {
        data class Added(val track: Track) : FileResult
        data class Updated(val track: Track) : FileResult
        data object Skipped : FileResult
        data object Unavailable : FileResult
        data object Failed : FileResult
    }

    private suspend fun processFile(
        file: SourceRef,
        seenStableIds: MutableSet<String>,
        batchArtists: MutableMap<String, Artist>,
        batchAlbums: MutableMap<String, Album>,
    ): FileResult {
        val stat = storage.stat(file) ?: return FileResult.Unavailable

        try {
            val headLen = StableId.CHUNK.toLong()
            val head = storage.readBytes(file, 0, headLen)
            val tailOffset = (stat.size - StableId.CHUNK).coerceAtLeast(0)
            val tail = if (stat.size > StableId.CHUNK) {
                storage.readBytes(file, tailOffset, StableId.CHUNK.toLong())
            } else head

            val stableId = StableId.compute(stat.size, head, tail)
            seenStableIds += stableId

            // Инкрементальность: файл не менялся → пропуск (§5.3)
            val existing = store.getTrack(stableId)
            if (existing != null &&
                existing.fileSize == stat.size &&
                existing.modificationTime == stat.modificationTime &&
                existing.sourceUri == file.uri
            ) {
                return FileResult.Skipped
            }

            val full = if (MetadataParser.needsFullRead(head) && stat.size <= maxFullReadBytes) {
                storage.readBytes(file)
            } else null

            val meta = MetadataParser.parse(head, tail, stat.size, full)
                ?: return FileResult.Failed // содержимое не распознано — «пропущен с ошибкой»

            // Fallback-метаданные (§5.3)
            val fallbackTitle = file.displayName.substringBeforeLast('.')
            val artistName = meta.artist?.takeIf { it.isNotBlank() } ?: Artist.UNKNOWN_NAME
            val albumTitle = meta.album?.takeIf { it.isNotBlank() } ?: Album.UNKNOWN_TITLE

            val artistId = Artist.idFor(artistName)
            val albumId = Album.idFor(artistId, albumTitle)
            batchArtists[artistId] = Artist(artistId, artistName)
            batchAlbums[albumId] = Album(
                id = albumId, artistId = artistId, title = albumTitle,
                year = meta.year, albumArtist = meta.albumArtist,
            )

            meta.artwork?.let { bytes ->
                runCatching { artworkRepository.store(stableId, bytes) }
                    .onFailure { log.w { "artwork cache failed for ${Log.redactUri(file.uri)}" } }
            }

            val track = Track(
                stableId = stableId,
                albumId = albumId,
                artistId = artistId,
                title = meta.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                artistName = artistName,
                albumTitle = albumTitle,
                albumArtist = meta.albumArtist,
                year = meta.year,
                trackNo = meta.trackNo,
                discNo = meta.discNo,
                durationMs = meta.durationMs,
                sampleRate = meta.sampleRate,
                bitDepth = meta.bitDepth,
                channels = meta.channels,
                format = if (meta.format == AudioFormat.UNKNOWN) {
                    AudioFormat.byExtension(file.displayName.substringAfterLast('.', ""))
                } else meta.format,
                sourceUri = file.uri,
                sourceDisplayName = file.displayName,
                fileSize = stat.size,
                modificationTime = stat.modificationTime,
                replayGain = meta.replayGain,
                hasEmbeddedArt = meta.artwork != null,
                hasEmbeddedLyrics = meta.embeddedLyrics != null,
                availability = TrackAvailability.AVAILABLE,
                addedAt = existing?.addedAt ?: clock.nowMs(),
            )
            return if (existing == null) FileResult.Added(track) else FileResult.Updated(track)
        } catch (e: DombraException) {
            log.w { "index failed for ${Log.redactUri(file.uri)}: ${e.error.message}" }
            return FileResult.Failed
        }
    }
}
