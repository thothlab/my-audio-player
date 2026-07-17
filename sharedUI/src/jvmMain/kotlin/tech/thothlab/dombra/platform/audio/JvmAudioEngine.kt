package tech.thothlab.dombra.platform.audio

import java.io.File
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import tech.thothlab.dombra.core.DispatcherProvider
import tech.thothlab.dombra.core.DombraError
import tech.thothlab.dombra.core.DombraException
import tech.thothlab.dombra.core.Log
import tech.thothlab.dombra.domain.ports.AudioEngine
import tech.thothlab.dombra.domain.ports.AudioFormatCapability
import tech.thothlab.dombra.domain.ports.AudioSource
import tech.thothlab.dombra.domain.ports.EngineState

/**
 * Desktop-JVM аудио-движок на javax.sound.sampled (§7.2 ТЗ).
 *
 * Разделение ядро/оболочка: [prepare] только декодирует (валидация формата и
 * файла, длительность) — работает без звукового устройства, покрыто CI-тестами.
 * [play] открывает [SourceDataLine] и запускает поток проигрывания — требует
 * аудио-выход, проверяется гейтед-тестом и `main()`-смоуком.
 *
 * Декодеры (mp3spi/jflac/vorbisspi) подхватываются с classpath — см. [JvmAudioCapability].
 */
