package tech.thothlab.dombra.platform.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.thothlab.dombra.core.DombraError
import tech.thothlab.dombra.core.Log
import tech.thothlab.dombra.domain.ports.AudioEngine
import tech.thothlab.dombra.domain.ports.AudioFormatCapability
import tech.thothlab.dombra.domain.ports.AudioSource
import tech.thothlab.dombra.domain.ports.EngineState

/**
 * Android аудио-движок на media3/ExoPlayer (§7.2 ТЗ).
 *
 * ExoPlayer привязан к потоку своего Looper: движок создаёт плеер и делает все
 * вызовы на main-потоке (`Dispatchers.Main`), состояние получает через
 * [Player.Listener]. Декодирование форматов — на стороне media3 (см. [Media3Capability]).
 *
 * ReplayGain (dB-gain) в базовом media3 нет без кастомного AudioProcessor — [supportsGainDb] = false (T11).
 */
class Media3AudioEngine(
    context: Context,
    private val capability: AudioFormatCapability = Media3Capability(),
) : AudioEngine {

    private val log = Log.withTag("Media3DBG")
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _position = MutableSharedFlow<Long>(
        replay = 1,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val positionMs: Flow<Long> = _position

    private var player: ExoPlayer? = null
    private var positionJob: Job? = null
    private var durationMs: Long? = null

    @Volatile private var volume: Float = 1f

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** Создаётся лениво на main-потоке. */
    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val p = ExoPlayer.Builder(appContext).build()
        p.volume = volume
        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> _state.value = EngineState.Preparing
                    Player.STATE_READY -> {
                        durationMs = player?.duration?.takeIf { it != C.TIME_UNSET }
                        _state.value = if (player?.isPlaying == true) {
                            EngineState.Playing(durationMs)
                        } else {
                            EngineState.Ready(durationMs)
                        }
                    }
                    Player.STATE_ENDED -> {
                        stopPositionUpdates()
                        emitPosition()
                        _state.value = EngineState.Completed
                    }
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _state.value = EngineState.Playing(durationMs)
                    startPositionUpdates()
                } else {
                    stopPositionUpdates()
                    // Пауза — только из READY; ENDED/IDLE не перетирать.
                    if (player?.playbackState == Player.STATE_READY) {
                        _state.value = EngineState.Paused(durationMs)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                stopPositionUpdates()
                log.w { "ExoPlayer error: ${error.errorCodeName}" }
                _state.value = EngineState.Failed(
                    DombraError.PlaybackFailed(error.message ?: "Ошибка ExoPlayer", retryable = true),
                )
            }
        })
        player = p
        return p
    }

    override suspend fun prepare(source: AudioSource) = withContext(Dispatchers.Main.immediate) {
        if (!capability.isSupported(source.format)) {
            _state.value = EngineState.Failed(
                DombraError.FormatUnsupported(source.format.displayName, capability.backendName),
            )
            return@withContext
        }
        val p = ensurePlayer()
        _state.value = EngineState.Preparing
        durationMs = null
        p.setMediaItem(MediaItem.fromUri(source.uri))
        p.playWhenReady = false
        p.prepare() // Ready/Failed придут через listener
        emitPosition()
    }

    override fun play() = onMain { player?.play() }

    override fun pause() = onMain { player?.pause() }

    override suspend fun seekTo(positionMs: Long) = withContext(Dispatchers.Main.immediate) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
        emitPosition()
    }

    override fun stop() = onMain {
        stopPositionUpdates()
        player?.stop()
        player?.clearMediaItems()
        _position.tryEmit(0L)
        _state.value = EngineState.Idle
    }

    override val supportsVolume: Boolean = true

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        onMain { player?.volume = this.volume }
    }

    // media3 baseline не даёт dB-gain без кастомного AudioProcessor — ReplayGain отложен на T11.
    override val supportsGainDb: Boolean = false

    override fun setGainDb(gainDb: Double) = Unit

    override fun release() = onMain {
        stopPositionUpdates()
        player?.release()
        player = null
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                emitPosition()
                delay(POSITION_THROTTLE_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun emitPosition() {
        val pos = player?.currentPosition ?: 0L
        _position.tryEmit(pos.coerceAtLeast(0L))
    }

    private companion object {
        const val POSITION_THROTTLE_MS = 250L
    }
}
