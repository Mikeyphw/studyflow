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
        validateManifest(payload.manifest)
        val manifestBytes = json.encodeToString(payload.manifest).encodeToByteArray()
        val payloadBytes = json.encodeToString(payload).encodeToByteArray()
        val checksumBytes = json.encodeToString(
            StudyPlanPackageChecksums(
                entries = mapOf(
                    ManifestEntry to sha256(manifestBytes),
                    PayloadEntry to sha256(payloadBytes),
                ),
            ),
        ).encodeToByteArray()

        ZipOutputStream(output.buffered()).use { zip ->
            writeEntry(zip, ManifestEntry, manifestBytes)
            writeEntry(zip, PayloadEntry, payloadBytes)
            writeEntry(zip, ChecksumsEntry, checksumBytes)
        }
    }

    fun read(input: InputStream): StudyPlanPackagePayload {
        val entries = linkedMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                validateEntryName(entry)
                if (entry.isDirectory) continue
                if (entry.name !in AllowedEntries) {
                    throw StudyPlanPackageException("Unexpected package entry: ${entry.name}")
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

        if (entries.keys != AllowedEntries) {
            val missing = AllowedEntries - entries.keys
            throw StudyPlanPackageException("Study Plan package is incomplete: ${missing.joinToString()}.")
        }

        val checksums = decode<StudyPlanPackageChecksums>(entries.getValue(ChecksumsEntry))
        if (checksums.algorithm != "SHA-256") {
            throw StudyPlanPackageException("Unsupported checksum algorithm: ${checksums.algorithm}")
        }
        listOf(ManifestEntry, PayloadEntry).forEach { name ->
            val expected = checksums.entries[name]
                ?: throw StudyPlanPackageException("Missing checksum for $name.")
            val actual = sha256(entries.getValue(name))
            if (!actual.equals(expected, ignoreCase = true)) {
                throw StudyPlanPackageException("Checksum verification failed for $name.")
            }
        }

        val manifest = decode<StudyPlanPackageManifest>(entries.getValue(ManifestEntry))
        validateManifest(manifest)
        val payload = decode<StudyPlanPackagePayload>(entries.getValue(PayloadEntry))
        val tableNames = payload.tables.map { it.name }
        if (tableNames.size != tableNames.toSet().size) {
            throw StudyPlanPackageException("Study Plan package contains duplicate tables.")
        }
        if (payload.manifest != manifest) {
            throw StudyPlanPackageException("Manifest and payload metadata do not match.")
        }
        val actualCounts = payload.tables.associate { it.name to it.rows.size }
        if (actualCounts != manifest.tableRowCounts) {
            throw StudyPlanPackageException("Table counts do not match the package manifest.")
        }
        return payload
    }

    fun preview(input: InputStream): StudyPlanImportPreview {
        val payload = read(input)
        val warnings = buildList {
            if (payload.manifest.includesAnnexMetadata && !payload.manifest.includesAnnexPayloads) {
                add("Annex metadata is included, but physical annex files are not part of this package.")
            }
            if (payload.manifest.exportMode == StudyPlanExportMode.TEMPLATE) {
                add("This package is a reusable template and does not contain personal study history.")
            }
        }
        return StudyPlanImportPreview(
            manifest = payload.manifest,
            totalRows = payload.tables.sumOf { it.rows.size },
            warnings = warnings,
        )
    }

    fun toByteArray(payload: StudyPlanPackagePayload): ByteArray =
        ByteArrayOutputStream().also { write(payload, it) }.toByteArray()

    fun fromByteArray(bytes: ByteArray): StudyPlanPackagePayload = read(ByteArrayInputStream(bytes))

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
        if (manifest.includesPrivateHistory != (manifest.exportMode == StudyPlanExportMode.FULL)) {
            throw StudyPlanPackageException("Study Plan package privacy metadata is inconsistent.")
        }
        if (("annexes" in manifest.tableRowCounts) != manifest.includesAnnexMetadata) {
            throw StudyPlanPackageException("Study Plan package annex metadata is inconsistent.")
        }
        if (manifest.includesAnnexPayloads) {
            throw StudyPlanPackageException("Annex payloads are reserved for the annex-storage phase.")
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
        const val ManifestEntry = "manifest.json"
        const val PayloadEntry = "payload.json"
        const val ChecksumsEntry = "checksums.json"
        val AllowedEntries = linkedSetOf(ManifestEntry, PayloadEntry, ChecksumsEntry)
        const val MaxEntryBytes = 48L * 1024L * 1024L
        const val MaxTotalBytes = 64L * 1024L * 1024L
    }
}
