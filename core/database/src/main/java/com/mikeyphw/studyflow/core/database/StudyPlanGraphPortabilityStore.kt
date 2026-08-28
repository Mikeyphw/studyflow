package com.mikeyphw.studyflow.core.database

import android.content.ContentValues
import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mikeyphw.studyflow.core.portability.PortableAnnexPayload
import com.mikeyphw.studyflow.core.portability.PortableRow
import com.mikeyphw.studyflow.core.portability.PortableTable
import com.mikeyphw.studyflow.core.portability.PortableValue
import com.mikeyphw.studyflow.core.portability.PortableValueType
import com.mikeyphw.studyflow.core.portability.PracticeConfigurationReferences
import com.mikeyphw.studyflow.core.portability.StudyPlanExportMode
import com.mikeyphw.studyflow.core.portability.StudyPlanExportOptions
import com.mikeyphw.studyflow.core.portability.StudyPlanImportStrategy
import com.mikeyphw.studyflow.core.portability.StudyPlanPackageManifest
import com.mikeyphw.studyflow.core.portability.StudyPlanPackagePayload
import com.mikeyphw.studyflow.core.portability.StudyPlanPrivacyPolicy
import java.util.UUID

class StudyPlanGraphPortabilityStore(
    private val roomDatabase: StudyFlowDatabase,
) {
    suspend fun exportPayload(
        planId: String,
        options: StudyPlanExportOptions,
        applicationVersion: String,
        exportedAtEpochMillis: Long,
    ): StudyPlanPackagePayload = roomDatabase.withTransaction {
        val database = roomDatabase.openHelper.writableDatabase
        val root = queryRows(database, RootSpec, planId)
        require(root.size == 1) { "Study Plan $planId does not exist." }
        val planName = root.single().values.getValue("name").value.orEmpty()
        val exportedPlanId = if (options.metadataPrivacyMode) privatePlanId() else planId
        val selectedSpecs = selectedSpecs(options)
        val tables = buildList {
            val rootRows = root.map { row ->
                StudyPlanPrivacyPolicy.sanitizeRoot(row, options).let { sanitized ->
                    if (options.metadataPrivacyMode) {
                        sanitized.withValues("id" to text(exportedPlanId))
                    } else {
                        sanitized
                    }
                }
            }
            add(PortableTable(RootSpec.tableName, rootRows))
            selectedSpecs.forEach { spec ->
                val sourceRows = queryRows(database, spec, planId).filter { row ->
                    spec.tableName != "annexes" || row.values["storageMode"]?.value == "IMPORTED_COPY"
                }
                val rows = sourceRows.map { row ->
                    val privacySanitized = StudyPlanPrivacyPolicy.sanitizeRow(spec.tableName, row, options)
                    val sanitized = if (spec.tableName == "annexes") {
                        sanitizeAnnex(privacySanitized, metadataPrivacyMode = options.metadataPrivacyMode)
                    } else {
                        privacySanitized
                    }
                    if (options.metadataPrivacyMode) {
                        privatizeIdentifiers(
                            row = sanitized,
                            spec = spec,
                            sourcePlanId = planId,
                            exportedPlanId = exportedPlanId,
                        ).remapPracticeConfiguration(spec.tableName) { rawQuestionId ->
                            StudyPlanPrivacyPolicy.pseudonymizeIdentifier(exportedPlanId, rawQuestionId)
                        }
                    } else {
                        sanitized
                    }
                }
                add(PortableTable(spec.tableName, rows))
            }
        }
        val counts = tables.associate { it.name to it.rows.size }
        val payload = StudyPlanPackagePayload(
            manifest = StudyPlanPackageManifest(
                applicationVersion = if (options.metadataPrivacyMode) {
                    StudyPlanPrivacyPolicy.PrivateApplicationVersion
                } else {
                    applicationVersion
                },
                exportedAtEpochMillis = if (options.metadataPrivacyMode) {
                    exportedAtEpochMillis - exportedAtEpochMillis % MillisPerDay
                } else {
                    exportedAtEpochMillis
                },
                sourceStudyPlanId = exportedPlanId,
                displayName = if (options.metadataPrivacyMode) {
                    StudyPlanPrivacyPolicy.PrivateDisplayName
                } else {
                    planName
                },
                exportMode = options.mode,
                includesPrivateHistory = options.mode == StudyPlanExportMode.FULL && !options.redactHistory,
                includesAnnexMetadata = options.includeAnnexMetadata &&
                    selectedSpecs.any { it.tableName == "annexes" },
                // Payload entries are attached by the filesystem-aware repository after this
                // database transaction. Keep the graph payload self-consistent until then.
                includesAnnexPayloads = false,
                tableRowCounts = counts,
                metadataPrivacyMode = options.metadataPrivacyMode,
                redactedSections = StudyPlanPrivacyPolicy.redactedSections(options),
            ),
            tables = tables,
        )
        validatePayload(payload)
        payload
    }

    fun validatePayload(payload: StudyPlanPackagePayload): Int {
        StudyPlanPrivacyPolicy.validatePayload(payload)
        val supportedSpecs = StudyPlanGraphDuplicator.PortabilityTableSpecs.associateBy { it.tableName }
        val supportedTableNames = supportedSpecs.keys + RootSpec.tableName
        val tableNames = payload.tables.map { it.name }
        require(tableNames.size == tableNames.toSet().size) {
            "Study Plan package contains duplicate table entries."
        }
        val unknownTables = tableNames.toSet() - supportedTableNames
        require(unknownTables.isEmpty()) {
            "Study Plan package contains unsupported tables: ${unknownTables.sorted().joinToString()}."
        }
        val tableMap = payload.tables.associateBy { it.name }
        val expectedTables = expectedTableNames(payload.manifest)
        require(tableMap.keys == expectedTables) {
            "Study Plan package table inventory does not match its declared export mode."
        }
        val counts = payload.tables.associate { it.name to it.rows.size }
        require(counts == payload.manifest.tableRowCounts) {
            "Study Plan package row counts do not match its manifest."
        }
        val root = tableMap[RootSpec.tableName]?.rows?.singleOrNull()
            ?: error("Study Plan package must contain exactly one root record.")
        require(root.values["id"]?.value == payload.manifest.sourceStudyPlanId) {
            "Study Plan package root identity does not match its manifest."
        }
        validateColumns(RootSpec, root)
        supportedSpecs.forEach { (tableName, spec) ->
            tableMap[tableName]?.rows.orEmpty().forEach { row ->
                validateColumns(spec, row)
                require(row.values["studyPlanId"]?.value == payload.manifest.sourceStudyPlanId) {
                    "Study Plan package row in $tableName belongs to a different Study Plan."
                }
                if (payload.manifest.metadataPrivacyMode) {
                    spec.remappedIdentifierColumns.forEach { column ->
                        val identifier = row.values[column]?.value ?: return@forEach
                        require(StudyPlanPrivacyPolicy.isPrivateIdentifier(identifier)) {
                            "Private package row in $tableName contains a non-private $column identifier."
                        }
                    }
                }
            }
        }
        validateRelationalOwnership(payload, tableMap)
        return counts.values.sum()
    }

    suspend fun importPayload(
        payload: StudyPlanPackagePayload,
        strategy: StudyPlanImportStrategy,
        targetPlanId: String,
        importedAtEpochMillis: Long,
        beforeCommit: suspend () -> Unit = {},
    ): Int {
        validatePayload(payload)
        return roomDatabase.withTransaction {
            val database = roomDatabase.openHelper.writableDatabase
            val exists = countPlan(database, targetPlanId) == 1
            when (strategy) {
                StudyPlanImportStrategy.CREATE_AS_NEW -> require(!exists) {
                    "A Study Plan with id $targetPlanId already exists."
                }
                StudyPlanImportStrategy.REPLACE_EXISTING,
                StudyPlanImportStrategy.MERGE_COMPATIBLE,
                -> require(exists) { "Study Plan $targetPlanId does not exist." }
            }

            if (strategy == StudyPlanImportStrategy.REPLACE_EXISTING) {
                clearPlanForReplacement(database, targetPlanId)
            }

            val importToken = UUID.randomUUID().toString()
            val prefix = if (strategy == StudyPlanImportStrategy.MERGE_COMPATIBLE) {
                "$targetPlanId:import:$importToken"
            } else {
                targetPlanId
            }
            val sourcePlanId = payload.manifest.sourceStudyPlanId
            var importedRows = 0

            val tableMap = payload.tables.associateBy { it.name }
            val root = tableMap.getValue(RootSpec.tableName).rows.single()
            if (strategy != StudyPlanImportStrategy.MERGE_COMPATIBLE) {
                val remappedRoot = remapRoot(
                    row = root,
                    targetPlanId = targetPlanId,
                    exportMode = payload.manifest.exportMode,
                    now = importedAtEpochMillis,
                )
                if (strategy == StudyPlanImportStrategy.REPLACE_EXISTING) {
                    updateRoot(database, remappedRoot, targetPlanId)
                } else {
                    insertRow(database = database, spec = RootSpec, row = remappedRoot)
                }
                importedRows++
            }

            StudyPlanGraphDuplicator.PortabilityTableSpecs
                .forEach { spec ->
                    if (strategy == StudyPlanImportStrategy.MERGE_COMPATIBLE &&
                        spec.tableName in MergeSkippedTables
                    ) return@forEach
                    tableMap[spec.tableName].orEmptyRows().forEach { row ->
                        insertRow(
                            database,
                            spec,
                            remapChild(row, spec, sourcePlanId, targetPlanId, prefix),
                        )
                        importedRows++
                    }
                }
            beforeCommit()
            importedRows
        }
    }

    private fun validateRelationalOwnership(
        payload: StudyPlanPackagePayload,
        tableMap: Map<String, PortableTable>,
    ) {
        val idsByTable = tableMap.mapValues { (_, table) ->
            table.rows.mapNotNull { row -> row.values["id"]?.value }.toSet()
        }

        fun requireReference(
            tableName: String,
            row: PortableRow,
            column: String,
            targetTable: String,
            required: Boolean = false,
        ) {
            val value = row.values[column]
            val reference = value?.value
            if (value == null || value.type == PortableValueType.NULL || reference.isNullOrBlank()) {
                require(!required) { "$tableName.$column must reference $targetTable." }
                return
            }
            require(reference in idsByTable[targetTable].orEmpty()) {
                "$tableName.$column references a missing or cross-plan $targetTable row."
            }
        }

        fun rows(tableName: String): List<PortableRow> = tableMap[tableName]?.rows.orEmpty()

        rows("exam_stages").forEach { row ->
            requireReference("exam_stages", row, "studyTargetId", "study_targets", required = true)
        }
        rows("exam_notice_revisions").forEach { row ->
            requireReference(
                "exam_notice_revisions",
                row,
                "studyTargetId",
                "study_targets",
                required = true,
            )
        }
        rows("subjects").forEach { row ->
            requireReference("subjects", row, "examStageId", "exam_stages")
        }
        rows("topics").forEach { row ->
            requireReference("topics", row, "subjectId", "subjects", required = true)
            requireReference("topics", row, "parentTopicId", "topics")
            requireReference("topics", row, "examStageId", "exam_stages")
            val parentId = row.values["parentTopicId"]?.value
            if (!parentId.isNullOrBlank()) {
                val parent = rows("topics").first { it.values["id"]?.value == parentId }
                require(parent.values["subjectId"]?.value == row.values["subjectId"]?.value) {
                    "Topic parent must belong to the same subject."
                }
            }
        }
        val topicsById = rows("topics").associateBy { row -> row.values["id"]?.value.orEmpty() }
        fun requireTopicSubjectConsistency(tableName: String, row: PortableRow) {
            val topicId = row.values["topicId"]?.value?.takeIf(String::isNotBlank) ?: return
            val topic = requireNotNull(topicsById[topicId]) {
                "$tableName.topicId references a missing or cross-plan topic."
            }
            val subjectId = row.values["subjectId"]?.value?.takeIf(String::isNotBlank)
            require(subjectId == topic.values["subjectId"]?.value) {
                "$tableName subject/topic ownership is inconsistent."
            }
        }
        rows("legislation_provisions").forEach { row ->
            requireReference(
                "legislation_provisions",
                row,
                "legislationDocumentId",
                "legislation_documents",
                required = true,
            )
        }
        rows("legislation_evidence_links").forEach { row ->
            requireReference(
                "legislation_evidence_links",
                row,
                "provisionId",
                "legislation_provisions",
                required = true,
            )
            requireReference("legislation_evidence_links", row, "questionId", "questions")
            requireReference("legislation_evidence_links", row, "errorEntryId", "error_entries")
            requireReference("legislation_evidence_links", row, "reviewItemId", "review_items")
            val targetCount = listOf("questionId", "errorEntryId", "reviewItemId").count { column ->
                row.values[column]?.let { it.type != PortableValueType.NULL && !it.value.isNullOrBlank() } == true
            }
            require(targetCount == 1) { "Legislation evidence link must reference exactly one evidence target." }
        }
        rows("study_cycle_items").forEach { row ->
            requireReference("study_cycle_items", row, "studyCycleId", "study_cycles", required = true)
            requireReference("study_cycle_items", row, "subjectId", "subjects")
            requireReference("study_cycle_items", row, "topicId", "topics")
            requireTopicSubjectConsistency("study_cycle_items", row)
        }
        rows("study_cycles").forEach { row ->
            val currentItemId = row.values["currentItemId"]?.value?.takeIf(String::isNotBlank) ?: return@forEach
            val item = rows("study_cycle_items").firstOrNull { it.values["id"]?.value == currentItemId }
            require(item != null && item.values["studyCycleId"]?.value == row.values["id"]?.value) {
                "Study cycle current item must belong to the same cycle."
            }
        }
        rows("schedule_rules").forEach { row ->
            requireReference("schedule_rules", row, "subjectId", "subjects")
        }
        rows("study_materials").forEach { row ->
            requireReference("study_materials", row, "subjectId", "subjects")
            requireReference("study_materials", row, "topicId", "topics")
            requireTopicSubjectConsistency("study_materials", row)
        }
        rows("video_resources").forEach { row ->
            requireReference("video_resources", row, "subjectId", "subjects")
            requireReference("video_resources", row, "topicId", "topics")
            requireTopicSubjectConsistency("video_resources", row)
        }
        rows("planned_activities").forEach { row ->
            requireReference("planned_activities", row, "subjectId", "subjects")
            requireReference("planned_activities", row, "topicId", "topics")
            requireReference("planned_activities", row, "materialId", "study_materials")
            requireReference("planned_activities", row, "videoResourceId", "video_resources")
            requireTopicSubjectConsistency("planned_activities", row)
        }
        rows("study_sessions").forEach { row ->
            requireReference("study_sessions", row, "plannedActivityId", "planned_activities")
            requireReference("study_sessions", row, "subjectId", "subjects")
            requireReference("study_sessions", row, "topicId", "topics")
            requireReference("study_sessions", row, "materialId", "study_materials")
            requireReference("study_sessions", row, "videoResourceId", "video_resources")
            requireTopicSubjectConsistency("study_sessions", row)
        }
        rows("study_goals").forEach { row ->
            requireReference("study_goals", row, "subjectId", "subjects")
            requireReference("study_goals", row, "topicId", "topics")
            requireTopicSubjectConsistency("study_goals", row)
        }
        rows("annexes").forEach { row ->
            require(row.values["storageMode"]?.value == "IMPORTED_COPY") {
                "Portable annex metadata may only describe app-owned imported copies."
            }
            require(row.values["storageReference"]?.value.orEmpty().isBlank()) {
                "Portable annex metadata must not contain a device-local storage path."
            }
            require(row.values["persistedPermission"]?.value in setOf("0", null)) {
                "Portable annex metadata must not claim a device-local persisted URI grant."
            }
            requireReference("annexes", row, "subjectId", "subjects")
            requireReference("annexes", row, "topicId", "topics")
            requireReference("annexes", row, "materialId", "study_materials")
            requireTopicSubjectConsistency("annexes", row)
        }
        rows("questions").forEach { row ->
            requireReference("questions", row, "subjectId", "subjects")
            requireReference("questions", row, "topicId", "topics")
            requireReference("questions", row, "examStageId", "exam_stages")
            requireReference("questions", row, "examNoticeRevisionId", "exam_notice_revisions")
            requireTopicSubjectConsistency("questions", row)
        }
        rows("flashcards").forEach { row ->
            requireReference("flashcards", row, "subjectId", "subjects")
            requireReference("flashcards", row, "topicId", "topics")
            requireTopicSubjectConsistency("flashcards", row)
        }
        rows("error_entries").forEach { row ->
            requireReference("error_entries", row, "questionId", "questions", required = true)
            requireReference("error_entries", row, "subjectId", "subjects")
            requireReference("error_entries", row, "topicId", "topics")
            requireTopicSubjectConsistency("error_entries", row)
        }
        rows("review_items").forEach { row ->
            requireReference("review_items", row, "subjectId", "subjects")
            requireReference("review_items", row, "topicId", "topics")
            requireReference("review_items", row, "questionId", "questions")
            requireReference("review_items", row, "flashcardId", "flashcards")
            requireReference("review_items", row, "errorEntryId", "error_entries")
            val targetCount = listOf("questionId", "flashcardId", "errorEntryId").count { column ->
                row.values[column]?.let { it.type != PortableValueType.NULL && !it.value.isNullOrBlank() } == true
            }
            require(targetCount == 1) { "Review item must reference exactly one review target." }
            requireTopicSubjectConsistency("review_items", row)
        }
        rows("study_notes").forEach { row ->
            requireReference("study_notes", row, "subjectId", "subjects")
            requireReference("study_notes", row, "topicId", "topics")
            requireReference("study_notes", row, "materialId", "study_materials")
            requireTopicSubjectConsistency("study_notes", row)
        }
        rows("code_snippets").forEach { row ->
            requireReference("code_snippets", row, "subjectId", "subjects")
            requireReference("code_snippets", row, "topicId", "topics")
            requireTopicSubjectConsistency("code_snippets", row)
        }
        rows("coding_exercises").forEach { row ->
            requireReference("coding_exercises", row, "subjectId", "subjects")
            requireReference("coding_exercises", row, "topicId", "topics")
            requireReference("coding_exercises", row, "snippetId", "code_snippets")
            requireTopicSubjectConsistency("coding_exercises", row)
        }
        rows("mini_projects").forEach { row ->
            requireReference("mini_projects", row, "subjectId", "subjects")
            requireReference("mini_projects", row, "topicId", "topics")
            requireTopicSubjectConsistency("mini_projects", row)
        }

        val questionIds = idsByTable["questions"].orEmpty()
        listOf("study_tests", "mock_exams").forEach { tableName ->
            rows(tableName).forEach { row ->
                val configuration = row.values["configurationJson"]?.value.orEmpty()
                PracticeConfigurationReferences.referencedQuestionIds(configuration).forEach { questionId ->
                    require(questionId in questionIds) {
                        "$tableName.configurationJson references a missing or cross-plan question."
                    }
                }
            }
        }

        require(rows("planned_activities").none { it.values["status"]?.value == "IN_PROGRESS" }) {
            "Study Plan packages cannot capture an in-progress activity; finish or cancel the active session first."
        }
        require(rows("study_sessions").none { it.values["status"]?.value in setOf("RUNNING", "PAUSED") }) {
            "Study Plan packages cannot capture a live Study Session; finish or cancel it first."
        }

        val manifestAnnexIds = payload.manifest.annexPayloads.map(PortableAnnexPayload::annexId).toSet()
        require(manifestAnnexIds.all { it in idsByTable["annexes"].orEmpty() }) {
            "Annex payload manifest references annex metadata that is not present in the package."
        }
    }

    private fun validateColumns(
        spec: StudyPlanGraphDuplicator.TableSpec,
        row: PortableRow,
    ) {
        val actual = row.values.keys
        val expected = spec.columns.toSet()
        val legacyExpected = when (spec.tableName) {
            "subjects", "topics" -> expected - "examStageId"
            "study_sessions" -> expected - "targetDurationMinutes"
            "study_plans" -> expected - "contentMode"
            "questions" -> expected - setOf(
                "organization", "role", "examStageId", "authorship",
                "examNoticeRevisionId", "externalReference", "points",
            )
            else -> emptySet()
        }
        require(actual == expected || actual == legacyExpected) {
            "Columns for ${spec.tableName} do not match the supported schema."
        }
    }

    private fun selectedSpecs(options: StudyPlanExportOptions): List<StudyPlanGraphDuplicator.TableSpec> =
        StudyPlanGraphDuplicator.TableSpecs.filter { it.tableName in selectedTableNames(options) }

    private fun selectedTableNames(options: StudyPlanExportOptions): Set<String> {
        var selected = baseTableNames(options.mode)
        if (!options.includeAnnexMetadata) selected -= "annexes"
        selected -= StudyPlanPrivacyPolicy.excludedTableNames(options)
        return selected
    }

    private fun expectedTableNames(manifest: StudyPlanPackageManifest): Set<String> =
        linkedSetOf(RootSpec.tableName).apply {
            var selected = baseTableNames(manifest.exportMode)
            if (!manifest.includesAnnexMetadata) selected -= "annexes"
            if (StudyPlanPrivacyPolicy.NotesSection in manifest.redactedSections) selected -= "study_notes"
            if (StudyPlanPrivacyPolicy.HistorySection in manifest.redactedSections) {
                selected -= StudyPlanPrivacyPolicy.HistoryTableNames
            }
            addAll(selected)
        }

    private fun baseTableNames(mode: StudyPlanExportMode): Set<String> = when (mode) {
        StudyPlanExportMode.FULL -> FullTables
        StudyPlanExportMode.PRIVATE_STRIPPED -> PrivateStrippedTables
        StudyPlanExportMode.STRUCTURE_ONLY,
        StudyPlanExportMode.TEMPLATE,
        -> StructureTables
    }

    private fun queryRows(
        database: SupportSQLiteDatabase,
        spec: StudyPlanGraphDuplicator.TableSpec,
        planId: String,
    ): List<PortableRow> {
        val identityColumn = if (spec.tableName == RootSpec.tableName) "id" else "studyPlanId"
        return database.query(
            "SELECT * FROM `${spec.tableName}` WHERE `$identityColumn` = ?",
            arrayOf(planId),
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toPortableRow()) } }
    }

    private fun Cursor.toPortableRow(): PortableRow = PortableRow(
        values = columnNames.indices.associate { index ->
            columnNames[index] to when (getType(index)) {
                Cursor.FIELD_TYPE_NULL -> PortableValue(PortableValueType.NULL)
                Cursor.FIELD_TYPE_INTEGER -> PortableValue(PortableValueType.INTEGER, getLong(index).toString())
                Cursor.FIELD_TYPE_FLOAT -> PortableValue(PortableValueType.REAL, getDouble(index).toString())
                Cursor.FIELD_TYPE_STRING -> PortableValue(PortableValueType.TEXT, getString(index))
                else -> error("Binary database values are not supported in Study Plan packages.")
            }
        },
    )

    private fun sanitizeAnnex(row: PortableRow, metadataPrivacyMode: Boolean): PortableRow = row.withValues(
        "displayName" to text(if (metadataPrivacyMode) "private-annex.bin" else row
            .values["displayName"]?.value.orEmpty()),
        "storageReference" to text(""),
        "persistedPermission" to integer(0),
    )

    private fun remapRoot(
        row: PortableRow,
        targetPlanId: String,
        exportMode: StudyPlanExportMode,
        now: Long,
    ): PortableRow = row.withValues(
        "id" to text(targetPlanId),
        "status" to text(if (exportMode == StudyPlanExportMode.TEMPLATE) "DRAFT" else "PAUSED"),
        "createdAtEpochMillis" to integer(now),
        "updatedAtEpochMillis" to integer(now),
        "trashedAtEpochMillis" to PortableValue(PortableValueType.NULL),
    )

    private fun remapChild(
        row: PortableRow,
        spec: StudyPlanGraphDuplicator.TableSpec,
        sourcePlanId: String,
        targetPlanId: String,
        prefix: String,
    ): PortableRow {
        val values = row.values.toMutableMap()
        values["studyPlanId"] = text(targetPlanId)
        spec.remappedIdentifierColumns.forEach { column ->
            val original = values[column] ?: return@forEach
            if (original.type != PortableValueType.NULL) {
                val raw = original.value.orEmpty()
                values[column] = text(if (raw == sourcePlanId) targetPlanId else "$prefix:$raw")
            }
        }
        return PortableRow(values).remapPracticeConfiguration(spec.tableName) { rawQuestionId ->
            "$prefix:$rawQuestionId"
        }
    }

    private fun clearPlanForReplacement(database: SupportSQLiteDatabase, planId: String) {
        StudyPlanGraphDuplicator.PortabilityTableSpecs.asReversed()
            .forEach { spec -> database.delete(spec.tableName, "studyPlanId = ?", arrayOf(planId)) }
    }

    private fun updateRoot(
        database: SupportSQLiteDatabase,
        row: PortableRow,
        targetPlanId: String,
    ) {
        val values = row.toContentValues(RootSpec).apply { remove("id") }
        val updated = database.update("study_plans", 0, values, "id = ?", arrayOf(targetPlanId))
        check(updated == 1) { "Could not replace Study Plan $targetPlanId." }
    }

    private fun insertRow(
        database: SupportSQLiteDatabase,
        spec: StudyPlanGraphDuplicator.TableSpec,
        row: PortableRow,
    ) {
        val result = database.insert(spec.tableName, 0, row.toContentValues(spec))
        check(result != -1L) { "Could not import row into ${spec.tableName}." }
    }

    private fun PortableRow.toContentValues(spec: StudyPlanGraphDuplicator.TableSpec): ContentValues {
        val allowed = spec.columns.toSet()
        val normalizedValues = values.toMutableMap().apply {
            if (spec.tableName in setOf("subjects", "topics") && "examStageId" !in this) {
                put("examStageId", PortableValue(PortableValueType.NULL))
            }
            if (spec.tableName == "study_sessions" && "targetDurationMinutes" !in this) {
                put("targetDurationMinutes", PortableValue(PortableValueType.NULL))
            }
            if (spec.tableName == "study_plans" && "contentMode" !in this) {
                put("contentMode", text("EXAM_PLAN"))
            }
            if (spec.tableName == "questions" && "authorship" !in this) {
                put("organization", PortableValue(PortableValueType.NULL))
                put("role", PortableValue(PortableValueType.NULL))
                put("examStageId", PortableValue(PortableValueType.NULL))
                put("authorship", text("UNKNOWN"))
                put("examNoticeRevisionId", PortableValue(PortableValueType.NULL))
                put("externalReference", PortableValue(PortableValueType.NULL))
                put("points", PortableValue(PortableValueType.NULL))
            }
        }
        require(normalizedValues.keys == allowed) {
            "Columns for ${spec.tableName} do not match the supported schema."
        }
        return ContentValues(normalizedValues.size).also { contentValues ->
            spec.columns.forEach { column ->
                val value = normalizedValues.getValue(column)
                when (value.type) {
                    PortableValueType.NULL -> contentValues.putNull(column)
                    PortableValueType.INTEGER -> contentValues.put(column, value.value!!.toLong())
                    PortableValueType.REAL -> contentValues.put(column, value.value!!.toDouble())
                    PortableValueType.TEXT -> contentValues.put(column, value.value.orEmpty())
                }
            }
        }
    }

    private fun PortableRow.remapPracticeConfiguration(
        tableName: String,
        transform: (String) -> String,
    ): PortableRow {
        if (tableName !in PracticeConfigurationTables) return this
        val configuration = values["configurationJson"]?.value.orEmpty()
        return withValues(
            "configurationJson" to text(
                PracticeConfigurationReferences.remapQuestionIds(configuration, transform),
            ),
        )
    }

    private fun countPlan(database: SupportSQLiteDatabase, planId: String): Int =
        database.query("SELECT COUNT(*) FROM study_plans WHERE id = ?", arrayOf(planId)).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun PortableTable?.orEmptyRows(): List<PortableRow> = this?.rows.orEmpty()

    private fun PortableRow.withValues(vararg replacements: Pair<String, PortableValue>): PortableRow =
        PortableRow(values + replacements)

    private fun text(value: String) = PortableValue(PortableValueType.TEXT, value)
    private fun integer(value: Number) = PortableValue(PortableValueType.INTEGER, value.toLong().toString())

    private fun privatizeIdentifiers(
        row: PortableRow,
        spec: StudyPlanGraphDuplicator.TableSpec,
        sourcePlanId: String,
        exportedPlanId: String,
    ): PortableRow {
        val privateValues = row.values.toMutableMap()
        privateValues["studyPlanId"] = text(exportedPlanId)
        spec.remappedIdentifierColumns.forEach { column ->
            val current = privateValues[column] ?: return@forEach
            val rawIdentifier = current.value ?: return@forEach
            privateValues[column] = text(
                if (rawIdentifier == sourcePlanId) {
                    exportedPlanId
                } else {
                    StudyPlanPrivacyPolicy.pseudonymizeIdentifier(exportedPlanId, rawIdentifier)
                },
            )
        }
        return PortableRow(privateValues)
    }

    private fun privatePlanId(): String = "private-" + UUID.randomUUID()
        .toString()
        .replace("-", "")
        .take(20)

    private companion object {
        const val MillisPerDay = 86_400_000L
        val RootSpec = StudyPlanGraphDuplicator.TableSpec(
            tableName = "study_plans",
            columns = listOf(
                "id", "name", "description", "type", "contentMode", "status", "examDateIso",
                "weeklyGoalHours", "progressPercent", "studiedMinutesThisWeek", "questionsAnswered",
                "averageAccuracyPercent", "createdAtEpochMillis", "updatedAtEpochMillis",
                "trashedAtEpochMillis",
            ),
            remappedIdentifierColumns = setOf("id"),
        )
        val StructureTables = setOf(
            "study_targets", "exam_stages", "subjects", "topics", "legislation_documents",
            "legislation_provisions", "study_cycles",
            "study_cycle_items", "study_strategies",
            "schedule_rules", "study_plan_settings", "programming_course_profiles",
            "termux_runtime_preferences",
        )
        val PrivateStrippedTables = StructureTables + setOf(
            "study_materials", "video_resources", "annexes", "planned_activities", "study_goals",
            "code_snippets", "coding_exercises", "mini_projects",
        )
        val FullTables = StudyPlanGraphDuplicator.PortabilityTableSpecs.mapTo(linkedSetOf()) { it.tableName }
        val PracticeConfigurationTables = setOf("study_tests", "mock_exams")
        val MergeSkippedTables = setOf(
            "study_targets",
            "exam_stages",
            "exam_notice_revisions",
            "legislation_documents",
            "legislation_provisions",
            "legislation_evidence_links",
            "study_cycles",
            "study_cycle_items",
            "study_strategies",
            "study_plan_settings",
            "programming_course_profiles",
            "termux_runtime_preferences",
        )
    }
}
