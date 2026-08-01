package com.mikeyphw.studyflow.core.domain

import com.mikeyphw.studyflow.core.model.LocalOnlyProvider
import com.mikeyphw.studyflow.core.model.StudySyncChangeSummary
import com.mikeyphw.studyflow.core.model.StudySyncConflictPolicy
import com.mikeyphw.studyflow.core.model.StudySyncConnectionStatus
import com.mikeyphw.studyflow.core.model.StudySyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyCloudSyncReadinessAuditorTest {
    @Test
    fun readyOnlyWhenFoundationTokenMatchesLocalData() {
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
            pendingUploadCount = 2,
            pendingDownloadCount = 0,
            blockedReason = null,
        )

        assertTrue(StudyCloudSyncReadinessAuditor.readyForProviderSelection(state, summary))
        assertFalse(
            StudyCloudSyncReadinessAuditor.readyForProviderSelection(
                state.copy(lastLocalChangeToken = "old"),
                summary,
            ),
        )
        assertFalse(
            StudyCloudSyncReadinessAuditor.readyForProviderSelection(
                state,
                summary.copy(planCount = 0),
            ),
        )
    }
}
