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
    val tableRowCounts: Map<String, Int>,
)

@Serializable
data class StudyPlanPackagePayload(
    val manifest: StudyPlanPackageManifest,
    val tables: List<PortableTable>,
)

@Serializable
data class StudyPlanPackageChecksums(
    val algorithm: String = "SHA-256",
    val entries: Map<String, String>,
)

data class StudyPlanImportPreview(
    val manifest: StudyPlanPackageManifest,
    val totalRows: Int,
    val warnings: List<String>,
)

data class StudyPlanImportResult(
    val studyPlanId: String,
    val strategy: StudyPlanImportStrategy,
    val importedRows: Int,
    val snapshotId: String?,
)
