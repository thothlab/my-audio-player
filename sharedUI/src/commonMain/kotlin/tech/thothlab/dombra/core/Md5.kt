package tech.thothlab.dombra.core

/**
 * Чистый Kotlin MD5 (RFC 1321) — нужен для Subsonic token-аутентификации
 * (`t = md5(password + salt)`). Не для безопасности, только для протокола Subsonic.
 */
object Md5 {
    private val S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )
    fun hex(input: String): String = hex(input.encodeToByteArray())

    fun hex(message: ByteArray): String {
        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476

        val origLenBits = message.size.toLong() * 8
        // padding: 0x80, затем нули до длины ≡ 56 (mod 64), затем 8 байт длины (LE).
        val padLen = ((56 - (message.size + 1) % 64) + 64) % 64
        val padded = ByteArray(message.size + 1 + padLen + 8)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 8 + i] = (origLenBits ushr (8 * i)).toByte()

        var chunk = 0
        while (chunk < padded.size) {
            val m = IntArray(16) { j ->
                val o = chunk + j * 4
                (padded[o].toInt() and 0xff) or
                    ((padded[o + 1].toInt() and 0xff) shl 8) or
                    ((padded[o + 2].toInt() and 0xff) shl 16) or
                    ((padded[o + 3].toInt() and 0xff) shl 24)
            }
            var a = a0; var b = b0; var c = c0; var d = d0
            for (i in 0 until 64) {
                var f: Int
                val g: Int
                when {
                    i < 16 -> { f = (b and c) or (b.inv() and d); g = i }
                    i < 32 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    i < 48 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                f += a + KK[i] + m[g]
                a = d; d = c; c = b
                b += f.rotateLeft(S[i])
            }
            a0 += a; b0 += b; c0 += c; d0 += d
            chunk += 64
        }
        return listOf(a0, b0, c0, d0).joinToString("") { word ->
            (0 until 4).joinToString("") { byteIndex ->
                ((word ushr (8 * byteIndex)) and 0xff).toString(16).padStart(2, '0')
            }
        }
    }

    // K[i] = floor(2^32 * abs(sin(i+1))) — константы RFC 1321.
    private val KK = intArrayOf(
        -0x28955b88, -0x173848aa, 0x242070db, -0x3e423112,
        -0xa83f051, 0x4787c62a, -0x57cfb9ed, -0x2b96aff,
        0x698098d8, -0x74bb0851, -0xa44f, -0x76a32842,
        0x6b901122, -0x2678e6d, -0x5986bc72, 0x49b40821,
        -0x9e1da9e, -0x3fbf4cc0, 0x265e5a51, -0x16493856,
        -0x29d0efa3, 0x2441453, -0x275e197f, -0x182c0438,
        0x21e1cde6, -0x3cc8f82a, -0xb2af279, 0x455a14ed,
        -0x561c16fb, -0x3105c08, 0x676f02d9, -0x72d5b376,
        -0x5c6be, -0x788e097f, 0x6d9d6122, -0x21ac7f4,
        -0x5b4115bc, 0x4bdecfa9, -0x944b4a0, -0x41404390,
        0x289b7ec6, -0x155ed806, -0x2b10cf7b, 0x4881d05,
        -0x262b2fc7, -0x1924661b, 0x1fa27cf8, -0x3b53a99b,
        -0xbd6ddbc, 0x432aff97, -0x546bdc59, -0x36c5fc7,
        0x655b59c3, -0x70f3336e, -0x100b83, -0x7a7ba22f,
        0x6fa87e4f, -0x1d31920, -0x5cfebcec, 0x4e0811a1,
        -0x8ac817e, -0x42c50dcb, 0x2ad7d2bb, -0x14792c6f,
    )
}
