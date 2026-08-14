package com.mikeyphw.studyflow.core.portability

import java.security.MessageDigest

/** Pure export-time privacy transformations and package invariants. */
object StudyPlanPrivacyPolicy {
    const val MetadataSection = "METADATA"
    const val NotesSection = "NOTES"
    const val HistorySection = "HISTORY"
    const val PrivateDisplayName = "Private StudyFlow backup"
    const val PrivateApplicationVersion = "redacted"
    const val PrivateExportFileName = "studyflow-private-backup.studyplan"

    val HistoryTableNames: Set<String> = setOf(
        "study_sessions",
        "study_tests",
        "mock_exams",
        "review_items",
        "error_entries",
    )

    private val AllowedSections = setOf(MetadataSection, NotesSection, HistorySection)
    private const val MillisPerDay = 86_400_000L

    fun isPrivateIdentifier(identifier: String): Boolean =
        identifier.matches(Regex("private-[0-9a-f]{20}"))

    fun pseudonymizeIdentifier(exportScopeId: String, rawIdentifier: String): String {
        require(exportScopeId.isNotBlank()) { "Private export scope must not be blank." }
        require(rawIdentifier.isNotBlank()) { "Private export identifier must not be blank." }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$exportScopeId:$rawIdentifier".encodeToByteArray())
        return "private-" + digest.joinToString("") { byte -> "%02x".format(byte) }.take(20)
    }

    fun redactedSections(options: StudyPlanExportOptions): Set<String> = buildSet {
        if (options.metadataPrivacyMode) add(MetadataSection)
        if (options.redactNotes) add(NotesSection)
        if (options.redactHistory || options.mode == StudyPlanExportMode.PRIVATE_STRIPPED) add(HistorySection)
    }

    fun excludedTableNames(options: StudyPlanExportOptions): Set<String> = buildSet {
        if (options.redactNotes) add("study_notes")
        if (options.redactHistory || options.mode == StudyPlanExportMode.PRIVATE_STRIPPED) addAll(HistoryTableNames)
    }

    fun sanitizeRoot(row: PortableRow, options: StudyPlanExportOptions): PortableRow {
        var sanitized = row
        if (options.mode != StudyPlanExportMode.FULL || options.redactHistory) {
            sanitized = sanitized.withValues(
                "status" to text("DRAFT"),
                "progressPercent" to integer(0),
                "studiedMinutesThisWeek" to integer(0),
                "questionsAnswered" to integer(0),
                "averageAccuracyPercent" to integer(0),
                "trashedAtEpochMillis" to nullValue(),
            )
        }
        if (options.metadataPrivacyMode) {
            sanitized = sanitized.withValues(
                "name" to text(PrivateDisplayName),
                "description" to text(""),
                "examDateIso" to nullValue(),
            )
        }
        return sanitized
    }

    fun sanitizeRow(
        tableName: String,
        row: PortableRow,
        options: StudyPlanExportOptions,
    ): PortableRow {
        if (!options.redactHistory && options.mode != StudyPlanExportMode.PRIVATE_STRIPPED) return row
        return when (tableName) {
            "subjects" -> row.withValues(
                "progressPercent" to integer(0),
                "accuracyPercent" to integer(0),
                "status" to text("NOT_STARTED"),
            )
            "topics" -> row.withValues(
                "progressPercent" to integer(0),
                "masteryPercent" to integer(0),
                "status" to text("NOT_STARTED"),
            )
            "study_materials" -> row.withValues("completedUnits" to integer(0))
            "video_resources" -> row.withValues(
                "playbackPositionMillis" to integer(0),
                "completed" to integer(0),
            )
            "planned_activities" -> row.withValues(
                "status" to text("PLANNED"),
                "completedAtEpochMillis" to nullValue(),
            )
            "study_goals" -> row.withValues(
                "currentValue" to real(0.0),
                "completedAtEpochMillis" to nullValue(),
            )
            else -> row
        }
    }

