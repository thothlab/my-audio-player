package tech.thothlab.dombra.data.metadata

import tech.thothlab.dombra.domain.model.ReplayGain

/**
 * ID3v2.2/2.3/2.4 + ID3v1 fallback. Возвращает только то, что смогли прочитать;
 * битые фреймы пропускаются, парсер не бросает исключений (§5.3).
 */
internal object Id3Parser {

    data class Id3Data(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val year: Int? = null,
        val trackNo: Int? = null,
        val discNo: Int? = null,
        val durationMs: Long? = null,
        val replayGain: ReplayGain = ReplayGain(),
        val artwork: ByteArray? = null,
        val lyrics: String? = null,
        val tagSize: Int = 0,
    )

    fun hasId3v2(data: ByteArray): Boolean =
        data.size >= 10 && data[0] == 'I'.code.toByte() && data[1] == 'D'.code.toByte() &&
            data[2] == '3'.code.toByte()

    fun syncsafe(b0: Int, b1: Int, b2: Int, b3: Int): Int =
        ((b0 and 0x7f) shl 21) or ((b1 and 0x7f) shl 14) or ((b2 and 0x7f) shl 7) or (b3 and 0x7f)

    fun parseV2(data: ByteArray): Id3Data {
        if (!hasId3v2(data)) return Id3Data()
        val major = data[3].toInt() and 0xff
        val flags = data[5].toInt() and 0xff
        val tagSize = syncsafe(
            data[6].toInt() and 0xff, data[7].toInt() and 0xff,
            data[8].toInt() and 0xff, data[9].toInt() and 0xff,
        )
        val end = (10 + tagSize).coerceAtMost(data.size)

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var albumArtist: String? = null
        var year: Int? = null
        var trackNo: Int? = null
        var discNo: Int? = null
        var durationMs: Long? = null
        var trackGain: Double? = null
        var albumGain: Double? = null
        var trackPeak: Double? = null
        var albumPeak: Double? = null
        var artwork: ByteArray? = null
        var lyrics: String? = null

        var pos = 10
        // extended header (v2.3: size не syncsafe; v2.4: syncsafe)
        if (flags and 0x40 != 0 && pos + 4 <= end) {
            val extSize = if (major >= 4) {
                syncsafe(
                    data[pos].toInt() and 0xff, data[pos + 1].toInt() and 0xff,
                    data[pos + 2].toInt() and 0xff, data[pos + 3].toInt() and 0xff,
                )
            } else {
                (((data[pos].toInt() and 0xff) shl 24) or ((data[pos + 1].toInt() and 0xff) shl 16) or
                    ((data[pos + 2].toInt() and 0xff) shl 8) or (data[pos + 3].toInt() and 0xff)) + 4
            }
            pos += extSize
        }

        val idLen = if (major <= 2) 3 else 4
        val headerLen = if (major <= 2) 6 else 10

        while (pos + headerLen <= end) {
            val idBytes = data.copyOfRange(pos, pos + idLen)
            if (idBytes.all { it.toInt() == 0 }) break
            val frameId = idBytes.decodeToString()
            val frameSize = when {
                major <= 2 -> ((data[pos + 3].toInt() and 0xff) shl 16) or
                    ((data[pos + 4].toInt() and 0xff) shl 8) or (data[pos + 5].toInt() and 0xff)
                major == 3 -> ((data[pos + 4].toInt() and 0xff) shl 24) or
                    ((data[pos + 5].toInt() and 0xff) shl 16) or
                    ((data[pos + 6].toInt() and 0xff) shl 8) or (data[pos + 7].toInt() and 0xff)
                else -> syncsafe(
                    data[pos + 4].toInt() and 0xff, data[pos + 5].toInt() and 0xff,
                    data[pos + 6].toInt() and 0xff, data[pos + 7].toInt() and 0xff,
                )
            }
            if (frameSize <= 0 || pos + headerLen + frameSize > end) break
            val body = data.copyOfRange(pos + headerLen, pos + headerLen + frameSize)
            pos += headerLen + frameSize

            when (frameId) {
                "TIT2", "TT2" -> title = text(body)
                "TPE1", "TP1" -> artist = text(body)
                "TALB", "TAL" -> album = text(body)
                "TPE2", "TP2" -> albumArtist = text(body)
                "TYER", "TYE", "TDRC", "TDA" -> year = year ?: text(body)?.take(4)?.toIntOrNull()
                "TRCK", "TRK" -> trackNo = text(body)?.substringBefore('/')?.trim()?.toIntOrNull()
                "TPOS", "TPA" -> discNo = text(body)?.substringBefore('/')?.trim()?.toIntOrNull()
                "TLEN" -> durationMs = text(body)?.toLongOrNull()
                "TXXX", "TXX" -> {
                    val (desc, value) = txxx(body) ?: continue
                    when (desc.uppercase()) {
                        "REPLAYGAIN_TRACK_GAIN" -> trackGain = parseGainDb(value)
                        "REPLAYGAIN_ALBUM_GAIN" -> albumGain = parseGainDb(value)
                        "REPLAYGAIN_TRACK_PEAK" -> trackPeak = value.trim().toDoubleOrNull()
                        "REPLAYGAIN_ALBUM_PEAK" -> albumPeak = value.trim().toDoubleOrNull()
                    }
                }
                "APIC", "PIC" -> if (artwork == null) artwork = apic(body, major)
                "USLT", "ULT" -> if (lyrics == null) lyrics = uslt(body)
            }
        }

        return Id3Data(
            title = title, artist = artist, album = album, albumArtist = albumArtist,
            year = year, trackNo = trackNo, discNo = discNo, durationMs = durationMs,
            replayGain = ReplayGain(trackGain, albumGain, trackPeak, albumPeak),
            artwork = artwork, lyrics = lyrics, tagSize = 10 + tagSize,
        )
    }

