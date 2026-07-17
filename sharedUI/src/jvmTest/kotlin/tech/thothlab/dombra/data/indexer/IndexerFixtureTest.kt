package tech.thothlab.dombra.data.indexer

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tech.thothlab.dombra.core.Clock
import tech.thothlab.dombra.core.DispatcherProvider
import tech.thothlab.dombra.data.store.InMemoryLibraryStore
import tech.thothlab.dombra.domain.model.TrackAvailability
import tech.thothlab.dombra.domain.ports.ArtworkRepository
import tech.thothlab.dombra.domain.ports.ScanEvent
import tech.thothlab.dombra.domain.ports.SourceRef
import tech.thothlab.dombra.platform.JavaFileStorageProvider

private class TestDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Default
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val io: CoroutineDispatcher get() = Dispatchers.Default
}

private class InMemoryArtwork : ArtworkRepository {
    val stored = mutableMapOf<String, ByteArray>()
    override suspend fun store(key: String, bytes: ByteArray) { stored[key] = bytes }
    override suspend fun load(key: String): ByteArray? = stored[key]
    override suspend fun contains(key: String): Boolean = key in stored
    override suspend fun clearAll() = stored.clear()
}

/** E2E индексатора на реальных fixtures: идемпотентность, fallback, corrupted, reconcile. */
class IndexerFixtureTest {

    private lateinit var dir: File
    private val store = InMemoryLibraryStore()
    private val artwork = InMemoryArtwork()
    private val dispatchers = TestDispatchers()
    private val storage = JavaFileStorageProvider(dispatchers)
    private val indexer = DefaultLibraryIndexer(
        storage = storage, store = store, artworkRepository = artwork,
        clock = Clock { 42L }, dispatchers = dispatchers,
    )

    @BeforeTest
    fun setUp() {
        // Копируем fixtures во временную папку — тесты не трогают оригиналы
        dir = Files.createTempDirectory("dombra-idx").toFile()
        val res = checkNotNull(javaClass.classLoader.getResource("fixtures"))
        File(res.toURI()).listFiles()!!.forEach { f ->
            if (f.extension != "png") f.copyTo(File(dir, f.name))
        }
    }

    private fun dirRef() = SourceRef(uri = dir.absolutePath, displayName = dir.name, isDirectory = true)

    @Test
    fun scanIndexesSupportedAndSkipsCorrupted() = runTest {
        val done = indexer.scan(listOf(dirRef()), fullScan = true).last() as ScanEvent.Done
        // 9 аудиофайлов: mp3, flac, 24bit.flac, wav, m4a, ogg, opus, no-tags.mp3, corrupted.mp3
        assertEquals(9, done.stats.found)
        assertEquals(8, done.stats.added)
        assertEquals(1, done.stats.failed) // corrupted.mp3
        assertEquals(8, store.getAllTracks().size)
    }

    @Test
    fun rescanIsIdempotent() = runTest {
        indexer.scan(listOf(dirRef()), fullScan = true).last()
        val second = indexer.scan(listOf(dirRef()), fullScan = true).last() as ScanEvent.Done
        assertEquals(0, second.stats.added)
        assertEquals(0, second.stats.updated)
        assertEquals(8, second.stats.skipped)
        assertEquals(8, store.getAllTracks().size)
    }

    @Test
    fun fallbackMetadataForUntagged() = runTest {
        indexer.scan(listOf(dirRef()), fullScan = true).last()
        val untagged = store.getAllTracks().first { it.sourceDisplayName == "no-tags.mp3" }
        assertEquals("no-tags", untagged.title)
        assertEquals("Unknown artist", untagged.artistName)
        assertEquals("Unknown album", untagged.albumTitle)
    }

    @Test
    fun renamedFileKeepsStableIdAndFavorites() = runTest {
        indexer.scan(listOf(dirRef()), fullScan = true).last()
        val track = store.getAllTracks().first { it.sourceDisplayName == "fixture.mp3" }
        store.setFavorite(track.stableId, true)

        val f = File(dir, "fixture.mp3")
        val renamed = File(dir, "renamed-song.mp3")
        // переименование обновляет mtime? нет — но uri меняется; выставим тот же mtime
        val mtime = f.lastModified()
        assertTrue(f.renameTo(renamed))
        renamed.setLastModified(mtime)

        indexer.scan(listOf(dirRef()), fullScan = true).last()
        val after = store.getAllTracks().first { it.sourceDisplayName == "renamed-song.mp3" }
        assertEquals(track.stableId, after.stableId)
        assertTrue(after.stableId in store.getFavorites())
        assertEquals(8, store.getAllTracks().size, "no duplicate after rename")
    }

    @Test
    fun reconcileRemovesVanishedAfterFullScan() = runTest {
        indexer.scan(listOf(dirRef()), fullScan = true).last()
        assertTrue(File(dir, "fixture.wav").delete())
        val done = indexer.scan(listOf(dirRef()), fullScan = true).last() as ScanEvent.Done
        assertEquals(7, store.getAllTracks().size)
        assertTrue(store.getAllTracks().none { it.sourceDisplayName == "fixture.wav" })
        assertNotNull(done)
    }

    @Test
    fun artworkCachedSeparately() = runTest {
        indexer.scan(listOf(dirRef()), fullScan = true).last()
        val mp3 = store.getAllTracks().first { it.sourceDisplayName == "fixture.mp3" }
        assertTrue(mp3.hasEmbeddedArt)
        assertNotNull(artwork.load(mp3.stableId))
    }

    @Test
    fun progressEventsEmitted() = runTest {
        val events = indexer.scan(listOf(dirRef()), fullScan = true).toList()
        assertTrue(events.first() is ScanEvent.Progress)
        assertTrue(events.last() is ScanEvent.Done)
    }
}
