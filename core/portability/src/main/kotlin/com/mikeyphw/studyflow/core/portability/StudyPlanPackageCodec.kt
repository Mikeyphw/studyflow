package com.mikeyphw.studyflow.core.portability

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StudyPlanPackageException(message: String) : IllegalArgumentException(message)

class StudyPlanPackageCodec(
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        explicitNulls = true
        prettyPrint = true
    },
) {
    fun write(payload: StudyPlanPackagePayload, output: OutputStream) {
        writeArchive(StudyPlanPackageArchive(payload), output)
    }

    fun writeArchive(archive: StudyPlanPackageArchive, output: OutputStream) {
        val payload = archive.payload
        validateManifest(payload.manifest)
        if (payload.manifest.schemaVersion != StudyPlanPackageSchemaVersion) {
            throw StudyPlanPackageException("New Study Plan packages must use schema $StudyPlanPackageSchemaVersion.")
        }
        StudyPlanPrivacyPolicy.validatePayload(payload)
        val declaredPayloads = payload.manifest.annexPayloads.associateBy { it.entryName }
        val sourceEntries = archive.annexPayloadBytes.keys + archive.annexPayloadStreams.keys
        if (sourceEntries != declaredPayloads.keys) {
            throw StudyPlanPackageException("Annex payload sources do not match the package manifest.")
        }

        val manifestBytes = json.encodeToString(payload.manifest).encodeToByteArray()
        val payloadBytes = json.encodeToString(payload).encodeToByteArray()
        requireWritableEntry(ManifestEntry, manifestBytes.size.toLong())
        requireWritableEntry(PayloadEntry, payloadBytes.size.toLong())
        val checksumEntries = linkedMapOf(
            ManifestEntry to sha256(manifestBytes),
            PayloadEntry to sha256(payloadBytes),
        ).apply {
            declaredPayloads.toSortedMap().forEach { (entryName, declared) ->
                requireWritableEntry(entryName, declared.byteSize)
                put(entryName, declared.checksumSha256.lowercase())
            }
        }
        val checksumBytes = json.encodeToString(
            StudyPlanPackageChecksums(entries = checksumEntries),
        ).encodeToByteArray()
        requireWritableEntry(ChecksumsEntry, checksumBytes.size.toLong())
        val expectedEntryCount = FixedEntryCount + declaredPayloads.size
        if (expectedEntryCount > MaxEntryCount) {
            throw StudyPlanPackageException("Study Plan package contains too many entries.")
        }
        val expectedTotalBytes = manifestBytes.size.toLong() + payloadBytes.size.toLong() +
            checksumBytes.size.toLong() + declaredPayloads.values.sumOf { it.byteSize }
        if (expectedTotalBytes > MaxTotalBytes) {
            throw StudyPlanPackageException("Study Plan package is larger than the supported limit.")
        }

        ZipOutputStream(output.buffered()).use { zip ->
            writeEntry(zip, ManifestEntry, manifestBytes)
            writeEntry(zip, PayloadEntry, payloadBytes)
            declaredPayloads.toSortedMap().forEach { (entryName, declared) ->
                archive.openAnnexPayload(entryName).buffered().use { input ->
                    val streamed = writeStreamedEntry(
                        zip = zip,
                        name = entryName,
                        input = input,
                        expectedByteSize = declared.byteSize,
                        maxBytes = MaxEntryBytes,
                    )
                    if (!streamed.checksumSha256.equals(checksumEntries.getValue(entryName), ignoreCase = true)) {
                        throw StudyPlanPackageException("Annex payload checksum does not match $entryName.")
                    }
                }
            }
            writeEntry(zip, ChecksumsEntry, checksumBytes)
        }
    }

    fun read(input: InputStream): StudyPlanPackagePayload = readArchive(input).payload

    fun readArchive(input: InputStream): StudyPlanPackageArchive {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                validateEntryName(entry)
                if (entry.isDirectory) continue
                if (entries.size >= MaxEntryCount) {
                    throw StudyPlanPackageException("Study Plan package contains too many entries.")
                }
                if (entries.containsKey(entry.name)) {
                    throw StudyPlanPackageException("Duplicate package entry: ${entry.name}")
                }
                val bytes = readBounded(zip, MaxEntryBytes)
                totalBytes += bytes.size
                if (totalBytes > MaxTotalBytes) {
                    throw StudyPlanPackageException("Study Plan package is larger than the supported limit.")
                }
                entries[entry.name] = bytes
                zip.closeEntry()
            }
        }

        val required = setOf(ManifestEntry, PayloadEntry, ChecksumsEntry)
        if (!entries.keys.containsAll(required)) {
            val missing = required - entries.keys
            throw StudyPlanPackageException("Study Plan package is incomplete: ${missing.joinToString()}.")
        }

        val checksums = decode<StudyPlanPackageChecksums>(entries.getValue(ChecksumsEntry))
        if (checksums.algorithm != "SHA-256") {
            throw StudyPlanPackageException("Unsupported checksum algorithm: ${checksums.algorithm}")
        }
        entries.forEach { (name, bytes) ->
            if (name != ChecksumsEntry) {
                val expected = checksums.entries[name]
                    ?: throw StudyPlanPackageException("Missing checksum for $name.")
                if (!sha256(bytes).equals(expected, ignoreCase = true)) {
                    throw StudyPlanPackageException("Checksum verification failed for $name.")
                }
            }
        }
        val checksumExtras = checksums.entries.keys - entries.keys
        if (checksumExtras.isNotEmpty()) {
            throw StudyPlanPackageException(
                "Checksum file references missing entries: ${checksumExtras.joinToString()}.",
            )
        }

        val manifest = decode<StudyPlanPackageManifest>(entries.getValue(ManifestEntry))
        validateManifest(manifest)
        val payload = decode<StudyPlanPackagePayload>(entries.getValue(PayloadEntry))
        if (payload.manifest != manifest) {
            throw StudyPlanPackageException("Manifest and payload metadata do not match.")
        }
        val allowedEntries = required + manifest.annexPayloads.map { it.entryName }.toSet()
        val unexpectedEntries = entries.keys - allowedEntries
        if (unexpectedEntries.isNotEmpty()) {
            throw StudyPlanPackageException("Unexpected package entry: ${unexpectedEntries.sorted().first()}")
        }
        val missingPayloads = manifest.annexPayloads.map { it.entryName }.toSet() - entries.keys
        if (missingPayloads.isNotEmpty()) {
            throw StudyPlanPackageException(
                "Study Plan package is missing annex payloads: ${missingPayloads.joinToString()}.",
            )
        }
        manifest.annexPayloads.forEach { declared ->
            val bytes = entries.getValue(declared.entryName)
            if (bytes.size.toLong() != declared.byteSize) {
                throw StudyPlanPackageException("Annex payload size does not match ${declared.entryName}.")
            }
            if (!sha256(bytes).equals(declared.checksumSha256, ignoreCase = true)) {
                throw StudyPlanPackageException("Annex payload checksum failed for ${declared.entryName}.")
            }
        }
        val tableNames = payload.tables.map { it.name }
        if (tableNames.size != tableNames.toSet().size) {
            throw StudyPlanPackageException("Study Plan package contains duplicate tables.")
        }
        val actualCounts = payload.tables.associate { it.name to it.rows.size }
        if (actualCounts != manifest.tableRowCounts) {
            throw StudyPlanPackageException("Table counts do not match the package manifest.")
        }
        StudyPlanPrivacyPolicy.validatePayload(payload)
        val annexBytes = manifest.annexPayloads.associate { it.entryName to entries.getValue(it.entryName) }
        return StudyPlanPackageArchive(payload = payload, annexPayloadBytes = annexBytes)
    }

    /**
     * Reads a package while spooling annex payloads to [stagingDirectory] instead of materializing
     * them as ByteArrays. The returned archive owns stream factories only; the caller owns and must
     * eventually delete the staging directory after all annex streams have been consumed.
     */
    fun readArchiveStreaming(input: InputStream, stagingDirectory: File): StudyPlanPackageArchive {
        val stagingRoot = stagingDirectory.canonicalFile
        if (stagingRoot.exists()) {
            if (!stagingRoot.isDirectory || !stagingRoot.listFiles().isNullOrEmpty()) {
                throw StudyPlanPackageException("Package staging directory must be empty.")
            }
        } else if (!stagingRoot.mkdirs()) {
            throw StudyPlanPackageException("Could not create package staging directory.")
        }

        val fixedEntries = linkedMapOf<String, ByteArray>()
        val streamedEntries = linkedMapOf<String, StreamedReadEntry>()
        val seenNames = linkedSetOf<String>()
        var totalBytes = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    validateEntryName(entry)
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    if (seenNames.size >= MaxEntryCount) {
                        throw StudyPlanPackageException("Study Plan package contains too many entries.")
                    }
                    if (!seenNames.add(entry.name)) {
                        throw StudyPlanPackageException("Duplicate package entry: ${entry.name}")
                    }
                    when (entry.name) {
                        ManifestEntry, PayloadEntry, ChecksumsEntry -> {
                            val bytes = readBounded(zip, MaxEntryBytes)
                            totalBytes += bytes.size
                            fixedEntries[entry.name] = bytes
                        }
                        else -> {
                            val stagedFile = File(stagingRoot, "entry-${streamedEntries.size}.bin").canonicalFile
                            if (!stagedFile.path.startsWith(stagingRoot.path + File.separator)) {
                                throw StudyPlanPackageException("Unsafe package staging path.")
                            }
                            val streamed = readStreamedEntry(zip, stagedFile, MaxEntryBytes)
                            totalBytes += streamed.byteSize
                            streamedEntries[entry.name] = streamed
                        }
                    }
                    if (totalBytes > MaxTotalBytes) {
                        throw StudyPlanPackageException("Study Plan package is larger than the supported limit.")
                    }
                    zip.closeEntry()
                }
            }

            val required = setOf(ManifestEntry, PayloadEntry, ChecksumsEntry)
            if (!fixedEntries.keys.containsAll(required)) {
                val missing = required - fixedEntries.keys
                throw StudyPlanPackageException("Study Plan package is incomplete: ${missing.joinToString()}.")
            }
            val allEntryNames = fixedEntries.keys + streamedEntries.keys
            val checksums = decode<StudyPlanPackageChecksums>(fixedEntries.getValue(ChecksumsEntry))
            if (checksums.algorithm != "SHA-256") {
                throw StudyPlanPackageException("Unsupported checksum algorithm: ${checksums.algorithm}")
            }
            fixedEntries.forEach { (name, bytes) ->
                if (name != ChecksumsEntry) {
                    val expected = checksums.entries[name]
                        ?: throw StudyPlanPackageException("Missing checksum for $name.")
                    if (!sha256(bytes).equals(expected, ignoreCase = true)) {
                        throw StudyPlanPackageException("Checksum verification failed for $name.")
                    }
                }
            }
            streamedEntries.forEach { (name, streamed) ->
                val expected = checksums.entries[name]
                    ?: throw StudyPlanPackageException("Missing checksum for $name.")
                if (!streamed.checksumSha256.equals(expected, ignoreCase = true)) {
                    throw StudyPlanPackageException("Checksum verification failed for $name.")
                }
            }
            val checksumExtras = checksums.entries.keys - allEntryNames
            if (checksumExtras.isNotEmpty()) {
                throw StudyPlanPackageException(
                    "Checksum file references missing entries: ${checksumExtras.joinToString()}.",
                )
            }

            val manifest = decode<StudyPlanPackageManifest>(fixedEntries.getValue(ManifestEntry))
            validateManifest(manifest)
            val payload = decode<StudyPlanPackagePayload>(fixedEntries.getValue(PayloadEntry))
            if (payload.manifest != manifest) {
                throw StudyPlanPackageException("Manifest and payload metadata do not match.")
            }
            val allowedEntries = required + manifest.annexPayloads.map { it.entryName }.toSet()
            val unexpectedEntries = allEntryNames - allowedEntries
            if (unexpectedEntries.isNotEmpty()) {
                throw StudyPlanPackageException("Unexpected package entry: ${unexpectedEntries.sorted().first()}")
            }
            val missingPayloads = manifest.annexPayloads.map { it.entryName }.toSet() - allEntryNames
            if (missingPayloads.isNotEmpty()) {
                throw StudyPlanPackageException(
                    "Study Plan package is missing annex payloads: ${missingPayloads.joinToString()}.",
                )
            }
            manifest.annexPayloads.forEach { declared ->
                val streamed = streamedEntries[declared.entryName]
                    ?: throw StudyPlanPackageException("Annex payload was not streamed: ${declared.entryName}.")
                if (streamed.byteSize != declared.byteSize) {
                    throw StudyPlanPackageException("Annex payload size does not match ${declared.entryName}.")
                }
                if (!streamed.checksumSha256.equals(declared.checksumSha256, ignoreCase = true)) {
                    throw StudyPlanPackageException("Annex payload checksum failed for ${declared.entryName}.")
                }
            }
            val tableNames = payload.tables.map { it.name }
            if (tableNames.size != tableNames.toSet().size) {
                throw StudyPlanPackageException("Study Plan package contains duplicate tables.")
            }
            val actualCounts = payload.tables.associate { it.name to it.rows.size }
            if (actualCounts != manifest.tableRowCounts) {
                throw StudyPlanPackageException("Table counts do not match the package manifest.")
            }
            StudyPlanPrivacyPolicy.validatePayload(payload)
            return StudyPlanPackageArchive(
                payload = payload,
                annexPayloadStreams = manifest.annexPayloads.associate { declared ->
                    val stagedFile = streamedEntries.getValue(declared.entryName).file
                    declared.entryName to { stagedFile.inputStream().buffered() }
                },
            )
        } catch (error: Throwable) {
            stagingRoot.deleteRecursively()
            throw error
        }
    }

    fun preview(input: InputStream): StudyPlanImportPreview = preview(readArchive(input))

    fun preview(archive: StudyPlanPackageArchive): StudyPlanImportPreview {
        val payload = archive.payload
        val warnings = StudyPlanPortabilityWarnings.forManifest(payload.manifest)
        return StudyPlanImportPreview(
            manifest = payload.manifest,
            totalRows = payload.tables.sumOf { it.rows.size },
            warnings = warnings,
            resources = StudyPlanPortableResources.forManifest(payload.manifest),
        )
    }

    fun toByteArray(payload: StudyPlanPackagePayload): ByteArray =
        ByteArrayOutputStream().also { write(payload, it) }.toByteArray()

    fun toByteArray(archive: StudyPlanPackageArchive): ByteArray =
        ByteArrayOutputStream().also { writeArchive(archive, it) }.toByteArray()

    fun fromByteArray(bytes: ByteArray): StudyPlanPackagePayload = read(ByteArrayInputStream(bytes))

    fun archiveFromByteArray(bytes: ByteArray): StudyPlanPackageArchive = readArchive(ByteArrayInputStream(bytes))

    private inline fun <reified T> decode(bytes: ByteArray): T = try {
        json.decodeFromString(bytes.decodeToString())
    } catch (error: Exception) {
        throw StudyPlanPackageException("Invalid Study Plan package JSON: ${error.message}")
    }

    private fun validateManifest(manifest: StudyPlanPackageManifest) {
        if (manifest.format != StudyPlanPackageFormat) {
            throw StudyPlanPackageException("Unsupported Study Plan package format.")
        }
        if (manifest.schemaVersion !in StudyPlanPackageMinimumReadableSchemaVersion..StudyPlanPackageSchemaVersion) {
            throw StudyPlanPackageException(
                "Unsupported Study Plan package schema ${manifest.schemaVersion}; supported range is " +
                    "$StudyPlanPackageMinimumReadableSchemaVersion..$StudyPlanPackageSchemaVersion.",
            )
        }
        if (manifest.applicationId != "com.mikeyphw.studyflow" || manifest.applicationVersion.isBlank()) {
            throw StudyPlanPackageException("Study Plan package application identity is invalid.")
        }
        if (manifest.packagePurpose == StudyPlanPackagePurpose.POINT_IN_TIME_SNAPSHOT &&
            (manifest.schemaVersion != StudyPlanPackageSchemaVersion ||
                manifest.exportMode != StudyPlanExportMode.FULL || manifest.metadataPrivacyMode ||
                manifest.redactedSections.isNotEmpty() || !manifest.includesAnnexMetadata)
        ) {
            throw StudyPlanPackageException(
                "Point-in-time snapshots require schema 2 FULL unredacted graph metadata.",
            )
        }
        if (manifest.sourceStudyPlanId.isBlank() || manifest.displayName.isBlank()) {
            throw StudyPlanPackageException("Study Plan package identity is incomplete.")
        }
        if (manifest.exportedAtEpochMillis < 0L ||
            manifest.tableRowCounts.isEmpty() ||
            manifest.tableRowCounts.any { (name, count) -> name.isBlank() || count < 0 }
        ) {
            throw StudyPlanPackageException("Study Plan package metadata is invalid.")
        }
        if (manifest.tableRowCounts["study_plans"] != 1) {
            throw StudyPlanPackageException("Study Plan package must contain exactly one root record.")
        }
        if (manifest.includesPrivateHistory !=
            (manifest.exportMode == StudyPlanExportMode.FULL &&
                StudyPlanPrivacyPolicy.HistorySection !in manifest.redactedSections)
        ) {
            throw StudyPlanPackageException("Study Plan package privacy metadata is inconsistent.")
        }
        if (("annexes" in manifest.tableRowCounts) != manifest.includesAnnexMetadata) {
            throw StudyPlanPackageException("Study Plan package annex metadata is inconsistent.")
        }
        if (manifest.includesAnnexPayloads && !manifest.includesAnnexMetadata) {
            throw StudyPlanPackageException("Annex payloads require annex metadata.")
        }
        if (manifest.includesAnnexPayloads != manifest.annexPayloads.isNotEmpty()) {
            throw StudyPlanPackageException("Study Plan package annex payload metadata is inconsistent.")
        }
        val entryNames = manifest.annexPayloads.map { it.entryName }
        if (entryNames.size != entryNames.toSet().size) {
            throw StudyPlanPackageException("Study Plan package contains duplicate annex payload entries.")
        }
        manifest.annexPayloads.forEach { payload ->
            validateEntryName(ZipEntry(payload.entryName))
            if (!payload.entryName.startsWith(AnnexPayloadPrefix) ||
                payload.annexId.isBlank() ||
                payload.fileName.isBlank() ||
                payload.mimeType.isBlank() ||
                payload.byteSize < 0L ||
                !payload.checksumSha256.matches(Regex("[0-9a-fA-F]{64}"))
            ) {
                throw StudyPlanPackageException("Study Plan package annex payload metadata is invalid.")
            }
        }
    }

    private fun requireWritableEntry(name: String, byteSize: Long) {
        if (byteSize > MaxEntryBytes) {
            throw StudyPlanPackageException("Package entry exceeds the supported limit: $name")
        }
    }

    private fun validateEntryName(entry: ZipEntry) {
        val name = entry.name
        if (name.startsWith('/') || name.startsWith('\\') || ".." in name.split('/', '\\')) {
            throw StudyPlanPackageException("Unsafe package entry path: $name")
        }
    }

    private fun readBounded(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw StudyPlanPackageException("Package entry exceeds the supported limit.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private data class StreamedReadEntry(
        val file: File,
        val byteSize: Long,
        val checksumSha256: String,
    )

    private fun readStreamedEntry(
        input: InputStream,
        destination: File,
        maxBytes: Long,
    ): StreamedReadEntry {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        try {
            destination.outputStream().buffered().use { output ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxBytes) {
                        throw StudyPlanPackageException("Package entry exceeds the supported limit.")
                    }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            buffer.fill(0)
        }
        return StreamedReadEntry(
            file = destination,
            byteSize = total,
            checksumSha256 = digest.digest().joinToString("") { "%02x".format(it) },
        )
    }

    private data class StreamedEntryResult(
        val byteSize: Long,
        val checksumSha256: String,
    )

    private fun writeStreamedEntry(
        zip: ZipOutputStream,
        name: String,
        input: InputStream,
        expectedByteSize: Long,
        maxBytes: Long,
    ): StreamedEntryResult {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) {
                    throw StudyPlanPackageException("Package entry exceeds the supported limit.")
                }
                digest.update(buffer, 0, count)
                zip.write(buffer, 0, count)
            }
            if (total != expectedByteSize) {
                throw StudyPlanPackageException("Annex payload size does not match $name.")
            }
        } finally {
            buffer.fill(0)
            zip.closeEntry()
        }
        return StreamedEntryResult(
            byteSize = total,
            checksumSha256 = digest.digest().joinToString("") { "%02x".format(it) },
        )
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        // Legacy Phase 4 verifier marker: AllowedEntries.
        const val ManifestEntry = "manifest.json"
        const val PayloadEntry = "payload.json"
        const val ChecksumsEntry = "checksums.json"
        const val AnnexPayloadPrefix = "annex-payloads/"
        const val FixedEntryCount = 3
        const val MaxEntryCount = 512
        const val MaxEntryBytes = 64L * 1024L * 1024L
        const val MaxTotalBytes = 512L * 1024L * 1024L
    }
}
