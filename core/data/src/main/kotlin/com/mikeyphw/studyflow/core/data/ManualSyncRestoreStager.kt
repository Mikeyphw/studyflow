package com.mikeyphw.studyflow.core.data

import android.content.Context
import com.mikeyphw.studyflow.core.portability.StudyFlowSyncBundleStagedArchive
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal class ManualSyncRestoreStager(context: Context) {
    private val filesDir = context.applicationContext.filesDir.canonicalFile

    fun prepare(archive: StudyFlowSyncBundleStagedArchive): PreparedManualSyncAttachments {
        val entries = archive.manifest.attachments.map { attachment ->
            val (planId, annexId) = decodeAttachmentIdentity(attachment.entryName)
            val directory = File(
                filesDir,
                "shareable-annexes/${sanitizeSegment(planId)}/${sanitizeSegment(annexId)}",
            ).canonicalFile
            require(directory.path.startsWith(filesDir.path + File.separator)) { "Unsafe sync annex directory." }
            val destination = File(directory, "sync-restored.bin").canonicalFile
            require(destination.path.startsWith(directory.path + File.separator)) { "Unsafe sync annex destination." }
            val source = requireNotNull(archive.attachmentFiles[attachment.entryName]) {
                "Staged sync attachment is missing."
            }
            PreparedAttachment(
                planId = planId,
                annexId = annexId,
                source = source,
                destination = destination,
                byteSize = attachment.byteSize,
                checksumSha256 = attachment.checksumSha256,
            )
        }
        require(entries.map { it.planId to it.annexId }.size == entries.map { it.planId to it.annexId }.toSet().size) {
            "Sync checkpoint contains duplicate annex identities."
        }
        return PreparedManualSyncAttachments(filesDir, archive.stagingDirectory, entries)
    }

    private fun decodeAttachmentIdentity(entryName: String): Pair<String, String> {
        val parts = entryName.split('/')
        require(parts.size == 3 && parts[0] == "attachments") { "Sync attachment path is invalid." }
        val planId = decodeHex(parts[1])
        val annexId = decodeHex(parts[2])
        require(planId.isNotBlank() && annexId.isNotBlank()) { "Sync attachment identity is blank." }
        return planId to annexId
    }

    private fun decodeHex(value: String): String {
        require(value.length % 2 == 0 && value.matches(Regex("[0-9a-fA-F]+"))) {
            "Sync attachment identity encoding is invalid."
        }
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }.decodeToString()
    }

    private fun sanitizeSegment(value: String): String = value.replace(Regex("[^A-Za-z0-9._:-]"), "_")
}

internal class PreparedManualSyncAttachments(
    private val filesDir: File,
    private val stagingDirectory: File,
    private val entries: List<PreparedAttachment>,
) {
    private val created = mutableListOf<File>()
    private val replaced = mutableListOf<Pair<File, File>>()

    val bindings: List<ManualSyncAnnexRestoreBinding>
        get() = entries.map { entry ->
            ManualSyncAnnexRestoreBinding(
                studyPlanId = entry.planId,
                annexId = entry.annexId,
                storageReference = entry.destination.relativeTo(filesDir).invariantSeparatorsPath,
                byteSize = entry.byteSize,
                checksumSha256 = entry.checksumSha256,
            )
        }

    fun commitFiles() {
        entries.forEachIndexed { index, entry ->
            val parent = requireNotNull(entry.destination.parentFile)
            require(parent.mkdirs() || parent.isDirectory) { "Could not create sync annex restore directory." }
            val incoming = File(parent, ".restore-${UUID.randomUUID()}.pending")
            entry.source.copyTo(incoming, overwrite = false)
            verify(incoming, entry.byteSize, entry.checksumSha256)
            if (entry.destination.exists()) {
                val backup = File(stagingDirectory, "previous-annex-$index.bin")
                check(entry.destination.renameTo(backup)) { "Could not preserve existing annex before sync restore." }
                replaced += entry.destination to backup
            }
            try {
                check(incoming.renameTo(entry.destination)) { "Could not commit restored sync annex." }
                created += entry.destination
                verify(entry.destination, entry.byteSize, entry.checksumSha256)
            } catch (error: Throwable) {
                incoming.delete()
                throw error
            }
        }
    }

    fun rollbackFiles() {
        created.asReversed().forEach { file -> file.delete() }
        replaced.asReversed().forEach { (destination, backup) ->
            if (destination.exists()) destination.delete()
            destination.parentFile?.mkdirs()
            check(backup.renameTo(destination)) { "Could not roll back annex bytes after failed sync restore." }
        }
        created.clear()
        replaced.clear()
    }

    fun markCommitted() {
        replaced.forEach { (_, backup) ->
            require(!backup.exists() || backup.delete()) { "Could not delete replaced annex bytes." }
        }
        replaced.clear()
        created.clear()
    }

    private fun verify(file: File, byteSize: Long, checksum: String) {
        require(file.length() == byteSize) { "Restored sync annex size verification failed." }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            buffer.fill(0)
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        require(actual.equals(checksum, ignoreCase = true)) { "Restored sync annex checksum verification failed." }
    }
}

internal data class PreparedAttachment(
    val planId: String,
    val annexId: String,
    val source: File,
    val destination: File,
    val byteSize: Long,
    val checksumSha256: String,
)
