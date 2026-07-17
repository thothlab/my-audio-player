package tech.thothlab.dombra.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tech.thothlab.dombra.data.store.LibraryStore
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Artist
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.model.TrackAvailability
import tech.thothlab.dombra.domain.ports.LibraryRepository
import tech.thothlab.dombra.domain.ports.TrackSort

/**
 * Общая реализация LibraryRepository. Поиск — case-insensitive и устойчив
 * к пустому запросу (§5.5); сортировки — title/artist/album/added/track order.
 */
class DefaultLibraryRepository(
    private val store: LibraryStore,
) : LibraryRepository {

    override fun tracks(sort: TrackSort, query: String): Flow<List<Track>> =
        store.tracksFlow().map { list -> list.filterBy(query).sortedBy(sort) }

    override fun favoriteTracks(query: String): Flow<List<Track>> =
        combine(store.tracksFlow(), store.favoritesFlow()) { tracks, favs ->
            tracks.filter { it.stableId in favs }.filterBy(query).sortedBy(TrackSort.TITLE)
        }

    override fun artists(query: String): Flow<List<Artist>> =
        store.artistsFlow().map { list ->
            list.filter { query.isBlank() || it.name.containsIgnoreCase(query) }
                .sortedBy { it.name.lowercase() }
        }

    override fun albums(query: String): Flow<List<Album>> =
        store.albumsFlow().map { list ->
            list.filter { query.isBlank() || it.title.containsIgnoreCase(query) }
                .sortedBy { it.title.lowercase() }
        }

    override fun albumTracks(albumId: String): Flow<List<Track>> =
        store.tracksFlow().map { list ->
            list.filter { it.albumId == albumId }.sortedBy(TrackSort.TRACK_ORDER)
        }

    override fun artistTracks(artistId: String): Flow<List<Track>> =
        store.tracksFlow().map { list ->
            list.filter { it.artistId == artistId }.sortedBy(TrackSort.ALBUM)
        }

    override fun artistAlbums(artistId: String): Flow<List<Album>> =
        store.albumsFlow().map { list ->
            list.filter { it.artistId == artistId }.sortedBy { it.title.lowercase() }
        }

    override suspend fun track(stableId: String): Track? = store.getTrack(stableId)

    override suspend fun artist(artistId: String): Artist? = store.getArtist(artistId)

    override suspend fun album(albumId: String): Album? = store.getAlbum(albumId)

    override fun isFavorite(stableId: String): Flow<Boolean> =
        store.favoritesFlow().map { stableId in it }

    override suspend fun setFavorite(stableId: String, favorite: Boolean) =
        store.setFavorite(stableId, favorite)

    override fun favoriteIds(): Flow<Set<String>> = store.favoritesFlow()

    override suspend fun removeFromLibrary(stableId: String) {
        store.deleteTracks(listOf(stableId))
        store.pruneOrphans()
    }

    override suspend fun markAvailability(stableId: String, available: Boolean) =
        store.updateAvailability(
            stableId,
            if (available) TrackAvailability.AVAILABLE else TrackAvailability.UNAVAILABLE,
        )

    private fun List<Track>.filterBy(query: String): List<Track> {
        if (query.isBlank()) return this
        return filter {
            it.title.containsIgnoreCase(query) ||
                it.artistName.containsIgnoreCase(query) ||
                it.albumTitle.containsIgnoreCase(query)
        }
    }

    private fun List<Track>.sortedBy(sort: TrackSort): List<Track> = when (sort) {
        TrackSort.TITLE -> sortedBy { it.title.lowercase() }
        TrackSort.ARTIST -> sortedWith(
            compareBy({ it.artistName.lowercase() }, { it.title.lowercase() }),
        )
        TrackSort.ALBUM -> sortedWith(
            compareBy({ it.albumTitle.lowercase() }, { it.discNo ?: 0 }, { it.trackNo ?: 0 }),
        )
        TrackSort.ADDED_DATE -> sortedByDescending { it.addedAt }
        TrackSort.TRACK_ORDER -> sortedWith(
            compareBy({ it.discNo ?: 0 }, { it.trackNo ?: 0 }, { it.title.lowercase() }),
        )
    }
}

internal fun String.containsIgnoreCase(other: String): Boolean =
    lowercase().contains(other.trim().lowercase())
