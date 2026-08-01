package com.mikeyphw.studyflow.core.portability

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
        StudyPlanPrivacyPolicy.validatePayload(payload)
        val declaredPayloads = payload.manifest.annexPayloads.associateBy { it.entryName }
        if (archive.annexPayloadBytes.keys != declaredPayloads.keys) {
            throw StudyPlanPackageException("Annex payload bytes do not match the package manifest.")
        }
        archive.annexPayloadBytes.forEach { (entryName, bytes) ->
            val declared = declaredPayloads.getValue(entryName)
            if (bytes.size.toLong() != declared.byteSize) {
                throw StudyPlanPackageException("Annex payload size does not match $entryName.")
            }
            if (!sha256(bytes).equals(declared.checksumSha256, ignoreCase = true)) {
                throw StudyPlanPackageException("Annex payload checksum does not match $entryName.")
            }
        }

        val manifestBytes = json.encodeToString(payload.manifest).encodeToByteArray()
        val payloadBytes = json.encodeToString(payload).encodeToByteArray()
        val checksumEntries = linkedMapOf(
            ManifestEntry to sha256(manifestBytes),
            PayloadEntry to sha256(payloadBytes),
        ).apply {
            archive.annexPayloadBytes.toSortedMap().forEach { (entryName, bytes) ->
                put(entryName, sha256(bytes))
            }
        }
        val checksumBytes = json.encodeToString(
            StudyPlanPackageChecksums(entries = checksumEntries),
        ).encodeToByteArray()

        ZipOutputStream(output.buffered()).use { zip ->
            writeEntry(zip, ManifestEntry, manifestBytes)
            writeEntry(zip, PayloadEntry, payloadBytes)
            writeEntry(zip, ChecksumsEntry, checksumBytes)
            archive.annexPayloadBytes.toSortedMap().forEach { (entryName, bytes) ->
                writeEntry(zip, entryName, bytes)
            }
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

    fun preview(input: InputStream): StudyPlanImportPreview = preview(readArchive(input))

    fun preview(archive: StudyPlanPackageArchive): StudyPlanImportPreview {
        val payload = archive.payload
        val warnings = StudyPlanPortabilityWarnings.forManifest(payload.manifest)
        return StudyPlanImportPreview(
            manifest = payload.manifest,
            totalRows = payload.tables.sumOf { it.rows.size },
            warnings = warnings,
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
        if (manifest.schemaVersion != StudyPlanPackageSchemaVersion) {
            throw StudyPlanPackageException(
                "Unsupported Study Plan package schema ${manifest.schemaVersion}; " +
                    "expected $StudyPlanPackageSchemaVersion.",
            )
        }
        if (manifest.applicationId != "com.mikeyphw.studyflow" || manifest.applicationVersion.isBlank()) {
            throw StudyPlanPackageException("Study Plan package application identity is invalid.")
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
        const val MaxEntryBytes = 64L * 1024L * 1024L
        const val MaxTotalBytes = 512L * 1024L * 1024L
    }
}
