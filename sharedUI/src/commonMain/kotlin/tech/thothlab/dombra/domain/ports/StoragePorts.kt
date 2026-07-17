package tech.thothlab.dombra.domain.ports

/**
 * Контракты доступа к файлам (§7.2 ТЗ). Возвращают доменные типы,
 * платформенные URI/исключения наружу не выходят.
 */

/** Стабильная ссылка на файл или каталог у конкретного провайдера. */
data class SourceRef(
    val uri: String,
    val displayName: String,
    val isDirectory: Boolean = false,
)

data class FileStat(
    val size: Long,
    val modificationTime: Long,
)

interface FilePicker {
    val canPickDirectory: Boolean

    /** Открыть системный выбор файлов. Пустой список = пользователь отменил. */
    suspend fun pickFiles(extensions: Set<String>): List<SourceRef>

    /** Открыть выбор каталога; null = отмена или нет поддержки. */
    suspend fun pickDirectory(): SourceRef?
}

interface StorageProvider {
    /** null — файл недоступен (удалён/отозван доступ). Не бросает. */
    suspend fun stat(ref: SourceRef): FileStat?

    /** Рекурсивный обход каталога; только файлы с поддерживаемыми расширениями. */
    suspend fun listAudioFiles(dir: SourceRef): List<SourceRef>

    /**
     * Чтение диапазона байт. range=null — весь файл.
     * Бросает DombraException(SourceUnavailable) при потере доступа.
     */
    suspend fun readBytes(ref: SourceRef, offset: Long = 0L, length: Long = Long.MAX_VALUE): ByteArray

    /** Закрепить долговременный доступ (SAF persistable permission, bookmark). */
    suspend fun persistAccess(ref: SourceRef)

    /** Физическое удаление файла, если провайдер разрешает. */
    val canDeleteFiles: Boolean
    suspend fun deleteFile(ref: SourceRef): Boolean
}
