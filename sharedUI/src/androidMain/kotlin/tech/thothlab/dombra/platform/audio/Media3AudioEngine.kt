package tech.thothlab.dombra.platform.audio

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
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
 * Android аудио-движок (§7.2 ТЗ). Плеер (ExoPlayer + FFmpeg) живёт в
 * [PlaybackService] (`MediaSessionService`) — так воспроизведение переживает
 * сворачивание, и появляются нотификация/локскрин/кнопки гарнитуры «бесплатно».
 * Движок управляет им через [MediaController] (тот же `Player`-интерфейс),
 * подключение асинхронное. Состояние — через [Player.Listener], позиция — поллинг ≤4 Гц.
 *
 * ReplayGain (dB-gain) отложен на T11 — [supportsGainDb] = false.
 */
class Media3AudioEngine(
    context: Context,
    private val capability: AudioFormatCapability = Media3Capability(),
) : AudioEngine {

    private val log = Log.withTag("Media3")
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

    private var player: Player? = null
    private val ready = CompletableDeferred<Player>()
    private var positionJob: Job? = null
    private var durationMs: Long? = null

    @Volatile private var volume: Float = 1f

    init {
        // Асинхронное подключение MediaController к PlaybackService (на main-потоке).
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                val c = runCatching { future.get() }.getOrNull()
                if (c == null) {
                    log.w { "MediaController не подключился" }
                    return@addListener
                }
                c.volume = volume
                c.addListener(playerListener)
                player = c
                ready.complete(c)
            },
            Executor { mainHandler.post(it) },
        )
    }

    private val playerListener = object : Player.Listener {
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
                if (player?.playbackState == Player.STATE_READY) {
                    _state.value = EngineState.Paused(durationMs)
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            // Есть аудио-дорожка, но ни одна не декодируется → честная ошибка, не молчаливая имитация.
            val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audio.isEmpty()) return
            val playable = audio.any { g -> (0 until g.length).any { g.isTrackSupported(it) } }
            if (!playable) {
                val mime = audio.first().getTrackFormat(0).sampleMimeType
                val friendly = when (mime) {
                    "audio/alac" -> "ALAC (Apple Lossless)"
                    "audio/flac" -> "FLAC"
                    else -> mime ?: "аудио"
                }
                log.w { "нет декодера для дорожки: $mime" }
                stopPositionUpdates()
                _state.value = EngineState.Failed(
                    DombraError.FormatUnsupported(friendly, capability.backendName),
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            stopPositionUpdates()
            log.w { "player error: ${error.errorCodeName}" }
            _state.value = EngineState.Failed(
                DombraError.PlaybackFailed(error.message ?: "Ошибка воспроизведения", retryable = true),
            )
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    override suspend fun prepare(source: AudioSource) = withContext(Dispatchers.Main.immediate) {
        if (!capability.isSupported(source.format)) {
            _state.value = EngineState.Failed(
                DombraError.FormatUnsupported(source.format.displayName, capability.backendName),
            )
            return@withContext
        }
        val c = player ?: ready.await()
        _state.value = EngineState.Preparing
        durationMs = null
        val item = MediaItem.Builder()
            .setUri(source.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(source.title ?: source.displayName)
                    .setArtist(source.artist)
                    .build(),
            )
            .build()
        c.setMediaItem(item)
        c.playWhenReady = false
        c.prepare()
        emitPosition()
    }

    override fun play() = onMain { player?.play() }

    override fun pause() = onMain { player?.pause() }

    override suspend fun seekTo(positionMs: Long): Unit = withContext(Dispatchers.Main.immediate) {
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
