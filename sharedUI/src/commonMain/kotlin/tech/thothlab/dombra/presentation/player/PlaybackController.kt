package tech.thothlab.dombra.presentation.player

import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import tech.thothlab.dombra.core.Clock
import tech.thothlab.dombra.core.DombraError
import tech.thothlab.dombra.core.DombraException
import tech.thothlab.dombra.core.IdGenerator
import tech.thothlab.dombra.core.Log
import tech.thothlab.dombra.data.store.LibraryStore
import tech.thothlab.dombra.domain.model.AppSettings
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.model.PlaybackSnapshot
import tech.thothlab.dombra.domain.model.QueueEntry
import tech.thothlab.dombra.domain.model.RepeatMode
import tech.thothlab.dombra.domain.model.ReplayGainMode
import tech.thothlab.dombra.domain.model.Track
import tech.thothlab.dombra.domain.model.TrackAvailability
import tech.thothlab.dombra.domain.ports.AudioEngine
import tech.thothlab.dombra.domain.ports.AudioFormatCapability
import tech.thothlab.dombra.domain.ports.AudioSource
import tech.thothlab.dombra.domain.ports.EngineState
import tech.thothlab.dombra.domain.ports.SettingsRepository
import tech.thothlab.dombra.domain.ports.SourceRef
import tech.thothlab.dombra.domain.ports.StorageProvider

/** Строка очереди с загруженным треком. */
data class QueueItem(
    val entryId: String,
    val track: Track,
)

data class PlayerState(
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long? = null,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffled: Boolean = false,
    val volume: Float = 1f,
    val error: DombraError? = null,
) {
    val currentItem: QueueItem? get() = queue.getOrNull(currentIndex)
    val currentTrack: Track? get() = currentItem?.track
    val hasQueue: Boolean get() = queue.isNotEmpty()
}

/** Разовые уведомления плеера (§5.8: пропуск недоступного файла и т.п.). */
sealed interface PlayerNotice {
    data class TrackSkipped(val track: Track, val reason: DombraError) : PlayerNotice
    data class PlaybackError(val track: Track?, val error: DombraError) : PlayerNotice
}

/**
 * Единственный источник истины воспроизведения (§5.8 ТЗ). UI не обращается
 * к AudioEngine напрямую. Очередь допускает дубли треков — идентификатор
 * строки очереди entryId уникален для вхождения.
 */
