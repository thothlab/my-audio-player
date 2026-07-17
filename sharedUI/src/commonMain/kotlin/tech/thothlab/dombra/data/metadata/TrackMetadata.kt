package tech.thothlab.dombra.data.metadata

import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.model.ReplayGain

/** Результат парсинга метаданных одного файла. Все поля опциональны (§5.3: fallback). */
data class TrackMetadata(
    val format: AudioFormat = AudioFormat.UNKNOWN,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val year: Int? = null,
    val trackNo: Int? = null,
    val discNo: Int? = null,
    val durationMs: Long? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val replayGain: ReplayGain = ReplayGain(),
    val artwork: ByteArray? = null,
    val embeddedLyrics: String? = null,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = title.hashCode()
}
