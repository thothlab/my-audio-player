package tech.thothlab.dombra.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tech.thothlab.dombra.core.Clock
import tech.thothlab.dombra.core.IdGenerator
import tech.thothlab.dombra.data.store.LibraryStore
import tech.thothlab.dombra.domain.model.Playlist
import tech.thothlab.dombra.domain.model.PlaylistItem
import tech.thothlab.dombra.domain.model.PlaylistWithItems
import tech.thothlab.dombra.domain.ports.PlaylistRepository

class DefaultPlaylistRepository(
    private val store: LibraryStore,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) : PlaylistRepository {

    override fun playlists(query: String): Flow<List<Playlist>> =
        store.playlistsFlow().map { list ->
            list.filter { query.isBlank() || it.title.containsIgnoreCase(query) }
                .sortedBy { it.title.lowercase() }
        }

    override fun playlist(id: String): Flow<PlaylistWithItems?> = store.playlistFlow(id)

    override suspend fun create(title: String): Playlist {
        val now = clock.nowMs()
        val playlist = Playlist(
            id = idGenerator.newId(),
            slug = uniqueSlug(title),
            title = title.trim(),
            createdAt = now,
            updatedAt = now,
        )
        store.upsertPlaylist(playlist)
        return playlist
    }

    override suspend fun rename(id: String, title: String) {
        val existing = store.getPlaylist(id)?.playlist ?: return
        store.upsertPlaylist(
            existing.copy(title = title.trim(), updatedAt = clock.nowMs()),
        )
    }

    override suspend fun delete(id: String) = store.deletePlaylist(id)

    override suspend fun addTrack(id: String, trackStableId: String) {
        val existing = store.getPlaylist(id) ?: return
        val items = existing.items + PlaylistItem(id, existing.items.size, trackStableId)
        store.replaceItems(id, items)
        touch(existing.playlist)
    }

    override suspend fun removeItem(id: String, position: Int) {
        val existing = store.getPlaylist(id) ?: return
        val items = existing.items.filterNot { it.position == position }
        store.replaceItems(id, items)
        touch(existing.playlist)
    }

    override suspend fun reorder(id: String, fromPosition: Int, toPosition: Int) {
        val existing = store.getPlaylist(id) ?: return
        val items = existing.items.sortedBy { it.position }.toMutableList()
        if (fromPosition !in items.indices || toPosition !in items.indices) return
        val moved = items.removeAt(fromPosition)
        items.add(toPosition, moved)
        store.replaceItems(id, items)
        touch(existing.playlist)
    }

    override suspend fun setCustomCover(id: String, coverUri: String?) {
        val existing = store.getPlaylist(id)?.playlist ?: return
        store.upsertPlaylist(existing.copy(customCoverUri = coverUri, updatedAt = clock.nowMs()))
    }

    override suspend fun touchLastPlayed(id: String) {
        val existing = store.getPlaylist(id)?.playlist ?: return
        store.upsertPlaylist(existing.copy(lastPlayedAt = clock.nowMs()))
    }

    private suspend fun touch(playlist: Playlist) {
        store.upsertPlaylist(playlist.copy(updatedAt = clock.nowMs()))
    }

    private suspend fun uniqueSlug(title: String): String {
        val base = title.trim().lowercase()
            .replace(Regex("[^a-z0-9а-яё]+"), "-")
            .trim('-')
            .ifEmpty { "playlist" }
        val existing = store.getAllPlaylists().map { it.playlist.slug }.toSet()
        if (base !in existing) return base
        var i = 2
        while ("$base-$i" in existing) i++
        return "$base-$i"
    }
}
