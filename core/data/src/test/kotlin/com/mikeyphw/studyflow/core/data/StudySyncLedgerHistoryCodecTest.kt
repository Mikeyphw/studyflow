package com.mikeyphw.studyflow.core.data

import com.mikeyphw.studyflow.core.model.StudySyncCheckpointRecord
import com.mikeyphw.studyflow.core.model.StudySyncLedgerEvent
import com.mikeyphw.studyflow.core.model.StudySyncLedgerEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySyncLedgerHistoryCodecTest {
    @Test
    fun `checkpoint history round trips durable upload and restore facts`() {
        val checkpoint = checkpoint(
            id = "checkpoint-α:1",
            createdAt = 20L,
            uploadedAt = 30L,
            remoteLocation = "content://sync/folder/checkpoint-α.sfbackup",
            restorePreviewedAt = 40L,
        )

        val decoded = StudySyncLedgerHistoryCodec.decodeCheckpoints(
            StudySyncLedgerHistoryCodec.encodeCheckpoints(listOf(checkpoint)),
        )

        assertEquals(listOf(checkpoint), decoded)
    }

    @Test
    fun `checkpoint history upsert replaces same checkpoint and keeps newest bounded records`() {
        var history = emptyList<StudySyncCheckpointRecord>()
        repeat(StudySyncLedgerHistoryCodec.MaxCheckpointHistory + 4) { index ->
            history = StudySyncLedgerHistoryCodec.upsertCheckpoint(
                history,
                checkpoint(id = "checkpoint-$index", createdAt = index.toLong()),
            )
        }
        history = StudySyncLedgerHistoryCodec.upsertCheckpoint(
            history,
            checkpoint(id = "checkpoint-67", createdAt = 67L, uploadedAt = 100L),
        )

        assertEquals(StudySyncLedgerHistoryCodec.MaxCheckpointHistory, history.size)
        assertEquals("checkpoint-4", history.first().id)
        assertEquals(100L, history.last().uploadedAtEpochMillis)
    }

    @Test
    fun `event history round trips text and keeps newest sequences`() {
        var history = emptyList<StudySyncLedgerEvent>()
        repeat(StudySyncLedgerHistoryCodec.MaxEventHistory + 3) { index ->
            history = StudySyncLedgerHistoryCodec.appendEvent(
                history,
                StudySyncLedgerEvent(
                    sequence = index.toLong() + 1L,
                    type = StudySyncLedgerEventType.REMOTE_CHECK_COMPLETED,
                    deviceId = "device:α",
                    checkpointId = if (index % 2 == 0) "checkpoint:$index" else null,
                    createdAtEpochMillis = index.toLong(),
                    message = "History\tmessage $index — ok",
                ),
            )
        }

        val decoded = StudySyncLedgerHistoryCodec.decodeEvents(
            StudySyncLedgerHistoryCodec.encodeEvents(history),
        )

        assertEquals(StudySyncLedgerHistoryCodec.MaxEventHistory, decoded.size)
        assertEquals(4L, decoded.first().sequence)
        assertTrue(decoded.last().message.contains("— ok"))
        assertEquals("checkpoint:130", decoded.last().checkpointId)
        assertNull(decoded[decoded.lastIndex - 1].checkpointId)
    }

    @Test
    fun `invalid persisted rows do not erase valid history`() {
        val valid = StudySyncLedgerHistoryCodec.encodeEvents(
            listOf(
                StudySyncLedgerEvent(
                    sequence = 7L,
                    type = StudySyncLedgerEventType.MANUAL_UPLOAD_RECORDED,
                    deviceId = "device-7",
                    checkpointId = "checkpoint-7",
                    createdAtEpochMillis = 70L,
                    message = "Uploaded.",
                ),
            ),
        )
        val mixed = valid + "not\ta\tvalid\trow\n"

        val decoded = StudySyncLedgerHistoryCodec.decodeEvents(mixed)

        assertEquals(1, decoded.size)
        assertEquals(7L, decoded.single().sequence)
    }

    private fun checkpoint(
        id: String,
        createdAt: Long,
        uploadedAt: Long? = null,
        remoteLocation: String? = null,
        restorePreviewedAt: Long? = null,
    ): StudySyncCheckpointRecord = StudySyncCheckpointRecord(
        id = id,
        deviceId = "device-1",
        deviceName = "Tablet",
        createdAtEpochMillis = createdAt,
        databaseRevision = createdAt,
        localChangeToken = "revision=$createdAt",
        changeCount = 1,
        uploadedAtEpochMillis = uploadedAt,
        remoteLocation = remoteLocation,
        restorePreviewedAtEpochMillis = restorePreviewedAt,
    )
}
