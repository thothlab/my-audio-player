package tech.thothlab.dombra.presentation.player

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import tech.thothlab.dombra.core.Clock
import tech.thothlab.dombra.core.IdGenerator
import tech.thothlab.dombra.data.repo.testTrack
import tech.thothlab.dombra.data.store.InMemoryLibraryStore
import tech.thothlab.dombra.domain.model.AppSettings
import tech.thothlab.dombra.domain.model.PlaybackSnapshot
import tech.thothlab.dombra.domain.model.RepeatMode
import tech.thothlab.dombra.domain.model.ReplayGain
import tech.thothlab.dombra.domain.model.ReplayGainMode
import tech.thothlab.dombra.domain.ports.FileStat
import tech.thothlab.dombra.domain.ports.SettingsRepository
import tech.thothlab.dombra.domain.ports.SourceRef
import tech.thothlab.dombra.domain.ports.StorageProvider

private class FakeStorage : StorageProvider {
    val missing = mutableSetOf<String>()
    override suspend fun stat(ref: SourceRef): FileStat? =
        if (ref.uri in missing) null else FileStat(1000L, 1L)
    override suspend fun listAudioFiles(dir: SourceRef): List<SourceRef> = emptyList()
    override suspend fun readBytes(ref: SourceRef, offset: Long, length: Long): ByteArray = ByteArray(0)
    override suspend fun persistAccess(ref: SourceRef) {}
    override val canDeleteFiles: Boolean = false
    override suspend fun deleteFile(ref: SourceRef): Boolean = false
}

private class InMemorySettings : SettingsRepository {
    val state = MutableStateFlow(AppSettings())
    var snapshot: PlaybackSnapshot? = null
    override val settings: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
    override suspend fun savePlaybackSnapshot(snapshot: PlaybackSnapshot) { this.snapshot = snapshot }
    override suspend fun loadPlaybackSnapshot(): PlaybackSnapshot? = snapshot
    override suspend fun secret(key: String): String? = null
    override suspend fun setSecret(key: String, value: String?) {}
}

private class SeqIds : IdGenerator {
    private var n = 0
    override fun newId(): String = "e${n++}"
}


