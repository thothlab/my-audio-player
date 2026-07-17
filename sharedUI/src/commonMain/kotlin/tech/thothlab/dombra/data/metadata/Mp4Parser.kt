package tech.thothlab.dombra.data.metadata

import tech.thothlab.dombra.domain.model.AudioFormat

/**
 * MP4/M4A: обход atom-дерева — mvhd (длительность), stsd/mp4a (частота/каналы),
 * moov.udta.meta.ilst (теги iTunes). moov может лежать в конце файла — парсеру
 * передаётся полное содержимое (индексатор дочитывает файл при необходимости).
 */
internal object Mp4Parser {

    fun isMp4(data: ByteArray): Boolean =
        data.size >= 12 && data.copyOfRange(4, 8).decodeToString() == "ftyp"

    fun parse(data: ByteArray): TrackMetadata {
        var durationMs: Long? = null
        var sampleRate: Int? = null
        var channels: Int? = null
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var year: Int? = null
        var trackNo: Int? = null
        var discNo: Int? = null
        var artwork: ByteArray? = null
        var lyrics: String? = null

        fun walk(start: Int, end: Int, path: String) {
            var pos = start
            while (pos + 8 <= end) {
                var size = ((data[pos].toInt() and 0xff).toLong() shl 24) or
                    ((data[pos + 1].toInt() and 0xff).toLong() shl 16) or
                    ((data[pos + 2].toInt() and 0xff).toLong() shl 8) or
                    (data[pos + 3].toInt() and 0xff).toLong()
                val type = TextDecoder.latin1(data.copyOfRange(pos + 4, pos + 8))
                var headerSize = 8
                if (size == 1L) { // 64-bit size
                    if (pos + 16 > end) return
                    size = 0
                    for (i in 8 until 16) size = (size shl 8) or (data[pos + i].toLong() and 0xff)
                    headerSize = 16
                }
                if (size < headerSize || pos + size > end) return
                val bodyStart = pos + headerSize
                val bodyEnd = (pos + size).toInt()
                val newPath = if (path.isEmpty()) type else "$path.$type"

                when (newPath) {
                    "moov", "moov.trak", "moov.trak.mdia", "moov.trak.mdia.minf",
                    "moov.trak.mdia.minf.stbl", "moov.udta", "moov.udta.meta.ilst",
                    -> walk(bodyStart, bodyEnd, newPath)

                    "moov.udta.meta" -> walk(bodyStart + 4, bodyEnd, newPath) // fullbox: 4 байта version/flags

                    "moov.mvhd" -> {
                        val version = data[bodyStart].toInt() and 0xff
                        val r = ByteReader(data, bodyStart + 4)
                        if (version == 1) {
                            r.skip(16)
                            val timescale = r.u32be()
                            val duration = r.u64be()
                            if (timescale > 0) durationMs = duration * 1000 / timescale
                        } else {
                            r.skip(8)
                            val timescale = r.u32be()
                            val duration = r.u32be()
                            if (timescale > 0) durationMs = duration * 1000 / timescale
                        }
                    }

                    "moov.trak.mdia.minf.stbl.stsd" -> {
                        val r = ByteReader(data, bodyStart + 8) // version/flags + entry count
                        if (r.canRead(8)) {
                            r.skip(4) // entry size
                            val fmt = r.ascii(4)
                            if (fmt == "mp4a" || fmt == "alac") {
                                r.skip(16) // reserved(6)+dataRefIdx(2)+version(2)+rev(2)+vendor(4)
                                channels = r.u16be()
                                r.u16be() // sample size
                                r.skip(4) // compression id + packet size
                                sampleRate = (r.u32be() ushr 16).toInt()
                            }
                        }
                    }

                    else -> {
                        if (path == "moov.udta.meta.ilst") {
                            val value = ilstValue(data, bodyStart, bodyEnd)
                            when (type) {
                                "©nam" -> title = value?.text
                                "©ART" -> artist = value?.text
                                "©alb" -> album = value?.text
                                "aART" -> albumArtist = value?.text
                                "©day" -> year = value?.text?.take(4)?.toIntOrNull()
                                "©lyr" -> lyrics = value?.text
                                "trkn" -> trackNo = value?.pairFirst
                                "disk" -> discNo = value?.pairFirst
                                "covr" -> if (artwork == null) artwork = value?.bytes
                            }
                        }
                    }
                }
                pos = bodyEnd
            }
        }

        walk(0, data.size, "")

        return TrackMetadata(
            format = AudioFormat.M4A,
            title = title, artist = artist, album = album, albumArtist = albumArtist,
            year = year, trackNo = trackNo, discNo = discNo,
            durationMs = durationMs, sampleRate = sampleRate, channels = channels,
            artwork = artwork, embeddedLyrics = lyrics,
        )
    }

    /** Найден ли moov в буфере (если нет — индексатору нужно дочитать файл целиком). */
    fun hasMoov(data: ByteArray): Boolean {
        var pos = 0
        while (pos + 8 <= data.size) {
            var size = ((data[pos].toInt() and 0xff).toLong() shl 24) or
                ((data[pos + 1].toInt() and 0xff).toLong() shl 16) or
                ((data[pos + 2].toInt() and 0xff).toLong() shl 8) or
                (data[pos + 3].toInt() and 0xff).toLong()
            val type = TextDecoder.latin1(data.copyOfRange(pos + 4, pos + 8))
            if (type == "moov") return pos + size <= data.size
            if (size == 1L) {
                if (pos + 16 > data.size) return false
                size = 0
                for (i in 8 until 16) size = (size shl 8) or (data[pos + i].toLong() and 0xff)
            }
            if (size < 8) return false
            pos += size.toInt()
        }
        return false
    }

    private class IlstValue(val text: String?, val bytes: ByteArray?, val pairFirst: Int?)

    /** ilst-атом содержит вложенный 'data': version/flags(4, тип во flags) + reserved(4) + payload. */
    private fun ilstValue(data: ByteArray, start: Int, end: Int): IlstValue? {
        var pos = start
        while (pos + 8 <= end) {
            val size = ((data[pos].toInt() and 0xff) shl 24) or
                ((data[pos + 1].toInt() and 0xff) shl 16) or
                ((data[pos + 2].toInt() and 0xff) shl 8) or (data[pos + 3].toInt() and 0xff)
            val type = TextDecoder.latin1(data.copyOfRange(pos + 4, pos + 8))
            if (size < 8 || pos + size > end) return null
            if (type == "data" && size >= 16) {
                val dataType = data[pos + 11].toInt() and 0xff
                val payload = data.copyOfRange(pos + 16, pos + size)
                return when (dataType) {
                    1 -> IlstValue(TextDecoder.utf8(payload), null, null)
                    13, 14 -> IlstValue(null, payload, null) // jpeg/png
                    0, 21 -> { // binary: trkn/disk = 2 нуля + номер u16be + total u16be
                        val first = if (payload.size >= 4) {
                            ((payload[2].toInt() and 0xff) shl 8) or (payload[3].toInt() and 0xff)
                        } else null
                        IlstValue(null, payload, first)
                    }
                    else -> IlstValue(null, payload, null)
                }
            }
            pos += size
        }
        return null
    }
}
