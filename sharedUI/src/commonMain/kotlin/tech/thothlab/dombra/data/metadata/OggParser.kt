package tech.thothlab.dombra.data.metadata

import tech.thothlab.dombra.domain.model.AudioFormat

/**
 * OGG-контейнер: Vorbis и Opus. Метаданные — из первых страниц (id header +
 * comment header), длительность — по granule position последней страницы в хвосте.
 */
internal object OggParser {

    fun isOgg(data: ByteArray): Boolean =
        data.size >= 4 && data.copyOfRange(0, 4).decodeToString() == "OggS"

    fun parse(head: ByteArray, tail: ByteArray): TrackMetadata {
        if (!isOgg(head)) return TrackMetadata(format = AudioFormat.OGG_VORBIS)

        // Склеиваем полезную нагрузку первых страниц (id + comment headers обычно в первых 2-3)
        val payload = ArrayList<Byte>(head.size)
        var pos = 0
        var pages = 0
        while (pos + 27 <= head.size && pages < 4) {
            if (head.copyOfRange(pos, pos + 4).decodeToString() != "OggS") break
            val segCount = head[pos + 26].toInt() and 0xff
            if (pos + 27 + segCount > head.size) break
            var bodyLen = 0
            for (i in 0 until segCount) bodyLen += head[pos + 27 + i].toInt() and 0xff
            val bodyStart = pos + 27 + segCount
            val bodyEnd = (bodyStart + bodyLen).coerceAtMost(head.size)
            for (i in bodyStart until bodyEnd) payload.add(head[i])
            pos = bodyStart + bodyLen
            pages++
        }
        val body = payload.toByteArray()

        var isOpus = false
        var sampleRate: Int? = null
        var channels: Int? = null
        var preSkip = 0
        var comments: Map<String, String> = emptyMap()

        val opusHead = body.indexOfSequence("OpusHead".encodeToByteArray())
        val vorbisId = body.indexOfSequence(byteArrayOf(0x01) + "vorbis".encodeToByteArray())
        when {
            opusHead >= 0 -> {
                isOpus = true
                val r = ByteReader(body, opusHead + 8)
                if (r.canRead(8)) {
                    r.u8() // version
                    channels = r.u8()
                    preSkip = r.u16le()
                    r.u32le() // input sample rate (справочно)
                    sampleRate = 48000 // Opus всегда декодируется в 48 кГц
                }
                val tags = body.indexOfSequence("OpusTags".encodeToByteArray())
                if (tags >= 0) comments = VorbisComments.parse(body, tags + 8, body.size - tags - 8)
            }
            vorbisId >= 0 -> {
                val r = ByteReader(body, vorbisId + 7)
                if (r.canRead(9)) {
                    r.u32le() // version
                    channels = r.u8()
                    sampleRate = r.u32le().toInt()
                }
                val cm = body.indexOfSequence(byteArrayOf(0x03) + "vorbis".encodeToByteArray())
                if (cm >= 0) comments = VorbisComments.parse(body, cm + 7, body.size - cm - 7)
            }
        }

        // Длительность: последняя страница в хвосте → granule position
        var durationMs: Long? = null
        val lastPage = tail.lastIndexOfSequence("OggS".encodeToByteArray())
        if (lastPage >= 0 && lastPage + 14 <= tail.size) {
            val r = ByteReader(tail, lastPage + 6)
            val granule = r.u64le()
            if (granule > 0 && sampleRate != null && sampleRate > 0) {
                val samples = if (isOpus) (granule - preSkip).coerceAtLeast(0) else granule
                durationMs = samples * 1000 / (if (isOpus) 48000 else sampleRate)
            }
        }

        return TrackMetadata(
            format = if (isOpus) AudioFormat.OPUS else AudioFormat.OGG_VORBIS,
            title = comments["TITLE"],
            artist = comments["ARTIST"],
            album = comments["ALBUM"],
            albumArtist = comments["ALBUMARTIST"],
            year = comments["DATE"]?.take(4)?.toIntOrNull(),
            trackNo = comments["TRACKNUMBER"]?.substringBefore('/')?.trim()?.toIntOrNull(),
            discNo = comments["DISCNUMBER"]?.substringBefore('/')?.trim()?.toIntOrNull(),
            durationMs = durationMs,
            sampleRate = sampleRate,
            channels = channels,
            replayGain = VorbisComments.replayGain(comments),
            embeddedLyrics = comments["LYRICS"] ?: comments["UNSYNCEDLYRICS"],
        )
    }
}

internal fun ByteArray.indexOfSequence(needle: ByteArray, from: Int = 0): Int {
    if (needle.isEmpty() || size < needle.size) return -1
    outer@ for (i in from..size - needle.size) {
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}

internal fun ByteArray.lastIndexOfSequence(needle: ByteArray): Int {
    if (needle.isEmpty() || size < needle.size) return -1
    outer@ for (i in size - needle.size downTo 0) {
        for (j in needle.indices) {
            if (this[i + j] != needle[j]) continue@outer
        }
        return i
    }
    return -1
}
