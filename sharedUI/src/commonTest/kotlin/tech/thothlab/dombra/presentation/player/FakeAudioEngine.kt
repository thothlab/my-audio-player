package tech.thothlab.dombra.presentation.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tech.thothlab.dombra.core.DombraError
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.ports.AudioEngine
import tech.thothlab.dombra.domain.ports.AudioFormatCapability
import tech.thothlab.dombra.domain.ports.AudioSource
import tech.thothlab.dombra.domain.ports.EngineState

class FakeAudioEngine : AudioEngine {

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state

    private val _position = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    override val positionMs: Flow<Long> = _position

    val preparedSources = mutableListOf<AudioSource>()
    var failOnPrepare: ((AudioSource) -> DombraError?)? = null
    var lastSeek: Long? = null
    var lastGainDb: Double? = null
    var released = false

    override suspend fun prepare(source: AudioSource) {
        preparedSources += source
        val error = failOnPrepare?.invoke(source)
        if (error != null) {
            _state.value = EngineState.Failed(error)
            return
        }
        _state.value = EngineState.Ready(durationMs = 3000L)
    }

    override fun play() {
        _state.value = EngineState.Playing(durationMs = 3000L)
    }

    override fun pause() {
        _state.value = EngineState.Paused(durationMs = 3000L)
    }

    override suspend fun seekTo(positionMs: Long) {
        lastSeek = positionMs
        _position.tryEmit(positionMs)
    }

    override fun stop() {
        _state.value = EngineState.Idle
    }

    override val supportsVolume: Boolean = true
    override fun setVolume(volume: Float) {}

    override val supportsGainDb: Boolean = true
    override fun setGainDb(gainDb: Double) {
        lastGainDb = gainDb
    }

    override fun release() {
        released = true
    }

    /** Тест сигналит «трек доиграл». */
    fun completeTrack() {
        _state.value = EngineState.Completed
    }
}

class FakeCapability(
    private val unsupported: Set<AudioFormat> = emptySet(),
) : AudioFormatCapability {
    override val backendName: String = "fake"
    override fun isSupported(format: AudioFormat): Boolean = format !in unsupported
    override val supportsBackgroundPlayback: Boolean = false
    override val supportsEqualizer: Boolean = false
}
