package tech.thothlab.dombra.data.metadata

import tech.thothlab.dombra.domain.model.AudioFormat

/** WAV (RIFF): fmt-чанк + data-размер (длительность) + LIST INFO теги. */
internal object WavParser {

    fun isWav(data: ByteArray): Boolean =
        data.size >= 12 && data.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            data.copyOfRange(8, 12).decodeToString() == "WAVE"

    fun parse(data: ByteArray): TrackMetadata {
        if (!isWav(data)) return TrackMetadata(format = AudioFormat.WAV)

        var sampleRate: Int? = null
        var channels: Int? = null
        var bitDepth: Int? = null
        var byteRate = 0L
        var dataSize = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var year: Int? = null
        var trackNo: Int? = null

        val r = ByteReader(data, 12)
        while (r.canRead(8)) {
            val chunkId = r.ascii(4)
            val chunkSize = r.u32le()
            val bodyStart = r.pos
            when (chunkId) {
                "fmt " -> {
                    if (r.canRead(16)) {
                        r.u16le() // audio format
                        channels = r.u16le()
                        sampleRate = r.u32le().toInt()
                        byteRate = r.u32le()
                        r.u16le() // block align
                        bitDepth = r.u16le()
                    }
                }
                "data" -> dataSize = chunkSize
                "LIST" -> {
                    if (r.canRead(4) && r.peekAscii(4) == "INFO") {
                        r.skip(4)
                        var read = 4L
                        while (read + 8 <= chunkSize && r.canRead(8)) {
                            val subId = r.ascii(4)
                            val subSize = r.u32le().toInt()
                            if (!r.canRead(subSize)) break
                            val value = TextDecoder.trimNulls(TextDecoder.latin1(r.bytes(subSize)))
                            when (subId) {
                                "INAM" -> title = value.ifEmpty { null }
                                "IART" -> artist = value.ifEmpty { null }
                                "IPRD" -> album = value.ifEmpty { null }
                                "ICRD" -> year = value.take(4).toIntOrNull()
                                "ITRK" -> trackNo = value.toIntOrNull()
                            }
                            if (subSize % 2 == 1) r.skip(1)
                            read += 8 + subSize + (subSize % 2)
                        }
                    }
                }
            }
            r.pos = bodyStart + chunkSize.toInt() + (chunkSize % 2).toInt()
            if (chunkSize <= 0) break
        }

        val durationMs = if (byteRate > 0 && dataSize > 0) dataSize * 1000 / byteRate else null

        return TrackMetadata(
            format = AudioFormat.WAV,
            title = title, artist = artist, album = album,
            year = year, trackNo = trackNo,
            durationMs = durationMs,
            sampleRate = sampleRate, bitDepth = bitDepth, channels = channels,
        )
    }
}
