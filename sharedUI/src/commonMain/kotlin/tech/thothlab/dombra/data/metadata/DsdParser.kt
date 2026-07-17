package tech.thothlab.dombra.data.metadata

import tech.thothlab.dombra.domain.model.AudioFormat

/**
 * DSD-контейнеры: DSF (Sony) и DFF (Philips DSDIFF). Извлекаются параметры
 * потока и ID3-теги (DSF); воспроизведение — по capability платформы (§5.9).
 */
internal object DsdParser {

    fun isDsf(data: ByteArray): Boolean =
        data.size >= 4 && data.copyOfRange(0, 4).decodeToString() == "DSD "

    fun isDff(data: ByteArray): Boolean =
        data.size >= 4 && data.copyOfRange(0, 4).decodeToString() == "FRM8"

    fun parseDsf(data: ByteArray): TrackMetadata {
        var sampleRate: Int? = null
        var channels: Int? = null
        var bitDepth: Int? = null
        var durationMs: Long? = null
        var id3 = Id3Parser.Id3Data()

        val r = ByteReader(data, 4)
        if (r.canRead(24)) {
            r.u64le() // chunk size
            r.u64le() // total file size
            val metadataPtr = r.u64le()
            // fmt-чанк следует сразу за DSD-чанком (28 байт)
            val fmt = ByteReader(data, 28)
            if (fmt.canRead(52) && fmt.ascii(4) == "fmt ") {
                fmt.u64le() // chunk size
                fmt.u32le() // version
                fmt.u32le() // format id
                fmt.u32le() // channel type
                channels = fmt.u32le().toInt()
                sampleRate = fmt.u32le().toInt()
                bitDepth = fmt.u32le().toInt()
                val sampleCount = fmt.u64le()
                if (sampleRate > 0 && sampleCount > 0) {
                    durationMs = sampleCount * 1000 / sampleRate
                }
            }
            if (metadataPtr in 1 until data.size.toLong()) {
                val tagStart = metadataPtr.toInt()
                if (Id3Parser.hasId3v2(data.copyOfRange(tagStart, data.size))) {
                    id3 = Id3Parser.parseV2(data.copyOfRange(tagStart, data.size))
                }
            }
        }

        return TrackMetadata(
            format = AudioFormat.DSF,
            title = id3.title, artist = id3.artist, album = id3.album,
            albumArtist = id3.albumArtist, year = id3.year,
            trackNo = id3.trackNo, discNo = id3.discNo,
            durationMs = durationMs, sampleRate = sampleRate,
            bitDepth = bitDepth, channels = channels,
            replayGain = id3.replayGain, artwork = id3.artwork,
            embeddedLyrics = id3.lyrics,
        )
    }

    fun parseDff(data: ByteArray): TrackMetadata {
        var sampleRate: Int? = null
        var channels: Int? = null
        var dataSize = 0L

        // FRM8 + size(8) + "DSD " + чанки: PROP { FS, CHNL }, DSD
        var pos = 16
        while (pos + 12 <= data.size) {
            val id = data.copyOfRange(pos, pos + 4).decodeToString()
            var size = 0L
            for (i in 4 until 12) size = (size shl 8) or (data[pos + i].toLong() and 0xff)
            val bodyStart = pos + 12
            when (id) {
                "PROP" -> {
                    var p = bodyStart + 4 // skip "SND "
                    val propEnd = (bodyStart + size).toInt().coerceAtMost(data.size)
                    while (p + 12 <= propEnd) {
                        val subId = data.copyOfRange(p, p + 4).decodeToString()
                        var subSize = 0L
                        for (i in 4 until 12) subSize = (subSize shl 8) or (data[p + i].toLong() and 0xff)
                        when (subId) {
                            "FS  " -> {
                                val r = ByteReader(data, p + 12)
                                if (r.canRead(4)) sampleRate = r.u32be().toInt()
                            }
                            "CHNL" -> {
                                val r = ByteReader(data, p + 12)
                                if (r.canRead(2)) channels = r.u16be()
                            }
                        }
                        p += 12 + subSize.toInt() + (subSize % 2).toInt()
                    }
                }
                "DSD " -> dataSize = size
            }
            if (size <= 0) break
            pos = bodyStart + size.toInt() + (size % 2).toInt()
        }

        val durationMs = if (sampleRate != null && sampleRate > 0 && channels != null &&
            channels > 0 && dataSize > 0
        ) {
            dataSize * 8 * 1000 / (sampleRate.toLong() * channels)
        } else null

        return TrackMetadata(
            format = AudioFormat.DFF,
            durationMs = durationMs,
            sampleRate = sampleRate,
            bitDepth = 1,
            channels = channels,
        )
    }
}
