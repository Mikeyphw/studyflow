package com.mikeyphw.studyflow.core.portability

import kotlinx.serialization.Serializable

const val StudyPlanPackageFormat = "com.mikeyphw.studyflow.studyplan"
const val StudyPlanPackageSchemaVersion = 2
const val StudyPlanPackageMinimumReadableSchemaVersion = 1
const val StudyPlanPackageMimeType = "application/vnd.studyflow.studyplan+zip"
const val StudyPlanPackageExtension = ".studyplan"

@Serializable
enum class StudyPlanPackagePurpose {
    PORTABLE,
    POINT_IN_TIME_SNAPSHOT,
}

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

enum class PortableResourceHandler {
    DOCUMENT,
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    GENERIC,
}

data class StudyPlanPortableResource(
    val fileName: String,
    val mimeType: String,
    val byteSize: Long,
    val checksumSha256: String,
    val handler: PortableResourceHandler,
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
    val packagePurpose: StudyPlanPackagePurpose = StudyPlanPackagePurpose.PORTABLE,
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
    val annexPayloadStreams: Map<String, () -> java.io.InputStream> = emptyMap(),
) {
    init {
        val declared = payload.manifest.annexPayloads.map { it.entryName }.toSet()
        require(annexPayloadBytes.keys.intersect(annexPayloadStreams.keys).isEmpty()) {
            "Annex payload entries must have exactly one byte or stream source."
        }
        require(annexPayloadBytes.keys + annexPayloadStreams.keys == declared) {
            "Annex payload sources must match the manifest entries."
        }
    }

    fun openAnnexPayload(entryName: String): java.io.InputStream =
        annexPayloadBytes[entryName]?.inputStream()
            ?: annexPayloadStreams[entryName]?.invoke()
            ?: throw StudyPlanPackageException("Missing annex payload source for $entryName.")
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
    val resources: List<StudyPlanPortableResource> = emptyList(),
)

data class StudyPlanBackupIntegrityReport(
    val encrypted: Boolean,
    val schemaVersion: Int,
    val checksumVerified: Boolean,
    val totalRows: Int,
    val annexPayloadCount: Int,
    val metadataPrivacyMode: Boolean,
    /** True only when metadata-private identifiers and annex paths were validated across the whole package. */
    val privateIdentifiersVerified: Boolean = false,
    val redactedSections: Set<String>,
    val warnings: List<StudyPlanPortabilityWarning>,
    val canRestore: Boolean,
)

data class StudyPlanImportTableDiff(
    val tableName: String,
    val incomingRows: Int,
    val currentRows: Int,
    val addedRows: Int,
    val removedRows: Int,
    val changedRows: Int,
    val unchangedRows: Int,
    val ignoredRows: Int = 0,
) {
    val hasChanges: Boolean
        get() = addedRows > 0 || removedRows > 0 || changedRows > 0 || ignoredRows > 0
}

data class StudyPlanRestoreDryRun(
    val strategy: StudyPlanImportStrategy,
    val targetStudyPlanId: String?,
    val totalRows: Int,
    val tableRowCounts: Map<String, Int>,
    val encrypted: Boolean,
    val wouldCreateSafetySnapshot: Boolean,
    val warnings: List<StudyPlanPortabilityWarning>,
    val databaseRowsChanged: Int = 0,
    val tableDiffs: List<StudyPlanImportTableDiff> = emptyList(),
    val transactionalRollbackProtected: Boolean = true,
)

data class StudyPlanImportResult(
    val studyPlanId: String,
    val strategy: StudyPlanImportStrategy,
    val importedRows: Int,
    val snapshotId: String?,
)
