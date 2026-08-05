package com.mikeyphw.studyflow.core.database

import android.content.ContentValues
import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mikeyphw.studyflow.core.portability.PortableRow
import com.mikeyphw.studyflow.core.portability.PortableTable
import com.mikeyphw.studyflow.core.portability.PortableValue
import com.mikeyphw.studyflow.core.portability.PortableValueType
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
                val rows = queryRows(database, spec, planId).map { row ->
                    val privacySanitized = StudyPlanPrivacyPolicy.sanitizeRow(spec.tableName, row, options)
                    val sanitized = if (spec.tableName == "annexes") {
                        sanitizeAnnex(privacySanitized)
                    } else {
                        privacySanitized
                    }
                    if (options.metadataPrivacyMode) {
                        privatizeIdentifiers(
                            row = sanitized,
                            spec = spec,
                            sourcePlanId = planId,
                            exportedPlanId = exportedPlanId,
                        )
                    } else {
                        sanitized
                    }
                }
                add(PortableTable(spec.tableName, rows))
            }
        }
        val counts = tables.associate { it.name to it.rows.size }
        StudyPlanPackagePayload(
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
                includesAnnexPayloads = options.includeAnnexPayloads &&
                    options.includeAnnexMetadata &&
                    selectedSpecs.any { it.tableName == "annexes" },
                tableRowCounts = counts,
                metadataPrivacyMode = options.metadataPrivacyMode,
                redactedSections = StudyPlanPrivacyPolicy.redactedSections(options),
            ),
            tables = tables,
        )
    }

    fun validatePayload(payload: StudyPlanPackagePayload): Int {
        StudyPlanPrivacyPolicy.validatePayload(payload)
        val supportedSpecs = StudyPlanGraphDuplicator.TableSpecs.associateBy { it.tableName }
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
        return counts.values.sum()
    }

    suspend fun importPayload(
        payload: StudyPlanPackagePayload,
        strategy: StudyPlanImportStrategy,
        targetPlanId: String,
        importedAtEpochMillis: Long,
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

            StudyPlanGraphDuplicator.TableSpecs
                .filterNot { it.tableName == "plan_snapshots" }
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
            importedRows
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

    private fun sanitizeAnnex(row: PortableRow): PortableRow = row.withValues(
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
        return PortableRow(values)
    }

    private fun clearPlanForReplacement(database: SupportSQLiteDatabase, planId: String) {
        StudyPlanGraphDuplicator.TableSpecs.asReversed()
            .filterNot { it.tableName == "plan_snapshots" }
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
                "id", "name", "description", "type", "status", "examDateIso", "weeklyGoalHours",
                "progressPercent", "studiedMinutesThisWeek", "questionsAnswered", "averageAccuracyPercent",
                "createdAtEpochMillis", "updatedAtEpochMillis", "trashedAtEpochMillis",
            ),
            remappedIdentifierColumns = setOf("id"),
        )
        val StructureTables = setOf(
            "study_targets", "exam_stages", "subjects", "topics", "study_strategies",
            "schedule_rules", "study_plan_settings",
        )
        val PrivateStrippedTables = StructureTables + setOf(
            "study_materials", "video_resources", "annexes", "planned_activities", "study_goals",
        )
        val FullTables = StudyPlanGraphDuplicator.TableSpecs.mapTo(linkedSetOf()) { it.tableName } -
            "plan_snapshots"
        val MergeSkippedTables = setOf(
            "study_targets",
            "exam_stages",
            "study_strategies",
            "study_plan_settings",
        )
    }
}
