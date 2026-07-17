package tech.thothlab.dombra.domain.ports

import kotlinx.coroutines.flow.Flow
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.ScanStats
import tech.thothlab.dombra.domain.model.Track

/** Индексация библиотеки (§5.3 ТЗ). */

sealed interface ScanEvent {
    data class Progress(val stats: ScanStats, val currentFile: String?) : ScanEvent
    data class Done(val stats: ScanStats) : ScanEvent
    data class Cancelled(val stats: ScanStats) : ScanEvent
    data class Failed(val message: String, val stats: ScanStats) : ScanEvent
}

interface LibraryIndexer {
    /**
     * Индексирует источники; события прогресса — по мере обработки батчей.
     * Полный rescan (fullScan=true) в конце выполняет reconcile: удаляет
     * действительно исчезнувшие записи (§5.3).
     */
    fun scan(sources: List<SourceRef>, fullScan: Boolean = false): Flow<ScanEvent>

    fun cancel()

    val isScanning: Flow<Boolean>
}

enum class TrackSort { TITLE, ARTIST, ALBUM, ADDED_DATE, TRACK_ORDER }

interface LibraryRepository {
    fun tracks(sort: TrackSort = TrackSort.TITLE, query: String = ""): Flow<List<Track>>
    fun favoriteTracks(query: String = ""): Flow<List<Track>>
    fun artists(query: String = ""): Flow<List<Artist>>
    fun albums(query: String = ""): Flow<List<Album>>
    fun albumTracks(albumId: String): Flow<List<Track>>
    fun artistTracks(artistId: String): Flow<List<Track>>
    fun artistAlbums(artistId: String): Flow<List<Album>>
    suspend fun track(stableId: String): Track?
    suspend fun artist(artistId: String): Artist?
    suspend fun album(albumId: String): Album?

    fun isFavorite(stableId: String): Flow<Boolean>
    suspend fun setFavorite(stableId: String, favorite: Boolean)
    fun favoriteIds(): Flow<Set<String>>

    /** Удаление из библиотеки; файл не трогается (§5.2). */
    suspend fun removeFromLibrary(stableId: String)

    suspend fun markAvailability(stableId: String, available: Boolean)
}
