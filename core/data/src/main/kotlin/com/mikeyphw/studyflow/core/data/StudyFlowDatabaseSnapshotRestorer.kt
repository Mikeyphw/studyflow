package com.mikeyphw.studyflow.core.data

import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowIntegrity
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File

internal data class ManualSyncAnnexRestoreBinding(
    val studyPlanId: String,
    val annexId: String,
    val storageReference: String,
    val byteSize: Long,
    val checksumSha256: String,
)

/** Reads and atomically replaces the logical Room graph captured by StudyFlowDatabaseSnapshotWriter. */
internal object StudyFlowDatabaseSnapshotRestorer {
    private const val SnapshotMagic = "studyflow-logical-database-snapshot"
    private const val SnapshotFormatVersion = 1
    private const val MaximumSnapshotBytes = 64L * 1024L * 1024L
    private const val MaximumTables = 256
    private const val MaximumColumns = 256
    private const val MaximumRowsPerTable = 1_000_000
    private const val MaximumValueBytes = 64 * 1024 * 1024
    private val SafeIdentifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val SnapshotExcludedTables = setOf("room_master_table", "android_metadata")
    private val SnapshotExcludedPrefixes = listOf("studyflow_search_fts")

    suspend fun restore(
        database: StudyFlowDatabase,
        snapshotFile: File,
        annexBindings: List<ManualSyncAnnexRestoreBinding>,
    ) {
        val snapshot = read(snapshotFile)
        database.withTransaction {
            val db = database.openHelper.writableDatabase
            validateInventory(db, snapshot)
            db.execSQL("PRAGMA defer_foreign_keys = ON")
            val triggers = captureTriggers(db)
            triggers.forEach { trigger ->
                db.execSQL("DROP TRIGGER ${quoteIdentifier(trigger.name)}")
            }

            // Restore without mutation/ownership triggers firing on a partially reconstructed graph.
            if (snapshot.tables.any { it.name == MetadataTable }) {
                db.execSQL("DELETE FROM ${quoteIdentifier(MetadataTable)}")
            }
            snapshot.tables.asReversed()
                .filterNot { it.name == MetadataTable }
                .forEach { table -> db.execSQL("DELETE FROM ${quoteIdentifier(table.name)}") }

            snapshot.tables.filterNot { it.name == MetadataTable }.forEach { table ->
                insertTable(db, table)
            }
            annexBindings.forEach { binding ->
                val statement = db.compileStatement(
                    "UPDATE annexes SET storageMode = 'IMPORTED_COPY', storageReference = ?, " +
                        "persistedPermission = 0, byteSize = ?, checksumSha256 = ? " +
                        "WHERE studyPlanId = ? AND id = ?",
                )
                statement.bindString(1, binding.storageReference)
                statement.bindLong(2, binding.byteSize)
                statement.bindString(3, binding.checksumSha256)
                statement.bindString(4, binding.studyPlanId)
                statement.bindString(5, binding.annexId)
                require(statement.executeUpdateDelete() == 1) {
                    "Sync attachment does not match an annex row in the restored database."
                }
            }
            snapshot.tables.firstOrNull { it.name == MetadataTable }?.let { metadata ->
                insertTable(db, metadata)
            }
            triggers.forEach { trigger -> db.execSQL(trigger.sql) }

            db.query("PRAGMA foreign_key_check").use { cursor ->
                require(!cursor.moveToFirst()) { "Restored sync checkpoint violates database foreign keys." }
            }
            require(StudyFlowIntegrity.logicalIntegrityViolationCount(db) == 0) {
                "Restored sync checkpoint violates StudyFlow ownership or value invariants."
            }
        }
    }

    fun inspect(snapshotFile: File): SnapshotInspection {
        val snapshot = read(snapshotFile)
        return SnapshotInspection(
            schemaVersion = snapshot.schemaVersion,
            tableCount = snapshot.tables.size,
            rowCount = snapshot.tables.sumOf { it.rows.size },
        )
    }

