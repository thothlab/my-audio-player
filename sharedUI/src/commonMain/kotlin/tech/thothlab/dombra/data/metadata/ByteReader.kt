package tech.thothlab.dombra.data.metadata

/** Курсор по байтовому массиву с big/little-endian чтением и декодированием строк. */
internal class ByteReader(val data: ByteArray, var pos: Int = 0) {

    val remaining: Int get() = data.size - pos

    fun canRead(n: Int): Boolean = pos + n <= data.size

    fun u8(): Int = data[pos++].toInt() and 0xff

    fun u16be(): Int = (u8() shl 8) or u8()

    fun u16le(): Int = u8() or (u8() shl 8)

    fun u24be(): Int = (u8() shl 16) or (u8() shl 8) or u8()

    fun u32be(): Long = ((u8().toLong()) shl 24) or ((u8().toLong()) shl 16) or
        ((u8().toLong()) shl 8) or u8().toLong()

    fun u32le(): Long = u8().toLong() or ((u8().toLong()) shl 8) or
        ((u8().toLong()) shl 16) or ((u8().toLong()) shl 24)

    fun u64be(): Long {
        var v = 0L
        repeat(8) { v = (v shl 8) or u8().toLong() }
        return v
    }

    fun u64le(): Long {
        var v = 0L
        for (i in 0 until 8) v = v or (u8().toLong() shl (8 * i))
        return v
    }

    fun bytes(n: Int): ByteArray {
        val end = (pos + n).coerceAtMost(data.size)
        val out = data.copyOfRange(pos, end)
        pos = end
        return out
    }

    fun skip(n: Int) {
        pos = (pos + n).coerceAtMost(data.size)
    }

    fun ascii(n: Int): String = bytes(n).decodeToString()

    fun peekAscii(n: Int): String {
        val end = (pos + n).coerceAtMost(data.size)
        return data.copyOfRange(pos, end).decodeToString()
    }
}

internal object TextDecoder {
    /** ISO-8859-1 → String. */
    fun latin1(bytes: ByteArray): String =
        buildString(bytes.size) { bytes.forEach { append((it.toInt() and 0xff).toChar()) } }

    fun utf8(bytes: ByteArray): String = bytes.decodeToString()

    /** UTF-16 c BOM (или без — по bomLittleEndian). */
    fun utf16(bytes: ByteArray, defaultLittleEndian: Boolean = true): String {
        var offset = 0
        var littleEndian = defaultLittleEndian
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xff
            val b1 = bytes[1].toInt() and 0xff
            if (b0 == 0xFF && b1 == 0xFE) { littleEndian = true; offset = 2 }
            else if (b0 == 0xFE && b1 == 0xFF) { littleEndian = false; offset = 2 }
        }
        val sb = StringBuilder()
        var i = offset
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xff
            val hi = bytes[i + 1].toInt() and 0xff
            val code = if (littleEndian) (hi shl 8) or lo else (lo shl 8) or hi
            if (code == 0) break
            sb.append(code.toChar())
            i += 2
        }
        return sb.toString()
    }

    fun trimNulls(s: String): String = s.trim { it == '\u0000' || it.isWhitespace() }
}
