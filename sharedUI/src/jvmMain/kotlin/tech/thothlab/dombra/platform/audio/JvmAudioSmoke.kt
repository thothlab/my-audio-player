package tech.thothlab.dombra.platform.audio

import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import tech.thothlab.dombra.core.DispatcherProvider
import tech.thothlab.dombra.core.ioDispatcher
import kotlinx.coroutines.Dispatchers
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.ports.AudioSource
import tech.thothlab.dombra.domain.ports.EngineState

/**
 * Ручной смоук: реально проигрывает файл через [JvmAudioEngine] (звук в колонки).
 * Единственная проверка «звук идёт», которую нельзя автоматизировать без железа.
 *
 * Запуск из IDE: выполнить `main`, передав путь к аудиофайлу первым аргументом.
 */
fun main(args: Array<String>) {
    val path = args.firstOrNull() ?: run {
        println("usage: JvmAudioSmoke <path-to-audio-file>")
        return
    }
    val file = File(path)
    val format = AudioFormat.byExtension(file.extension)

    val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Default
        override val default = Dispatchers.Default
        override val io = ioDispatcher()
    }
    val engine = JvmAudioEngine(dispatchers)

    runBlocking {
        engine.prepare(AudioSource(file.absolutePath, file.name, format))
        println("state after prepare: ${engine.state.value}")
        if (engine.state.value is EngineState.Failed) return@runBlocking

        engine.play()
        while (engine.state.value !is EngineState.Completed && engine.state.value !is EngineState.Failed) {
            delay(200)
        }
        println("final state: ${engine.state.value}")
        engine.release()
    }
}
