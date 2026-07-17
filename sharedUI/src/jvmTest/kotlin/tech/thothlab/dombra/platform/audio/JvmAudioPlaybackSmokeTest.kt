package tech.thothlab.dombra.platform.audio

import java.io.File
import javax.sound.sampled.AudioFormat as JavaAudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import tech.thothlab.dombra.core.DispatcherProvider
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.ports.AudioSource
import tech.thothlab.dombra.domain.ports.EngineState

/**
 * Реальное проигрывание через [SourceDataLine]. Пропускается (JUnit assume),
 * когда аудио-выхода нет (linux-CI). На машине с устройством прогоняет WAV
 * до [EngineState.Completed] и проверяет, что позиция продвинулась.
 */
class JvmAudioPlaybackSmokeTest {

    private val dispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = Dispatchers.Default
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val io: CoroutineDispatcher = Dispatchers.IO
    }

    private fun audioOutputAvailable(): Boolean = runCatching {
        AudioSystem.isLineSupported(
            DataLine.Info(SourceDataLine::class.java, JavaAudioFormat(44100f, 16, 2, true, false)),
        )
    }.getOrDefault(false)

    private fun fixturePath(name: String): String =
        File(checkNotNull(javaClass.classLoader.getResource("fixtures/$name")).toURI()).absolutePath

    @Test
    fun wavPlaysToCompletion() {
        assumeTrue("Нет аудио-выхода — пропуск playback-смоука", audioOutputAvailable())

        val engine = JvmAudioEngine(dispatchers)
        val positions = mutableListOf<Long>()

        runBlocking {
            val collector = launch { engine.positionMs.collect { positions += it } }
            engine.prepare(AudioSource(fixturePath("fixture.wav"), "fixture.wav", AudioFormat.WAV))
            assertTrue(engine.state.value is EngineState.Ready, "prepare must reach Ready")

            engine.setVolume(0f) // тест немой: проверяем тракт, не бужу колонки
            engine.play()

            val completed = withTimeoutOrNull(10_000) {
                while (engine.state.value !is EngineState.Completed) {
                    if (engine.state.value is EngineState.Failed) break
                    delay(50)
                }
                engine.state.value
            }
            collector.cancel()
            engine.release()

            assertTrue(completed is EngineState.Completed, "expected Completed, got $completed / ${engine.state.value}")
            assertTrue(positions.any { it > 0 }, "position must advance during playback")
        }
    }

    /** Пауза → возобновление → stop не виснут (в т.ч. если поток блокирован в line.write). */
    @Test
    fun pauseResumeStopDoesNotHang() {
        assumeTrue("Нет аудио-выхода — пропуск playback-смоука", audioOutputAvailable())

        val engine = JvmAudioEngine(dispatchers)
        runBlocking {
            withTimeout(15_000) {
                engine.prepare(AudioSource(fixturePath("fixture.wav"), "fixture.wav", AudioFormat.WAV))
                engine.setVolume(0f)
                engine.play()
                // дождаться реального старта проигрывания
                while (engine.state.value !is EngineState.Playing) delay(20)
                delay(150)

                engine.pause()
                assertTrue(engine.state.value is EngineState.Paused, "expected Paused, got ${engine.state.value}")

                engine.play() // resume
                while (engine.state.value !is EngineState.Playing) delay(20)

                engine.stop() // здесь мог зависнуть висящий line.write — ловится withTimeout
                assertTrue(engine.state.value is EngineState.Idle, "expected Idle, got ${engine.state.value}")
            }
            engine.release()
        }
    }
}
