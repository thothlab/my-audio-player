package tech.thothlab.dombra.data.store

import kotlinx.coroutines.flow.Flow
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.EqBand
import tech.thothlab.dombra.domain.model.EqPreset
import tech.thothlab.dombra.domain.model.EqSettings
import tech.thothlab.dombra.domain.model.Lyrics
import tech.thothlab.dombra.domain.model.Playlist
import tech.thothlab.dombra.domain.model.PlaylistItem
import tech.thothlab.dombra.domain.model.PlaylistWithItems
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.model.TrackAvailability

/**
 * Низкоуровневый контракт хранилища библиотеки. Реализации:
 * - Room (android/jvm/ios) — nonWebMain;
 * - браузерное хранилище (js/wasm) — webMain;
 * - in-memory — тесты и базовая web-реализация.
 *
 * Репозитории (§7.2 ТЗ) — общая логика в commonMain поверх этого контракта,
 * поэтому поведение идентично на всех платформах (specs/storage: «Эквивалентное web-хранилище»).
 */
interface LibraryStore {
    // Tracks
    fun tracksFlow(): Flow<List<Track>>
    suspend fun upsertTracks(tracks: List<Track>)
    suspend fun getTrack(stableId: String): Track?
    suspend fun getAllTracks(): List<Track>
    suspend fun deleteTracks(stableIds: Collection<String>)
    suspend fun updateAvailability(stableId: String, availability: TrackAvailability)

    // Artists / Albums
    fun artistsFlow(): Flow<List<Artist>>
    fun albumsFlow(): Flow<List<Album>>
    suspend fun upsertArtists(artists: List<Artist>)
    suspend fun upsertAlbums(albums: List<Album>)
    suspend fun getArtist(id: String): Artist?
    suspend fun getAlbum(id: String): Album?

    /** Удаляет исполнителей и альбомы, на которые не ссылается ни один трек. */
    suspend fun pruneOrphans()

    // Favorites
    fun favoritesFlow(): Flow<Set<String>>
    suspend fun getFavorites(): Set<String>
    suspend fun setFavorite(stableId: String, favorite: Boolean)

    // Playlists
    fun playlistsFlow(): Flow<List<Playlist>>
    fun playlistFlow(id: String): Flow<PlaylistWithItems?>
    suspend fun getPlaylist(id: String): PlaylistWithItems?
    suspend fun getAllPlaylists(): List<PlaylistWithItems>
    suspend fun upsertPlaylist(playlist: Playlist)
    suspend fun deletePlaylist(id: String)

    /** Атомарно заменяет элементы плейлиста (позиции перенумеровываются 0..n-1). */
    suspend fun replaceItems(playlistId: String, items: List<PlaylistItem>)

    // Lyrics cache
    suspend fun getLyrics(stableId: String): Lyrics?
    suspend fun saveLyrics(lyrics: Lyrics)
    suspend fun deleteLyrics(stableId: String)

    // EQ
    fun eqSettingsFlow(): Flow<EqSettings>
    suspend fun saveEqSettings(settings: EqSettings)
    fun eqPresetsFlow(): Flow<List<EqPreset>>
    suspend fun getEqBands(presetId: String): List<EqBand>
    suspend fun saveEqPreset(preset: EqPreset, bands: List<EqBand>)
    suspend fun deleteEqPreset(presetId: String)
}
