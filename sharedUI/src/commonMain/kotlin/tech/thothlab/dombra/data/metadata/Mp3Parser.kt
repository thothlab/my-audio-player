package tech.thothlab.dombra.data.metadata

import tech.thothlab.dombra.domain.model.AudioFormat

/**
 * MP3: заголовок первого аудиофрейма (sample rate/channels) + длительность
 * из Xing/Info (VBR) либо CBR-оценки по битрейту и размеру файла.
 */
internal object Mp3Parser {

    private val BITRATES_V1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val BITRATES_V2_L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
    private val SAMPLE_RATES_V1 = intArrayOf(44100, 48000, 32000, 0)
    private val SAMPLE_RATES_V2 = intArrayOf(22050, 24000, 16000, 0)
    private val SAMPLE_RATES_V25 = intArrayOf(11025, 12000, 8000, 0)

    data class Mp3Info(
        val sampleRate: Int,
        val channels: Int,
        val bitrateKbps: Int,
        val durationMs: Long?,
    )

    /**
     * @param head начало файла (после возможного ID3v2 ищется синхро-слово фрейма)
     * @param id3Size размер ID3v2-тега
     * @param fileSize полный размер файла для CBR-оценки
     */
    fun parse(head: ByteArray, id3Size: Int, fileSize: Long): Mp3Info? {
        var pos = id3Size.coerceAtLeast(0)
        // ищем frame sync в пределах головы
        while (pos + 4 < head.size) {
            if ((head[pos].toInt() and 0xff) == 0xff && (head[pos + 1].toInt() and 0xe0) == 0xe0) {
                val info = tryFrame(head, pos, fileSize, id3Size)
                if (info != null) return info
            }
            pos++
        }
        return null
    }

    private fun tryFrame(data: ByteArray, offset: Int, fileSize: Long, id3Size: Int): Mp3Info? {
        val b1 = data[offset + 1].toInt() and 0xff
        val b2 = data[offset + 2].toInt() and 0xff
        val b3 = data[offset + 3].toInt() and 0xff

        val versionBits = (b1 ushr 3) and 0x03 // 0=2.5, 2=v2, 3=v1
        val layerBits = (b1 ushr 1) and 0x03   // 1=Layer III
        if (versionBits == 1 || layerBits != 1) return null

        val bitrateIdx = (b2 ushr 4) and 0x0f
        val sampleIdx = (b2 ushr 2) and 0x03
        if (bitrateIdx == 0 || bitrateIdx == 15 || sampleIdx == 3) return null

        val sampleRate = when (versionBits) {
            3 -> SAMPLE_RATES_V1[sampleIdx]
            2 -> SAMPLE_RATES_V2[sampleIdx]
            else -> SAMPLE_RATES_V25[sampleIdx]
        }
        val bitrate = if (versionBits == 3) BITRATES_V1_L3[bitrateIdx] else BITRATES_V2_L3[bitrateIdx]
        if (sampleRate == 0 || bitrate == 0) return null

        val channelMode = (b3 ushr 6) and 0x03
        val channels = if (channelMode == 3) 1 else 2
        val samplesPerFrame = if (versionBits == 3) 1152 else 576

        // Xing/Info заголовок: offset зависит от версии и режима каналов
        val sideInfo = when {
            versionBits == 3 && channels == 2 -> 32
            versionBits == 3 -> 17
            channels == 2 -> 17
            else -> 9
        }
        val xingPos = offset + 4 + sideInfo
        var durationMs: Long? = null
        if (xingPos + 16 < data.size) {
            val tag = data.copyOfRange(xingPos, xingPos + 4).decodeToString()
            if (tag == "Xing" || tag == "Info") {
                val flags = ((data[xingPos + 4].toInt() and 0xff) shl 24) or
                    ((data[xingPos + 5].toInt() and 0xff) shl 16) or
                    ((data[xingPos + 6].toInt() and 0xff) shl 8) or
                    (data[xingPos + 7].toInt() and 0xff)
                if (flags and 0x01 != 0) {
                    val frames = ((data[xingPos + 8].toInt() and 0xff) shl 24) or
                        ((data[xingPos + 9].toInt() and 0xff) shl 16) or
                        ((data[xingPos + 10].toInt() and 0xff) shl 8) or
                        (data[xingPos + 11].toInt() and 0xff)
                    durationMs = frames.toLong() * samplesPerFrame * 1000 / sampleRate
                }
            }
        }
        if (durationMs == null) {
            val audioBytes = fileSize - id3Size
            if (audioBytes > 0) durationMs = audioBytes * 8 / bitrate // kbps → ms
        }
        return Mp3Info(sampleRate, channels, bitrate, durationMs)
    }

    fun toMetadata(head: ByteArray, tail: ByteArray, fileSize: Long): TrackMetadata {
        val id3 = Id3Parser.parseV2(head)
        val v1 = if (id3.title == null && id3.artist == null) Id3Parser.parseV1(tail) else null
        val mp3 = parse(head, id3.tagSize, fileSize)
        return TrackMetadata(
            format = AudioFormat.MP3,
            title = id3.title ?: v1?.title,
            artist = id3.artist ?: v1?.artist,
            album = id3.album ?: v1?.album,
            albumArtist = id3.albumArtist,
            year = id3.year ?: v1?.year,
            trackNo = id3.trackNo ?: v1?.trackNo,
            discNo = id3.discNo,
            durationMs = id3.durationMs ?: mp3?.durationMs,
            sampleRate = mp3?.sampleRate,
            bitDepth = null,
            channels = mp3?.channels,
            replayGain = id3.replayGain,
            artwork = id3.artwork,
            embeddedLyrics = id3.lyrics,
        )
    }
}
