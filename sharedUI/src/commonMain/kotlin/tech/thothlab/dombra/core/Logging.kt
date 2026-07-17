package tech.thothlab.dombra.core

import co.touchlab.kermit.Logger

/**
 * Logging policy (§8.3 ТЗ): логи не содержат токены, полные security-scoped URL,
 * содержимое lyrics и пользовательские пути без необходимости.
 */
object Log {
    fun withTag(tag: String): Logger = Logger.withTag(tag)

    /** Маскирует секрет: первые 4 символа + длина. */
    fun redactSecret(value: String?): String =
        if (value.isNullOrEmpty()) "<empty>" else "${value.take(4)}…(${value.length})"

    /** Сокращает пользовательский путь/URI до имени файла. */
    fun redactUri(uri: String?): String =
        uri?.substringAfterLast('/')?.takeLast(48) ?: "<null>"
}
