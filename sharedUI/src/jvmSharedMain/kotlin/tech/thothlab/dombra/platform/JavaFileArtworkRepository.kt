package tech.thothlab.dombra.platform

import java.io.File
import kotlinx.coroutines.withContext
import tech.thothlab.dombra.core.DispatcherProvider
import tech.thothlab.dombra.core.Sha256
import tech.thothlab.dombra.domain.ports.ArtworkRepository

/**
 * Файловый кэш artwork (§6.2 ТЗ: не BLOB в базе). Имя файла — hash ключа,
 * чтобы не зависеть от спецсимволов в stableId/URI.
 */
class JavaFileArtworkRepository(
    cacheDir: File,
    private val dispatchers: DispatcherProvider,
) : ArtworkRepository {

    private val dir = File(cacheDir, "artwork").apply { mkdirs() }

    private fun fileFor(key: String) = File(dir, Sha256.hashHex(key.encodeToByteArray()).take(32))

    override suspend fun store(key: String, bytes: ByteArray) = withContext(dispatchers.io) {
        fileFor(key).writeBytes(bytes)
    }

    override suspend fun load(key: String): ByteArray? = withContext(dispatchers.io) {
        val f = fileFor(key)
        if (f.exists()) f.readBytes() else null
    }

    override suspend fun contains(key: String): Boolean = withContext(dispatchers.io) {
        fileFor(key).exists()
    }

    override suspend fun clearAll(): Unit = withContext(dispatchers.io) {
        dir.listFiles()?.forEach { it.delete() }
    }
}
