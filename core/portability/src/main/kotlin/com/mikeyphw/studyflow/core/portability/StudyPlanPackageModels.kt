package com.mikeyphw.studyflow.core.portability

import kotlinx.serialization.Serializable

const val StudyPlanPackageFormat = "com.mikeyphw.studyflow.studyplan"
const val StudyPlanPackageSchemaVersion = 1
const val StudyPlanPackageMimeType = "application/vnd.studyflow.studyplan+zip"
const val StudyPlanPackageExtension = ".studyplan"

@Serializable
enum class StudyPlanExportMode {
    FULL,
    PRIVATE_STRIPPED,
    STRUCTURE_ONLY,
    TEMPLATE,
}

@Serializable
data class StudyPlanExportOptions(
    val mode: StudyPlanExportMode = StudyPlanExportMode.FULL,
    val includeAnnexMetadata: Boolean = true,
    val includeAnnexPayloads: Boolean = false,
    val metadataPrivacyMode: Boolean = false,
    val redactNotes: Boolean = false,
    val redactHistory: Boolean = false,
)

@Serializable
enum class StudyPlanImportStrategy {
    CREATE_AS_NEW,
    REPLACE_EXISTING,
    MERGE_COMPATIBLE,
}

@Serializable
enum class PortableValueType {
    NULL,
    INTEGER,
    REAL,
    TEXT,
}

@Serializable
data class PortableValue(
    val type: PortableValueType,
    val value: String? = null,
)

@Serializable
data class PortableRow(
    val values: Map<String, PortableValue>,
)

@Serializable
data class PortableTable(
    val name: String,
    val rows: List<PortableRow>,
)

@Serializable
data class PortableAnnexPayload(
    val annexId: String,
    val entryName: String,
    val fileName: String,
    val mimeType: String,
    val byteSize: Long,
    val checksumSha256: String,
)

@Serializable
data class StudyPlanPackageManifest(
    val format: String = StudyPlanPackageFormat,
    val schemaVersion: Int = StudyPlanPackageSchemaVersion,
    val applicationId: String = "com.mikeyphw.studyflow",
    val applicationVersion: String,
    val exportedAtEpochMillis: Long,
    val sourceStudyPlanId: String,
    val displayName: String,
    val exportMode: StudyPlanExportMode,
    val includesPrivateHistory: Boolean,
    val includesAnnexMetadata: Boolean,
    val includesAnnexPayloads: Boolean = false,
    val annexPayloads: List<PortableAnnexPayload> = emptyList(),
    val tableRowCounts: Map<String, Int>,
    val metadataPrivacyMode: Boolean = false,
    val redactedSections: Set<String> = emptySet(),
)

@Serializable
data class StudyPlanPackagePayload(
    val manifest: StudyPlanPackageManifest,
    val tables: List<PortableTable>,
)

data class StudyPlanPackageArchive(
    val payload: StudyPlanPackagePayload,
    val annexPayloadBytes: Map<String, ByteArray> = emptyMap(),
) {
    init {
        val declared = payload.manifest.annexPayloads.map { it.entryName }.toSet()
        require(annexPayloadBytes.keys == declared) {
            "Annex payload bytes must match the manifest entries."
        }
    }
}

@Serializable
data class StudyPlanPackageChecksums(
    val algorithm: String = "SHA-256",
    val entries: Map<String, String>,
)

enum class StudyPlanPortabilityWarningType {
    ANNEX_METADATA_ONLY,
    ANNEX_PAYLOADS_INCLUDED,
    TEMPLATE_WITHOUT_HISTORY,
    PRIVACY_REDACTIONS,
    REPLACE_CREATES_SAFETY_SNAPSHOT,
}

data class StudyPlanPortabilityWarning(
    val type: StudyPlanPortabilityWarningType,
    val redactedSections: Set<String> = emptySet(),
)

data class StudyPlanImportPreview(
    val manifest: StudyPlanPackageManifest,
    val totalRows: Int,
    val warnings: List<StudyPlanPortabilityWarning>,
    val encrypted: Boolean = false,
)

data class StudyPlanBackupIntegrityReport(
    val encrypted: Boolean,
    val schemaVersion: Int,
    val checksumVerified: Boolean,
    val totalRows: Int,
    val annexPayloadCount: Int,
    val metadataPrivacyMode: Boolean,
    val redactedSections: Set<String>,
    val warnings: List<StudyPlanPortabilityWarning>,
    val canRestore: Boolean,
)

data class StudyPlanRestoreDryRun(
    val strategy: StudyPlanImportStrategy,
    val targetStudyPlanId: String?,
    val totalRows: Int,
    val tableRowCounts: Map<String, Int>,
    val encrypted: Boolean,
    val wouldCreateSafetySnapshot: Boolean,
    val warnings: List<StudyPlanPortabilityWarning>,
    val databaseRowsChanged: Int = 0,
)

data class StudyPlanImportResult(
    val studyPlanId: String,
    val strategy: StudyPlanImportStrategy,
    val importedRows: Int,
    val snapshotId: String?,
)
