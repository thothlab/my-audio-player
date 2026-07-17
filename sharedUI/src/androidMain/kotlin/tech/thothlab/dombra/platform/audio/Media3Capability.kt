package tech.thothlab.dombra.platform.audio

import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.ports.AudioFormatCapability

/**
 * Матрица возможностей Android-бэкенда (media3/ExoPlayer, §5.9 ТЗ).
 * ExoPlayer нативно тянет mp3/aac(m4a)/flac/wav/ogg-vorbis/opus (через свои
 * экстракторы + системные декодеры). DSD (DSF/DFF) ExoPlayer не поддерживает.
 *
 * Фоновое воспроизведение и EQ появятся позже (MediaSessionService, T11) —
 * сейчас честно false.
 */
class Media3Capability : AudioFormatCapability {
    override val backendName: String = "Android (media3)"

    private val supported = setOf(
        AudioFormat.MP3,
        AudioFormat.M4A,
        AudioFormat.FLAC,
        AudioFormat.WAV,
        AudioFormat.OGG_VORBIS,
        AudioFormat.OPUS,
    )

    override fun isSupported(format: AudioFormat): Boolean = format in supported

    override val supportsBackgroundPlayback: Boolean = false

    override val supportsEqualizer: Boolean = false

    override val notes: Map<AudioFormat, String> = mapOf(
        AudioFormat.DSF to "DSD: не поддерживается ExoPlayer",
        AudioFormat.DFF to "DSD: не поддерживается ExoPlayer",
    )
}
