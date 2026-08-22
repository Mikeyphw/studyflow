package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.StudySyncCheckpointRecord
import com.mikeyphw.studyflow.core.model.StudySyncDeviceIdentity
import com.mikeyphw.studyflow.core.model.StudySyncLedgerEvent
import com.mikeyphw.studyflow.core.model.StudySyncLedgerEventType
import com.mikeyphw.studyflow.core.model.StudySyncLedgerFactory
import com.mikeyphw.studyflow.core.model.StudySyncLedgerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySyncLedgerTest {
    @Test
    fun `ledger keeps durable device and checkpoint context`() {
        val device = StudySyncLedgerFactory.localDevice(
            id = StudySyncDeviceIdentity.stableDeviceId("Tablet"),
            name = "Tablet",
            firstSeenAtEpochMillis = 10L,
            lastSeenAtEpochMillis = 20L,
        )
        val checkpoint = StudySyncCheckpointRecord(
            id = StudySyncLedgerFactory.checkpointId(device.id, 4L, 30L),
            deviceId = device.id,
            deviceName = device.name,
            createdAtEpochMillis = 30L,
            databaseRevision = 4L,
            localChangeToken = "revision=4",
            changeCount = 2,
            uploadedAtEpochMillis = 40L,
            remoteLocation = "content://sync-folder",
        )
        val event = StudySyncLedgerEvent(
            sequence = 1L,
            type = StudySyncLedgerEventType.MANUAL_UPLOAD_RECORDED,
            deviceId = device.id,
            checkpointId = checkpoint.id,
            createdAtEpochMillis = 40L,
            message = "Uploaded encrypted checkpoint.",
        )
        val ledger = StudySyncLedgerSnapshot(
            devices = listOf(device),
            checkpoints = listOf(checkpoint),
            events = listOf(event),
        )

        assertEquals(device, ledger.currentDevice)
        assertEquals(checkpoint, ledger.latestCheckpoint)
        assertEquals(1, ledger.uploadedCheckpointCount)
        assertFalse(ledger.hasConflicts)
        assertTrue(ledger.events.single().message.contains("checkpoint"))
    }
}
