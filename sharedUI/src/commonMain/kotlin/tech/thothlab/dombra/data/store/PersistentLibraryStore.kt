package tech.thothlab.dombra.data.store

import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
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

/** Полный сериализуемый снимок библиотеки — формат web-персиста (T03). */
@Serializable
data class LibrarySnapshot(
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val playlistItems: List<PlaylistItem> = emptyList(),
    val lyrics: List<Lyrics> = emptyList(),
    val eqSettings: EqSettings = EqSettings(),
    val eqPresets: List<EqPreset> = emptyList(),
    val eqBands: List<EqBand> = emptyList(),
)

/**
 * Персистентная реализация для платформ без Room: логика — InMemory,
 * после каждой мутации наружу уходит [LibrarySnapshot] (web: kstore/localStorage).
 * [load] восстанавливает состояние при старте — сценарий «Перезапуск вкладки».
 */
class PersistentLibraryStore(
    private val persist: suspend (LibrarySnapshot) -> Unit,
    private val restore: suspend () -> LibrarySnapshot?,
) : InMemoryLibraryStore() {

    suspend fun load() {
        val snapshot = restore() ?: return
        mutex.withLock {
            tracksState.value = snapshot.tracks.associateBy { it.stableId }
            artistsState.value = snapshot.artists.associateBy { it.id }
            albumsState.value = snapshot.albums.associateBy { it.id }
            favoritesState.value = snapshot.favorites
            val itemsByPlaylist = snapshot.playlistItems.groupBy { it.playlistId }
            playlistsState.value = snapshot.playlists.associate { playlist ->
                playlist.id to PlaylistWithItems(
                    playlist,
                    itemsByPlaylist[playlist.id].orEmpty().sortedBy { it.position },
                )
            }
            lyricsState.value = snapshot.lyrics.associateBy { it.trackStableId }
            eqSettingsState.value = snapshot.eqSettings
            val bandsByPreset = snapshot.eqBands.groupBy { it.presetId }
            eqPresetsState.value = snapshot.eqPresets.associate { preset ->
                preset.id to (preset to bandsByPreset[preset.id].orEmpty().sortedBy { it.index })
            }
        }
    }

    override suspend fun onMutated() = persist(snapshot())

    private fun snapshot() = LibrarySnapshot(
        tracks = tracksState.value.values.toList(),
        artists = artistsState.value.values.toList(),
        albums = albumsState.value.values.toList(),
        favorites = favoritesState.value,
        playlists = playlistsState.value.values.map { it.playlist },
        playlistItems = playlistsState.value.values.flatMap { it.items },
        lyrics = lyricsState.value.values.toList(),
        eqSettings = eqSettingsState.value,
        eqPresets = eqPresetsState.value.values.map { it.first },
        eqBands = eqPresetsState.value.values.flatMap { it.second },
    )
}
