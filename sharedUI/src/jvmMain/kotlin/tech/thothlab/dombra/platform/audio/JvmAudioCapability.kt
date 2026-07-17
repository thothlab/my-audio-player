package tech.thothlab.dombra.platform.audio

import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.ports.AudioFormatCapability

/**
 * Матрица возможностей Desktop-JVM backend-а (§5.9 ТЗ) — честный список по факту
 * доступных javax.sound SPI (подтверждён `DecoderProbeTest`):
 * WAV нативно; MP3/FLAC/OGG-Vorbis — через mp3spi/jflac/vorbisspi на classpath.
 * Opus и AAC/M4A не имеют стабильного javax.sound SPI → unsupported.
 * DSD (DSF/DFF) на JVM не декодируется.
 */
class JvmAudioCapability : AudioFormatCapability {
    override val backendName: String = "JVM (javax.sound)"

    private val supported = setOf(
        AudioFormat.WAV,
        AudioFormat.MP3,
        AudioFormat.FLAC,
        AudioFormat.OGG_VORBIS,
    )

    override fun isSupported(format: AudioFormat): Boolean = format in supported

    override val supportsBackgroundPlayback: Boolean = false

    // EQ на JVM-бэкенде — отдельная задача (T11), базовый движок его не даёт.
    override val supportsEqualizer: Boolean = false

    override val notes: Map<AudioFormat, String> = mapOf(
        AudioFormat.M4A to "AAC/M4A: нет javax.sound SPI на JVM",
        AudioFormat.OPUS to "Opus: нет javax.sound SPI на JVM",
        AudioFormat.DSF to "DSD: не поддерживается JVM-бэкендом",
        AudioFormat.DFF to "DSD: не поддерживается JVM-бэкендом",
    )
}
