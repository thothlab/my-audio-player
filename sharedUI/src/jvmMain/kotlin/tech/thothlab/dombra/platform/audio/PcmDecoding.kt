package tech.thothlab.dombra.platform.audio

import java.io.BufferedInputStream
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Декодирование в PCM для JVM-движка. `AudioSystem` сам подхватывает SPI
 * (mp3spi/jflac/vorbisspi) с classpath, поэтому код не зависит от формата.
 * Целевой PCM: 16-bit signed, little-endian, частота/каналы исходника.
 */
internal object PcmDecoding {

    /** Открывает файл и возвращает поток, декодированный в 16-bit PCM. Бросает при неподдерживаемом формате/битом файле. */
    fun openPcmStream(file: File): AudioInputStream {
        val encoded = AudioSystem.getAudioInputStream(BufferedInputStream(file.inputStream()))
        val pcm = pcmFormatFor(encoded.format)
        return AudioSystem.getAudioInputStream(pcm, encoded)
    }

    fun pcmFormatFor(base: AudioFormat): AudioFormat {
        val rate = base.sampleRate.takeIf { it > 0 } ?: 44100f
        val channels = base.channels.takeIf { it > 0 } ?: 2
        return AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            rate,
            16,
            channels,
            channels * 2, // frameSize: 16 бит × каналы
            rate,
            false, // little-endian
        )
    }

    /** Длительность из PCM-потока; null если неизвестна (обычно у сжатых форматов). */
    fun durationMs(pcm: AudioInputStream): Long? {
        val frames = pcm.frameLength
        val rate = pcm.format.sampleRate
        if (frames <= 0 || rate <= 0f) return null
        return (frames / rate * 1000f).toLong()
    }

    /** Пропускает поток на позицию ms (для seek); возвращает фактически достигнутую позицию в кадрах. */
    fun skipToMs(pcm: AudioInputStream, ms: Long): Long {
        val frameSize = pcm.format.frameSize
        val rate = pcm.format.sampleRate
        if (ms <= 0 || frameSize <= 0 || rate <= 0f) return 0
        val targetFrame = (ms / 1000.0 * rate).toLong()
        var toSkip = targetFrame * frameSize
        while (toSkip > 0) {
            val skipped = pcm.skip(toSkip)
            if (skipped <= 0) break
            toSkip -= skipped
        }
        return targetFrame - toSkip / frameSize
    }
}
