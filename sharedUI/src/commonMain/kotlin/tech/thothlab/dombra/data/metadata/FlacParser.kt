package tech.thothlab.dombra.data.metadata

import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.model.ReplayGain

/** FLAC: STREAMINFO + VORBIS_COMMENT + PICTURE (§5.3 ТЗ). */
internal object FlacParser {

    fun isFlac(data: ByteArray): Boolean =
        data.size >= 4 && data[0] == 'f'.code.toByte() && data[1] == 'L'.code.toByte() &&
            data[2] == 'a'.code.toByte() && data[3] == 'C'.code.toByte()

    fun parse(data: ByteArray): TrackMetadata {
        if (!isFlac(data)) return TrackMetadata(format = AudioFormat.FLAC)

        var sampleRate: Int? = null
        var channels: Int? = null
        var bitDepth: Int? = null
        var durationMs: Long? = null
        var comments: Map<String, String> = emptyMap()
        var artwork: ByteArray? = null

        var pos = 4
        var last = false
        while (!last && pos + 4 <= data.size) {
            val header = data[pos].toInt() and 0xff
            last = header and 0x80 != 0
            val type = header and 0x7f
            val size = ((data[pos + 1].toInt() and 0xff) shl 16) or
                ((data[pos + 2].toInt() and 0xff) shl 8) or (data[pos + 3].toInt() and 0xff)
            val bodyStart = pos + 4
            if (bodyStart + size > data.size) break
            when (type) {
                0 -> { // STREAMINFO
                    if (size >= 34) {
                        val r = ByteReader(data, bodyStart + 10)
                        val b0 = r.u8(); val b1 = r.u8(); val b2 = r.u8()
                        sampleRate = (b0 shl 12) or (b1 shl 4) or (b2 ushr 4)
                        channels = ((b2 ushr 1) and 0x07) + 1
                        val b3 = r.u8()
                        bitDepth = (((b2 and 0x01) shl 4) or (b3 ushr 4)) + 1
                        var totalSamples = (b3 and 0x0f).toLong()
                        repeat(4) { totalSamples = (totalSamples shl 8) or r.u8().toLong() }
                        if (totalSamples > 0 && sampleRate > 0) {
                            durationMs = totalSamples * 1000 / sampleRate
                        }
                    }
                }
                4 -> comments = VorbisComments.parse(data, bodyStart, size)
                6 -> { // PICTURE
                    if (artwork == null) artwork = picture(data, bodyStart, size)
                }
            }
            pos = bodyStart + size
        }

        return TrackMetadata(
            format = AudioFormat.FLAC,
            title = comments["TITLE"],
            artist = comments["ARTIST"],
            album = comments["ALBUM"],
            albumArtist = comments["ALBUMARTIST"],
            year = comments["DATE"]?.take(4)?.toIntOrNull() ?: comments["YEAR"]?.toIntOrNull(),
            trackNo = comments["TRACKNUMBER"]?.substringBefore('/')?.trim()?.toIntOrNull(),
            discNo = comments["DISCNUMBER"]?.substringBefore('/')?.trim()?.toIntOrNull(),
            durationMs = durationMs,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            replayGain = VorbisComments.replayGain(comments),
            artwork = artwork,
            embeddedLyrics = comments["LYRICS"] ?: comments["UNSYNCEDLYRICS"],
        )
    }

    private fun picture(data: ByteArray, start: Int, size: Int): ByteArray? {
        val r = ByteReader(data, start)
        if (!r.canRead(32)) return null
        r.u32be() // picture type
        val mimeLen = r.u32be().toInt()
        r.skip(mimeLen)
        val descLen = r.u32be().toInt()
        r.skip(descLen)
        r.skip(16) // width, height, depth, colors
        val dataLen = r.u32be().toInt()
        if (dataLen <= 0 || r.pos + dataLen > start + size || !r.canRead(dataLen)) return null
        return r.bytes(dataLen)
    }
}

/** Vorbis comments — общий формат FLAC/OGG/Opus. */
internal object VorbisComments {

    /** @return ключи в UPPERCASE. */
    fun parse(data: ByteArray, start: Int, size: Int): Map<String, String> {
        val r = ByteReader(data, start)
        val end = start + size
        val out = mutableMapOf<String, String>()
        if (!r.canRead(4)) return out
        val vendorLen = r.u32le().toInt()
        if (vendorLen < 0 || r.pos + vendorLen > end) return out
        r.skip(vendorLen)
        if (!r.canRead(4)) return out
        val count = r.u32le().toInt()
        repeat(count.coerceAtMost(1024)) {
            if (!r.canRead(4)) return out
            val len = r.u32le().toInt()
            if (len < 0 || r.pos + len > end) return out
            val s = TextDecoder.utf8(r.bytes(len))
            val eq = s.indexOf('=')
            if (eq > 0) {
                val key = s.substring(0, eq).uppercase()
                if (key !in out) out[key] = s.substring(eq + 1)
            }
        }
        return out
    }

    fun replayGain(comments: Map<String, String>): ReplayGain = ReplayGain(
        trackGainDb = comments["REPLAYGAIN_TRACK_GAIN"]?.let(Id3Parser::parseGainDb),
        albumGainDb = comments["REPLAYGAIN_ALBUM_GAIN"]?.let(Id3Parser::parseGainDb),
        trackPeak = comments["REPLAYGAIN_TRACK_PEAK"]?.trim()?.toDoubleOrNull(),
        albumPeak = comments["REPLAYGAIN_ALBUM_PEAK"]?.trim()?.toDoubleOrNull(),
    )
}