    private fun read(file: File): LogicalSnapshot {
        require(file.isFile && file.length() in 1..MaximumSnapshotBytes) {
            "Sync logical database snapshot is missing or exceeds the supported size."
        }
        var budget = 0L
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            fun readBytes(): ByteArray {
                val length = input.readInt()
                require(length in 0..MaximumValueBytes) { "Logical snapshot value length is invalid." }
                budget += 4L + length
                require(budget <= MaximumSnapshotBytes) { "Logical snapshot exceeds the supported size." }
                return ByteArray(length).also(input::readFully)
            }
            fun readUtf8(): String = readBytes().decodeToString()

            require(readUtf8() == SnapshotMagic) { "Logical snapshot magic is invalid." }
            require(input.readInt() == SnapshotFormatVersion) { "Logical snapshot format is unsupported." }
            val schemaVersion = input.readInt()
            require(schemaVersion == StudyFlowDatabase.SCHEMA_VERSION) {
                "Sync checkpoint database schema $schemaVersion is incompatible with " +
                    "${StudyFlowDatabase.SCHEMA_VERSION}."
            }
            val tableCount = input.readInt()
            require(tableCount in 1..MaximumTables) { "Logical snapshot table count is invalid." }
            val tables = buildList {
                repeat(tableCount) {
                    val name = readUtf8()
                    require(SafeIdentifier.matches(name)) { "Logical snapshot table name is unsafe." }
                    val columnCount = input.readInt()
                    require(columnCount in 1..MaximumColumns) { "Logical snapshot column count is invalid." }
                    val columns = List(columnCount) {
                        readUtf8().also { column ->
                            require(SafeIdentifier.matches(column)) { "Logical snapshot column name is unsafe." }
                        }
                    }
                    require(columns.size == columns.toSet().size) { "Logical snapshot has duplicate columns." }
                    val rowCount = input.readInt()
                    require(rowCount in 0..MaximumRowsPerTable) { "Logical snapshot row count is invalid." }
                    val rows = buildList {
                        repeat(rowCount) {
                            add(List(columnCount) { readValue(input) { readBytes() } })
                        }
                    }
                    add(LogicalTable(name, columns, rows))
                }
            }
            require(tables.map { it.name }.size == tables.map { it.name }.toSet().size) {
                "Logical snapshot has duplicate tables."
            }
            try {
                require(input.read() == -1) { "Logical snapshot contains trailing bytes." }
            } catch (_: EOFException) {
                // EOF is the expected terminal state.
            }
            return LogicalSnapshot(schemaVersion, tables)
        }
    }

    private fun readValue(input: DataInputStream, readBytes: () -> ByteArray): Any? = when (input.readUnsignedByte()) {
        ValueNull -> null
        ValueInteger -> input.readLong()
        ValueFloat -> input.readDouble()
        ValueString -> readBytes().decodeToString()
        ValueBlob -> readBytes()
        else -> error("Logical snapshot contains an unsupported SQLite value type.")
    }

    private fun validateInventory(db: SupportSQLiteDatabase, snapshot: LogicalSnapshot) {
        val currentTables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val table = cursor.getString(0)
                    if (table !in SnapshotExcludedTables && SnapshotExcludedPrefixes.none(table::startsWith)) add(table)
                }
            }
        }
        require(snapshot.tables.map { it.name }.sorted() == currentTables.sorted()) {
            "Sync checkpoint logical table inventory does not match the current StudyFlow schema."
        }
        snapshot.tables.forEach { table ->
            val columns = db.query("PRAGMA table_info(${quoteIdentifier(table.name)})").use { cursor ->
                buildList {
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                }
            }
            require(columns == table.columns) {
                "Sync checkpoint columns do not match current table ${table.name}."
            }
        }
    }

    private fun captureTriggers(db: SupportSQLiteDatabase): List<DatabaseTrigger> =
        db.query("SELECT name, sql FROM sqlite_master WHERE type = 'trigger' AND sql IS NOT NULL ORDER BY name")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0)
                        val sql = cursor.getString(1)
                        require(SafeIdentifier.matches(name)) { "Database trigger name is unsafe." }
                        require(sql.isNotBlank()) { "Database trigger definition is blank." }
                        add(DatabaseTrigger(name, sql))
                    }
                }
            }

    private fun insertTable(db: SupportSQLiteDatabase, table: LogicalTable) {
        if (table.rows.isEmpty()) return
        val columnSql = table.columns.joinToString(", ") { column -> quoteIdentifier(column) }
        val placeholders = List(table.columns.size) { "?" }.joinToString(", ")
        val statement = db.compileStatement(
            "INSERT INTO ${quoteIdentifier(table.name)} ($columnSql) VALUES ($placeholders)",
        )
        table.rows.forEach { row ->
            statement.clearBindings()
            row.forEachIndexed { index, value ->
                when (value) {
                    null -> statement.bindNull(index + 1)
                    is Long -> statement.bindLong(index + 1, value)
                    is Double -> statement.bindDouble(index + 1, value)
                    is String -> statement.bindString(index + 1, value)
                    is ByteArray -> statement.bindBlob(index + 1, value)
                    else -> error("Unsupported logical snapshot value type ${value::class}.")
                }
            }
            statement.executeInsert()
        }
    }

    private fun quoteIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    data class SnapshotInspection(val schemaVersion: Int, val tableCount: Int, val rowCount: Int)
    private data class LogicalSnapshot(val schemaVersion: Int, val tables: List<LogicalTable>)
    private data class LogicalTable(val name: String, val columns: List<String>, val rows: List<List<Any?>>)
    private data class DatabaseTrigger(val name: String, val sql: String)

    private const val MetadataTable = "database_metadata"
    private const val ValueNull = 0
    private const val ValueInteger = 1
    private const val ValueFloat = 2
    private const val ValueString = 3
    private const val ValueBlob = 4
}
