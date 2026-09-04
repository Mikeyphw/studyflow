package com.mikeyphw.studyflow.core.data

import android.database.Cursor
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Captures a logical snapshot of the Room database without closing the process-wide Room instance.
 * The caller holds a Room transaction while this table/column/value stream and attachment metadata
 * are captured, so the database portion represents one graph revision without depending on SQLite
 * file layout, WAL state, or Android SQLite version.
 */
internal object StudyFlowDatabaseSnapshotWriter {
    private const val SnapshotMagic = "studyflow-logical-database-snapshot"
    private const val SnapshotFormatVersion = 1
    private const val MaximumSnapshotBytes = 64 * 1024 * 1024

    fun capture(database: StudyFlowDatabase): ByteArray {
        val db = database.openHelper.readableDatabase
        val output = ByteArrayOutputStream()
        DataOutputStream(BufferedOutputStream(BoundedSnapshotOutputStream(output))).use { data ->
            data.writeUtf8(SnapshotMagic)
            data.writeInt(SnapshotFormatVersion)
            data.writeInt(StudyFlowDatabase.SCHEMA_VERSION)
            val tables = db.query(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            data.writeInt(tables.size)
            tables.forEach { table ->
                data.writeUtf8(table)
                db.query("SELECT * FROM ${quoteIdentifier(table)}").use { cursor ->
                    data.writeInt(cursor.columnCount)
                    cursor.columnNames.forEach { columnName -> data.writeUtf8(columnName) }
                    data.writeInt(cursor.count)
                    while (cursor.moveToNext()) {
                        repeat(cursor.columnCount) { columnIndex ->
                            when (cursor.getType(columnIndex)) {
                                Cursor.FIELD_TYPE_NULL -> data.writeByte(ValueNull)
                                Cursor.FIELD_TYPE_INTEGER -> {
                                    data.writeByte(ValueInteger)
                                    data.writeLong(cursor.getLong(columnIndex))
                                }
                                Cursor.FIELD_TYPE_FLOAT -> {
                                    data.writeByte(ValueFloat)
                                    data.writeDouble(cursor.getDouble(columnIndex))
                                }
                                Cursor.FIELD_TYPE_STRING -> {
                                    data.writeByte(ValueString)
                                    data.writeUtf8(cursor.getString(columnIndex))
                                }
                                Cursor.FIELD_TYPE_BLOB -> {
                                    data.writeByte(ValueBlob)
                                    data.writeBytesWithLength(cursor.getBlob(columnIndex))
                                }
                                else -> error("Unsupported SQLite cursor value type.")
                            }
                        }
                    }
                }
            }
        }
        return output.toByteArray()
    }

    private fun quoteIdentifier(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""

    private fun DataOutputStream.writeUtf8(value: String) =
        writeBytesWithLength(value.encodeToByteArray())

    private fun DataOutputStream.writeBytesWithLength(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private class BoundedSnapshotOutputStream(
        private val delegate: ByteArrayOutputStream,
    ) : java.io.OutputStream() {
        private var count = 0

        override fun write(value: Int) {
            ensureCapacity(1)
            delegate.write(value)
            count++
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            ensureCapacity(length)
            delegate.write(bytes, offset, length)
            count += length
        }

        private fun ensureCapacity(additionalBytes: Int) {
            require(additionalBytes >= 0 && count.toLong() + additionalBytes <= MaximumSnapshotBytes) {
                "StudyFlow database snapshot exceeds the 64 MiB sync bundle entry limit."
            }
        }
    }

    private const val ValueNull = 0
    private const val ValueInteger = 1
    private const val ValueFloat = 2
    private const val ValueString = 3
    private const val ValueBlob = 4
}
