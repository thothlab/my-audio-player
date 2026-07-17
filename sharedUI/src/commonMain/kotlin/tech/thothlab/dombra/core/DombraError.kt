package tech.thothlab.dombra.core

/**
 * Типизированные ошибки домена (§7.2 ТЗ). Общие интерфейсы возвращают их,
 * а не платформенные исключения.
 */
sealed interface DombraError {
    val message: String

    /** Файл/источник недоступен: удалён, отозван доступ, вне sandbox. */
    data class SourceUnavailable(val uri: String, override val message: String = "Источник недоступен") : DombraError

    /** Контент не распарсился (битый файл, неожиданный формат). */
    data class ParseFailed(val uri: String?, override val message: String) : DombraError

    /** Формат обнаружен, но платформа/вывод его не поддерживает. */
    data class FormatUnsupported(val format: String, val platform: String, override val message: String = "Формат $format не поддерживается на $platform") : DombraError

    /** Ошибка декодирования/воспроизведения. */
    data class PlaybackFailed(override val message: String, val retryable: Boolean = true) : DombraError

    /** Отказ платформы в доступе. */
    data class PermissionDenied(override val message: String = "Нет доступа") : DombraError

    /** Ошибка локального хранилища/базы. */
    data class StorageFailed(override val message: String) : DombraError

    /** Сетевая ошибка внешнего API. */
    data class NetworkFailed(override val message: String, val retryable: Boolean = true) : DombraError

    /** Провайдер не сконфигурирован (нет API-ключей). */
    data class NotConfigured(val provider: String, override val message: String = "Источник $provider не настроен") : DombraError

    /** Операция отменена пользователем. */
    data object Cancelled : DombraError {
        override val message: String = "Отменено"
    }

    data class Unknown(override val message: String) : DombraError
}

class DombraException(val error: DombraError, cause: Throwable? = null) :
    Exception(error.message, cause)

/** Результат операции с типизированной ошибкой. */
sealed interface DombraResult<out T> {
    data class Ok<T>(val value: T) : DombraResult<T>
    data class Err(val error: DombraError) : DombraResult<Nothing>
}

inline fun <T, R> DombraResult<T>.map(f: (T) -> R): DombraResult<R> = when (this) {
    is DombraResult.Ok -> DombraResult.Ok(f(value))
    is DombraResult.Err -> this
}

fun <T> DombraResult<T>.getOrNull(): T? = (this as? DombraResult.Ok)?.value

fun <T> DombraResult<T>.errorOrNull(): DombraError? = (this as? DombraResult.Err)?.error
