package tech.thothlab.dombra.data.repo

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import tech.thothlab.dombra.core.Clock
import tech.thothlab.dombra.core.IdGenerator
import tech.thothlab.dombra.data.store.InMemoryLibraryStore

private class FakeClock(var now: Long = 1000L) : Clock {
    override fun nowMs(): Long = now++
}

private class SeqIdGenerator : IdGenerator {
    private var counter = 0
    override fun newId(): String = "id-${counter++}"
}

class PlaylistRepositoryTest {

    private val store = InMemoryLibraryStore()
    private val repo = DefaultPlaylistRepository(store, FakeClock(), SeqIdGenerator())

    @Test
    fun createRenameDelete() = runTest {
        val p = repo.create("Мой плейлист")
        assertEquals("Мой плейлист", p.title)
        repo.rename(p.id, "Новое имя")
        assertEquals("Новое имя", repo.playlist(p.id).first()?.playlist?.title)
        repo.delete(p.id)
        assertNull(repo.playlist(p.id).first())
    }

    @Test
    fun slugsAreUnique() = runTest {
        val a = repo.create("Rock")
        val b = repo.create("Rock")
        val c = repo.create("Rock")
        assertEquals("rock", a.slug)
        assertEquals("rock-2", b.slug)
        assertEquals("rock-3", c.slug)
    }

    @Test
    fun duplicateTrackAllowed() = runTest {
        val p = repo.create("Dups")
        repo.addTrack(p.id, "track-a")
        repo.addTrack(p.id, "track-b")
        repo.addTrack(p.id, "track-a")
        val items = repo.playlist(p.id).first()!!.items
        assertEquals(listOf("track-a", "track-b", "track-a"), items.map { it.trackStableId })
        assertEquals(listOf(0, 1, 2), items.map { it.position })
    }

    @Test
    fun removeSingleOccurrence() = runTest {
        val p = repo.create("Dups")
        repo.addTrack(p.id, "a")
        repo.addTrack(p.id, "b")
        repo.addTrack(p.id, "a")
        repo.removeItem(p.id, 2)
        val items = repo.playlist(p.id).first()!!.items
        assertEquals(listOf("a", "b"), items.map { it.trackStableId })
    }

    @Test
    fun reorderPersistsPositions() = runTest {
        val p = repo.create("Order")
        repo.addTrack(p.id, "a")
        repo.addTrack(p.id, "b")
        repo.addTrack(p.id, "c")
        repo.reorder(p.id, fromPosition = 2, toPosition = 0)
        val items = repo.playlist(p.id).first()!!.items
        assertEquals(listOf("c", "a", "b"), items.map { it.trackStableId })
        assertEquals(listOf(0, 1, 2), items.map { it.position })
    }

    @Test
    fun deletePlaylistKeepsLibrary() = runTest {
        val p = repo.create("Temp")
        repo.addTrack(p.id, "a")
        store.setFavorite("a", true)
        repo.delete(p.id)
        assertEquals(setOf("a"), store.getFavorites())
    }
}
