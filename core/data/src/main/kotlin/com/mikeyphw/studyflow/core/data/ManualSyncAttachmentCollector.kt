package com.mikeyphw.studyflow.core.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.model.AnnexStorageMode
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncAttachment
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

internal data class ManualSyncAttachmentSource(
    val manifest: StudyFlowSyncAttachment,
    val openStream: () -> InputStream,
)

internal class ManualSyncAttachmentCollector(
    context: Context,
    private val database: StudyFlowDatabase,
) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val filesDir = appContext.filesDir.canonicalFile

    suspend fun collect(): List<ManualSyncAttachmentSource> {
        val sources = mutableListOf<ManualSyncAttachmentSource>()
        database.studyPlanDao().snapshotAll().forEach { plan ->
            database.studyLibraryDao().findAnnexes(plan.id).forEach { annex ->
                val open = when (enumValueOf<AnnexStorageMode>(annex.storageMode)) {
                    AnnexStorageMode.IMPORTED_COPY -> importedCopyStream(annex.storageReference)
                    AnnexStorageMode.LINKED_DOCUMENT -> linkedDocumentStream(annex.storageReference)
                }
                require(annex.byteSize <= MaximumAttachmentBytes) {
                    "Annex ${annex.displayName} exceeds the 64 MiB sync attachment limit."
                }
                val inspected = inspect(open)
                require(inspected.byteSize == annex.byteSize) {
                    "Annex ${annex.displayName} changed size since it was added to StudyFlow."
                }
                annex.checksumSha256?.takeIf(String::isNotBlank)?.let { expected ->
                    require(inspected.checksumSha256.equals(expected, ignoreCase = true)) {
                        "Annex ${annex.displayName} changed since it was added to StudyFlow."
                    }
                }
                sources += ManualSyncAttachmentSource(
                    manifest = StudyFlowSyncAttachment(
                        entryName = attachmentEntryName(plan.id, annex.id),
                        byteSize = inspected.byteSize,
                        checksumSha256 = inspected.checksumSha256,
                    ),
                    openStream = open,
                )
            }
        }
        return sources.sortedBy { source -> source.manifest.entryName }
    }

    private fun importedCopyStream(storageReference: String): () -> InputStream {
        require(storageReference.isNotBlank()) { "Imported annex storage reference cannot be blank." }
        require(!File(storageReference).isAbsolute) { "Imported annex storage reference must remain app-relative." }
        val file = File(filesDir, storageReference).canonicalFile
        require(file.path == filesDir.path || file.path.startsWith(filesDir.path + File.separator)) {
            "Imported annex storage reference escapes app files."
        }
        require(file.isFile) { "Imported annex file is missing from app storage." }
        return { file.inputStream() }
    }

    private fun linkedDocumentStream(storageReference: String): () -> InputStream {
        val uri = Uri.parse(storageReference)
        require(uri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
            "Linked annex storage reference must use a content URI."
        }
        return {
            requireNotNull(resolver.openInputStream(uri)) {
                "Linked annex document is no longer readable."
            }
        }
    }

    private fun inspect(open: () -> InputStream): InspectedAttachment {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        open().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                require(bytes <= MaximumAttachmentBytes) { "Sync attachment exceeds the 64 MiB entry limit." }
                digest.update(buffer, 0, count)
            }
        }
        return InspectedAttachment(
            byteSize = bytes,
            checksumSha256 = digest.digest().toHex(),
        )
    }

    private fun attachmentEntryName(planId: String, annexId: String): String =
        "attachments/${planId.toArchiveSegment()}/${annexId.toArchiveSegment()}"

    private fun String.toArchiveSegment(): String = encodeToByteArray().joinToString("") { byte ->
        "%02x".format(byte)
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }

    private data class InspectedAttachment(
        val byteSize: Long,
        val checksumSha256: String,
    )

    private companion object {
        const val MaximumAttachmentBytes = 64L * 1024L * 1024L
    }
}
