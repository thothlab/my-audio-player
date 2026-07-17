package tech.thothlab.dombra.platform

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import tech.thothlab.dombra.core.DombraError
import tech.thothlab.dombra.core.DombraException
import tech.thothlab.dombra.core.Log
import tech.thothlab.dombra.domain.model.AudioFormat
import tech.thothlab.dombra.domain.ports.FileStat
import tech.thothlab.dombra.domain.ports.SourceRef
import tech.thothlab.dombra.domain.ports.StorageProvider

/**
 * StorageProvider поверх SAF (Storage Access Framework). `SourceRef.uri` —
 * строка content-URI документа в дереве, выданном `ACTION_OPEN_DOCUMENT_TREE`.
 * Такой URI одновременно читается через ContentResolver и играется media3.
 */
class ContentUriStorageProvider(
    context: Context,
    private val io: CoroutineDispatcher,
) : StorageProvider {

    private val log = Log.withTag("SafStorage")
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun stat(ref: SourceRef): FileStat? = withContext(io) {
        runCatching {
            resolver.query(
                Uri.parse(ref.uri),
                arrayOf(DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null, null, null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val sizeCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                // Размер нужен для контентного stableId — без него файл не индексируем
                // (лучше пропустить, чем испортить идентификатор нулевым размером).
                if (sizeCol < 0 || c.isNull(sizeCol)) return@use null
                val modCol = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val modTime = if (modCol >= 0 && !c.isNull(modCol)) c.getLong(modCol) else 0L
                FileStat(size = c.getLong(sizeCol), modificationTime = modTime)
            }
        }.getOrNull()
    }

    override suspend fun listAudioFiles(dir: SourceRef): List<SourceRef> = withContext(io) {
        val treeUri = Uri.parse(dir.uri)
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrElse { DocumentsContract.getDocumentId(treeUri) }
        val out = mutableListOf<SourceRef>()
        collectInto(treeUri, rootDocId, out)
        out
    }

    private fun collectInto(treeUri: Uri, parentDocId: String, out: MutableList<SourceRef>) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    collectInto(treeUri, docId, out)
                } else if (name.substringAfterLast('.', "").lowercase() in AudioFormat.supportedExtensions) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    out += SourceRef(uri = docUri.toString(), displayName = name)
                }
            }
        }
    }

    override suspend fun readBytes(ref: SourceRef, offset: Long, length: Long): ByteArray =
        withContext(io) {
            val stream = runCatching { resolver.openInputStream(Uri.parse(ref.uri)) }.getOrNull()
                ?: throw DombraException(DombraError.SourceUnavailable(ref.uri))
            stream.use { input ->
                var toSkip = offset
                while (toSkip > 0) {
                    val skipped = input.skip(toSkip)
                    if (skipped <= 0) break
                    toSkip -= skipped
                }
                if (length == Long.MAX_VALUE) {
                    input.readBytes()
                } else {
                    val buf = ByteArray(length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    var read = 0
                    while (read < buf.size) {
                        val n = input.read(buf, read, buf.size - read)
                        if (n < 0) break
                        read += n
                    }
                    if (read == buf.size) buf else buf.copyOf(read)
                }
            }
        }

    override suspend fun persistAccess(ref: SourceRef) = withContext(io) {
        runCatching {
            resolver.takePersistableUriPermission(Uri.parse(ref.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { log.w { "persist permission failed" } }
        Unit
    }

    override val canDeleteFiles: Boolean = true

    override suspend fun deleteFile(ref: SourceRef): Boolean = withContext(io) {
        runCatching { DocumentsContract.deleteDocument(resolver, Uri.parse(ref.uri)) }.getOrDefault(false)
    }
}