class PlaybackController(
    private val engine: AudioEngine,
    private val capability: AudioFormatCapability,
    private val store: LibraryStore,
    private val storage: StorageProvider,
    private val settings: SettingsRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) {
    private val log = Log.withTag("Playback")

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    private val _notices = MutableSharedFlow<PlayerNotice>(extraBufferCapacity = 16)
    val notices: SharedFlow<PlayerNotice> = _notices

    /** Порядок до включения shuffle — для восстановления (§5.8). */
    private var originalOrder: List<QueueItem> = emptyList()
    private var prepareJob: Job? = null
    private var appSettings = AppSettings()

    init {
        scope.launch {
            settings.settings.collect { appSettings = it }
        }
        scope.launch {
            engine.state.collect { engineState -> onEngineState(engineState) }
        }
        scope.launch {
            engine.positionMs.collect { pos ->
                _state.value = _state.value.copy(positionMs = pos)
            }
        }
    }

    // --- Запуск ---

    fun playNow(track: Track, context: List<Track> = emptyList()) {
        val tracks = context.ifEmpty { listOf(track) }
        val queue = tracks.map { QueueItem(idGenerator.newId(), it) }
        val index = tracks.indexOfFirst { it.stableId == track.stableId }.coerceAtLeast(0)
        originalOrder = queue
        _state.value = _state.value.copy(queue = queue, currentIndex = index, shuffled = false, error = null)
        startCurrent(autoPlay = true)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val queue = tracks.map { QueueItem(idGenerator.newId(), it) }
        originalOrder = queue
        _state.value = _state.value.copy(
            queue = queue,
            currentIndex = startIndex.coerceIn(0, queue.lastIndex),
            shuffled = false,
            error = null,
        )
        startCurrent(autoPlay = true)
    }

    // --- Транспорт ---

    fun togglePlayPause() {
        val s = _state.value
        if (s.currentTrack == null) return
        if (s.isPlaying) engine.pause() else engine.play()
    }

    fun next() = advance(step = 1, user = true)

    fun previous() {
        val s = _state.value
        if (s.positionMs > 3000) {
            seekTo(0L)
            return
        }
        advance(step = -1, user = true)
    }

    fun seekTo(positionMs: Long) {
        scope.launch { engine.seekTo(positionMs) }
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        if (engine.supportsVolume) engine.setVolume(v)
        _state.value = _state.value.copy(volume = v)
    }

    fun cycleRepeatMode() {
        val nextMode = when (_state.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _state.value = _state.value.copy(repeatMode = nextMode)
        persistSnapshot()
    }

    fun toggleShuffle() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        if (!s.shuffled) {
            originalOrder = s.queue
            val current = s.currentItem
            val rest = s.queue.filter { it.entryId != current?.entryId }.shuffled(random)
            val newQueue = listOfNotNull(current) + rest
            _state.value = s.copy(queue = newQueue, currentIndex = 0, shuffled = true)
        } else {
            val current = s.currentItem
            val restored = originalOrder.filter { orig ->
                s.queue.any { it.entryId == orig.entryId }
            }
            val idx = restored.indexOfFirst { it.entryId == current?.entryId }
            _state.value = s.copy(queue = restored, currentIndex = idx.coerceAtLeast(0), shuffled = false)
        }
        persistSnapshot()
    }

    // --- Очередь ---

    fun addNext(track: Track) {
        val s = _state.value
        val item = QueueItem(idGenerator.newId(), track)
        if (s.queue.isEmpty()) {
            playNow(track)
            return
        }
        val queue = s.queue.toMutableList().apply { add(s.currentIndex + 1, item) }
        originalOrder = originalOrder + item
        _state.value = s.copy(queue = queue)
        persistSnapshot()
    }

    fun addLast(track: Track) {
        val s = _state.value
        val item = QueueItem(idGenerator.newId(), track)
        if (s.queue.isEmpty()) {
            playNow(track)
            return
        }
        originalOrder = originalOrder + item
        _state.value = s.copy(queue = s.queue + item)
        persistSnapshot()
    }

    fun removeEntry(entryId: String) {
        val s = _state.value
        val idx = s.queue.indexOfFirst { it.entryId == entryId }
        if (idx < 0) return
        val queue = s.queue.toMutableList().apply { removeAt(idx) }
        originalOrder = originalOrder.filter { it.entryId != entryId }
        val newIndex = when {
            queue.isEmpty() -> -1
            idx < s.currentIndex -> s.currentIndex - 1
            idx == s.currentIndex -> s.currentIndex.coerceAtMost(queue.lastIndex)
            else -> s.currentIndex
        }
        _state.value = s.copy(queue = queue, currentIndex = newIndex)
        if (idx == s.currentIndex) {
            if (queue.isEmpty()) stop() else startCurrent(autoPlay = s.isPlaying)
        }
        persistSnapshot()
    }

    fun moveEntry(fromIndex: Int, toIndex: Int) {
        val s = _state.value
        if (fromIndex !in s.queue.indices || toIndex !in s.queue.indices) return
        val queue = s.queue.toMutableList()
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        val currentEntry = s.currentItem?.entryId
        val newIndex = queue.indexOfFirst { it.entryId == currentEntry }
        _state.value = s.copy(queue = queue, currentIndex = newIndex)
        persistSnapshot()
    }

    fun stop() {
        prepareJob?.cancel()
        engine.stop()
        _state.value = _state.value.copy(isPlaying = false, positionMs = 0L)
        persistSnapshot()
    }

    // --- Восстановление (§4.2.13) ---

    suspend fun restore() {
        val snapshot = settings.loadPlaybackSnapshot() ?: return
        if (snapshot.queue.isEmpty()) return
        val items = snapshot.queue.mapNotNull { entry ->
            store.getTrack(entry.trackStableId)?.let { QueueItem(entry.entryId, it) }
        }
        if (items.isEmpty()) return
        originalOrder = snapshot.originalOrder.mapNotNull { entry ->
            items.firstOrNull { it.entryId == entry.entryId }
                ?: store.getTrack(entry.trackStableId)?.let { QueueItem(entry.entryId, it) }
        }
        val index = snapshot.currentIndex.coerceIn(0, items.lastIndex)
        _state.value = _state.value.copy(
            queue = items,
            currentIndex = index,
            repeatMode = snapshot.repeatMode,
            shuffled = snapshot.shuffled,
            positionMs = snapshot.positionMs,
            isPlaying = false, // автозапуск запрещён (Web policy; §5.14)
        )
        // Готовим текущий трек на паузе с сохранённой позицией
        prepareCurrent(autoPlay = false, seekMs = snapshot.positionMs)
    }

    // --- Внутреннее ---

    private fun startCurrent(autoPlay: Boolean) {
        prepareCurrent(autoPlay = autoPlay, seekMs = 0L)
    }

    private fun prepareCurrent(autoPlay: Boolean, seekMs: Long) {
        val item = _state.value.currentItem ?: return
        prepareJob?.cancel()
        prepareJob = scope.launch {
            _state.value = _state.value.copy(isBuffering = true, error = null)
            try {
                if (!capability.isSupported(item.track.format)) {
                    throw DombraException(
                        DombraError.FormatUnsupported(item.track.format.displayName, capability.backendName),
                    )
                }
                val stat = storage.stat(SourceRef(item.track.sourceUri, item.track.sourceDisplayName))
                if (stat == null) {
                    store.updateAvailability(item.track.stableId, TrackAvailability.UNAVAILABLE)
                    throw DombraException(DombraError.SourceUnavailable(item.track.sourceUri))
                }
                engine.prepare(item.track.toAudioSource(effectiveGainDb(item.track)))
                if (seekMs > 0) engine.seekTo(seekMs)
                applyReplayGain(item.track)
                if (autoPlay) engine.play()
                persistSnapshot()
            } catch (e: DombraException) {
                log.w { "prepare failed: ${e.error.message}" }
                _notices.tryEmit(PlayerNotice.TrackSkipped(item.track, e.error))
                _state.value = _state.value.copy(isBuffering = false, error = e.error)
                autoSkipAfterFailure()
            }
        }
    }

    /** Недоступный трек пропускается, очередь продолжает работу (§5.8). */
    private var consecutiveFailures = 0

    private fun autoSkipAfterFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= _state.value.queue.size) {
            consecutiveFailures = 0
            _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
            return
        }
        advance(step = 1, user = false)
    }

    private fun advance(step: Int, user: Boolean) {
        val s = _state.value
        if (s.queue.isEmpty()) return
        val repeat = s.repeatMode
        val candidate = s.currentIndex + step
        val nextIndex = when {
            // автопереход при repeat=ONE повторяет текущий трек
            !user && repeat == RepeatMode.ONE -> s.currentIndex
            // конец очереди: ALL — по кольцу; ручной next — тоже по кольцу; автопереход — стоп
            candidate > s.queue.lastIndex -> if (repeat == RepeatMode.ALL || user) 0 else -1
            // назад с первого трека: ALL — в конец, иначе остаёмся на первом
            candidate < 0 -> if (repeat == RepeatMode.ALL) s.queue.lastIndex else 0
            else -> candidate
        }
        if (nextIndex < 0) {
            // конец очереди без repeat — останавливаемся
            engine.stop()
            _state.value = s.copy(isPlaying = false, positionMs = 0L)
            persistSnapshot()
            return
        }
        _state.value = s.copy(currentIndex = nextIndex)
        startCurrent(autoPlay = s.isPlaying || !user)
    }

    private fun onEngineState(engineState: EngineState) {
        val s = _state.value
        when (engineState) {
            is EngineState.Playing -> {
                consecutiveFailures = 0
                _state.value = s.copy(
                    isPlaying = true, isBuffering = false, durationMs = engineState.durationMs,
                )
            }
            is EngineState.Paused -> _state.value = s.copy(
                isPlaying = false, isBuffering = false, durationMs = engineState.durationMs,
            )
            is EngineState.Ready -> _state.value = s.copy(
                isBuffering = false, durationMs = engineState.durationMs,
            )
            EngineState.Completed -> advance(step = 1, user = false)
            is EngineState.Failed -> {
                val track = s.currentTrack
                _notices.tryEmit(PlayerNotice.PlaybackError(track, engineState.error))
                _state.value = s.copy(isPlaying = false, isBuffering = false, error = engineState.error)
                autoSkipAfterFailure()
            }
            EngineState.Preparing -> _state.value = s.copy(isBuffering = true)
            EngineState.Idle -> Unit
        }
    }

    private fun effectiveGainDb(track: Track): Double? {
        val rg = track.replayGain
        val gain = when (appSettings.replayGainMode) {
            ReplayGainMode.OFF -> return null
            ReplayGainMode.TRACK -> rg.trackGainDb ?: rg.albumGainDb
            ReplayGainMode.ALBUM -> rg.albumGainDb ?: rg.trackGainDb
        } ?: return null
        return gain + appSettings.replayGainPreampDb
    }

    private fun applyReplayGain(track: Track) {
        if (!engine.supportsGainDb) return
        engine.setGainDb(effectiveGainDb(track) ?: 0.0)
    }

    private fun persistSnapshot() {
        val s = _state.value
        scope.launch {
            settings.savePlaybackSnapshot(
                PlaybackSnapshot(
                    queue = s.queue.map { QueueEntry(it.entryId, it.track.stableId) },
                    originalOrder = originalOrder.map { QueueEntry(it.entryId, it.track.stableId) },
                    currentIndex = s.currentIndex,
                    positionMs = s.positionMs,
                    repeatMode = s.repeatMode,
                    shuffled = s.shuffled,
                    timestamp = clock.nowMs(),
                ),
            )
        }
    }
}

internal fun Track.toAudioSource(gainDb: Double?) = AudioSource(
    uri = sourceUri,
    displayName = sourceDisplayName,
    format = if (format == AudioFormat.UNKNOWN) {
        AudioFormat.byExtension(sourceDisplayName.substringAfterLast('.', ""))
    } else format,
    replayGainDb = gainDb,
    title = title,
    artist = artistName,
)
