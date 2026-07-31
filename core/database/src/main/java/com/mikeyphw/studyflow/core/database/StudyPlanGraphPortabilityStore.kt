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
        val selectedSpecs = selectedSpecs(options)
        val tables = buildList {
            add(PortableTable(RootSpec.tableName, root.map { sanitizeRoot(it, options.mode) }))
            selectedSpecs.forEach { spec ->
                val rows = queryRows(database, spec, planId).map { row ->
                    if (spec.tableName == "annexes") sanitizeAnnex(row) else row
                }
                add(PortableTable(spec.tableName, rows))
            }
        }
        val counts = tables.associate { it.name to it.rows.size }
        StudyPlanPackagePayload(
            manifest = StudyPlanPackageManifest(
                applicationVersion = applicationVersion,
                exportedAtEpochMillis = exportedAtEpochMillis,
                sourceStudyPlanId = planId,
                displayName = planName,
                exportMode = options.mode,
                includesPrivateHistory = options.mode == StudyPlanExportMode.FULL,
                includesAnnexMetadata = options.includeAnnexMetadata &&
                    selectedSpecs.any { it.tableName == "annexes" },
                includesAnnexPayloads = false,
                tableRowCounts = counts,
            ),
            tables = tables,
        )
    }

    suspend fun importPayload(
        payload: StudyPlanPackagePayload,
        strategy: StudyPlanImportStrategy,
        targetPlanId: String,
        importedAtEpochMillis: Long,
    ): Int = roomDatabase.withTransaction {
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

        val supportedTableNames = StudyPlanGraphDuplicator.TableSpecs
            .mapTo(mutableSetOf(RootSpec.tableName)) { it.tableName }
        val unknownTables = payload.tables.map { it.name }.toSet() - supportedTableNames
        require(unknownTables.isEmpty()) {
            "Study Plan package contains unsupported tables: ${unknownTables.sorted().joinToString()}."
        }
        val tableMap = payload.tables.associateBy { it.name }
        val expectedTables = expectedTableNames(
            mode = payload.manifest.exportMode,
            includeAnnexMetadata = payload.manifest.includesAnnexMetadata,
        )
        require(tableMap.keys == expectedTables) {
            "Study Plan package table inventory does not match its declared export mode."
        }
        val root = tableMap[RootSpec.tableName]?.rows?.singleOrNull()
            ?: error("Study Plan package must contain exactly one root record.")
        require(root.values["id"]?.value == sourcePlanId) {
            "Study Plan package root identity does not match its manifest."
        }
        if (strategy != StudyPlanImportStrategy.MERGE_COMPATIBLE) {
            val remappedRoot = remapRoot(root, targetPlanId, payload.manifest.exportMode, importedAtEpochMillis)
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

    private fun selectedSpecs(options: StudyPlanExportOptions): List<StudyPlanGraphDuplicator.TableSpec> =
        StudyPlanGraphDuplicator.TableSpecs.filter {
            it.tableName in selectedTableNames(options.mode, options.includeAnnexMetadata)
        }

    private fun selectedTableNames(
        mode: StudyPlanExportMode,
        includeAnnexMetadata: Boolean,
    ): Set<String> = when (mode) {
        StudyPlanExportMode.FULL -> FullTables
        StudyPlanExportMode.PRIVATE_STRIPPED -> PrivateStrippedTables
        StudyPlanExportMode.STRUCTURE_ONLY,
        StudyPlanExportMode.TEMPLATE,
        -> StructureTables
    }.let { selected ->
        if (includeAnnexMetadata) selected else selected - "annexes"
    }

    private fun expectedTableNames(
        mode: StudyPlanExportMode,
        includeAnnexMetadata: Boolean,
    ): Set<String> = linkedSetOf(RootSpec.tableName).apply {
        addAll(selectedTableNames(mode, includeAnnexMetadata))
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

    private fun sanitizeRoot(row: PortableRow, mode: StudyPlanExportMode): PortableRow {
        if (mode == StudyPlanExportMode.FULL) return row
        return row.withValues(
            "status" to text("DRAFT"),
            "progressPercent" to integer(0),
            "studiedMinutesThisWeek" to integer(0),
            "questionsAnswered" to integer(0),
            "averageAccuracyPercent" to integer(0),
            "trashedAtEpochMillis" to PortableValue(PortableValueType.NULL),
        )
    }

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

    private companion object {
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
