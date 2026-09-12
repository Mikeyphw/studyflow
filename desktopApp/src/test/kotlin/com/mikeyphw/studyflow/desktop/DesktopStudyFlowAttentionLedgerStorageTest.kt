package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionCategory
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedger
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrence
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionOccurrenceId
import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerAdmission
import com.mikeyphw.studyflow.core.link.StudyFlowDeviceId
import com.mikeyphw.studyflow.core.link.StudyFlowWorkspaceId
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopStudyFlowAttentionLedgerStorageTest {
    @Test
    fun fileStorageRestoresAttentionLedgerAcrossControllerProcessBoundaries() {
        val root = Files.createTempDirectory("studyflow-attention-ledger-test")
        try {
            val storage = DesktopStudyFlowAttentionLedgerStorage(root)
            val first = StudyFlowAttentionLedger(storage)
            val occurrence = StudyFlowAttentionOccurrence(
                occurrenceId = StudyFlowAttentionOccurrenceId("planned:desktop:5000"),
                workspaceId = StudyFlowWorkspaceId("workspace-main"),
                category = StudyFlowAttentionCategory.PLANNED_STUDY,
                scheduledAtEpochMillis = 5_000L,
            )
            assertEquals(
                StudyFlowAttentionLedgerAdmission.ACCEPTED,
                first.admitInboundDelivery(
                    occurrence = occurrence,
                    sourceDeviceId = StudyFlowDeviceId("phone"),
                    targetDeviceId = StudyFlowDeviceId("desktop"),
                    sequence = 3,
                    deliveredAtEpochMillis = 5_100L,
                ),
            )

            val restored = StudyFlowAttentionLedger(DesktopStudyFlowAttentionLedgerStorage(root))
            assertEquals(3L, restored.lastSequenceFor(StudyFlowDeviceId("phone")))
            assertTrue(restored.entry(occurrence.occurrenceId) != null)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
