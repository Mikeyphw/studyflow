package com.mikeyphw.studyflow.core.data

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mikeyphw.studyflow.core.database.StudyFlowDatabase
import com.mikeyphw.studyflow.core.link.StudyFlowDomainOperation
import com.mikeyphw.studyflow.core.link.StudyFlowEntityId
import com.mikeyphw.studyflow.core.link.StudyFlowOperationId
import com.mikeyphw.studyflow.core.link.StudyFlowOperationKind
import com.mikeyphw.studyflow.core.link.StudyFlowProductMutation
import com.mikeyphw.studyflow.core.link.StudyFlowProductStateBridge
import com.mikeyphw.studyflow.core.link.StudyFlowProductStateContract
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * OV08 production bridge. It diffs Room by stable primary key and journals row-level changes rather
 * than opaque database snapshots, keeping ordinary repository writes and remote replay on the same
 * canonical database graph. Remote projection is transactional and refreshes the local baseline so
 * replay cannot echo back as a new mutation.
 */
internal class RoomStudyFlowSflpProductStateBridge(
    private val database: StudyFlowDatabase,
) : StudyFlowProductStateBridge {
    private val lock = Any()
    private var baseline: Map<RowKey, RowSnapshot>? = null
    private val projectedOperationIds = linkedSetOf<String>()

    override fun captureLocalMutations(workspaceId: StudyFlowWorkspaceId): List<StudyFlowProductMutation> = synchronized(lock) {
        val current = captureRows(database.openHelper.readableDatabase)
        val previous = baseline
        baseline = current
        if (previous == null) return@synchronized emptyList()
        buildList {
            current.forEach { (key, row) ->
                if (previous[key] != row) add(row.toMutation(false))
            }
            previous.forEach { (key, row) ->
                if (key !in current) add(row.toMutation(true))
            }
        }
    }

    override fun projectResolved(workspaceId: StudyFlowWorkspaceId, operations: List<StudyFlowDomainOperation>): Int = synchronized(lock) {
        val pending = operations.filter { op ->
            op.operationId.value !in projectedOperationIds &&
                op.operationType in setOf(StudyFlowProductStateContract.RowUpsert, StudyFlowProductStateContract.RowDelete)
        }
        if (pending.isEmpty()) return@synchronized 0
        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            pending.forEach { operation -> applyOperation(db, operation) }
        }
        projectedOperationIds += pending.map { it.operationId.value }
        if (projectedOperationIds.size > 8_192) {
            val keep = projectedOperationIds.takeLast(4_096).toSet()
            projectedOperationIds.retainAll(keep)
        }
        baseline = captureRows(database.openHelper.readableDatabase)
        pending.size
    }

    private fun applyOperation(db: SupportSQLiteDatabase, operation: StudyFlowDomainOperation) {
        val payload = operation.payload
        val table = payload.getValue("table").jsonPrimitive.content
        require(SafeIdentifier.matches(table) && table in tableNames(db)) { "SFLP product mutation targets an unknown table" }
        val primaryKey = payload.getValue("primaryKey").jsonObject
        require(primaryKey.isNotEmpty()) { "SFLP product mutation has no primary key" }
        if (operation.kind == StudyFlowOperationKind.TOMBSTONE || operation.operationType == StudyFlowProductStateContract.RowDelete) {
            val args = primaryKey.values.map(::decodeValue).toTypedArray()
            val where = primaryKey.keys.joinToString(" AND ") { "${quote(it)} = ?" }
            db.execSQL("DELETE FROM ${quote(table)} WHERE $where", args)
            return
        }
        val values = payload.getValue("values").jsonObject
        require(values.isNotEmpty()) { "SFLP product upsert has no values" }
        val columns = values.keys.toList()
        val placeholders = List(columns.size) { "?" }.joinToString(", ")
        db.execSQL(
            "INSERT OR REPLACE INTO ${quote(table)} (${columns.joinToString(", ") { quote(it) }}) VALUES ($placeholders)",
            columns.map { decodeValue(values.getValue(it)) }.toTypedArray(),
        )
    }

    private fun captureRows(db: SupportSQLiteDatabase): Map<RowKey, RowSnapshot> = buildMap {
        tableNames(db).forEach { table ->
            val pk = primaryKeyColumns(db, table)
            require(pk.isNotEmpty()) { "StudyFlow sync table $table has no primary key" }
            val order = pk.joinToString(", ") { quote(it) }
            db.query("SELECT * FROM ${quote(table)} ORDER BY $order").use { cursor ->
                while (cursor.moveToNext()) {
                    val values = buildJsonObject {
                        cursor.columnNames.forEachIndexed { index, column -> put(column, encodeValue(cursor, index)) }
                    }
                    val primary = buildJsonObject { pk.forEach { column -> put(column, values.getValue(column)) } }
                    val key = RowKey(table, canonicalKey(primary))
                    put(key, RowSnapshot(table, primary, values))
                }
            }
        }
    }

    private fun tableNames(db: SupportSQLiteDatabase): List<String> = db.query(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            if (name !in ExcludedTables && ExcludedPrefixes.none(name::startsWith)) add(name)
        }
    } }

    private fun primaryKeyColumns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(${quote(table)})").use { cursor -> buildList {
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val pkIndex = cursor.getColumnIndexOrThrow("pk")
            while (cursor.moveToNext()) if (cursor.getInt(pkIndex) > 0) add(cursor.getInt(pkIndex) to cursor.getString(nameIndex))
        }.sortedBy { it.first }.map { it.second } }

    private fun RowSnapshot.toMutation(delete: Boolean): StudyFlowProductMutation {
        val keyHash = sha256("$table|${canonicalKey(primaryKey)}")
        val payload = buildJsonObject {
            put("schema", 1)
            put("table", table)
            put("primaryKey", primaryKey)
            if (!delete) put("values", values)
        }
        return StudyFlowProductMutation(
            operationId = StudyFlowOperationId("op-${UUID.randomUUID()}"),
            entityId = StudyFlowEntityId("row-${keyHash.take(48)}"),
            operationType = if (delete) StudyFlowProductStateContract.RowDelete else StudyFlowProductStateContract.RowUpsert,
            tombstone = delete,
            payload = payload,
        )
    }

    private fun encodeValue(cursor: Cursor, index: Int): JsonObject = buildJsonObject {
        when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> put("type", "null")
            Cursor.FIELD_TYPE_INTEGER -> { put("type", "integer"); put("value", cursor.getLong(index)) }
            Cursor.FIELD_TYPE_FLOAT -> { put("type", "float"); put("value", cursor.getDouble(index)) }
            Cursor.FIELD_TYPE_STRING -> { put("type", "string"); put("value", cursor.getString(index)) }
            Cursor.FIELD_TYPE_BLOB -> { put("type", "blob"); put("value", Base64.getEncoder().encodeToString(cursor.getBlob(index))) }
            else -> error("Unsupported SQLite value type")
        }
    }

    private fun decodeValue(value: kotlinx.serialization.json.JsonElement): Any? {
        val obj = value.jsonObject
        return when (obj.getValue("type").jsonPrimitive.content) {
            "null" -> null
            "integer" -> obj.getValue("value").jsonPrimitive.content.toLong()
            "float" -> obj.getValue("value").jsonPrimitive.content.toDouble()
            "string" -> obj.getValue("value").jsonPrimitive.content
            "blob" -> Base64.getDecoder().decode(obj.getValue("value").jsonPrimitive.content)
            else -> error("Unsupported SFLP SQLite value type")
        }
    }

    private fun canonicalKey(key: JsonObject) = key.entries.joinToString("|") { (name, value) -> "$name=$value" }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    private fun quote(value: String): String { require(SafeIdentifier.matches(value)); return "\"$value\"" }

    private data class RowKey(val table: String, val primaryKey: String)
    private data class RowSnapshot(val table: String, val primaryKey: JsonObject, val values: JsonObject)
    private companion object {
        val SafeIdentifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
        val ExcludedTables = setOf("room_master_table", "android_metadata")
        val ExcludedPrefixes = listOf("studyflow_search_fts")
    }
}
