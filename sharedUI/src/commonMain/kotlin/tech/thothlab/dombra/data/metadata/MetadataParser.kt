package tech.thothlab.dombra.data.metadata

import tech.thothlab.dombra.domain.model.AudioFormat

/**
 * Диспетчер парсеров: формат определяется по магическим байтам (§5.2 ТЗ:
 * расширение — не доказательство корректности). Возвращает null, если
 * содержимое не распознано ни одним парсером.
 */
object MetadataParser {

    /** Определение формата по содержимому. */
    fun detectFormat(head: ByteArray): AudioFormat = when {
        FlacParser.isFlac(head) -> AudioFormat.FLAC
        WavParser.isWav(head) -> AudioFormat.WAV
        Mp4Parser.isMp4(head) -> AudioFormat.M4A
        OggParser.isOgg(head) -> AudioFormat.OGG_VORBIS // уточняется в parse (Opus)
        DsdParser.isDsf(head) -> AudioFormat.DSF
        DsdParser.isDff(head) -> AudioFormat.DFF
        Id3Parser.hasId3v2(head) -> AudioFormat.MP3
        isMp3FrameSync(head) -> AudioFormat.MP3
        else -> AudioFormat.UNKNOWN
    }

    /**
     * @param head первые [StableId.CHUNK]+ байт файла
     * @param tail последние байты файла (ID3v1, Ogg granule)
     * @param full полное содержимое, если индексатор дочитал файл (m4a с moov в конце, dsf-теги)
     */
    fun parse(head: ByteArray, tail: ByteArray, fileSize: Long, full: ByteArray? = null): TrackMetadata? {
        val data = full ?: head
        return when (detectFormat(head)) {
            AudioFormat.FLAC -> FlacParser.parse(data)
            AudioFormat.WAV -> WavParser.parse(data)
            AudioFormat.M4A -> Mp4Parser.parse(data)
            AudioFormat.OGG_VORBIS, AudioFormat.OPUS -> OggParser.parse(data, tail)
            AudioFormat.DSF -> DsdParser.parseDsf(data)
            AudioFormat.DFF -> DsdParser.parseDff(data)
            AudioFormat.MP3 -> Mp3Parser.toMetadata(data, tail, fileSize)
            AudioFormat.UNKNOWN -> null
        }
    }

    /** Нужно ли читать файл целиком для полного парсинга. */
    fun needsFullRead(head: ByteArray): Boolean = when (detectFormat(head)) {
        AudioFormat.M4A -> !Mp4Parser.hasMoov(head)
        AudioFormat.DSF -> true // ID3-теги лежат в конце по metadata pointer
        else -> false
    }

    private fun isMp3FrameSync(head: ByteArray): Boolean {
        if (head.size < 2) return false
        return (head[0].toInt() and 0xff) == 0xff && (head[1].toInt() and 0xe0) == 0xe0
    }
}