    /** ID3v1: последние 128 байт файла, "TAG". */
    fun parseV1(tail: ByteArray): Id3Data {
        if (tail.size < 128) return Id3Data()
        val block = tail.copyOfRange(tail.size - 128, tail.size)
        if (block[0] != 'T'.code.toByte() || block[1] != 'A'.code.toByte() ||
            block[2] != 'G'.code.toByte()
        ) return Id3Data()

        fun field(from: Int, len: Int): String? {
            val raw = block.copyOfRange(from, from + len)
            val s = TextDecoder.trimNulls(TextDecoder.latin1(raw))
            return s.ifEmpty { null }
        }
        val trackNo = if (block[125].toInt() == 0 && block[126].toInt() != 0) {
            block[126].toInt() and 0xff
        } else null
        return Id3Data(
            title = field(3, 30),
            artist = field(33, 30),
            album = field(63, 30),
            year = field(93, 4)?.toIntOrNull(),
            trackNo = trackNo,
        )
    }

    private fun text(body: ByteArray): String? {
        if (body.isEmpty()) return null
        val enc = body[0].toInt() and 0xff
        val payload = body.copyOfRange(1, body.size)
        val s = decode(enc, payload)
        return TextDecoder.trimNulls(s).ifEmpty { null }
    }

    private fun decode(enc: Int, payload: ByteArray): String = when (enc) {
        0 -> TextDecoder.latin1(payload)
        1 -> TextDecoder.utf16(payload)
        2 -> TextDecoder.utf16(payload, defaultLittleEndian = false)
        3 -> TextDecoder.utf8(payload)
        else -> TextDecoder.latin1(payload)
    }

    /** TXXX: encoding(1) + description NUL value. */
    private fun txxx(body: ByteArray): Pair<String, String>? {
        if (body.size < 2) return null
        val enc = body[0].toInt() and 0xff
        val payload = body.copyOfRange(1, body.size)
        return when (enc) {
            0, 3 -> {
                val idx = payload.indexOfFirst { it.toInt() == 0 }
                if (idx < 0) return null
                val desc = decode(enc, payload.copyOfRange(0, idx))
                val value = decode(enc, payload.copyOfRange(idx + 1, payload.size))
                desc to TextDecoder.trimNulls(value)
            }
            1, 2 -> {
                var idx = -1
                var i = 0
                while (i + 1 < payload.size) {
                    if (payload[i].toInt() == 0 && payload[i + 1].toInt() == 0) { idx = i; break }
                    i += 2
                }
                if (idx < 0) return null
                val desc = decode(enc, payload.copyOfRange(0, idx))
                val value = decode(enc, payload.copyOfRange(idx + 2, payload.size))
                desc to TextDecoder.trimNulls(value)
            }
            else -> null
        }
    }

    /** APIC: encoding(1) + mime NUL + pictureType(1) + description NUL(+enc) + data. */
    private fun apic(body: ByteArray, major: Int): ByteArray? {
        if (body.size < 4) return null
        val enc = body[0].toInt() and 0xff
        var pos = 1
        if (major <= 2) {
            pos += 3 // формат изображения 3 байта ("JPG"/"PNG")
        } else {
            while (pos < body.size && body[pos].toInt() != 0) pos++
            pos++ // NUL
        }
        if (pos >= body.size) return null
        pos++ // picture type
        // description
        if (enc == 1 || enc == 2) {
            while (pos + 1 < body.size && !(body[pos].toInt() == 0 && body[pos + 1].toInt() == 0)) pos += 2
            pos += 2
        } else {
            while (pos < body.size && body[pos].toInt() != 0) pos++
            pos++
        }
        if (pos >= body.size) return null
        return body.copyOfRange(pos, body.size)
    }

    /** USLT: encoding(1) + language(3) + descriptor NUL(+enc) + text. */
    private fun uslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val enc = body[0].toInt() and 0xff
        var pos = 4
        if (enc == 1 || enc == 2) {
            while (pos + 1 < body.size && !(body[pos].toInt() == 0 && body[pos + 1].toInt() == 0)) pos += 2
            pos += 2
        } else {
            while (pos < body.size && body[pos].toInt() != 0) pos++
            pos++
        }
        if (pos >= body.size) return null
        val s = decode(enc, body.copyOfRange(pos, body.size))
        return TextDecoder.trimNulls(s).ifEmpty { null }
    }

    /** "-6.50 dB" → -6.5 */
    fun parseGainDb(value: String): Double? =
        Regex("[-+]?[0-9]*\\.?[0-9]+").find(value.trim())?.value?.toDoubleOrNull()
}
