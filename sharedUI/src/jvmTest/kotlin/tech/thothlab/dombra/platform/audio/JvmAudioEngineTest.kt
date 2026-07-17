package tech.thothlab.dombra.platform.audio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import tech.thothlab.dombra.core.DispatcherProvider
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.ports.AudioSource
import tech.thothlab.dombra.domain.ports.EngineState

/**
 * Decode/state-ядро JVM-движка: работает без аудио-устройства (prepare только
 * декодирует), поэтому проходит на CI. Проигрывание — в [JvmAudioPlaybackSmokeTest].
 */
class JvmAudioEngineTest {

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Default
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
    }

    private fun fixturePath(name: String): String =
        File(checkNotNull(javaClass.classLoader.getResource("fixtures/$name")).toURI()).absolutePath

    private fun source(name: String, format: AudioFormat) =
        AudioSource(uri = fixturePath(name), displayName = name, format = format)

    private fun prepared(name: String, format: AudioFormat): EngineState {
        val engine = JvmAudioEngine(dispatchers)
        runBlocking { engine.prepare(source(name, format)) }
        val state = engine.state.value
        engine.release()
        return state
    }

    @Test
    fun wavPrepareReadyWithDuration() {
        val state = prepared("fixture.wav", AudioFormat.WAV)
        assertTrue(state is EngineState.Ready, "expected Ready, got $state")
        assertTrue((state.durationMs ?: 0) > 0, "WAV duration must be known, got ${state.durationMs}")
    }

    @Test
    fun mp3PrepareReady() {
        assertTrue(prepared("fixture.mp3", AudioFormat.MP3) is EngineState.Ready)
    }

    @Test
    fun flacPrepareReady() {
        assertTrue(prepared("fixture.flac", AudioFormat.FLAC) is EngineState.Ready)
    }

    @Test
    fun oggPrepareReady() {
        assertTrue(prepared("fixture.ogg", AudioFormat.OGG_VORBIS) is EngineState.Ready)
    }

    @Test
    fun missingFileFailsUnavailable() {
        val engine = JvmAudioEngine(dispatchers)
        runBlocking {
            engine.prepare(AudioSource("/no/such/file.wav", "file.wav", AudioFormat.WAV))
        }
        val state = engine.state.value
        assertTrue(state is EngineState.Failed, "expected Failed, got $state")
        assertTrue(state.error is tech.thothlab.dombra.core.DombraError.SourceUnavailable)
    }

    @Test
    fun unsupportedFormatFailsWithActionableError() {
        val state = prepared("fixture.m4a", AudioFormat.M4A)
        assertTrue(state is EngineState.Failed, "expected Failed, got $state")
        assertTrue(state.error is tech.thothlab.dombra.core.DombraError.FormatUnsupported)
    }

    @Test
    fun corruptedFileFails() {
        // corrupted.mp3 — усечённый: декод первых кадров должен упасть → Failed
        assertTrue(prepared("corrupted.mp3", AudioFormat.MP3) is EngineState.Failed)
    }

    @Test
    fun capabilityMatrixIsHonest() {
        val cap = JvmAudioCapability()
        for (f in listOf(AudioFormat.WAV, AudioFormat.MP3, AudioFormat.FLAC, AudioFormat.OGG_VORBIS)) {
            assertTrue(cap.isSupported(f), "$f must be supported")
        }
        for (f in listOf(AudioFormat.M4A, AudioFormat.OPUS, AudioFormat.DSF, AudioFormat.DFF)) {
            assertEquals(false, cap.isSupported(f), "$f must be unsupported")
        }
    }
}
