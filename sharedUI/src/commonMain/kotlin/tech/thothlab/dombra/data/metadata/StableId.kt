package tech.thothlab.dombra.data.metadata

import tech.thothlab.dombra.core.Sha256

/**
 * Стабильный идентификатор трека (§6.2 ТЗ, решение D-02).
 *
 * Формат: `sha256( "<fileSize>:" + head(64KiB) + tail(64KiB) )`, hex-строка (64 символа).
 * - Детерминирован для одного физического файла на любой платформе.
 * - Переживает переименование/перемещение (в отличие от path-hash в Cosmos).
 * - Для файла меньше 128 KiB head/tail перекрываются — это допустимо,
 *   вычисление остаётся детерминированным.
 */
object StableId {
    const val CHUNK = 64 * 1024

    fun compute(fileSize: Long, head: ByteArray, tail: ByteArray): String {
        val prefix = "$fileSize:".encodeToByteArray()
        return Sha256.hashHex(prefix + head + tail)
    }
}