    fun validatePayload(payload: StudyPlanPackagePayload) {
        val manifest = payload.manifest
        val unknownSections = manifest.redactedSections - AllowedSections
        require(unknownSections.isEmpty()) {
            "Study Plan package declares unsupported privacy redactions."
        }
        require(manifest.metadataPrivacyMode == (MetadataSection in manifest.redactedSections)) {
            "Study Plan package metadata privacy declaration is inconsistent."
        }
        val tables = payload.tables.associateBy(PortableTable::name)
        val root = tables["study_plans"]?.rows?.singleOrNull()
            ?: throw StudyPlanPackageException("Study Plan package must contain exactly one root record.")

        if (manifest.metadataPrivacyMode) {
            require(manifest.applicationVersion == PrivateApplicationVersion) {
                "Private package application metadata was not redacted."
            }
            require(manifest.displayName == PrivateDisplayName) {
                "Private package display metadata was not redacted."
            }
            require(isPrivateIdentifier(manifest.sourceStudyPlanId)) {
                "Private package plan identity is invalid."
            }
            manifest.annexPayloads.forEach { annex ->
                require(isPrivateIdentifier(annex.annexId)) {
                    "Private package annex identity is invalid."
                }
                require(annex.entryName == "annex-payloads/${annex.annexId}/payload.bin") {
                    "Private package annex entry path must not expose the source filename."
                }
                require(annex.fileName == "private-annex.bin") {
                    "Private package annex filename must not expose the source filename."
                }
            }
            require(manifest.exportedAtEpochMillis % MillisPerDay == 0L) {
                "Private package export time must be day-granular."
            }
            require(root.textValue("name") == PrivateDisplayName) {
                "Private package root name was not redacted."
            }
            require(root.textValue("description").isEmpty()) {
                "Private package root description was not redacted."
            }
            require(root.isNull("examDateIso")) {
                "Private package exam date was not redacted."
            }
        }

        if (NotesSection in manifest.redactedSections) {
            require("study_notes" !in tables) { "Private notes are present in a redacted package." }
        }
        if (HistorySection in manifest.redactedSections) {
            require(tables.keys.intersect(HistoryTableNames).isEmpty()) {
                "Study history tables are present in a redacted package."
            }
            validateHistoryRedaction(root, tables)
        }
    }

    private fun validateHistoryRedaction(
        root: PortableRow,
        tables: Map<String, PortableTable>,
    ) {
        require(root.textValue("status") == "DRAFT") { "Root status retains study history." }
        listOf(
            "progressPercent",
            "studiedMinutesThisWeek",
            "questionsAnswered",
            "averageAccuracyPercent",
        ).forEach { column -> require(root.isZero(column)) { "Root $column retains study history." } }
        require(root.isNull("trashedAtEpochMillis")) { "Root trash timestamp retains study history." }

        tables["subjects"]?.rows.orEmpty().forEach { row ->
            require(row.isZero("progressPercent") && row.isZero("accuracyPercent")) {
                "Subject progress remains in a history-redacted package."
            }
            require(row.textValue("status") == "NOT_STARTED") {
                "Subject status remains in a history-redacted package."
            }
        }
        tables["topics"]?.rows.orEmpty().forEach { row ->
            require(row.isZero("progressPercent") && row.isZero("masteryPercent")) {
                "Topic progress remains in a history-redacted package."
            }
            require(row.textValue("status") == "NOT_STARTED") {
                "Topic status remains in a history-redacted package."
            }
        }
        tables["study_materials"]?.rows.orEmpty().forEach { row ->
            require(row.isZero("completedUnits")) { "Material progress remains in a history-redacted package." }
        }
        tables["video_resources"]?.rows.orEmpty().forEach { row ->
            require(row.isZero("playbackPositionMillis") && row.isZero("completed")) {
                "Video progress remains in a history-redacted package."
            }
        }
        tables["planned_activities"]?.rows.orEmpty().forEach { row ->
            require(row.textValue("status") == "PLANNED" && row.isNull("completedAtEpochMillis")) {
                "Activity completion remains in a history-redacted package."
            }
        }
        tables["study_goals"]?.rows.orEmpty().forEach { row ->
            require(row.isZero("currentValue") && row.isNull("completedAtEpochMillis")) {
                "Goal progress remains in a history-redacted package."
            }
        }
    }

    private fun PortableRow.withValues(vararg replacements: Pair<String, PortableValue>): PortableRow =
        PortableRow(values + replacements)

    private fun PortableRow.textValue(column: String): String = values[column]?.value.orEmpty()

    private fun PortableRow.isNull(column: String): Boolean = values[column]?.type == PortableValueType.NULL

    private fun PortableRow.isZero(column: String): Boolean = values[column]?.value?.toDoubleOrNull() == 0.0

    private fun text(value: String) = PortableValue(PortableValueType.TEXT, value)
    private fun integer(value: Number) = PortableValue(PortableValueType.INTEGER, value.toLong().toString())
    private fun real(value: Number) = PortableValue(PortableValueType.REAL, value.toDouble().toString())
    private fun nullValue() = PortableValue(PortableValueType.NULL)
}
