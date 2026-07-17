package tech.thothlab.dombra.domain.ports

import kotlinx.coroutines.flow.Flow
import tech.thothlab.dombra.domain.model.Playlist
import tech.thothlab.dombra.domain.model.PlaylistWithItems

interface PlaylistRepository {
    fun playlists(query: String = ""): Flow<List<Playlist>>
    fun playlist(id: String): Flow<PlaylistWithItems?>

    suspend fun create(title: String): Playlist
    suspend fun rename(id: String, title: String)
    suspend fun delete(id: String)

    /** Дубликаты допустимы (§5.7): трек добавляется новой позицией в конец. */
    suspend fun addTrack(id: String, trackStableId: String)
    suspend fun removeItem(id: String, position: Int)
    suspend fun reorder(id: String, fromPosition: Int, toPosition: Int)

    suspend fun setCustomCover(id: String, coverUri: String?)
    suspend fun touchLastPlayed(id: String)
}