class JvmAudioEngine(
    private val dispatchers: DispatcherProvider,
    private val capability: AudioFormatCapability = JvmAudioCapability(),
) : AudioEngine {

    private val log = Log.withTag("JvmAudio")

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _position = MutableSharedFlow<Long>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    override val positionMs: Flow<Long> = _position

    private val lock = Any()
    private var prepared: Prepared? = null
    private var worker: PlaybackWorker? = null
    private var startMs: Long = 0L

    @Volatile private var volume: Float = 1f
    @Volatile private var gainDb: Double = 0.0

    private data class Prepared(val file: File, val durationMs: Long?)

    override suspend fun prepare(source: AudioSource) = withContext(dispatchers.io) {
        stopWorker()
        synchronized(lock) { startMs = 0L }
        _state.value = EngineState.Preparing
        try {
            if (!capability.isSupported(source.format)) {
                throw DombraException(
                    DombraError.FormatUnsupported(source.format.displayName, capability.backendName),
                )
            }
            val file = File(source.uri)
            if (!file.isFile || !file.canRead()) {
                throw DombraException(DombraError.SourceUnavailable(source.uri))
            }
            source.replayGainDb?.let { gainDb = it }
            val durationMs = validateAndMeasure(file)
            synchronized(lock) { prepared = Prepared(file, durationMs) }
            _position.tryEmit(0L)
            _state.value = EngineState.Ready(durationMs)
        } catch (e: DombraException) {
            log.w { "prepare failed: ${e.error.message}" }
            _state.value = EngineState.Failed(e.error)
        } catch (e: Exception) {
            log.w { "decode failed: ${e.message}" }
            _state.value = EngineState.Failed(
                DombraError.PlaybackFailed(e.message ?: "Не удалось декодировать", retryable = false),
            )
        }
    }

    /** Форсирует декод первых кадров (ловит битый файл) и возвращает длительность. */
    private fun validateAndMeasure(file: File): Long? {
        PcmDecoding.openPcmStream(file).use { pcm ->
            val duration = PcmDecoding.durationMs(pcm)
            // Некоторые SPI (vorbisspi) на первых read() возвращают 0 (прайминг) —
            // читаем до первого ненулевого блока или EOF, ограничив число попыток.
            val probe = ByteArray(4096)
            var produced = 0
            var attempts = 0
            while (produced == 0 && attempts < 128) {
                val n = pcm.read(probe)
                if (n < 0) break
                produced += n
                attempts++
            }
            if (produced <= 0) {
                throw DombraException(
                    DombraError.PlaybackFailed("Пустой аудиопоток", retryable = false),
                )
            }
            return duration
        }
    }

    override fun play() {
        synchronized(lock) {
            val p = prepared ?: return
            val existing = worker
            if (existing != null) {
                existing.resume()
                return
            }
            val w = PlaybackWorker(p.file, p.durationMs, startMs)
            worker = w
            try {
                w.start()
            } catch (e: Exception) {
                worker = null
                log.w { "audio output unavailable: ${e.message}" }
                _state.value = EngineState.Failed(
                    DombraError.PlaybackFailed("Аудио-выход недоступен: ${e.message}", retryable = true),
                )
            }
        }
    }

    override fun pause() {
        synchronized(lock) { worker?.pause() }
    }

    override suspend fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        synchronized(lock) {
            val w = worker
            if (w != null) {
                w.requestSeek(target)
            } else {
                startMs = target
                _position.tryEmit(target)
            }
        }
    }

    override fun stop() {
        stopWorker()
        synchronized(lock) { startMs = 0L }
        _position.tryEmit(0L)
        _state.value = EngineState.Idle
    }

    override val supportsVolume: Boolean = true

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        synchronized(lock) { worker?.applyGain() }
    }

    override val supportsGainDb: Boolean = true

    override fun setGainDb(gainDb: Double) {
        this.gainDb = gainDb
        synchronized(lock) { worker?.applyGain() }
    }

    override fun release() {
        stopWorker()
        synchronized(lock) { prepared = null }
    }

    private fun stopWorker() {
        val w = synchronized(lock) { worker.also { worker = null } }
        w?.stopAndJoin()
    }

    /** Итоговая поправка в дБ = громкость(0..1)→дБ + ReplayGain, ограниченная контролом линии. */
    private fun combinedGainDb(): Double {
        val volDb = if (volume <= 0f) -80.0 else 20.0 * log10(volume.toDouble())
        return volDb + gainDb
    }

    /** Поток проигрывания: блокирующий IO javax.sound под volatile-флагами (устойчивее борьбы с отменой корутин). */
    private inner class PlaybackWorker(
        private val file: File,
        private val durationMs: Long?,
        private val initialMs: Long,
    ) {
        private lateinit var line: SourceDataLine
        private var thread: Thread? = null

        @Volatile private var lineReady = false
        @Volatile private var paused = false
        @Volatile private var stopRequested = false
        @Volatile private var seekRequestMs = -1L
        @Volatile private var framePosition = 0L
        private var frameRate = 44100f
        private var frameSize = 4

        fun start() {
            val first = PcmDecoding.openPcmStream(file)
            val fmt = first.format
            frameRate = fmt.sampleRate
            frameSize = fmt.frameSize
            line = AudioSystem.getSourceDataLine(fmt)
            line.open(fmt)
            lineReady = true
            applyGain()
            line.start()
            _state.value = EngineState.Playing(durationMs)
            thread = Thread({ runLoop(first) }, "dombra-jvm-audio").apply {
                isDaemon = true
                start()
            }
        }

        private fun runLoop(firstStream: AudioInputStream) {
            var stream = firstStream
            var lastEmit = 0L
            try {
                if (initialMs > 0) framePosition = PcmDecoding.skipToMs(stream, initialMs)
                val buf = ByteArray(4096)
                while (!stopRequested) {
                    val seek = seekRequestMs
                    if (seek >= 0) {
                        seekRequestMs = -1L
                        stream.close()
                        line.flush()
                        stream = PcmDecoding.openPcmStream(file)
                        framePosition = PcmDecoding.skipToMs(stream, seek)
                        emitPosition()
                        lastEmit = nowMs()
                    }
                    if (paused) {
                        Thread.sleep(20)
                        continue
                    }
                    val n = stream.read(buf)
                    if (n < 0) {
                        line.drain()
                        onCompleted()
                        return
                    }
                    var off = 0
                    while (off < n && !stopRequested && seekRequestMs < 0 && !paused) {
                        val written = line.write(buf, off, n - off)
                        off += written
                        framePosition += written / frameSize
                        val now = nowMs()
                        if (now - lastEmit >= POSITION_THROTTLE_MS) {
                            emitPosition()
                            lastEmit = now
                        }
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                log.w { "playback loop error: ${e.message}" }
                _state.value = EngineState.Failed(
                    DombraError.PlaybackFailed(e.message ?: "Ошибка проигрывания", retryable = true),
                )
            } finally {
                runCatching { stream.close() }
                runCatching { line.stop() }
                runCatching { line.close() }
            }
        }

        private fun onCompleted() {
            synchronized(lock) { if (worker === this) worker = null }
            emitPosition()
            _state.value = EngineState.Completed
        }

        fun pause() {
            if (paused) return
            paused = true
            runCatching { line.stop() }
            _state.value = EngineState.Paused(durationMs)
        }

        fun resume() {
            if (!paused) return
            paused = false
            runCatching { line.start() }
            _state.value = EngineState.Playing(durationMs)
        }

        fun requestSeek(ms: Long) {
            seekRequestMs = ms
        }

        fun applyGain() {
            if (!lineReady) return
            if (!line.isControlSupported(FloatControl.Type.MASTER_GAIN)) return
            val control = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val value = combinedGainDb().toFloat().coerceIn(control.minimum, control.maximum)
            control.value = value
        }

        fun stopAndJoin() {
            stopRequested = true
            thread?.let {
                it.interrupt()
                runCatching { it.join(1000) }
            }
        }

        private fun emitPosition() {
            val ms = (framePosition / frameRate * 1000f).toLong()
            _position.tryEmit(ms)
        }
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000

    private companion object {
        const val POSITION_THROTTLE_MS = 250L
    }
}
