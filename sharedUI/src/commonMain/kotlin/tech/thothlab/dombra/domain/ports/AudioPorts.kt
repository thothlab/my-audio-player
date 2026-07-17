package tech.thothlab.dombra.domain.ports

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tech.thothlab.dombra.core.DombraError
import tech.thothlab.dombra.domain.model.AudioFormat

/** Контракт аудио-движка (§7.2 ТЗ). Одна реализация на платформенный backend. */

sealed interface EngineState {
    data object Idle : EngineState
    data object Preparing : EngineState
    data class Ready(val durationMs: Long?) : EngineState
    data class Playing(val durationMs: Long?) : EngineState
    data class Paused(val durationMs: Long?) : EngineState
    data object Completed : EngineState
    data class Failed(val error: DombraError) : EngineState
}

/** Источник аудио для движка: URI + опциональные байты (web-сессия). */
data class AudioSource(
    val uri: String,
    val displayName: String,
    val format: AudioFormat,
    val replayGainDb: Double? = null,
)

interface AudioEngine {
    val state: StateFlow<EngineState>

    /** Позиция воспроизведения; эмитится не чаще ~4 Гц (§5.8: UI не дёргается тиками). */
    val positionMs: Flow<Long>

    suspend fun prepare(source: AudioSource)
    fun play()
    fun pause()
    suspend fun seekTo(positionMs: Long)
    fun stop()

    /** 0.0..1.0; null — backend не поддерживает управление громкостью. */
    val supportsVolume: Boolean
    fun setVolume(volume: Float)

    /** Применить поправку громкости в дБ (ReplayGain); false — не поддерживается. */
    val supportsGainDb: Boolean
    fun setGainDb(gainDb: Double)

    fun release()
}

/** Матрица возможностей backend-а (§5.9 ТЗ): честный список, не обещания. */
interface AudioFormatCapability {
    val backendName: String
    fun isSupported(format: AudioFormat): Boolean
    val supportsBackgroundPlayback: Boolean
    val supportsEqualizer: Boolean
    val notes: Map<AudioFormat, String> get() = emptyMap()
}

interface BackgroundPlaybackController {
    /** Включить платформенную интеграцию (foreground service / media session / notification). */
    fun activate()
    fun deactivate()
    val isAvailable: Boolean
}
