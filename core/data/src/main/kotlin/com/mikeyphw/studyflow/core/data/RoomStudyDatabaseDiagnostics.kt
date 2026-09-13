package com.mikeyphw.studyflow.core.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowIntegrity
import com.mikeyphw.studyflow.core.database.StudyFlowMigrations
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthLabReport
import com.mikeyphw.studyflow.core.diagnostics.DatabaseHealthMessage
import com.mikeyphw.studyflow.core.diagnostics.DebugLabStatus
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun inspectStudyFlowDatabase(
    database: StudyFlowDatabase,
): DatabaseHealthLabReport = withContext(Dispatchers.IO) {
    databaseHealthReportOrUnavailable(
        expectedSchemaVersion = StudyFlowDatabase.SCHEMA_VERSION,
    ) {
        val sqlite = database.openHelper.readableDatabase
        val schemaVersion = sqlite.version
        val quickCheckPassed = sqlite.singleString("PRAGMA quick_check")
            .equals("ok", ignoreCase = true)
        val foreignKeyViolationCount = sqlite.rowCount("PRAGMA foreign_key_check")
        val logicalIntegrityViolationCount = StudyFlowIntegrity.logicalIntegrityViolationCount(sqlite)
        val tableNames = sqlite.userTableNames()
        val totalRows = tableNames.sumOf { tableName -> sqlite.tableRowCount(tableName) }
        val metadataPresent = sqlite.singleLong("SELECT COUNT(*) FROM database_metadata") > 0L
        val migrationPathComplete = migrationPathComplete(
            fromVersion = 1,
            toVersion = StudyFlowDatabase.SCHEMA_VERSION,
        )
        val message = when {
            schemaVersion != StudyFlowDatabase.SCHEMA_VERSION -> DatabaseHealthMessage.SCHEMA_MISMATCH
            !quickCheckPassed -> DatabaseHealthMessage.QUICK_CHECK_FAILED
            foreignKeyViolationCount > 0 -> DatabaseHealthMessage.FOREIGN_KEY_VIOLATIONS
            logicalIntegrityViolationCount > 0 -> DatabaseHealthMessage.LOGICAL_INTEGRITY_VIOLATIONS
            !metadataPresent -> DatabaseHealthMessage.METADATA_MISSING
            !migrationPathComplete -> DatabaseHealthMessage.MIGRATION_PATH_INCOMPLETE
            else -> DatabaseHealthMessage.NONE
        }
        DatabaseHealthLabReport(
            status = if (message == DatabaseHealthMessage.NONE) DebugLabStatus.PASSED else DebugLabStatus.FAILED,
            message = message,
            schemaVersion = schemaVersion,
            expectedSchemaVersion = StudyFlowDatabase.SCHEMA_VERSION,
            quickCheckPassed = quickCheckPassed,
            foreignKeyViolationCount = foreignKeyViolationCount,
            logicalIntegrityViolationCount = logicalIntegrityViolationCount,
            userTableCount = tableNames.size,
            totalEntityRows = totalRows,
            metadataPresent = metadataPresent,
            migrationPathComplete = migrationPathComplete,
        )
    }
}

internal inline fun databaseHealthReportOrUnavailable(
    expectedSchemaVersion: Int,
    block: () -> DatabaseHealthLabReport,
): DatabaseHealthLabReport = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    DatabaseHealthLabReport(
        status = DebugLabStatus.FAILED,
        message = DatabaseHealthMessage.DATABASE_UNAVAILABLE,
        expectedSchemaVersion = expectedSchemaVersion,
    )
}

internal fun migrationPathComplete(fromVersion: Int, toVersion: Int): Boolean {
    if (fromVersion == toVersion) return true
    val migrationsByStart = StudyFlowMigrations.All.groupBy { migration -> migration.startVersion }
    var current = fromVersion
    val visited = mutableSetOf<Int>()
    while (current < toVersion && visited.add(current)) {
        val next = migrationsByStart[current]
            .orEmpty()
            .filter { migration -> migration.endVersion <= toVersion }
            .maxByOrNull { migration -> migration.endVersion }
            ?: return false
        current = next.endVersion
    }
    return current == toVersion
}

private fun SupportSQLiteDatabase.singleString(sql: String): String? = query(sql).use { cursor ->
    if (cursor.moveToFirst()) cursor.getString(0) else null
}

private fun SupportSQLiteDatabase.singleLong(sql: String): Long = query(sql).use { cursor ->
    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
}

private fun SupportSQLiteDatabase.rowCount(sql: String): Int = query(sql).use { cursor ->
    var count = 0
    while (cursor.moveToNext()) count += 1
    count
}

private fun SupportSQLiteDatabase.userTableNames(): List<String> = query(
    "SELECT name FROM sqlite_master WHERE type = 'table' " +
        "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('android_metadata', 'room_master_table') " +
        "ORDER BY name",
).use { cursor ->
    buildList {
        while (cursor.moveToNext()) add(cursor.getString(0))
    }
}

private fun SupportSQLiteDatabase.tableRowCount(tableName: String): Long {
    val quoted = tableName.replace("\"", "\"\"")
    return singleLong("SELECT COUNT(*) FROM \"$quoted\"")
}