/**
 * coroutines-test 1.11: advanceUntilIdle() не выполняет задачи, запланированные
 * на текущее виртуальное время (no-op для каскадов StateFlow → collector → launch).
 * runCurrent() прогоняет весь каскад целиком, включая старт корутин backgroundScope.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.settle() = runCurrent()

class PlaybackControllerTest {

    private fun buildController(
        scope: kotlinx.coroutines.CoroutineScope,
        engine: FakeAudioEngine = FakeAudioEngine(),
        storage: FakeStorage = FakeStorage(),
        settings: InMemorySettings = InMemorySettings(),
        store: InMemoryLibraryStore = InMemoryLibraryStore(),
    ) = PlaybackController(
        engine = engine,
        capability = FakeCapability(),
        store = store,
        storage = storage,
        settings = settings,
        idGenerator = SeqIds(),
        clock = Clock { 1L },
        scope = scope,
        random = Random(42),
    )

    private val tracks = listOf(
        testTrack("a", "Alpha"),
        testTrack("b", "Beta"),
        testTrack("c", "Gamma"),
    )

    @Test
    fun playNowBuildsQueueWithEntryIds() = runTest {
        val engine = FakeAudioEngine()
        val c = buildController(backgroundScope, engine)
        c.playNow(tracks[1], tracks)
        settle()
        assertEquals(3, c.state.value.queue.size)
        assertEquals(1, c.state.value.currentIndex)
        assertEquals("Beta", c.state.value.currentTrack?.title)
        assertTrue(c.state.value.isPlaying)
        assertEquals(1, engine.preparedSources.size)
    }

    @Test
    fun repeatOneReplaysSameTrack() = runTest {
        val engine = FakeAudioEngine()
        val c = buildController(backgroundScope, engine)
        c.playQueue(tracks)
        settle()
        c.cycleRepeatMode() // ALL
        c.cycleRepeatMode() // ONE
        engine.completeTrack()
        settle()
        assertEquals(0, c.state.value.currentIndex)
        assertEquals(2, engine.preparedSources.size)
        assertTrue(c.state.value.isPlaying)
    }

    @Test
    fun completionAdvancesAndStopsAtEndWithoutRepeat() = runTest {
        val engine = FakeAudioEngine()
        val c = buildController(backgroundScope, engine)
        c.playQueue(tracks)
        settle()
        engine.completeTrack(); settle()
        assertEquals(1, c.state.value.currentIndex)
        engine.completeTrack(); settle()
        assertEquals(2, c.state.value.currentIndex)
        engine.completeTrack(); settle()
        assertFalse(c.state.value.isPlaying, "должен остановиться в конце очереди")
    }

    @Test
    fun repeatAllWrapsAround() = runTest {
        val engine = FakeAudioEngine()
        val c = buildController(backgroundScope, engine)
        c.playQueue(tracks, startIndex = 2)
        settle()
        c.cycleRepeatMode() // ALL
        engine.completeTrack()
        settle()
        assertEquals(0, c.state.value.currentIndex)
        assertTrue(c.state.value.isPlaying)
    }

    @Test
    fun shuffleKeepsCurrentAndRestoresOrder() = runTest {
        val c = buildController(backgroundScope)
        c.playQueue(tracks, startIndex = 1)
        settle()
        c.toggleShuffle()
        settle()
        assertTrue(c.state.value.shuffled)
        assertEquals("Beta", c.state.value.currentTrack?.title, "текущий трек не меняется")
        assertEquals(0, c.state.value.currentIndex)

        c.toggleShuffle()
        settle()
        assertFalse(c.state.value.shuffled)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), c.state.value.queue.map { it.track.title })
        assertEquals("Beta", c.state.value.currentTrack?.title)
    }

    @Test
    fun duplicateEntriesRemovedIndividually() = runTest {
        val c = buildController(backgroundScope)
        c.playQueue(listOf(tracks[0], tracks[1], tracks[0]))
        settle()
        val queue = c.state.value.queue
        assertEquals(3, queue.size)
        assertEquals(queue[0].track.stableId, queue[2].track.stableId)

        c.removeEntry(queue[2].entryId)
        settle()
        assertEquals(listOf("Alpha", "Beta"), c.state.value.queue.map { it.track.title })
        assertEquals("Alpha", c.state.value.currentTrack?.title, "играющее вхождение не тронуто")
    }

    @Test
    fun unavailableTrackIsSkippedWithNotice() = runTest {
        val engine = FakeAudioEngine()
        val storage = FakeStorage()
        val store = InMemoryLibraryStore()
        store.upsertTracks(tracks)
        val c = buildController(backgroundScope, engine, storage, store = store)

        storage.missing += tracks[1].sourceUri // B недоступен
        val notices = mutableListOf<PlayerNotice>()
        val job = backgroundScope.launch { c.notices.collect { notices += it } }
        c.playQueue(tracks)
        settle()
        engine.completeTrack() // A доиграл → B пропущен → C
        settle()
        assertEquals("Gamma", c.state.value.currentTrack?.title)
        assertTrue(notices.any { it is PlayerNotice.TrackSkipped })
        job.cancel()
    }

    @Test
    fun previousRestartsAfterThreeSeconds() = runTest {
        val engine = FakeAudioEngine()
        val c = buildController(backgroundScope, engine)
        c.playQueue(tracks, startIndex = 1)
        settle()
        c.seekTo(5000L)
        settle()
        c.previous()
        settle()
        assertEquals(1, c.state.value.currentIndex, "перезапуск того же трека")
        assertEquals(0L, engine.lastSeek)
    }

    @Test
    fun restoreRebuildsQueuePausedAtPosition() = runTest {
        val settings = InMemorySettings()
        val store = InMemoryLibraryStore()
        store.upsertTracks(tracks)
        val engine = FakeAudioEngine()

        // первая «сессия»
        val c1 = buildController(backgroundScope, engine, settings = settings, store = store)
        c1.playQueue(tracks, startIndex = 1)
        settle()
        c1.seekTo(1234L)
        c1.cycleRepeatMode()
        settle()
        assertNotNull(settings.snapshot)

        // «перезапуск»
        val engine2 = FakeAudioEngine()
        val c2 = buildController(backgroundScope, engine2, settings = settings, store = store)
        c2.restore()
        settle()
        val s = c2.state.value
        assertEquals(3, s.queue.size)
        assertEquals(1, s.currentIndex)
        assertEquals(RepeatMode.ALL, s.repeatMode)
        assertFalse(s.isPlaying, "без автозапуска после рестарта")
    }

    @Test
    fun replayGainAppliedFromSettings() = runTest {
        val engine = FakeAudioEngine()
        val settings = InMemorySettings()
        settings.state.value = AppSettings(replayGainMode = ReplayGainMode.TRACK, replayGainPreampDb = 1.0)
        val rgTrack = testTrack("rg", "Loud").copy(replayGain = ReplayGain(trackGainDb = -6.5))
        val c = buildController(backgroundScope, engine, settings = settings)
        c.playNow(rgTrack)
        settle()
        assertEquals(-5.5, engine.lastGainDb)
    }
}
