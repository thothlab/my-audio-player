package tech.thothlab.dombra.data.store.room

import androidx.room.Room
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import tech.thothlab.dombra.data.store.LibraryStore
import tech.thothlab.dombra.data.store.LibraryStoreContractTest
import tech.thothlab.dombra.domain.model.Album
import tech.thothlab.dombra.domain.model.Playlist

/** Контракт LibraryStore против Room (bundled SQLite, in-memory). */
class RoomLibraryStoreTest : LibraryStoreContractTest() {

    private val databases = mutableListOf<DombraDatabase>()

    override fun createStore(): LibraryStore {
        val db = Room.inMemoryDatabaseBuilder<DombraDatabase>().buildDombraDatabase()
        databases += db
        return RoomLibraryStore(db)
    }

    @AfterTest
    fun closeDatabases() {
        databases.forEach { it.close() }
        databases.clear()
    }

    // --- Room-специфика: unique-констрейнты схемы (T03 §4) ---

    @Test
    fun albumDuplicateArtistTitleCannotCreateSecondRow() = runTest {
        val store = createStore()
        store.upsertAlbums(listOf(Album("album:x|lp", "artist:x", "LP")))
        // другой id, та же пара (artistId, title): бросит или проигнорирует,
        // но второй строки быть не должно
        runCatching { store.upsertAlbums(listOf(Album("album:rogue", "artist:x", "LP"))) }
        val albums = store.albumsFlow().first()
        assertEquals(1, albums.count { it.artistId == "artist:x" && it.title == "LP" })
    }

    @Test
    fun playlistDuplicateSlugCannotCreateSecondRow() = runTest {
        val store = createStore()
        val base = Playlist(id = "p1", slug = "mix", title = "Mix", createdAt = 1L, updatedAt = 1L)
        store.upsertPlaylist(base)
        runCatching { store.upsertPlaylist(base.copy(id = "p2", title = "Rogue")) }
        assertEquals(1, store.getAllPlaylists().count { it.playlist.slug == "mix" })
    }
}
