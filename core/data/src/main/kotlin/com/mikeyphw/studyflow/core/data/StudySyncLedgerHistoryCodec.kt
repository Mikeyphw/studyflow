package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.model.StudySyncCheckpointRecord
import com.mikeyphw.studyflow.core.model.StudySyncLedgerEvent
import com.mikeyphw.studyflow.core.model.StudySyncLedgerEventType
import java.util.Base64

/**
 * Compact, versioned persistence for the bounded sync ledger kept in Preferences DataStore.
 * String fields are URL-safe base64 encoded so user/device text cannot alter row boundaries.
 */
internal object StudySyncLedgerHistoryCodec {
    const val MaxCheckpointHistory: Int = 64
    const val MaxEventHistory: Int = 128

    private const val CheckpointHeader = "studyflow-sync-checkpoints-v1"
    private const val EventHeader = "studyflow-sync-events-v1"

    fun encodeCheckpoints(checkpoints: List<StudySyncCheckpointRecord>): String = buildString {
        appendLine(CheckpointHeader)
        normalizeCheckpoints(checkpoints).forEach { checkpoint ->
            append(
                listOf(
                    encodeText(checkpoint.id),
                    encodeText(checkpoint.deviceId),
                    encodeText(checkpoint.deviceName),
                    checkpoint.createdAtEpochMillis.toString(),
                    checkpoint.databaseRevision.toString(),
                    encodeText(checkpoint.localChangeToken),
                    checkpoint.changeCount.toString(),
                    checkpoint.uploadedAtEpochMillis?.toString().orEmpty(),
                    checkpoint.remoteLocation?.let(::encodeText).orEmpty(),
                    checkpoint.restorePreviewedAtEpochMillis?.toString().orEmpty(),
                    checkpoint.conflictCount.toString(),
                ).joinToString("\t"),
            )
            append('\n')
        }
    }

    fun decodeCheckpoints(serialized: String?): List<StudySyncCheckpointRecord> {
        if (serialized.isNullOrBlank()) return emptyList()
        val lines = serialized.lineSequence().toList()
        if (lines.firstOrNull() != CheckpointHeader) return emptyList()
        return normalizeCheckpoints(
            lines.drop(1).mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                runCatching { decodeCheckpoint(line) }.getOrNull()
            },
        )
    }

    fun upsertCheckpoint(
        existing: List<StudySyncCheckpointRecord>,
        checkpoint: StudySyncCheckpointRecord,
    ): List<StudySyncCheckpointRecord> = normalizeCheckpoints(
        existing.filterNot { item -> item.id == checkpoint.id } + checkpoint,
    )

    fun encodeEvents(events: List<StudySyncLedgerEvent>): String = buildString {
        appendLine(EventHeader)
        normalizeEvents(events).forEach { event ->
            append(
                listOf(
                    event.sequence.toString(),
                    event.type.name,
                    encodeText(event.deviceId),
                    event.checkpointId?.let(::encodeText).orEmpty(),
                    event.createdAtEpochMillis.toString(),
                    encodeText(event.message),
                ).joinToString("\t"),
            )
            append('\n')
        }
    }

    fun decodeEvents(serialized: String?): List<StudySyncLedgerEvent> {
        if (serialized.isNullOrBlank()) return emptyList()
        val lines = serialized.lineSequence().toList()
        if (lines.firstOrNull() != EventHeader) return emptyList()
        return normalizeEvents(
            lines.drop(1).mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                runCatching { decodeEvent(line) }.getOrNull()
            },
        )
    }

    fun appendEvent(
        existing: List<StudySyncLedgerEvent>,
        event: StudySyncLedgerEvent,
    ): List<StudySyncLedgerEvent> = normalizeEvents(
        existing.filterNot { item -> item.sequence == event.sequence } + event,
    )

    private fun normalizeCheckpoints(
        checkpoints: List<StudySyncCheckpointRecord>,
    ): List<StudySyncCheckpointRecord> = checkpoints
        .associateBy { checkpoint -> checkpoint.id }
        .values
        .sortedWith(compareBy<StudySyncCheckpointRecord> { it.createdAtEpochMillis }.thenBy { it.id })
        .takeLast(MaxCheckpointHistory)

    private fun normalizeEvents(events: List<StudySyncLedgerEvent>): List<StudySyncLedgerEvent> = events
        .associateBy { event -> event.sequence }
        .values
        .sortedBy { event -> event.sequence }
        .takeLast(MaxEventHistory)

    private fun decodeCheckpoint(line: String): StudySyncCheckpointRecord {
        val parts = line.split('\t')
        require(parts.size == 11) { "Invalid persisted sync checkpoint row." }
        return StudySyncCheckpointRecord(
            id = decodeText(parts[0]),
            deviceId = decodeText(parts[1]),
            deviceName = decodeText(parts[2]),
            createdAtEpochMillis = parts[3].toLong(),
            databaseRevision = parts[4].toLong(),
            localChangeToken = decodeText(parts[5]),
            changeCount = parts[6].toInt(),
            uploadedAtEpochMillis = parts[7].takeIf(String::isNotEmpty)?.toLong(),
            remoteLocation = parts[8].takeIf(String::isNotEmpty)?.let(::decodeText),
            restorePreviewedAtEpochMillis = parts[9].takeIf(String::isNotEmpty)?.toLong(),
            conflictCount = parts[10].toInt(),
        )
    }

    private fun decodeEvent(line: String): StudySyncLedgerEvent {
        val parts = line.split('\t')
        require(parts.size == 6) { "Invalid persisted sync ledger event row." }
        return StudySyncLedgerEvent(
            sequence = parts[0].toLong(),
            type = enumValueOf<StudySyncLedgerEventType>(parts[1]),
            deviceId = decodeText(parts[2]),
            checkpointId = parts[3].takeIf(String::isNotEmpty)?.let(::decodeText),
            createdAtEpochMillis = parts[4].toLong(),
            message = decodeText(parts[5]),
        )
    }

    private fun encodeText(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.encodeToByteArray())

    private fun decodeText(value: String): String =
        Base64.getUrlDecoder().decode(value).decodeToString()
}
