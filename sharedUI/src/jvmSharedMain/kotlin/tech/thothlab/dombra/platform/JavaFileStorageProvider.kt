package tech.thothlab.dombra.platform

import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.withContext
import tech.thothlab.dombra.core.DispatcherProvider
import tech.thothlab.dombra.core.DombraError
import tech.thothlab.dombra.core.DombraException
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.ports.FileStat
import tech.thothlab.dombra.domain.ports.SourceRef
import tech.thothlab.dombra.domain.ports.StorageProvider

/**
 * Файловый StorageProvider для Desktop JVM и локальных file://-путей Android.
 * URI-формат: абсолютный путь файловой системы.
 */
class JavaFileStorageProvider(
    private val dispatchers: DispatcherProvider,
) : StorageProvider {

    override suspend fun stat(ref: SourceRef): FileStat? = withContext(dispatchers.io) {
        val f = File(ref.uri)
        if (!f.exists() || !f.canRead()) null
        else FileStat(size = f.length(), modificationTime = f.lastModified())
    }

    override suspend fun listAudioFiles(dir: SourceRef): List<SourceRef> =
        withContext(dispatchers.io) {
            val root = File(dir.uri)
            if (!root.isDirectory) return@withContext emptyList()
            root.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in AudioFormat.supportedExtensions }
                .map { SourceRef(uri = it.absolutePath, displayName = it.name) }
                .toList()
        }

    override suspend fun readBytes(ref: SourceRef, offset: Long, length: Long): ByteArray =
        withContext(dispatchers.io) {
            val f = File(ref.uri)
            if (!f.exists() || !f.canRead()) {
                throw DombraException(DombraError.SourceUnavailable(ref.uri))
            }
            try {
                RandomAccessFile(f, "r").use { raf ->
                    val size = raf.length()
                    val start = offset.coerceIn(0, size)
                    val len = length.coerceAtMost(size - start).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val out = ByteArray(len)
                    raf.seek(start)
                    raf.readFully(out)
                    out
                }
            } catch (e: java.io.IOException) {
                throw DombraException(DombraError.SourceUnavailable(ref.uri, e.message ?: "IO error"), e)
            }
        }

    override suspend fun persistAccess(ref: SourceRef) {
        // Файловая система Desktop: доступ и так постоянный.
    }

    override val canDeleteFiles: Boolean = true

    override suspend fun deleteFile(ref: SourceRef): Boolean = withContext(dispatchers.io) {
        File(ref.uri).delete()
    }
}
