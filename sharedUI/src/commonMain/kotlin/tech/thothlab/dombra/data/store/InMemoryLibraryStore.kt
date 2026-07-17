package tech.thothlab.dombra.data.store

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * In-memory реализация: используется в тестах и как ядро web-хранилища
 * (webMain добавляет персист в browser storage через [onMutated]).
 */
open class InMemoryLibraryStore : LibraryStore {

    protected val mutex = Mutex()

    protected val tracksState = MutableStateFlow<Map<String, Track>>(emptyMap())
    protected val artistsState = MutableStateFlow<Map<String, Artist>>(emptyMap())
    protected val albumsState = MutableStateFlow<Map<String, Album>>(emptyMap())
    protected val favoritesState = MutableStateFlow<Set<String>>(emptySet())
    protected val playlistsState = MutableStateFlow<Map<String, PlaylistWithItems>>(emptyMap())
    protected val lyricsState = MutableStateFlow<Map<String, Lyrics>>(emptyMap())
    protected val eqSettingsState = MutableStateFlow(EqSettings())
    protected val eqPresetsState = MutableStateFlow<Map<String, Pair<EqPreset, List<EqBand>>>>(emptyMap())

    /** Хук для персиста после каждой мутации. */
    protected open suspend fun onMutated() {}

    // Tracks

    override fun tracksFlow(): Flow<List<Track>> = tracksState.map { it.values.toList() }

    override suspend fun upsertTracks(tracks: List<Track>) = mutate {
        tracksState.value = tracksState.value + tracks.associateBy { it.stableId }
    }

    override suspend fun getTrack(stableId: String): Track? = tracksState.value[stableId]

    override suspend fun getAllTracks(): List<Track> = tracksState.value.values.toList()

    override suspend fun deleteTracks(stableIds: Collection<String>) = mutate {
        tracksState.value = tracksState.value - stableIds.toSet()
        favoritesState.value = favoritesState.value - stableIds.toSet()
        lyricsState.value = lyricsState.value - stableIds.toSet()
    }

    override suspend fun updateAvailability(stableId: String, availability: TrackAvailability) = mutate {
        val track = tracksState.value[stableId] ?: return@mutate
        tracksState.value = tracksState.value + (stableId to track.copy(availability = availability))
    }

    // Artists / Albums

    override fun artistsFlow(): Flow<List<Artist>> = artistsState.map { it.values.toList() }

    override fun albumsFlow(): Flow<List<Album>> = albumsState.map { it.values.toList() }

    override suspend fun upsertArtists(artists: List<Artist>) = mutate {
        artistsState.value = artistsState.value + artists.associateBy { it.id }
    }

    override suspend fun upsertAlbums(albums: List<Album>) = mutate {
        albumsState.value = albumsState.value + albums.associateBy { it.id }
    }

    override suspend fun getArtist(id: String): Artist? = artistsState.value[id]

    override suspend fun getAlbum(id: String): Album? = albumsState.value[id]

    override suspend fun pruneOrphans() = mutate {
        val tracks = tracksState.value.values
        val usedAlbums = tracks.map { it.albumId }.toSet()
        val usedArtists = tracks.map { it.artistId }.toSet() +
            albumsState.value.values.filter { it.id in usedAlbums }.map { it.artistId }
        albumsState.value = albumsState.value.filterKeys { it in usedAlbums }
        artistsState.value = artistsState.value.filterKeys { it in usedArtists }
    }

    // Favorites

    override fun favoritesFlow(): Flow<Set<String>> = favoritesState

    override suspend fun getFavorites(): Set<String> = favoritesState.value

    override suspend fun setFavorite(stableId: String, favorite: Boolean) = mutate {
        favoritesState.value =
            if (favorite) favoritesState.value + stableId else favoritesState.value - stableId
    }

    // Playlists

    override fun playlistsFlow(): Flow<List<Playlist>> =
        playlistsState.map { all -> all.values.map { it.playlist } }

    override fun playlistFlow(id: String): Flow<PlaylistWithItems?> =
        playlistsState.map { it[id] }

    override suspend fun getPlaylist(id: String): PlaylistWithItems? = playlistsState.value[id]

    override suspend fun getAllPlaylists(): List<PlaylistWithItems> =
        playlistsState.value.values.toList()

    override suspend fun upsertPlaylist(playlist: Playlist) = mutate {
        val existing = playlistsState.value[playlist.id]
        playlistsState.value = playlistsState.value +
            (playlist.id to PlaylistWithItems(playlist, existing?.items ?: emptyList()))
    }

    override suspend fun deletePlaylist(id: String) = mutate {
        playlistsState.value = playlistsState.value - id
    }

    override suspend fun replaceItems(playlistId: String, items: List<PlaylistItem>) = mutate {
        val existing = playlistsState.value[playlistId] ?: return@mutate
        val renumbered = items.mapIndexed { index, item ->
            item.copy(playlistId = playlistId, position = index)
        }
        playlistsState.value = playlistsState.value +
            (playlistId to existing.copy(items = renumbered))
    }

    // Lyrics

    override suspend fun getLyrics(stableId: String): Lyrics? = lyricsState.value[stableId]

    override suspend fun saveLyrics(lyrics: Lyrics) = mutate {
        lyricsState.value = lyricsState.value + (lyrics.trackStableId to lyrics)
    }

    override suspend fun deleteLyrics(stableId: String) = mutate {
        lyricsState.value = lyricsState.value - stableId
    }

    // EQ

    override fun eqSettingsFlow(): Flow<EqSettings> = eqSettingsState

    override suspend fun saveEqSettings(settings: EqSettings) = mutate {
        eqSettingsState.value = settings
    }

    override fun eqPresetsFlow(): Flow<List<EqPreset>> =
        eqPresetsState.map { all -> all.values.map { it.first } }

    override suspend fun getEqBands(presetId: String): List<EqBand> =
        eqPresetsState.value[presetId]?.second ?: emptyList()

    override suspend fun saveEqPreset(preset: EqPreset, bands: List<EqBand>) = mutate {
        eqPresetsState.value = eqPresetsState.value + (preset.id to (preset to bands))
    }

    override suspend fun deleteEqPreset(presetId: String) = mutate {
        eqPresetsState.value = eqPresetsState.value - presetId
        val current = eqSettingsState.value
        if (current.activePresetId == presetId) {
            eqSettingsState.value = current.copy(activePresetId = null)
        }
    }

    private suspend inline fun mutate(block: () -> Unit) {
        mutex.withLock { block() }
        onMutated()
    }
}
