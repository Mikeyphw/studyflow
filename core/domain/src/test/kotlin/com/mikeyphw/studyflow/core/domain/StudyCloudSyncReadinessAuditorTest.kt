package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.LocalOnlyProvider
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLocalCheckpointReadinessAuditorTest {
    @Test
    fun readyOnlyWhenCheckpointTokenMatchesLocalData() {
        val summary = StudySyncChangeSummary(
            planCount = 2,
            activePlanCount = 1,
            latestUpdatedAtEpochMillis = 42L,
            localChangeToken = "plans=2;active=1;updated=42",
        )
        val state = StudySyncState(
            enabled = true,
            provider = LocalOnlyProvider,
            connectionStatus = StudySyncConnectionStatus.LOCAL_READY,
            conflictPolicy = StudySyncConflictPolicy.MANUAL_REVIEW,
            deviceName = "Phone",
            lastPreparedAtEpochMillis = 84L,
            lastLocalChangeToken = summary.localChangeToken,
            pendingLocalChangeCount = 2,
            blockedReason = null,
        )

        assertTrue(StudyLocalCheckpointReadinessAuditor.localCheckpointReady(state, summary))
        assertFalse(
            StudyLocalCheckpointReadinessAuditor.localCheckpointReady(
                state.copy(lastLocalChangeToken = "old"),
                summary,
            ),
        )
        assertFalse(
            StudyLocalCheckpointReadinessAuditor.localCheckpointReady(
                state,
                summary.copy(planCount = 0),
            ),
        )
    }
}
