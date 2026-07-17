package tech.thothlab.dombra.platform.audio

import java.io.BufferedInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Проба: подтверждает, что SPI-декодеры (mp3spi/vorbisspi/jflac) резолвятся
 * на JDK 21 и реально декодируют фикстуры в PCM. Формирует capability-матрицу
 * JVM-бэкенда: если формат здесь декодируется — он supported.
 */
class DecoderProbeTest {

    private fun stream(name: String) = BufferedInputStream(
        checkNotNull(javaClass.classLoader.getResource("fixtures/$name")) {
            "fixture $name not found"
        }.openStream(),
    )

    /** Декодирует до PCM и считает реально прочитанные семплы. */
    private fun decodePcmFrames(name: String): Long {
        stream(name).use { input ->
            AudioSystem.getAudioInputStream(input).use { encoded ->
                val base = encoded.format
                val pcm = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.sampleRate.takeIf { it > 0 } ?: 44100f,
                    16,
                    base.channels.takeIf { it > 0 } ?: 2,
                    (base.channels.takeIf { it > 0 } ?: 2) * 2,
                    base.sampleRate.takeIf { it > 0 } ?: 44100f,
                    false,
                )
                AudioSystem.getAudioInputStream(pcm, encoded).use { decoded ->
                    val buf = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = decoded.read(buf)
                        if (n < 0) break
                        total += n
                    }
                    return total
                }
            }
        }
    }

    @Test
    fun wavDecodesNatively() {
        assertTrue(decodePcmFrames("fixture.wav") > 0, "WAV must decode with built-in reader")
    }

    @Test
    fun mp3DecodesViaSpi() {
        assertTrue(decodePcmFrames("fixture.mp3") > 0, "mp3spi must decode fixture.mp3 to PCM")
    }

    @Test
    fun flacDecodesViaSpi() {
        assertTrue(decodePcmFrames("fixture.flac") > 0, "jflac must decode fixture.flac to PCM")
    }

    @Test
    fun oggVorbisDecodesViaSpi() {
        assertTrue(decodePcmFrames("fixture.ogg") > 0, "vorbisspi must decode fixture.ogg to PCM")
    }

    /** opus/m4a: нет чистого javax.sound SPI → честный unsupported (фиксация факта). */
    @Test
    fun opusAndM4aAreNotDecodable() {
        for (name in listOf("fixture.opus", "fixture.m4a")) {
            val decoded = runCatching { decodePcmFrames(name) }.isSuccess
            if (decoded) fail("$name unexpectedly decoded — revisit capability matrix (можно пометить supported)")
        }
    }
}
