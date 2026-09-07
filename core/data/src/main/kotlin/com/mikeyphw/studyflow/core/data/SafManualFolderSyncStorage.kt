package com.mikeyphw.studyflow.core.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

internal data class ManualFolderSyncDocument(
    val uri: String,
    val fileName: String,
    val byteSize: Long,
    val lastModifiedAtEpochMillis: Long?,
)

/** SAF tree storage used by the manual-only local-folder sync lane. */
internal class SafManualFolderSyncStorage(
    context: Context,
) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    fun listCheckpointDocuments(folderUri: String): List<ManualFolderSyncDocument> {
        val treeUri = requireTreeUri(folderUri)
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val documents = mutableListOf<ManualFolderSyncDocument>()
        requireNotNull(resolver.query(childrenUri, projection, null, null, null)) {
            "The selected sync folder cannot be enumerated."
        }.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(mimeIndex)
                val fileName = cursor.getString(nameIndex).orEmpty()
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR ||
                    !fileName.endsWith(SyncBundleExtension, ignoreCase = true)
                ) {
                    continue
                }
                val documentId = cursor.getString(idIndex)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                documents += ManualFolderSyncDocument(
                    uri = documentUri.toString(),
                    fileName = fileName,
                    byteSize = cursor.longOrZero(sizeIndex),
                    lastModifiedAtEpochMillis = cursor.longOrNull(modifiedIndex),
                )
            }
        }
        return documents.sortedWith(
            compareByDescending<ManualFolderSyncDocument> { it.lastModifiedAtEpochMillis ?: Long.MIN_VALUE }
                .thenByDescending { it.fileName },
        )
    }

    fun writeCheckpoint(
        folderUri: String,
        fileName: String,
        write: (OutputStream) -> Unit,
    ): ManualFolderSyncDocument {
        require(fileName.endsWith(SyncBundleExtension)) { "Manual sync file must use the .sfbackup extension." }
        require('/' !in fileName && '\\' !in fileName) { "Manual sync file name must be a single path segment." }
        val treeUri = requireTreeUri(folderUri)
        val rootDocumentUri = rootDocumentUri(treeUri)
        val temporaryName = "$fileName.${UUID.randomUUID()}.partial"
        val temporaryUri = requireNotNull(
            DocumentsContract.createDocument(resolver, rootDocumentUri, SyncMimeType, temporaryName),
        ) { "The selected sync folder rejected checkpoint creation." }
        try {
            requireNotNull(resolver.openOutputStream(temporaryUri, "w")) {
                "The selected sync checkpoint cannot be opened for writing."
            }.use { output -> write(output) }
            val committedUri = commitTemporaryDocument(rootDocumentUri, temporaryUri, fileName)
            return inspectDocument(committedUri)
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, temporaryUri) }
            throw error
        }
    }

    fun releasePersistedPermission(folderUri: String) {
        val uri = requireTreeUri(folderUri)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.releasePersistableUriPermission(uri, flags) }
    }

    fun openCheckpoint(documentUri: String): InputStream {
        val uri = Uri.parse(documentUri)
        require(uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
            "Manual sync checkpoint must use a content URI."
        }
        return requireNotNull(resolver.openInputStream(uri)) {
            "The selected sync checkpoint cannot be opened."
        }
    }

    private fun commitTemporaryDocument(rootDocumentUri: Uri, temporaryUri: Uri, fileName: String): Uri {
        val renamed = runCatching {
            DocumentsContract.renameDocument(resolver, temporaryUri, fileName)
        }.getOrNull()
        if (renamed != null) return renamed

        val finalUri = requireNotNull(
            DocumentsContract.createDocument(resolver, rootDocumentUri, SyncMimeType, fileName),
        ) { "The selected sync folder cannot commit the checkpoint file." }
        try {
            requireNotNull(resolver.openInputStream(temporaryUri)).use { input ->
                requireNotNull(resolver.openOutputStream(finalUri, "w")).use { output ->
                    input.copyTo(output)
                }
            }
            require(DocumentsContract.deleteDocument(resolver, temporaryUri)) {
                "The temporary sync checkpoint could not be removed after commit."
            }
            return finalUri
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, finalUri) }
            throw error
        }
    }

    private fun inspectDocument(documentUri: Uri): ManualFolderSyncDocument {
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return requireNotNull(resolver.query(documentUri, projection, null, null, null)) {
            "The committed sync checkpoint cannot be inspected."
        }.use { cursor ->
            require(cursor.moveToFirst()) { "The committed sync checkpoint disappeared." }
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            ManualFolderSyncDocument(
                uri = documentUri.toString(),
                fileName = cursor.getString(nameIndex),
                byteSize = cursor.longOrZero(sizeIndex),
                lastModifiedAtEpochMillis = cursor.longOrNull(modifiedIndex),
            )
        }
    }


    private fun rootDocumentUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )

    private fun requireTreeUri(value: String): Uri {
        val uri = Uri.parse(value)
        require(uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
            "Manual sync folder must use a content URI."
        }
        require(isTreeUriCompat(uri)) { "Manual sync folder must be a SAF tree URI." }
        return uri
    }

    private fun isTreeUriCompat(uri: Uri): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DocumentsContract.isTreeUri(uri)
        } else {
            runCatching { DocumentsContract.getTreeDocumentId(uri) }.isSuccess
        }

    private fun android.database.Cursor.longOrZero(index: Int): Long =
        if (isNull(index)) 0L else getLong(index).coerceAtLeast(0L)

    private fun android.database.Cursor.longOrNull(index: Int): Long? =
        if (isNull(index)) null else getLong(index).takeIf { it >= 0L }

    private companion object {
        const val SyncBundleExtension = ".sfbackup"
        const val SyncMimeType = "application/vnd.studyflow.sync+encrypted"
    }
}
